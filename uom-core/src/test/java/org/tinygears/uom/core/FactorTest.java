package org.tinygears.uom.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.tinygears.uom.core.Factor.ONE;
import static org.tinygears.uom.core.Factor.PI;
import static org.tinygears.uom.core.Factor.ZERO;
import static org.tinygears.uom.core.Factor.decimal;
import static org.tinygears.uom.core.Factor.of;
import static org.tinygears.uom.core.Factor.ofLong;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
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
}
