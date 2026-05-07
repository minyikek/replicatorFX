package com.replicatorfx;

import com.replicatorfx.config.ConfigLoader;
import com.replicatorfx.config.ConfigWatcher;
import com.replicatorfx.config.SimulatorConfig;
import com.replicatorfx.pipeline.GbmTickerNode;
import com.replicatorfx.pipeline.PublisherNode;
import com.replicatorfx.publisher.AeronPublisher;
import com.replicatorfx.publisher.MessageEncoder;
import com.replicatorfx.simulator.ValueDateCalculator;
import org.agrona.CloseHelper;

import java.nio.file.Path;

public final class Main {

    public static void main(String[] args) throws Exception {
        Path configPath = args.length > 0 ? Path.of(args[0]) : Path.of("config.yaml");

        SimulatorConfig config = ConfigLoader.load(configPath);

        AeronPublisher publisher = new AeronPublisher(config);
        MessageEncoder encoder   = new MessageEncoder(config.global, ValueDateCalculator.spotValueDate());

        GbmTickerNode  tickerNode    = new GbmTickerNode(config);
        PublisherNode  publisherNode = new PublisherNode(encoder, publisher);

        // Wire pipeline: ticker → [Disruptor ring buffer] → publisher
        publisherNode.subscribe1(tickerNode);
        publisherNode.start();

        // Config hot-reload feeds directly into the ticker node
        ConfigWatcher watcher     = new ConfigWatcher(configPath, tickerNode::onConfig);
        Thread        watchThread = new Thread(watcher, "config-watcher");
        watchThread.setDaemon(true);

        Thread tickerThread = new Thread(tickerNode, "rate-ticker");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[main] shutdown requested");
            tickerNode.close();
            CloseHelper.quietClose(publisher);
        }, "shutdown-hook"));

        System.out.printf("[main] pipeline started | channel=%s stream=%d | %d pair(s)%n",
            config.aeron.channel, config.aeron.streamId, config.pairs.size());
        System.out.printf("[main] edit %s to add/remove/tune pairs at runtime%n",
            configPath.toAbsolutePath());

        watchThread.start();
        tickerThread.start();
        tickerThread.join();

        publisher.close();
        watcher.close();
    }
}
