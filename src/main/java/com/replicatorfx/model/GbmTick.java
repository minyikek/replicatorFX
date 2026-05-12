package com.replicatorfx.model;

public record GbmTick(
    String ccyPair,
    String instrument,
    String lpName,
    long   mid,            // fixed-point, scaled by 10^decimalPlaces
    int    decimalPlaces,  // price precision, e.g. 5 for EUR/USD, 3 for USD/JPY
    double spreadPips,
    double pipSize,
    double bidSize,
    double askSize,
    long   rateId,         // monotonically increasing per ccyPair
    long   timestampMs,    // System.currentTimeMillis() at tick generation
    long   processTimeNanos
) {}
