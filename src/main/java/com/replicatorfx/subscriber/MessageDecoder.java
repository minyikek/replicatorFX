package com.replicatorfx.subscriber;

import com.replicatorfx.sbe.BooleanType;
import com.replicatorfx.sbe.MessageHeaderDecoder;
import com.replicatorfx.sbe.OrderBookSnapshotDecoder;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;

public final class MessageDecoder {

    private final MessageHeaderDecoder     headerDecoder  = new MessageHeaderDecoder();
    private final OrderBookSnapshotDecoder snapshotDecoder = new OrderBookSnapshotDecoder();

    public void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
        headerDecoder.wrap(buffer, offset);

        if (headerDecoder.templateId() != OrderBookSnapshotDecoder.TEMPLATE_ID) {
            return;
        }

        snapshotDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);

        String ccyPair   = snapshotDecoder.ccyPair();
        String tenor     = snapshotDecoder.tenor();
        long   seqId     = snapshotDecoder.seqId();
        long   sendTime  = snapshotDecoder.sendingTime();
        double pipSize   = snapshotDecoder.pipSize();
        int    decPlaces = pipSizeToDecimalPlaces(pipSize);

        double bid = Double.NaN, bidSize = Double.NaN;
        double ask = Double.NaN, askSize = Double.NaN;
        String bidLp = "", askLp = "";

        for (var b : snapshotDecoder.bids()) {
            bid     = b.price();
            bidSize = b.size();
            b.valueDate();          // must consume var data in declaration order
            bidLp   = b.lpName();
            b.entryId();
        }

        for (var a : snapshotDecoder.asks()) {
            ask     = a.price();
            askSize = a.size();
            a.valueDate();          // must consume var data in declaration order
            askLp   = a.lpName();
            a.entryId();
        }

        String fmt = "[%s/%s] bid=%." + decPlaces + "f (%.0f) ask=%." + decPlaces + "f (%.0f) lp=%s seqId=%d latencyNs=%d%n";
        System.out.printf(fmt,
            ccyPair, tenor,
            bid, bidSize,
            ask, askSize,
            bidLp,
            seqId,
            System.nanoTime() - sendTime
        );
    }

    // -log10(pipSize) + 1: e.g. 0.00001 → 5dp, 0.01 → 3dp, 0.001 → 4dp
    private static int pipSizeToDecimalPlaces(double pipSize) {
        return (int) Math.round(-Math.log10(pipSize)) + 1;
    }
}
