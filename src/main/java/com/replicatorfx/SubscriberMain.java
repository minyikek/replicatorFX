package com.replicatorfx;

import com.replicatorfx.config.ConfigLoader;
import com.replicatorfx.config.SimulatorConfig;
import com.replicatorfx.subscriber.MessageDecoder;
import io.aeron.Aeron;
import io.aeron.Subscription;

import java.nio.file.Path;
import java.util.concurrent.locks.LockSupport;

public final class SubscriberMain {

    private static final int FRAGMENT_LIMIT = 10;

    public static void main(String[] args) throws Exception {
        Path configPath = args.length > 0 ? Path.of(args[0]) : Path.of("config.yaml");

        SimulatorConfig config = ConfigLoader.load(configPath);
        MessageDecoder  decoder = new MessageDecoder();

        try (Aeron        aeron        = Aeron.connect();
             Subscription subscription = aeron.addSubscription(
                 config.aeron.channel, config.aeron.streamId)) {

            System.out.printf("[subscriber] listening on channel=%s stream=%d%n",
                config.aeron.channel, config.aeron.streamId);

            while (!Thread.currentThread().isInterrupted()) {
                int fragments = subscription.poll(decoder::onFragment, FRAGMENT_LIMIT);
                if (fragments == 0) {
                    LockSupport.parkNanos(10_000L); // 10µs idle sleep
                }
            }
        }
    }
}
