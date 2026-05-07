package io.lightning.conduit.node;

import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import io.lightning.conduit.dispatcher.Dispatcher;

public class DisruptorComponent<T> {

    private int                    bufferSize  = 1024;
    private Disruptor<Event<T>>    disruptor;
    private RingBuffer<Event<T>>   ringBuffer;
    private WaitStrategy           waitStrategy = new BusySpinWaitStrategy();

    public int                  getBufferSize()                         { return bufferSize; }
    public Disruptor<Event<T>>  getDisruptorInstance()                  { return disruptor; }
    public RingBuffer<Event<T>> getRingBuffer()                         { return ringBuffer; }
    public WaitStrategy         getWaitStrategy()                       { return waitStrategy; }

    public void setBufferSize(int bufferSize)                           { this.bufferSize = bufferSize; }
    public void setDisruptor(Disruptor<Event<T>> disruptor)             { this.disruptor = disruptor; }
    public void setRingBuffer(RingBuffer<Event<T>> ringBuffer)          { this.ringBuffer = ringBuffer; }
    public void setWaitStrategy(WaitStrategy waitStrategy)              { this.waitStrategy = waitStrategy; }

    public Disruptor<Event<T>> getDisruptor() {
        if (disruptor == null) {
            disruptor = new Disruptor<>(
                Event::new,
                bufferSize,
                DaemonThreadFactory.INSTANCE,
                ProducerType.MULTI,
                waitStrategy
            );
        }
        return disruptor;
    }

    @SuppressWarnings("unchecked")
    public void register(Dispatcher<?>... dispatchers) {
        for (int i = 0; i < dispatchers.length; i++) {
            Dispatcher<T> dispatcher = (Dispatcher<T>) dispatchers[i];
            final int index = i + 1;
            dispatcher.register(e -> {
                long sequence        = ringBuffer.next();
                Event<T> event       = ringBuffer.get(sequence);
                event.set(e, index);
                ringBuffer.publish(sequence);
            });
        }
    }
}
