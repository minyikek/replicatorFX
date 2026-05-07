package io.lightning.conduit.node;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;

public abstract class DisruptorNode1<E1> extends Node1<E1> {

    private final DisruptorComponent<E1> disruptorComponent = new DisruptorComponent<>();

    public DisruptorNode1<E1> bufferSize(int bufferSize) {
        disruptorComponent.setBufferSize(bufferSize);
        return this;
    }

    public DisruptorNode1<E1> waitStrategy(WaitStrategy waitStrategy) {
        disruptorComponent.setWaitStrategy(waitStrategy);
        return this;
    }

    @Override
    public void start() {
        Disruptor<Event<E1>> disruptor = disruptorComponent.getDisruptor();
        disruptor.handleEventsWith((event, l, b) -> {
            if (event.getType() == 1) {
                onEvent1(event.getPayload());
            }
        });

        RingBuffer<Event<E1>> ringBuffer = disruptor.start();
        this.disruptorComponent.setRingBuffer(ringBuffer);
        this.disruptorComponent.register(
                this.dispatcher1
        );
    }
}
