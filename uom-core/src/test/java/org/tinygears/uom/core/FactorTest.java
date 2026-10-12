// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core;

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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

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
        assertEquals(new BigDecimal("3.1415926535897932384626433832795028841971693993751"),
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
    void zeroAndLimits() {
        assertThrows(ArithmeticException.class, () -> ONE.divide(ZERO));
        assertThrows(ArithmeticException.class, () -> ZERO.pow(-1));
        assertEquals(ONE, ofLong(7).pow(0));
        assertThrows(ArithmeticException.class, () -> ofLong(10).pow(100_000));
        assertThrows(ArithmeticException.class, () -> ofLong(2).root(100_000));
    }

    /** Reference values of pi, pi^2 and 250/pi, computed from Machin's pi with ample guard digits. */
    private static List<BigDecimal> references(int digits) {
        MathContext mc = new MathContext(digits + 20);
        BigDecimal pi = machin(digits + 20);
        return List.of(pi, pi.multiply(pi, mc), BigDecimal.valueOf(250).divide(pi, mc));
    }

    private static void assertPiValues(List<BigDecimal> reference, int p) {
        MathContext mc = new MathContext(p);
        Factor[] factors = {PI, PI.pow(2), ofLong(250).divide(PI)};
        for (int i = 0; i < factors.length; i++) {
            assertEquals(reference.get(i).round(mc), factors[i].decimalValue(mc), factors[i] + " at " + p);
        }
    }

    @Test
    void piCacheBucketsGiveCorrectDigitsInAnyOrder() {
        List<BigDecimal> reference = references(320);
        List<Integer> ascending = List.of(1, 54, 63, 64, 65, 100, 127, 128, 129, 191, 192, 193, 250, 255, 256, 257, 300);
        List<Integer> descending = new ArrayList<>(ascending);
        Collections.reverse(descending);
        for (List<Integer> order : List.of(ascending, descending)) {
            Factor.clearPiCache();
            for (int p : order) {
                assertPiValues(reference, p);
            }
        }
        // Within one bucket: the larger request first, then the smaller, and the reverse.
        for (int[] pair : new int[][] {{54, 1}, {1, 54}, {64, 54}, {54, 64}, {65, 128}, {128, 65}}) {
            Factor.clearPiCache();
            assertPiValues(reference, pair[0]);
            assertPiValues(reference, pair[1]);
        }
    }

    @Test
    void piCacheIsBoundedAndCorrectAfterEviction() {
        List<BigDecimal> reference = references(1120);
        Factor.clearPiCache();
        // Twice over 18 buckets: the cache is cleared on the way and refilled.
        for (int round = 0; round < 2; round++) {
            for (int p = 1; p <= 1100; p += 64) {
                assertPiValues(reference, p);
                assertTrue(Factor.piCacheSize() <= 16, "cache size " + Factor.piCacheSize());
            }
        }
    }

    /** Reference pi by Machin's formula, 16 atan(1/5) - 4 atan(1/239). */
    private static BigDecimal machin(int digits) {
        MathContext mc = new MathContext(digits + 10);
        return atanInverse(5, mc).multiply(BigDecimal.valueOf(16)).subtract(atanInverse(239, mc).multiply(
                BigDecimal.valueOf(4))).round(new MathContext(digits));
    }

    private static BigDecimal atanInverse(int x, MathContext mc) {
        BigDecimal x2 = BigDecimal.valueOf((long) x * x);
        BigDecimal eps = BigDecimal.ONE.movePointLeft(mc.getPrecision());
        BigDecimal power = BigDecimal.ONE.divide(BigDecimal.valueOf(x), mc);
        BigDecimal sum = power;
        for (int k = 1; ; k++) {
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
        for (int p : new int[] {1, 2, 15, 63, 64, 65, 500, 1000, 3000}) {
            assertEquals(reference.round(new MathContext(p)), PI.decimalValue(new MathContext(p)), "digits " + p);
        }
    }

    @Test
    void piToTheMaximumContextPrecisionIsFast() {
        // Precision 100000 plus guard digits, as a decimal quantity at the bound of its context needs.
        BigDecimal pi = assertTimeoutPreemptively(Duration.ofSeconds(20),
                () -> PI.decimalValue(new MathContext(200_100)));
        assertEquals(200_100, pi.precision());
        assertEquals(machin(3000), pi.round(new MathContext(3000)));
    }

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
        for (Factor f : new Factor[] {of(6, 4), of(-6, 4), PI.divide(ofLong(180)), ofLong(2).root(2),
                ofLong(-2).multiply(ofLong(2).root(2)), PI.pow(-2).multiply(of(3, 7)).root(3), ZERO, ONE}) {
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
        for (Factor f : new Factor[] {ZERO, of(6, 4), of(-3, 7), PI.divide(ofLong(180)), ofLong(2).root(2),
                PI.root(3), ofLong(-8).root(3), huge}) {
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
        assertThrows(ArithmeticException.class,
                () -> Factor.of(BigInteger.ONE.shiftLeft(70_000), BigInteger.ONE, 0, 1));
    }

    @Test
    void hugeRootIndexReducesQuicklyWithoutOverflow() {
        BigInteger one = BigInteger.ONE;
        BigInteger three = BigInteger.valueOf(3);
        Factor f = assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> Factor.of(three, one, 1, Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, f.rootIndex());
        assertEquals(1, f.piExponent());
        assertEquals(ONE, Factor.of(one, one, 0, Integer.MAX_VALUE));
        assertEquals(PI, Factor.of(one, one, Integer.MAX_VALUE, Integer.MAX_VALUE));
        Factor g = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> Factor.of(three, one, 0, 1 << 30));
        assertEquals(1 << 30, g.rootIndex());
        Factor h = assertTimeoutPreemptively(Duration.ofSeconds(2),
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
            BigInteger[] ref = referenceReduce(n.divide(g).multiply(BigInteger.valueOf(d.signum())),
                    d.divide(g), k, r);
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
        return new BigInteger[] {n, d, BigInteger.valueOf(k), BigInteger.valueOf(r)};
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
