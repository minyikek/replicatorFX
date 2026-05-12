package com.replicatorfx.pricing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the fixed-point bid/ask arithmetic that MessageEncoder applies before
 * publishing via Aeron.  The helpers here mirror the private statics in MessageEncoder
 * so the logic is tested independently of the SBE/Aeron infrastructure.
 */
class FixedPointPricingTest {

    // ── helpers (mirrors MessageEncoder private statics) ─────────────────────

    private static long pow10(int n) {
        long r = 1L;
        for (int i = 0; i < n; i++) r *= 10L;
        return r;
    }

    private static double roundToDecimalPlaces(double value, int dp) {
        double scale = pow10(dp);
        return Math.round(value * scale) / scale;
    }

    private record Prices(double bid, double ask) {}

    private static Prices calcBidAsk(long midFP, int decimalPlaces,
                                     double spreadPips, double pipSize) {
        long scale        = pow10(decimalPlaces);
        long halfSpreadFP = Math.round(spreadPips * pipSize * scale / 2.0);
        long bidFP        = midFP - halfSpreadFP;
        long askFP        = midFP + halfSpreadFP;
        return new Prices(
            roundToDecimalPlaces(bidFP / (double) scale, decimalPlaces),
            roundToDecimalPlaces(askFP / (double) scale, decimalPlaces)
        );
    }

    // ── pow10 ────────────────────────────────────────────────────────────────

    @Test
    void pow10ReturnsCorrectScale() {
        assertEquals(1L,       pow10(0));
        assertEquals(10L,      pow10(1));
        assertEquals(100L,     pow10(2));
        assertEquals(1_000L,   pow10(3));
        assertEquals(100_000L, pow10(5));
    }

    // ── rounding ─────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "round({0}, {1}dp) = {2}")
    @CsvSource({
        "1.084999, 5, 1.085",
        "1.085001, 5, 1.085",
        "1.085005, 5, 1.08501",
        "149.4999, 3, 149.5",
        "2350.005, 2, 2350.01",
        "2350.004, 2, 2350.0",
    })
    void roundToDecimalPlacesIsCorrect(double input, int dp, double expected) {
        assertEquals(expected, roundToDecimalPlaces(input, dp), 1e-10);
    }

    // ── EUR/USD (5 dp, pipSize 0.00001, spread 2.0 pips) ────────────────────

    @Test
    void eurUsdBidAskAroundMid() {
        // mid = 1.08500, scale = 10^5 = 100_000
        // halfSpreadFP = round(2.0 * 0.00001 * 100_000 / 2) = round(1.0) = 1
        Prices p = calcBidAsk(108_500L, 5, 2.0, 0.00001);

        assertEquals(1.08499, p.bid(), 1e-10, "EUR/USD bid");
        assertEquals(1.08501, p.ask(), 1e-10, "EUR/USD ask");
    }

    @Test
    void eurUsdSpreadEqualsConfiguredPips() {
        Prices p = calcBidAsk(108_500L, 5, 2.0, 0.00001);
        double spreadPips = (p.ask() - p.bid()) / 0.00001;
        assertEquals(2.0, spreadPips, 1e-9, "spread should be 2 pips");
    }

    @Test
    void eurUsdAskIsAboveMid() {
        Prices p = calcBidAsk(108_500L, 5, 2.0, 0.00001);
        assertTrue(p.ask() > 1.08500, "ask must be above mid");
    }

    @Test
    void eurUsdBidIsBelowMid() {
        Prices p = calcBidAsk(108_500L, 5, 2.0, 0.00001);
        assertTrue(p.bid() < 1.08500, "bid must be below mid");
    }

    // ── GBP/USD (5 dp, pipSize 0.00001, spread 2.5 pips) ───────────────────

    @Test
    void gbpUsdOddHalfSpreadRoundsCorrectly() {
        // halfSpreadFP = round(2.5 * 0.00001 * 100_000 / 2) = round(1.25) = 1
        // Java: Math.round(1.25) = 1  (rounds to nearest, half-up → 1 since .25 < .5)
        Prices p = calcBidAsk(127_000L, 5, 2.5, 0.00001);

        assertEquals(1.26999, p.bid(), 1e-10, "GBP/USD bid");
        assertEquals(1.27001, p.ask(), 1e-10, "GBP/USD ask");
    }

    // ── USD/JPY (3 dp, pipSize 0.01, spread 3.0 pips) ───────────────────────

    @Test
    void usdJpyBidAskAroundMid() {
        // mid = 149.500, scale = 10^3 = 1_000
        // halfSpreadFP = round(3.0 * 0.01 * 1_000 / 2) = round(15.0) = 15
        Prices p = calcBidAsk(149_500L, 3, 3.0, 0.01);

        assertEquals(149.485, p.bid(), 1e-10, "USD/JPY bid");
        assertEquals(149.515, p.ask(), 1e-10, "USD/JPY ask");
    }

    @Test
    void usdJpySpreadEqualsConfiguredPips() {
        Prices p = calcBidAsk(149_500L, 3, 3.0, 0.01);
        double spreadPips = (p.ask() - p.bid()) / 0.01;
        assertEquals(3.0, spreadPips, 1e-9, "spread should be 3 pips");
    }

    @Test
    void usdJpyPricesRoundedToThreeDecimalPlaces() {
        Prices p = calcBidAsk(149_500L, 3, 3.0, 0.01);
        // 149.485 × 1000 = 149485 exactly → no residual beyond 3 dp
        assertEquals(149_485L, Math.round(p.bid() * 1_000));
        assertEquals(149_515L, Math.round(p.ask() * 1_000));
    }

    // ── XAU/USD (2 dp, pipSize 0.01, spread 5.0 pips) ───────────────────────

    @Test
    void xauUsdBidAskAroundMid() {
        // mid = 2350.00, scale = 10^2 = 100
        // halfSpreadFP = round(5.0 * 0.01 * 100 / 2) = round(2.5) = 3  (half-up)
        Prices p = calcBidAsk(235_000L, 2, 5.0, 0.01);

        assertEquals(2349.97, p.bid(), 1e-10, "XAU/USD bid");
        assertEquals(2350.03, p.ask(), 1e-10, "XAU/USD ask");
    }

    @Test
    void xauUsdPricesRoundedToTwoDecimalPlaces() {
        Prices p = calcBidAsk(235_000L, 2, 5.0, 0.01);
        assertEquals(234_997L, Math.round(p.bid() * 100));
        assertEquals(235_003L, Math.round(p.ask() * 100));
    }

    // ── invariants ───────────────────────────────────────────────────────────

    @Test
    void bidIsAlwaysLessThanAsk() {
        assertAll(
            () -> { Prices p = calcBidAsk(108_500L, 5, 2.0, 0.00001); assertTrue(p.bid() < p.ask()); },
            () -> { Prices p = calcBidAsk(149_500L, 3, 3.0, 0.01);    assertTrue(p.bid() < p.ask()); },
            () -> { Prices p = calcBidAsk(235_000L, 2, 5.0, 0.01);    assertTrue(p.bid() < p.ask()); }
        );
    }

    @Test
    void zeroPipSpreadProducesBidEqualToAsk() {
        Prices p = calcBidAsk(108_500L, 5, 0.0, 0.00001);
        assertEquals(p.bid(), p.ask());
    }

    @Test
    void outputIsRoundedToConfiguredDecimalPlaces() {
        // Introduce a fractional-pip mid to ensure rounding is applied
        // midFP = 108_5003 is not a valid 5dp long but 108_500 is; use a case
        // where the division could produce extra digits without rounding.
        // mid=108_501, spread=1.0 pip → halfSpreadFP=1, bidFP=108_500, askFP=108_502
        Prices p = calcBidAsk(108_501L, 5, 1.0, 0.00001);
        // Verify no more than 5 decimal places appear
        String bidStr = Double.toString(p.bid());
        String askStr = Double.toString(p.ask());
        int bidDp = bidStr.contains(".") ? bidStr.length() - bidStr.indexOf('.') - 1 : 0;
        int askDp = askStr.contains(".") ? askStr.length() - askStr.indexOf('.') - 1 : 0;
        assertTrue(bidDp <= 5, "bid has > 5 decimal places: " + bidStr);
        assertTrue(askDp <= 5, "ask has > 5 decimal places: " + askStr);
    }
}
