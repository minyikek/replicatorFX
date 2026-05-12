package com.replicatorfx.config;

public class PairConfig {
    public String  ccyPair;
    public String  instrument;
    public String  lpName;
    public long    initialMidPrice; // fixed-point long, e.g. 108500 = 1.08500 with decimalPlaces=5
    public int     decimalPlaces;   // price precision, e.g. 5 for EUR/USD, 3 for USD/JPY
    public double  volatility;
    public double  drift;
    public double  spreadPips;
    public double  pipSize;         // e.g. 0.00001 (majors), 0.01 (JPY/XAU)
    public double  tickIntervalMs;  // supports sub-millisecond, e.g. 0.5 = 500µs (2 ticks/ms)
    public double  bidSize;
    public double  askSize;
    public boolean enabled = true;  // false → ticker skips this pair
}
