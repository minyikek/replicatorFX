package com.replicatorfx.simulator;

import com.replicatorfx.config.PairConfig;

import java.util.random.RandomGeneratorFactory;
import java.util.random.RandomGenerator;

public final class GbmPriceModel {

    // SplittableRandom is faster than ThreadLocalRandom for single-threaded use
    private final RandomGenerator rng =
        RandomGeneratorFactory.of("SplittableRandom").create();

    /**
     * Geometric Brownian Motion tick:
     *   S(t+dt) = S(t) * exp( (μ - σ²/2)*dt  +  σ*√dt*Z )
     * where dt is in years and Z ~ N(0,1).
     */
    public double nextPrice(double currentPrice, PairConfig config) {
        double dtYears  = config.tickIntervalMs / (365.0 * 24.0 * 3_600_000.0);
        double mu       = config.drift;
        double sigma    = config.volatility;
        double z        = rng.nextGaussian();
        double exponent = (mu - 0.5 * sigma * sigma) * dtYears
                        + sigma * Math.sqrt(dtYears) * z;
        return currentPrice * Math.exp(exponent);
    }
}
