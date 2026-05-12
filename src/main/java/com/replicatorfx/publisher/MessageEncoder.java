package com.replicatorfx.publisher;

import com.replicatorfx.config.GlobalConfig;
import com.replicatorfx.model.GbmTick;
import com.replicatorfx.sbe.BooleanType;
import com.replicatorfx.sbe.MessageHeaderEncoder;
import com.replicatorfx.sbe.OrderBookSnapshotEncoder;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class MessageEncoder {

    private static final int BUFFER_SIZE = 4096;

    private final MessageHeaderEncoder     headerEncoder = new MessageHeaderEncoder();
    private final OrderBookSnapshotEncoder encoder       = new OrderBookSnapshotEncoder();
    private final MutableDirectBuffer      buffer        =
        new UnsafeBuffer(ByteBuffer.allocateDirect(BUFFER_SIZE));

    private final GlobalConfig globalConfig;
    private final String       valueDate;

    public MessageEncoder(GlobalConfig globalConfig, String valueDate) {
        this.globalConfig = globalConfig;
        this.valueDate    = valueDate;
    }

    public int encode(GbmTick tick) {
        long now   = System.nanoTime();
        long scale = pow10(tick.decimalPlaces());

        // Fixed-point half-spread: spreadPips × pipSize expressed in scaled integer units
        long halfSpreadFP = Math.round(tick.spreadPips() * tick.pipSize() * scale / 2.0);
        long bidFP        = tick.mid() - halfSpreadFP;
        long askFP        = tick.mid() + halfSpreadFP;

        // Convert to double, rounded to decimalPlaces, before publishing via Aeron
        double bid = roundToDecimalPlaces(bidFP / (double) scale, tick.decimalPlaces());
        double ask = roundToDecimalPlaces(askFP / (double) scale, tick.decimalPlaces());

        String bidEntryId = UUID.randomUUID().toString();
        String askEntryId = UUID.randomUUID().toString();
        String traceId    = UUID.randomUUID().toString();

        headerEncoder.wrap(buffer, 0)
            .blockLength(OrderBookSnapshotEncoder.BLOCK_LENGTH)
            .templateId(OrderBookSnapshotEncoder.TEMPLATE_ID)
            .schemaId(OrderBookSnapshotEncoder.SCHEMA_ID)
            .version(OrderBookSnapshotEncoder.SCHEMA_VERSION);

        encoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH)
            .seqId(now)
            .sendingTime(now)
            .processTime(tick.processTimeNanos())
            .lastUpdateTimeStamp(now)
            .forward(BooleanType.FALSE)
            .isModifiedByInvoke(BooleanType.FALSE)
            .pipSize(tick.pipSize());

        encoder.putInstrument(toFixedBytes(tick.instrument(), 10), 0);
        encoder.putTenor(toFixedBytes("SP", 10), 0);
        encoder.putCcyPair(toFixedBytes(tick.ccyPair(), 7), 0);

        OrderBookSnapshotEncoder.BidsEncoder bidsEncoder = encoder.bidsCount(1).next();
        bidsEncoder.price(bid)
                   .size(tick.bidSize())
                   .tradeable(BooleanType.TRUE)
                   .valueDate(valueDate)
                   .lpName(tick.lpName())
                   .entryId(bidEntryId);

        OrderBookSnapshotEncoder.AsksEncoder asksEncoder = encoder.asksCount(1).next();
        asksEncoder.price(ask)
                   .size(tick.askSize())
                   .tradeable(BooleanType.TRUE)
                   .valueDate(valueDate)
                   .lpName(tick.lpName())
                   .entryId(askEntryId);

        encoder.fixSession(globalConfig.fixSession)
               .takerCompID(globalConfig.takerCompID)
               .senderCompID(globalConfig.senderCompID)
               .sourceSystem(globalConfig.sourceSystem)
               .valueDate(valueDate)
               .traceId(traceId);

        return MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
    }

    public MutableDirectBuffer buffer() {
        return buffer;
    }

    private static double roundToDecimalPlaces(double value, int decimalPlaces) {
        double scale = pow10(decimalPlaces);
        return Math.round(value * scale) / scale;
    }

    private static long pow10(int n) {
        long result = 1L;
        for (int i = 0; i < n; i++) result *= 10L;
        return result;
    }

    private static byte[] toFixedBytes(String value, int targetLen) {
        byte[] result = new byte[targetLen];
        byte[] src    = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(src, 0, result, 0, Math.min(src.length, targetLen));
        return result;
    }
}
