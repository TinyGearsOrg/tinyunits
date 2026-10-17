/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tinygears.uom.core.Factor.ONE;
import static org.tinygears.uom.core.Factor.PI;
import static org.tinygears.uom.core.Factor.ZERO;
import static org.tinygears.uom.core.Factor.decimal;
import static org.tinygears.uom.core.Factor.of;
import static org.tinygears.uom.core.Factor.ofLong;

class FactorTest {

    @Test
    void normalizes() {
        assertEquals(of(1, 2), of(2, 4));
        assertEquals(of(-1, 2), of(1, -2));
    }

    @Test
    void decimalLiteralIsExact() {
        assertEquals(of(254, 10000), decimal("0.0254"));
        assertEquals(ofLong(1000), decimal("1e3"));
        assertEquals(of(-3, 2), decimal("-1.5"));
    }

    @Test
    void arithmetic() {
        assertEquals(of(1, 4), of(1, 3).multiply(of(3, 4)));
        assertEquals(of(1, 2), of(1, 3).add(of(1, 6)));
        assertEquals(ONE, ofLong(1).divide(ofLong(3)).multiply(ofLong(3)));
    }

    @Test
    void piIsTracked() {
        assertEquals(ONE, PI.multiply(PI.pow(-1)));
        assertEquals(Math.PI / 180, PI.divide(ofLong(180)).doubleValue(), 1e-15);
        assertThrows(ArithmeticException.class, () -> PI.add(ONE));
    }

    @Test
    void piToFiftyDigits() {
        assertEquals(
                new BigDecimal("3.1415926535897932384626433832795028841971693993751"),
                PI.decimalValue(new MathContext(50)));
    }

    @Test
    void rootsNormalize() {
        Factor sqrt2 = ofLong(2).root(2);
        assertEquals(of(1, 2), of(1, 4).root(2));
        assertFalse(sqrt2.isExact());
        assertEquals(ofLong(2), sqrt2.pow(2));
        assertEquals(ofLong(2), sqrt2.multiply(sqrt2));
        assertEquals(1.4142135623730951, sqrt2.doubleValue());
        assertEquals(new BigDecimal("1.41421356237309504880168872421"), sqrt2.decimalValue(new MathContext(30)));
        assertThrows(ArithmeticException.class, () -> sqrt2.add(ONE));
        assertThrows(ArithmeticException.class, () -> ofLong(-4).root(2));
    }

    @Test
    void doubleValueRoundsOnce() {
        assertEquals(9.007199254740994E15, decimal("9007199254740993.0000000001").doubleValue());
        assertEquals(-9.007199254740994E15, decimal("-9007199254740993.0000000001").doubleValue());
        assertEquals(1.0 / 3.0, of(1, 3).doubleValue());
        assertEquals(Double.MIN_VALUE, of(BigInteger.ONE, BigInteger.TWO.pow(1074)).doubleValue());
    }

    @Test
    void thePartsFactoryTakesAnyPartsAndNormalizesThem() {
        assertEquals(Factor.ofLong(2), Factor.of(BigInteger.valueOf(4), BigInteger.ONE, 0, 2));
        assertEquals(PI, Factor.of(BigInteger.ONE, BigInteger.ONE, 2, 2));
        // The sign stays outside the root, and the denominator is made positive.
        assertEquals(Factor.ofLong(-2), Factor.of(BigInteger.valueOf(-4), BigInteger.ONE, 0, 2));
        assertEquals(Factor.of(-3, 2), Factor.of(BigInteger.valueOf(6), BigInteger.valueOf(-4), 0, 1));
        assertEquals(
                "root index must be positive: 0",
                assertThrows(IllegalArgumentException.class, () -> Factor.of(BigInteger.ONE, BigInteger.ONE, 0, 0))
                        .getMessage());
        assertEquals(
                "division by zero",
                assertThrows(ArithmeticException.class, () -> Factor.of(BigInteger.ONE, BigInteger.ZERO, 0, 1))
                        .getMessage());
        assertEquals(
                "factor too large",
                assertThrows(
                        ArithmeticException.class,
                        () -> Factor.of(BigInteger.TWO.pow(70_000), BigInteger.ONE, 0, 1)).getMessage());
    }

    @Test
    void theSignOfARootIsWrittenOutsideIt() {
        assertEquals("(2)^(1/2)", ofLong(2).root(2).toString());
        assertEquals("-(2)^(1/2)", ofLong(2).root(2).negate().toString());
        assertEquals("-(1/3*pi^1)^(1/3)", PI.divide(ofLong(3)).root(3).negate().toString());
        assertEquals("-3/2", of(-3, 2).toString());
    }

    @Test
    void hugeDecimalsAreRejectedFromTheirScale() {
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            for (String v : new String[] { "1e1000000", "1e-1000000" }) {
                assertEquals(
                        "factor too large",
                        assertThrows(ArithmeticException.class, () -> decimal(new BigDecimal(v))).getMessage());
                assertThrows(ArithmeticException.class, () -> BaseUnits.METRE.multiply(new BigDecimal(v)));
            }
        });
        assertEquals(decimal("0.0254"), decimal(new BigDecimal("0.0254")));
        assertEquals(ofLong(1200), decimal(new BigDecimal("1.2e3")));
    }

    @Test
    void theSizeLimitAppliesToTheNormalForm() {
        // x: 40000 bits and not a perfect square, so sqrt(x) stays a root
        BigInteger x = BigInteger.TWO.pow(40_000).add(BigInteger.valueOf(3));
        Factor root = Factor.of(x, BigInteger.ONE, 0, 2);
        assertEquals(Factor.of(x, BigInteger.ONE, 0, 1), root.multiply(root));
        assertEquals(Factor.of(x, BigInteger.ONE, 0, 1), Factor.of(x.multiply(x), BigInteger.ONE, 0, 2));
        assertEquals(root, Factor.of(x.multiply(x).multiply(x), BigInteger.ONE, 0, 6));
        // beyond 2 * r * 65536 bits nothing is reduced
        assertEquals(
                "factor too large",
                assertThrows(
                        ArithmeticException.class,
                        () -> Factor.of(BigInteger.TWO.pow(4 * 65_536 + 1), BigInteger.ONE, 0, 2)).getMessage());
    }

    @Test
    void powAndRootThrowWhereTheJavadocSays() {
        assertEquals(
                "exponent 1001 exceeds 1000",
                assertThrows(ArithmeticException.class, () -> Factor.ofLong(2).pow(1001)).getMessage());
        assertEquals(
                "exponent -1001 exceeds 1000",
                assertThrows(ArithmeticException.class, () -> Factor.ofLong(2).root(-1001)).getMessage());
        assertEquals(
                "zeroth root",
                assertThrows(ArithmeticException.class, () -> Factor.ofLong(2).root(0)).getMessage());
        assertEquals(
                "even root of a negative number",
                assertThrows(ArithmeticException.class, () -> Factor.ofLong(-4).root(2)).getMessage());
        assertEquals(Factor.ofLong(-2), Factor.ofLong(-8).root(3));
        assertEquals("division by zero", assertThrows(ArithmeticException.class, () -> ZERO.pow(-1)).getMessage());
        assertEquals("division by zero", assertThrows(ArithmeticException.class, () -> ZERO.root(-1)).getMessage());
        assertEquals(ONE, ZERO.pow(0));
        assertEquals(Factor.of(1, 2), Factor.ofLong(4).root(-2));
    }

    @Test
    void zeroAndLimits() {
        assertThrows(ArithmeticException.class, () -> ONE.divide(ZERO));
        assertThrows(ArithmeticException.class, () -> ZERO.pow(-1));
        assertEquals(ONE, ofLong(7).pow(0));
        assertThrows(ArithmeticException.class, () -> ofLong(10).pow(100_000));
        assertThrows(ArithmeticException.class, () -> ofLong(2).root(100_000));
    }

    /** Reference values of pi, pi^2 and 250/pi, computed from Machin's pi with ample guard digits. */
    @Test
    void sizeIsCapped() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertThrows(ArithmeticException.class, () -> ofLong(10).pow(1000).pow(1000));
            Factor big = ofLong(2).pow(1000).pow(65);
            assertEquals(65_001, big.numerator().bitLength());
            assertThrows(ArithmeticException.class, () -> ofLong(2).pow(1000).pow(66));
            assertThrows(ArithmeticException.class, () -> ONE.divide(big).pow(2));
            assertThrows(ArithmeticException.class, () -> big.root(2).multiply(ofLong(3).root(3)));
            assertThrows(ArithmeticException.class, () -> of(BigInteger.ONE, BigInteger.TWO.pow(70_000)));
            assertThrows(ArithmeticException.class, () -> decimal("1e100000000"));
            assertEquals(1001, ofLong(2).pow(-1000).denominator().bitLength());
            assertEquals(PI.pow(1000), PI.pow(500).multiply(PI.pow(500)));
            assertEquals(BigInteger.TEN.pow(1000), ofLong(10).pow(1000).numerator());
            assertEquals(BigInteger.TEN.pow(19_000), decimal("1e19000").numerator());
        });
    }

    @Test
    void parts() {
        assertParts(of(6, 4), 3, 2, 0, 1);
        assertParts(of(-6, 4), -3, 2, 0, 1);
        assertParts(PI.divide(ofLong(180)), 1, 180, 1, 1);
        assertParts(ofLong(2).root(2), 2, 1, 0, 2);
        assertParts(ofLong(-2).multiply(ofLong(2).root(2)), -8, 1, 0, 2);
    }

    private static void assertParts(Factor f, long n, long d, int k, int r) {
        assertEquals(BigInteger.valueOf(n), f.numerator());
        assertEquals(BigInteger.valueOf(d), f.denominator());
        assertEquals(k, f.piExponent());
        assertEquals(r, f.rootIndex());
    }

    @Test
    void partsReconstructTheFactor() {
        for (Factor f : new Factor[] {
            of(6, 4),
            of(-6, 4),
            PI.divide(ofLong(180)),
            ofLong(2).root(2),
            ofLong(-2).multiply(ofLong(2).root(2)),
            PI.pow(-2).multiply(of(3, 7)).root(3),
            ZERO,
            ONE }) {
            Factor magnitude = of(f.numerator().abs(), f.denominator()).multiply(PI.pow(f.piExponent()))
                    .root(f.rootIndex());
            assertEquals(f, f.signum() < 0 ? magnitude.negate() : magnitude);
        }
    }

    @Test
    void canonicalForm() {
        assertCanonical(ofLong(8).root(2), ofLong(2).multiply(ofLong(2).root(2)));
        assertCanonical(ofLong(32).root(6), ofLong(2).root(2).multiply(ofLong(2).root(3)));
        assertCanonical(PI, PI.root(2).multiply(PI.root(2)));
        assertCanonical(ofLong(2).root(6), ofLong(2).root(2).root(3));
        assertCanonical(ofLong(-2), ofLong(-8).root(3));
        assertEquals(6, ofLong(3).root(2).multiply(ofLong(5).root(3)).rootIndex());
        assertEquals(12, ofLong(3).root(4).multiply(ofLong(5).root(6)).rootIndex());
        assertThrows(ArithmeticException.class, () -> ofLong(-8).root(2));
        assertThrows(ArithmeticException.class, () -> ofLong(-2).root(4));
    }

    private static void assertCanonical(Factor expected, Factor actual) {
        assertEquals(expected, actual);
        assertEquals(expected.hashCode(), actual.hashCode());
    }

    @Test
    void partsRoundTrip() {
        Factor huge = Factor.of(BigInteger.ONE, BigInteger.ONE, 1500, 1);
        for (Factor f : new Factor[] {
            ZERO,
            of(6, 4),
            of(-3, 7),
            PI.divide(ofLong(180)),
            ofLong(2).root(2),
            PI.root(3),
            ofLong(-8).root(3),
            huge }) {
            Factor g = Factor.of(f.numerator(), f.denominator(), f.piExponent(), f.rootIndex());
            assertEquals(f, g);
            assertEquals(f.hashCode(), g.hashCode());
        }
        assertEquals(1500, huge.piExponent());
    }

    @Test
    void partsBeyondPowAndRootLimits() {
        Factor pi = Factor.of(BigInteger.ONE, BigInteger.ONE, 1500, 1);
        Factor root = Factor.of(BigInteger.valueOf(3), BigInteger.ONE, 0, 2000);
        assertEquals(2000, root.rootIndex());
        assertTrue(!pi.equals(root));
    }

    @Test
    void partsRejectBadInput() {
        assertThrows(IllegalArgumentException.class, () -> Factor.of(BigInteger.ONE, BigInteger.ONE, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> Factor.of(BigInteger.ONE, BigInteger.ONE, 0, -1));
        assertThrows(ArithmeticException.class, () -> Factor.of(BigInteger.ONE, BigInteger.ZERO, 0, 1));
        assertThrows(
                ArithmeticException.class,
                () -> Factor.of(BigInteger.ONE.shiftLeft(70_000), BigInteger.ONE, 0, 1));
    }

    @Test
    void hugeRootIndexReducesQuicklyWithoutOverflow() {
        BigInteger one = BigInteger.ONE;
        BigInteger three = BigInteger.valueOf(3);
        Factor f = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> Factor.of(three, one, 1, Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, f.rootIndex());
        assertEquals(1, f.piExponent());
        assertEquals(ONE, Factor.of(one, one, 0, Integer.MAX_VALUE));
        assertEquals(PI, Factor.of(one, one, Integer.MAX_VALUE, Integer.MAX_VALUE));
        Factor g = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> Factor.of(three, one, 0, 1 << 30));
        assertEquals(1 << 30, g.rootIndex());
        Factor h = assertTimeoutPreemptively(
                Duration.ofSeconds(2),
                () -> Factor.of(BigInteger.valueOf(4), one, 0, 2_147_483_646));
        assertEquals(1_073_741_823, h.rootIndex());
        assertEquals(BigInteger.TWO, h.numerator());
    }

    @Test
    void rootIndexReductionMatchesReference() {
        java.util.Random rnd = new java.util.Random(42);
        for (int i = 0; i < 2000; i++) {
            BigInteger n = BigInteger.valueOf(rnd.nextInt(2000) - 1000);
            BigInteger d = BigInteger.valueOf(rnd.nextInt(50) + 1);
            int k = rnd.nextInt(61) - 30;
            int r = rnd.nextInt(5000) + 1;
            if (rnd.nextBoolean()) {
                r = (rnd.nextInt(12) + 1) * (rnd.nextInt(12) + 1) * (rnd.nextInt(12) + 1);
                n = BigInteger.valueOf(rnd.nextInt(6) + 1).pow(rnd.nextInt(8) + 1)
                        .multiply(BigInteger.valueOf(rnd.nextBoolean() ? -1 : 1));
                k = r * (rnd.nextInt(3) - 1);
            }
            Factor f = Factor.of(n, d, k, r);
            BigInteger g = n.gcd(d);
            if (n.signum() == 0) {
                assertEquals(ZERO, f);
                continue;
            }
            BigInteger[] ref = referenceReduce(n.divide(g).multiply(BigInteger.valueOf(d.signum())), d.divide(g), k, r);
            assertEquals(ref[0], f.numerator());
            assertEquals(ref[1], f.denominator());
            assertEquals(ref[2].intValue(), f.piExponent());
            assertEquals(ref[3].intValue(), f.rootIndex());
        }
    }

    private static BigInteger[] referenceReduce(BigInteger n, BigInteger d, int k, int r) {
        for (int p = 2; p <= r; p++) {
            while (r % p == 0 && k % p == 0) {
                BigInteger a = refRoot(n.abs(), p);
                BigInteger b = refRoot(d, p);
                if (a == null || b == null) {
                    break;
                }
                n = n.signum() < 0 ? a.negate() : a;
                d = b;
                k /= p;
                r /= p;
            }
        }
        return new BigInteger[] { n, d, BigInteger.valueOf(k), BigInteger.valueOf(r) };
    }

    private static BigInteger refRoot(BigInteger x, int p) {
        BigInteger y = BigInteger.ONE.shiftLeft(x.bitLength() / p + 1);
        BigInteger pm1 = BigInteger.valueOf(p - 1);
        BigInteger bp = BigInteger.valueOf(p);
        while (true) {
            BigInteger t = y.multiply(pm1).add(x.divide(y.pow(p - 1))).divide(bp);
            if (t.compareTo(y) >= 0) {
                break;
            }
            y = t;
        }
        return y.pow(p).equals(x) ? y : null;
    }
}
