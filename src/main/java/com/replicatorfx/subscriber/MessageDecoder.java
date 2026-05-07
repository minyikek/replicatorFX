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

        String ccyPair  = snapshotDecoder.ccyPair();
        String tenor    = snapshotDecoder.tenor();
        long   seqId    = snapshotDecoder.seqId();
        long   sendTime = snapshotDecoder.sendingTime();

        double bid = Double.NaN, bidSize = Double.NaN;
        double ask = Double.NaN, askSize = Double.NaN;
        String bidLp = "", askLp = "";

        for (var b : snapshotDecoder.bids()) {
            bid     = b.price();
            bidSize = b.size();
            bidLp   = b.lpName();
        }

        for (var a : snapshotDecoder.asks()) {
            ask     = a.price();
            askSize = a.size();
            askLp   = a.lpName();
        }

        System.out.printf(
            "[%s/%s] bid=%.5f (%.0f) ask=%.5f (%.0f) lp=%s seqId=%d latencyNs=%d%n",
            ccyPair, tenor,
            bid, bidSize,
            ask, askSize,
            bidLp,
            seqId,
            System.nanoTime() - sendTime
        );
    }
}
