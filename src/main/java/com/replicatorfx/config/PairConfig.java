package com.replicatorfx.config;

public class PairConfig {
    public String ccyPair;
    public String instrument;
    public String lpName;
    public double initialMidPrice;
    public double volatility;
    public double drift;
    public double spreadPips;
    public double pipSize;         // e.g. 0.00001 (majors), 0.01 (JPY/XAU), 0.001 (XAG/USD)
    public double tickIntervalMs;  // supports sub-millisecond, e.g. 0.5 = 500µs (2 ticks/ms)
    public double bidSize;
    public double askSize;
}
