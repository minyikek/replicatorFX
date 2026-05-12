package com.replicatorfx.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {

    private static SimulatorConfig config;

    @BeforeAll
    static void load() throws IOException {
        Path yaml = Paths.get("src/main/resources/config.yaml");
        config = ConfigLoader.load(yaml);
    }

    @Test
    void pairsListIsNotEmpty() {
        assertNotNull(config.pairs);
        assertFalse(config.pairs.isEmpty());
    }

    @Test
    void allPairsHaveDecimalPlaces() {
        for (PairConfig pc : config.pairs) {
            assertTrue(pc.decimalPlaces > 0,
                pc.ccyPair + " must have decimalPlaces > 0, got " + pc.decimalPlaces);
        }
    }

    @Test
    void allPairsHavePositivePipSize() {
        for (PairConfig pc : config.pairs) {
            assertTrue(pc.pipSize > 0,
                pc.ccyPair + " must have pipSize > 0, got " + pc.pipSize);
        }
    }

    // ── EUR/USD ──────────────────────────────────────────────────────────────

    @Test
    void eurUsdInitialMidPriceIsLong() {
        PairConfig eur = pair("EUR/USD");
        // 1.08500 × 10^5 = 108500
        assertEquals(108_500L, eur.initialMidPrice);
    }

    @Test
    void eurUsdDecimalPlaces() {
        assertEquals(5, pair("EUR/USD").decimalPlaces);
    }

    @Test
    void eurUsdPipSize() {
        assertEquals(0.00001, pair("EUR/USD").pipSize, 1e-10);
    }

    // ── GBP/USD ──────────────────────────────────────────────────────────────

    @Test
    void gbpUsdInitialMidPriceIsLong() {
        // 1.27000 × 10^5 = 127000
        assertEquals(127_000L, pair("GBP/USD").initialMidPrice);
    }

    @Test
    void gbpUsdDecimalPlaces() {
        assertEquals(5, pair("GBP/USD").decimalPlaces);
    }

    // ── USD/JPY ──────────────────────────────────────────────────────────────

    @Test
    void usdJpyInitialMidPriceIsLong() {
        // 149.500 × 10^3 = 149500
        assertEquals(149_500L, pair("USD/JPY").initialMidPrice);
    }

    @Test
    void usdJpyDecimalPlaces() {
        assertEquals(3, pair("USD/JPY").decimalPlaces);
    }

    @Test
    void usdJpyPipSize() {
        assertEquals(0.01, pair("USD/JPY").pipSize, 1e-10);
    }

    // ── XAU/USD ──────────────────────────────────────────────────────────────

    @Test
    void xauUsdInitialMidPriceIsLong() {
        // 2350.00 × 10^2 = 235000
        assertEquals(235_000L, pair("XAU/USD").initialMidPrice);
    }

    @Test
    void xauUsdDecimalPlaces() {
        assertEquals(2, pair("XAU/USD").decimalPlaces);
    }

    // ── consistency: initialMidPrice encodes the right real price ────────────

    @Test
    void initialMidPriceRealValueMatchesExpected() {
        assertAll(
            () -> assertEquals(1.08500, realPrice("EUR/USD"), 1e-10),
            () -> assertEquals(1.27000, realPrice("GBP/USD"), 1e-10),
            () -> assertEquals(149.500, realPrice("USD/JPY"), 1e-9),
            () -> assertEquals(2350.00, realPrice("XAU/USD"), 1e-9)
        );
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private PairConfig pair(String ccyPair) {
        return config.pairs.stream()
            .filter(p -> p.ccyPair.equals(ccyPair))
            .findFirst()
            .orElseThrow(() -> new AssertionError("pair not found: " + ccyPair));
    }

    private double realPrice(String ccyPair) {
        PairConfig pc = pair(ccyPair);
        return pc.initialMidPrice / Math.pow(10, pc.decimalPlaces);
    }
}
