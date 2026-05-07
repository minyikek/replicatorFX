package com.replicatorfx.pipeline;

import com.lmax.disruptor.BusySpinWaitStrategy;
import com.replicatorfx.model.GbmTick;
import com.replicatorfx.publisher.AeronPublisher;
import com.replicatorfx.publisher.MessageEncoder;
import io.lightning.conduit.node.DisruptorNode1;

/**
 * Terminal node in the pipeline: receives a GbmTick from the Disruptor ring
 * buffer, encodes it as an SBE OrderBookSnapshot, and offers it to Aeron.
 *
 * The Disruptor provides a lock-free, pre-allocated queue between GbmTickerNode
 * (producer) and this single-threaded consumer, decoupling tick generation from
 * Aeron back-pressure.
 */
public final class PublisherNode extends DisruptorNode1<GbmTick> {

    private final MessageEncoder encoder;
    private final AeronPublisher publisher;

    public PublisherNode(MessageEncoder encoder, AeronPublisher publisher) {
        this.encoder   = encoder;
        this.publisher = publisher;
        bufferSize(2048).waitStrategy(new BusySpinWaitStrategy());
    }

    @Override
    protected void onEvent1(GbmTick tick) {
        int length = encoder.encode(tick);
        publisher.offer(encoder.buffer(), 0, length);
    }
}
