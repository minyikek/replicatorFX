package com.replicatorfx.pipeline;

import com.replicatorfx.config.PairConfig;
import com.replicatorfx.simulator.GbmPriceModel;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the fixed-point ↔ double conversion logic used in GbmTickerNode.run():
 *   1. midDouble = currentMid / scale        (long → double)
 *   2. newDouble = gbm.nextPrice(midDouble)  (GBM step)
 *   3. newMidFP  = Math.round(newDouble * scale)  (double → long)
 */
class GbmFixedPointRoundTripTest {

    private static long pow10(int n) {
        long r = 1L;
        for (int i = 0; i < n; i++) r *= 10L;
        return r;
    }

    private static PairConfig makeConfig(int decimalPlaces, double volatility) {
        PairConfig pc = new PairConfig();
        pc.ccyPair        = "EUR/USD";
        pc.instrument     = "FXSPOT";
        pc.lpName         = "LP";
        pc.initialMidPrice = 108_500L;
        pc.decimalPlaces  = decimalPlaces;
        pc.volatility     = volatility;
        pc.drift          = 0.0;
        pc.spreadPips     = 2.0;
        pc.pipSize        = 0.00001;
        pc.tickIntervalMs = 100.0;
        pc.bidSize        = 1_000_000.0;
        pc.askSize        = 1_000_000.0;
        return pc;
    }

    // ── long → double conversion ─────────────────────────────────────────────

    @Test
    void fixedPointToDoublePreservesValue() {
        long  midFP       = 108_500L;
        int   dp          = 5;
        long  scale       = pow10(dp);
        double midDouble  = midFP / (double) scale;
        assertEquals(1.08500, midDouble, 1e-10);
    }

    @Test
    void usdJpyFixedPointToDouble() {
        long  midFP      = 149_500L;
        long  scale      = pow10(3);
        double midDouble = midFP / (double) scale;
        assertEquals(149.500, midDouble, 1e-9);
    }

    @Test
    void xauUsdFixedPointToDouble() {
        long   midFP     = 235_000L;
        long   scale     = pow10(2);
        double midDouble = midFP / (double) scale;
        assertEquals(2350.00, midDouble, 1e-9);
    }

    // ── double → long rounding ───────────────────────────────────────────────

    @Test
    void roundTripWithExactValue() {
        int  dp    = 5;
        long scale = pow10(dp);
        // 1.08500 × 100_000 = 108_500.0 exactly
        double d   = 1.08500;
        long   fp  = Math.round(d * scale);
        assertEquals(108_500L, fp);
    }

    @Test
    void roundTripRoundsHalfUp() {
        int  dp    = 5;
        long scale = pow10(dp);
        // 1.085005 × 100_000 = 108_500.5 → rounds to 108_501
        long fp = Math.round(1.085005 * scale);
        assertEquals(108_501L, fp);
    }

    @Test
    void roundTripRoundsDown() {
        int  dp    = 5;
        long scale = pow10(dp);
        // 1.084994 × 100_000 = 108_499.4 → rounds to 108_499
        long fp = Math.round(1.084994 * scale);
        assertEquals(108_499L, fp);
    }

    // ── GBM step produces positive price ────────────────────────────────────

    @RepeatedTest(20)
    void gbmAlwaysProducesPositivePrice() {
        GbmPriceModel gbm    = new GbmPriceModel();
        PairConfig    config = makeConfig(5, 0.10);
        long          scale  = pow10(config.decimalPlaces);

        double midDouble = 108_500L / (double) scale;
        double newDouble = gbm.nextPrice(midDouble, config);
        assertTrue(newDouble > 0, "GBM price must be positive, got " + newDouble);
    }

    @RepeatedTest(20)
    void gbmStepProducesNonZeroFixedPoint() {
        GbmPriceModel gbm    = new GbmPriceModel();
        PairConfig    config = makeConfig(5, 0.10);
        long          scale  = pow10(config.decimalPlaces);

        double midDouble = 108_500L / (double) scale;
        long   newMidFP  = Math.round(gbm.nextPrice(midDouble, config) * scale);
        assertTrue(newMidFP > 0, "fixed-point mid must be positive");
    }

    // ── accumulated drift stays within reasonable bounds ─────────────────────

    @Test
    void hundredGbmStepsStayWithinReasonableBounds() {
        GbmPriceModel gbm    = new GbmPriceModel();
        PairConfig    config = makeConfig(5, 0.10);
        long          scale  = pow10(config.decimalPlaces);

        long currentFP = 108_500L;
        for (int i = 0; i < 100; i++) {
            double d  = currentFP / (double) scale;
            double nd = gbm.nextPrice(d, config);
            currentFP = Math.round(nd * scale);
        }

        double finalPrice = currentFP / (double) scale;
        // After 100 ticks at 100ms each (10 s) with σ=0.10 annualised,
        // price should stay comfortably between 0.50 and 2.00.
        assertTrue(finalPrice > 0.50, "price drifted below 0.50: " + finalPrice);
        assertTrue(finalPrice < 2.00, "price drifted above 2.00: " + finalPrice);
    }

    // ── scale consistency: decimalPlaces matches pipSize ─────────────────────

    @Test
    void onePipEqualsOneScaledUnit_eurUsd() {
        int  dp       = 5;
        long scale    = pow10(dp);
        double pipSize = 0.00001;
        // 1 pip in fixed-point = pipSize × scale
        long pipFP = Math.round(pipSize * scale);
        assertEquals(1L, pipFP, "1 pip should equal 1 fixed-point unit for EUR/USD");
    }

    @Test
    void onePipEqualsCorrectScaledUnits_usdJpy() {
        int    dp      = 3;
        long   scale   = pow10(dp);
        double pipSize = 0.01;
        // 1 pip in fixed-point = 0.01 × 1000 = 10
        long pipFP = Math.round(pipSize * scale);
        assertEquals(10L, pipFP, "1 pip should equal 10 fixed-point units for USD/JPY");
    }
}
