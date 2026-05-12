package com.replicatorfx.model;

import com.replicatorfx.config.PairConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PairStateTest {

    private static PairConfig makeConfig(String ccyPair, long initialMidPrice, int decimalPlaces) {
        PairConfig pc = new PairConfig();
        pc.ccyPair        = ccyPair;
        pc.instrument     = "FXSPOT";
        pc.lpName         = "LP";
        pc.initialMidPrice = initialMidPrice;
        pc.decimalPlaces  = decimalPlaces;
        pc.volatility     = 0.10;
        pc.drift          = 0.0;
        pc.spreadPips     = 2.0;
        pc.pipSize        = 0.00001;
        pc.tickIntervalMs = 100.0;
        pc.bidSize        = 1_000_000.0;
        pc.askSize        = 1_000_000.0;
        return pc;
    }

    @Test
    void currentMidInitialisedFromConfig() {
        PairConfig pc = makeConfig("EUR/USD", 108_500L, 5);
        PairState  s  = new PairState(pc);
        assertEquals(108_500L, s.currentMid);
    }

    @Test
    void currentMidIsStoredAsLong() {
        PairConfig pc    = makeConfig("USD/JPY", 149_500L, 3);
        PairState  state = new PairState(pc);
        // volatile long field — verify the type and value
        assertEquals(149_500L, state.currentMid);
    }

    @Test
    void lastTickNanoIsZeroOnConstruction() {
        PairState s = new PairState(makeConfig("EUR/USD", 108_500L, 5));
        assertEquals(0L, s.lastTickNano);
    }

    @Test
    void configReferenceHoldsInitialConfig() {
        PairConfig pc = makeConfig("GBP/USD", 127_000L, 5);
        PairState  s  = new PairState(pc);
        assertSame(pc, s.config.get());
    }

    @Test
    void currentMidCanBeUpdatedAsFixedPoint() {
        PairState s = new PairState(makeConfig("EUR/USD", 108_500L, 5));
        s.currentMid = 108_502L;  // simulates a GBM tick
        assertEquals(108_502L, s.currentMid);
    }

    @Test
    void currentMidRealValueMatchesExpected() {
        PairConfig pc    = makeConfig("USD/JPY", 149_500L, 3);
        PairState  state = new PairState(pc);
        double     real  = state.currentMid / Math.pow(10, pc.decimalPlaces);
        assertEquals(149.500, real, 1e-9);
    }
}
