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
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Tick source node: runs the GBM spin loop and dispatches a GbmTick for each
 * pair that is due. Also acts as the config-update sink — call onConfig() when
 * the hot-reload watcher fires to add, remove, or retune pairs without stopping
 * the loop.
 */
public final class GbmTickerNode implements EventDispatcher<GbmTick>, Runnable, AutoCloseable {

    private final List<Listener<GbmTick>>                  listeners    = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, PairState>     pairs        = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong>    rateCounters = new ConcurrentHashMap<>();
    private final GbmPriceModel                            gbm          = new GbmPriceModel();
    private volatile boolean                               running      = true;

    public GbmTickerNode(SimulatorConfig config) {
        config.pairs.forEach(pc -> {
            pairs.put(pc.ccyPair, new PairState(pc));
            rateCounters.put(pc.ccyPair, new AtomicLong(0));
        });
    }

    public void onConfig(SimulatorConfig newConfig) {
        for (PairConfig pc : newConfig.pairs) {
            PairState existing = pairs.get(pc.ccyPair);
            if (existing != null) {
                PairConfig old = existing.config.get();
                if (old.initialMidPrice != pc.initialMidPrice) {
                    existing.currentMid = pc.initialMidPrice;
                }
                existing.config.set(pc);
            } else {
                pairs.put(pc.ccyPair, new PairState(pc));
                rateCounters.put(pc.ccyPair, new AtomicLong(0));
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
                    state.lastTickNano = now; // advance always — prevents tick burst on re-enable

                    if (!config.enabled) continue;

                    long   processTime  = System.nanoTime();
                    long   timestampMs  = System.currentTimeMillis();
                    long   scale       = pow10(config.decimalPlaces);
                    // GBM operates in real-valued space; convert fixed-point → double → fixed-point
                    double midDouble   = state.currentMid / (double) scale;
                    double newDouble   = gbm.nextPrice(midDouble, config);
                    long   newMidFP    = Math.round(newDouble * scale);
                    state.currentMid   = newMidFP;

                    long rateId = rateCounters
                        .computeIfAbsent(config.ccyPair, k -> new AtomicLong(0))
                        .incrementAndGet();

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
                        rateId,
                        timestampMs,
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
