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

    @Test
    void piCacheIsBounded() {
        for (int p = 1; p <= 300; p++) {
            PI.decimalValue(new MathContext(p));
        }
        assertTrue(Factor.piCacheSize() <= 16);
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
}
