package com.replicatorfx.model;

public record GbmTick(
    String ccyPair,
    String instrument,
    String lpName,
    double mid,
    double spreadPips,
    double pipSize,
    double bidSize,
    double askSize,
    long   processTimeNanos
) {}
