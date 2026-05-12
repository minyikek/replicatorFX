package com.replicatorfx.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.replicatorfx.config.PairConfig;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PairConfigDto {
    public String ccyPair;
    public String instrument;
    public String lpName;
    public double initialMidPrice; // human-readable decimal, e.g. 1.08500 (not fixed-point long)
    public int    decimalPlaces;
    public double volatility;
    public double drift;
    public double spreadPips;
    public double pipSize;
    public double tickIntervalMs;
    public double bidSize;
    public double askSize;

    public static PairConfigDto from(PairConfig pc) {
        PairConfigDto d    = new PairConfigDto();
        d.ccyPair          = pc.ccyPair;
        d.instrument       = pc.instrument;
        d.lpName           = pc.lpName;
        d.decimalPlaces    = pc.decimalPlaces;
        d.initialMidPrice  = pc.initialMidPrice / (double) pow10(pc.decimalPlaces);
        d.volatility       = pc.volatility;
        d.drift            = pc.drift;
        d.spreadPips       = pc.spreadPips;
        d.pipSize          = pc.pipSize;
        d.tickIntervalMs   = pc.tickIntervalMs;
        d.bidSize          = pc.bidSize;
        d.askSize          = pc.askSize;
        return d;
    }

    public PairConfig toPairConfig() {
        PairConfig pc      = new PairConfig();
        pc.ccyPair         = this.ccyPair;
        pc.instrument      = this.instrument == null || this.instrument.isBlank() ? "SPOT" : this.instrument;
        pc.lpName          = this.lpName;
        pc.decimalPlaces   = this.decimalPlaces;
        pc.initialMidPrice = Math.round(this.initialMidPrice * pow10(this.decimalPlaces));
        pc.volatility      = this.volatility;
        pc.drift           = this.drift;
        pc.spreadPips      = this.spreadPips;
        pc.pipSize         = this.pipSize;
        pc.tickIntervalMs  = this.tickIntervalMs;
        pc.bidSize         = this.bidSize;
        pc.askSize         = this.askSize;
        return pc;
    }

    private static long pow10(int n) {
        long r = 1L;
        for (int i = 0; i < n; i++) r *= 10;
        return r;
    }
}
