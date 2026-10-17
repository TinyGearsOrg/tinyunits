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
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecimalsTest {

    @Test
    void divideEqualsBigDecimalDivide() {
        Random random = new Random(20261013);
        RoundingMode[] modes = RoundingMode.values();
        int[] precisions = { 0, 1, 2, 3, 7, 16, 34, 100 };
        int exact = 0;
        for (int i = 0; i < 5000; i++) {
            BigDecimal y = divisor(random);
            BigDecimal q = number(random, 25);
            // a third are exact quotients
            BigDecimal x = random.nextInt(3) == 0 ? y.multiply(q) : q;
            MathContext mc = new MathContext(
                    precisions[random.nextInt(precisions.length)],
                    modes[random.nextInt(modes.length)]);
            String expected = outcome(() -> x.divide(y, mc));
            assertEquals(expected, outcome(() -> Decimals.divide(x, y, mc)), x + " / " + y + " " + mc);
            exact += expected.startsWith("java.lang.ArithmeticException") ? 0 : 1;
        }
        assertTrue(exact > 2500, "most cases have a result: " + exact);
    }

    @Test
    void terminatesExactlyWhenBigDecimalDivideIsExact() {
        Random random = new Random(20261016);
        int terminating = 0;
        for (int i = 0; i < 5000; i++) {
            BigDecimal y = divisor(random);
            BigDecimal q = number(random, 25);
            BigDecimal x = random.nextInt(3) == 0 ? y.multiply(q) : q;
            boolean exact = !outcome(() -> x.divide(y)).startsWith("java.lang.ArithmeticException");
            assertEquals(exact, Decimals.terminates(x, y), x + " / " + y);
            terminating += exact ? 1 : 0;
        }
        assertTrue(terminating > 1500 && terminating < 4500, "both outcomes occur: " + terminating);
        assertTrue(Decimals.terminates(BigDecimal.ZERO, new BigDecimal("3")));
        assertTrue(Decimals.terminates(new BigDecimal("6"), new BigDecimal("-3")));
        assertEquals(false, Decimals.terminates(BigDecimal.ONE, new BigDecimal("3")));
    }

    @Test
    void edgeCases() {
        MathContext mc = new MathContext(5);
        BigDecimal[][] cases = {
            { BigDecimal.ZERO, BigDecimal.ONE },
            { new BigDecimal("0E+7"), new BigDecimal("8") },
            { BigDecimal.ONE, BigDecimal.ZERO },
            { new BigDecimal("1000"), new BigDecimal("1") },
            { new BigDecimal("1234000"), new BigDecimal("1E-3") },
            { new BigDecimal("1.50"), new BigDecimal("-2") },
            { new BigDecimal("3"), new BigDecimal(BigInteger.TWO.pow(200)) },
            { new BigDecimal("21"), new BigDecimal("3") },
            // dividends that are exactly 5^(2^m): their factors of 5 decide the trailing zeros
            { new BigDecimal("25"), new BigDecimal("125") },
            { new BigDecimal("625"), new BigDecimal("1953125") },
            { new BigDecimal("1E+2147483647"), new BigDecimal("1E-10") } };
        for (BigDecimal[] c : cases) {
            for (MathContext m : new MathContext[] {
                mc,
                MathContext.UNLIMITED,
                new MathContext(2, RoundingMode.DOWN) }) {
                assertEquals(
                        outcome(() -> c[0].divide(c[1], m)),
                        outcome(() -> Decimals.divide(c[0], c[1], m)),
                        c[0] + " / " + c[1] + " " + m);
            }
        }
    }

    @Test
    void exactQuotientAtHighPrecisionIsFast() {
        BigDecimal q = assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> Decimals.divide(new BigDecimal("1.5"), new BigDecimal("1000"), new MathContext(1_000_000)));
        assertEquals(new BigDecimal("0.0015"), q);
    }

    @Test
    void hugePowersOfTwoAndFiveEqualBigDecimalDivide() {
        Random random = new Random(70_000);
        MathContext[] contexts = {
            MathContext.DECIMAL128,
            MathContext.DECIMAL32,
            new MathContext(1000, RoundingMode.DOWN),
            new MathContext(100_000, RoundingMode.HALF_EVEN),
            MathContext.UNLIMITED };
        long helperNanos = 0;
        for (int n = 0; n < 60; n++) {
            int k = random.nextInt(70_001);
            BigInteger u = switch (n % 3) {
                case 0 -> BigInteger.ONE.shiftLeft(k);
                case 1 -> BigInteger.valueOf(5).pow(k);
                default -> BigInteger.valueOf(7).multiply(BigInteger.valueOf(5).pow(k / 2));
            };
            BigDecimal y = new BigDecimal(u, random.nextInt(41) - 20);
            BigDecimal q = new BigDecimal(new BigInteger(1 + random.nextInt(232_000), random), random.nextInt(41) - 20);
            // half are exact quotients; 10^m and 5^m factors give trailing zeros to strip
            BigDecimal x = (random.nextBoolean() ? y.multiply(q) : q)
                    .multiply(new BigDecimal(BigInteger.TEN.pow(random.nextInt(random.nextBoolean() ? 10 : 30_000))));
            MathContext mc = contexts[random.nextInt(contexts.length)];
            if (mc.getPrecision() == 0 && k > 20_000) {
                mc = MathContext.DECIMAL64;
            }
            MathContext m = mc;
            String expected = outcome(() -> x.divide(y, m));
            long start = System.nanoTime();
            String actual = outcome(() -> Decimals.divide(x, y, m));
            helperNanos += System.nanoTime() - start;
            assertEquals(expected, actual, "2^/5^" + k + " case " + n + " " + m);
        }
        assertTrue(helperNanos < 5_000_000_000L, "helper took " + helperNanos / 1_000_000 + " ms");
    }

    @Test
    void largeDivisorPowersAreFast() {
        BigInteger five = BigInteger.valueOf(5);
        BigDecimal[][] cases = {
            { new BigDecimal(BigInteger.TEN.pow(100_000)), new BigDecimal(BigInteger.ONE.shiftLeft(65_535)) },
            { BigDecimal.ONE, new BigDecimal(five.pow(100_000)) },
            { new BigDecimal(3), new BigDecimal(BigInteger.valueOf(7).multiply(five.pow(28_000))) },
            { BigDecimal.ONE, new BigDecimal(five.pow(28_000)) },
            { new BigDecimal(BigInteger.TEN.pow(115_000)), new BigDecimal(BigInteger.ONE.shiftLeft(65_535)) } };
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            for (BigDecimal[] c : cases) {
                Decimals.divide(c[0], c[1], MathContext.DECIMAL128);
                Decimals.divide(c[0], c[1], new MathContext(100_000));
            }
        });
        for (BigDecimal[] c : cases) {
            assertEquals(
                    outcome(() -> c[0].divide(c[1], MathContext.DECIMAL128)),
                    outcome(() -> Decimals.divide(c[0], c[1], MathContext.DECIMAL128)));
        }
    }

    private static BigDecimal divisor(Random random) {
        BigInteger u = BigInteger.TWO.pow(random.nextInt(12)).multiply(BigInteger.valueOf(5).pow(random.nextInt(12)));
        if (random.nextInt(3) == 0) {
            u = u.multiply(BigInteger.valueOf(1 + random.nextInt(50)));
        }
        BigDecimal d = new BigDecimal(u, random.nextInt(20) - 10);
        return random.nextBoolean() ? d.negate() : d;
    }

    private static BigDecimal number(Random random, int digits) {
        BigInteger u = new BigInteger(1 + random.nextInt(digits * 4), random);
        if (random.nextInt(4) == 0) {
            u = u.multiply(BigInteger.TEN.pow(random.nextInt(15)));
        }
        BigDecimal x = new BigDecimal(u, random.nextInt(40) - 20);
        return random.nextBoolean() ? x.negate() : x;
    }

    /** The result with its scale, or the exception class and message. */
    private static String outcome(java.util.function.Supplier<BigDecimal> division) {
        try {
            BigDecimal r = division.get();
            return r.unscaledValue() + "E" + (-r.scale());
        } catch (ArithmeticException e) {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }
}
