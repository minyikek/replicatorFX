package com.replicatorfx.pipeline;

import com.replicatorfx.config.PairConfig;
import com.replicatorfx.config.SimulatorConfig;
import com.replicatorfx.model.GbmTick;
import com.replicatorfx.model.PairState;
import com.replicatorfx.simulator.GbmPriceModel;
import io.lightning.conduit.dispatcher.EventDispatcher;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Tick source node: runs the GBM spin loop and dispatches a GbmTick for each
 * pair that is due. Also acts as the config-update sink — call onConfig() when
 * the hot-reload watcher fires to add, remove, or retune pairs without stopping
 * the loop.
 */
public final class GbmTickerNode implements EventDispatcher<GbmTick>, Runnable, AutoCloseable {

    private final List<Listener<GbmTick>>             listeners = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, PairState> pairs     = new ConcurrentHashMap<>();
    private final GbmPriceModel                        gbm       = new GbmPriceModel();
    private volatile boolean                           running   = true;

    public GbmTickerNode(SimulatorConfig config) {
        config.pairs.forEach(pc -> pairs.put(pc.ccyPair, new PairState(pc)));
    }

    public void onConfig(SimulatorConfig newConfig) {
        for (PairConfig pc : newConfig.pairs) {
            PairState existing = pairs.get(pc.ccyPair);
            if (existing != null) {
                existing.config.set(pc);
            } else {
                pairs.put(pc.ccyPair, new PairState(pc));
                System.out.println("[ticker] added pair: " + pc.ccyPair);
            }
        }
        Set<String> active = newConfig.pairs.stream()
            .map(p -> p.ccyPair)
            .collect(Collectors.toSet());
        pairs.keySet().removeIf(key -> {
            boolean removed = !active.contains(key);
            if (removed) System.out.println("[ticker] removed pair: " + key);
            return removed;
        });
    }

    @Override
    public void run() {
        while (running && !Thread.currentThread().isInterrupted()) {
            long now = System.nanoTime();

            for (PairState state : pairs.values()) {
                PairConfig config     = state.config.get();
                long       intervalNs = (long) (config.tickIntervalMs * 1_000_000.0);

                if (now - state.lastTickNano >= intervalNs) {
                    long   processTime = System.nanoTime();
                    long   scale       = pow10(config.decimalPlaces);
                    // GBM operates in real-valued space; convert fixed-point → double → fixed-point
                    double midDouble   = state.currentMid / (double) scale;
                    double newDouble   = gbm.nextPrice(midDouble, config);
                    long   newMidFP    = Math.round(newDouble * scale);
                    state.currentMid   = newMidFP;
                    state.lastTickNano = now;

                    dispatch(new GbmTick(
                        config.ccyPair,
                        config.instrument,
                        config.lpName,
                        newMidFP,
                        config.decimalPlaces,
                        config.spreadPips,
                        config.pipSize,
                        config.bidSize,
                        config.askSize,
                        processTime
                    ));
                }
            }

            Thread.onSpinWait();
        }
    }

    @Override
    public List<Listener<GbmTick>> getListeners() {
        return listeners;
    }

    @Override
    public void close() {
        running = false;
    }

    private static long pow10(int n) {
        long result = 1L;
        for (int i = 0; i < n; i++) result *= 10L;
        return result;
    }
}
