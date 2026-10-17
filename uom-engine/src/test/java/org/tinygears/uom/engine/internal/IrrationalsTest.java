/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.engine.internal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.engine.Factor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tinygears.uom.engine.Factor.PI;
import static org.tinygears.uom.engine.Factor.ofLong;

/** Pi, its cache, and the enclosures and correct rounding of irrational factors. */
class IrrationalsTest {

    private static List<BigDecimal> references(int digits) {
        MathContext mc = new MathContext(digits + 20);
        BigDecimal pi = machin(digits + 20);
        return List.of(pi, pi.multiply(pi, mc), BigDecimal.valueOf(250).divide(pi, mc));
    }

    private static void assertPiValues(List<BigDecimal> reference, int p) {
        MathContext mc = new MathContext(p);
        Factor[] factors = { PI, PI.pow(2), ofLong(250).divide(PI) };
        for (int i = 0; i < factors.length; i++) {
            assertEquals(reference.get(i).round(mc), factors[i].decimalValue(mc), factors[i] + " at " + p);
        }
    }

    @Test
    void piCacheBucketsGiveCorrectDigitsInAnyOrder() {
        List<BigDecimal> reference = references(320);
        List<Integer> ascending = List
                .of(1, 54, 63, 64, 65, 100, 127, 128, 129, 191, 192, 193, 250, 255, 256, 257, 300);
        List<Integer> descending = new ArrayList<>(ascending);
        Collections.reverse(descending);
        for (List<Integer> order : List.of(ascending, descending)) {
            Irrationals.clearPiCache();
            for (int p : order) {
                assertPiValues(reference, p);
            }
        }
        // Within one bucket: the larger request first, then the smaller, and the reverse.
        for (int[] pair : new int[][] { { 54, 1 }, { 1, 54 }, { 64, 54 }, { 54, 64 }, { 65, 128 }, { 128, 65 } }) {
            Irrationals.clearPiCache();
            assertPiValues(reference, pair[0]);
            assertPiValues(reference, pair[1]);
        }
    }

    @Test
    void piCacheIsBoundedAndCorrectAfterEviction() {
        List<BigDecimal> reference = references(1120);
        Irrationals.clearPiCache();
        // Twice over 18 buckets, more than the cache holds: entries are evicted and computed again.
        for (int round = 0; round < 2; round++) {
            for (int p = 1; p <= 1100; p += 64) {
                assertPiValues(reference, p);
                // The 64-digit bucket stays: it is used again before each new bucket.
                assertPiValues(reference, 20);
                assertTrue(
                        Irrationals.piCacheSize() <= Irrationals.PI_CACHE_SIZE,
                        "cache size " + Irrationals.piCacheSize());
                assertTrue(Irrationals.piCacheHolds(64), "64-digit bucket evicted at " + p);
            }
        }
        assertEquals(Irrationals.PI_CACHE_SIZE, Irrationals.piCacheSize());
    }

    /** Reference pi by Machin's formula, 16 atan(1/5) - 4 atan(1/239). */
    private static BigDecimal machin(int digits) {
        MathContext mc = new MathContext(digits + 10);
        return atanInverse(5, mc).multiply(BigDecimal.valueOf(16))
                .subtract(atanInverse(239, mc).multiply(BigDecimal.valueOf(4))).round(new MathContext(digits));
    }

    private static BigDecimal atanInverse(int x, MathContext mc) {
        BigDecimal x2 = BigDecimal.valueOf((long) x * x);
        BigDecimal eps = BigDecimal.ONE.movePointLeft(mc.getPrecision());
        BigDecimal power = BigDecimal.ONE.divide(BigDecimal.valueOf(x), mc);
        BigDecimal sum = power;
        for (int k = 1;; k++) {
            power = power.divide(x2, mc);
            BigDecimal term = power.divide(BigDecimal.valueOf(2L * k + 1), mc);
            if (term.compareTo(eps) < 0) {
                return sum;
            }
            sum = k % 2 == 0 ? sum.add(term, mc) : sum.subtract(term, mc);
        }
    }

    @Test
    void piMatchesMachinsFormula() {
        BigDecimal reference = machin(3020);
        for (int p : new int[] { 1, 2, 15, 63, 64, 65, 500, 1000, 3000 }) {
            assertEquals(reference.round(new MathContext(p)), PI.decimalValue(new MathContext(p)), "digits " + p);
        }
    }

    @Test
    void piToTheMaximumContextPrecisionIsFast() {
        // Precision 100000 plus guard digits, as a decimal quantity at the bound of its context needs; 2.5 s alone and
        // 10 to 13 s under load on JDK 21, 0.6 s on JDK 25: the 120 s bound only catches quadratic or worse.
        BigDecimal pi = assertTimeoutPreemptively(
                Duration.ofSeconds(120),
                () -> PI.decimalValue(new MathContext(200_100)));
        assertEquals(200_100, pi.precision());
        assertEquals(machin(3000), pi.round(new MathContext(3000)));
    }

    /** sqrt(2) truncated to {@code digits} digits after the point, exactly. */
    private static BigDecimal sqrt2(int digits) {
        return new BigDecimal(BigInteger.TWO.multiply(BigInteger.TEN.pow(2 * digits)).sqrt(), digits);
    }

    @Test
    void nearTiesRoundCorrectly() {
        // Each of these rounded wrongly with five fixed guard digits: the digits after the last kept one start 4999 or 5000.
        MathContext halfUp = new MathContext(2706, RoundingMode.HALF_UP);
        assertEquals(sqrt2(2800).round(halfUp), ofLong(2).root(2).decimalValue(halfUp));
        BigDecimal pi = machin(900);
        for (int p : new int[] { 761, 762, 763 }) {
            MathContext mc = new MathContext(p, RoundingMode.HALF_UP);
            assertEquals(pi.round(mc), PI.decimalValue(mc), "pi at " + p);
            assertEquals(
                    pi.divide(BigDecimal.valueOf(180), new MathContext(900)).round(mc),
                    PI.divide(ofLong(180)).decimalValue(mc),
                    "degree at " + p);
        }
    }

    @Test
    void everyRoundingModeRoundsTheExactValue() {
        BigDecimal pi = machin(420);
        BigDecimal root = sqrt2(420);
        BigDecimal negativeRoot = root.negate();
        for (RoundingMode mode : RoundingMode.values()) {
            if (mode == RoundingMode.UNNECESSARY) {
                continue;
            }
            for (int p = 1; p <= 400; p++) {
                MathContext mc = new MathContext(p, mode);
                assertEquals(pi.round(mc), PI.decimalValue(mc), mode + " pi at " + p);
                assertEquals(root.round(mc), ofLong(2).root(2).decimalValue(mc), mode + " sqrt 2 at " + p);
                assertEquals(
                        negativeRoot.round(mc),
                        Factor.of(BigInteger.valueOf(-2), BigInteger.ONE, 0, 2).decimalValue(mc),
                        mode + " -sqrt 2 at " + p);
            }
        }
    }

    @Test
    void anIrrationalValueIsNeverExact() {
        assertEquals(
                "Rounding necessary",
                assertThrows(
                        ArithmeticException.class,
                        () -> PI.decimalValue(new MathContext(5, RoundingMode.UNNECESSARY))).getMessage());
        assertEquals(
                "Rounding necessary",
                assertThrows(
                        ArithmeticException.class,
                        () -> ofLong(2).root(2).decimalValue(new MathContext(1000, RoundingMode.UNNECESSARY)))
                        .getMessage());
    }

    @Test
    void enclosuresHoldTheValueAndNarrowWithTheDigits() {
        MathContext mc = new MathContext(300);
        BigDecimal pi = machin(300);
        BigDecimal root = sqrt2(300);
        Object[][] cases = {
            { PI, pi },
            { PI.pow(-3), BigDecimal.ONE.divide(pi.pow(3, mc), mc) },
            { ofLong(2).root(2), root },
            { Factor.of(BigInteger.valueOf(-2), BigInteger.ONE, 0, 2), root.negate() },
            { PI.multiply(ofLong(2)).root(2), pi.multiply(BigDecimal.valueOf(2)).sqrt(mc) } };
        for (Object[] c : cases) {
            Factor f = (Factor) c[0];
            BigDecimal v = (BigDecimal) c[1];
            for (int digits : new int[] { 1, 20, 100, 250 }) {
                Interval x = Irrationals.enclose(f, digits);
                assertTrue(x.lo().compareTo(v) <= 0 && v.compareTo(x.hi()) <= 0, f + " at " + digits + ": " + x);
                BigDecimal width = x.hi().subtract(x.lo()).divide(v.abs(), MathContext.DECIMAL64);
                assertTrue(width.compareTo(BigDecimal.ONE.scaleByPowerOfTen(-digits)) < 0, f + " width " + width);
            }
        }
        // The fifth root of 1/3, checked exactly: 3 lo^5 <= 1 <= 3 hi^5.
        Interval x = Irrationals.enclose(Factor.of(1, 3).root(5), 50);
        BigDecimal three = BigDecimal.valueOf(3);
        assertTrue(x.lo().pow(5).multiply(three).compareTo(BigDecimal.ONE) <= 0, x.toString());
        assertTrue(x.hi().pow(5).multiply(three).compareTo(BigDecimal.ONE) >= 0, x.toString());
        // A terminating rational factor is a point.
        assertEquals(Interval.of(new BigDecimal("0.125")), Irrationals.enclose(Factor.of(1, 8), 5));
    }
}
