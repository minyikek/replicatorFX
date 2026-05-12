package com.replicatorfx.model;

import com.replicatorfx.config.PairConfig;

import java.util.concurrent.atomic.AtomicReference;

public final class PairState {

    public final AtomicReference<PairConfig> config;
    public volatile long currentMid;    // fixed-point, scaled by 10^decimalPlaces
    public volatile long lastTickNano;

    public PairState(PairConfig config) {
        this.config       = new AtomicReference<>(config);
        this.currentMid   = config.initialMidPrice;
        this.lastTickNano = 0L;
    }
}
