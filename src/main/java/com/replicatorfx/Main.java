package com.replicatorfx;

import com.replicatorfx.config.ConfigLoader;
import com.replicatorfx.config.ConfigWatcher;
import com.replicatorfx.config.SimulatorConfig;
import com.replicatorfx.http.ConfigHttpServer;
import com.replicatorfx.pipeline.GbmTickerNode;
import com.replicatorfx.pipeline.PublisherNode;
import com.replicatorfx.publisher.AeronPublisher;
import com.replicatorfx.publisher.MessageEncoder;
import com.replicatorfx.simulator.ValueDateCalculator;
import org.agrona.CloseHelper;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public final class Main {

    public static void main(String[] args) throws Exception {
        Path configPath = args.length > 0 ? Path.of(args[0]) : Path.of("config.yaml");

        SimulatorConfig config = ConfigLoader.load(configPath);

        GbmTickerNode tickerNode = new GbmTickerNode(config);

        // HTTP config UI starts immediately — does not depend on Aeron being ready
        ConfigHttpServer httpServer = new ConfigHttpServer(configPath, tickerNode);

        // Config hot-reload feeds directly into the ticker node
        ConfigWatcher watcher     = new ConfigWatcher(configPath, tickerNode::onConfig);
        Thread        watchThread = new Thread(watcher, "config-watcher");
        watchThread.setDaemon(true);

        Thread tickerThread = new Thread(tickerNode, "rate-ticker");

        // AeronPublisher blocks in awaitConnected() until a subscriber joins.
        // Run it on a daemon thread so the UI and ticker are not held up.
        AtomicReference<AeronPublisher> pubRef = new AtomicReference<>();
        Thread aeronInit = new Thread(() -> {
            try {
                AeronPublisher publisher = new AeronPublisher(config);
                pubRef.set(publisher);
                MessageEncoder encoder    = new MessageEncoder(config.global, ValueDateCalculator.spotValueDate());
                PublisherNode  pubNode    = new PublisherNode(encoder, publisher);
                pubNode.subscribe1(tickerNode);
                pubNode.start();
                System.out.println("[main] Aeron pipeline ready");
            } catch (Exception e) {
                System.err.println("[main] Aeron init failed: " + e.getMessage());
            }
        }, "aeron-init");
        aeronInit.setDaemon(true);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[main] shutdown requested");
            tickerNode.close();
            AeronPublisher pub = pubRef.get();
            if (pub != null) CloseHelper.quietClose(pub);
            httpServer.close();
        }, "shutdown-hook"));

        System.out.printf("[main] pipeline started | channel=%s stream=%d | %d pair(s)%n",
            config.aeron.channel, config.aeron.streamId, config.pairs.size());

        watchThread.start();
        aeronInit.start();
        tickerThread.start();
        tickerThread.join();

        watcher.close();
        httpServer.close();
    }
}
