// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.quantity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.tinygears.uom.core.quantity.Precision.Kind;

class PrecisionTest {

    private static final class Custom extends Number {
        @Override public int intValue() { return 1; }
        @Override public long longValue() { return 1; }
        @Override public float floatValue() { return 1; }
        @Override public double doubleValue() { return 1; }
    }

    @Test
    void kinds() {
        assertEquals(Kind.EXACT, Precision.kind((byte) 1));
        assertEquals(Kind.EXACT, Precision.kind((short) 1));
        assertEquals(Kind.EXACT, Precision.kind(1));
        assertEquals(Kind.EXACT, Precision.kind(1L));
        assertEquals(Kind.EXACT, Precision.kind(BigInteger.ONE));
        assertEquals(Kind.EXACT, Precision.kind(new AtomicInteger(1)));
        assertEquals(Kind.EXACT, Precision.kind(new AtomicLong(1)));
        assertEquals(Kind.DOUBLE, Precision.kind(1.0));
        assertEquals(Kind.DOUBLE, Precision.kind(1.0f));
        assertEquals(Kind.DECIMAL, Precision.kind(BigDecimal.ONE));
        assertEquals(Kind.DOUBLE, Precision.kind(new Custom()));
    }

    @Test
    void kindRejectsNull() {
        assertThrows(NullPointerException.class, () -> Precision.kind(null));
    }

    @Test
    void toDoubleRejectsOutOfRangeDecimals() {
        assertThrows(ArithmeticException.class, () -> Precision.toDouble(new BigDecimal("1e400")));
        assertThrows(ArithmeticException.class, () -> Precision.toDouble(new BigDecimal("-1e400")));
        assertEquals("value out of double range: precision 1, scale -400",
                assertThrows(ArithmeticException.class, () -> Precision.toDouble(new BigDecimal("1e400")))
                        .getMessage());
        assertEquals(0.0, Precision.toDouble(new BigDecimal("1e-400")));
        assertEquals(2.5, Precision.toDouble(new BigDecimal("2.5")));
    }

    @Test
    void toDecimal() {
        assertEquals(new BigDecimal("0.1"), Precision.toDecimal(0.1));
        assertEquals(new BigDecimal("0.1"), Precision.toDecimal(0.1f));
        assertEquals(new BigDecimal("7"), Precision.toDecimal(7L));
        assertEquals(new BigDecimal("12"), Precision.toDecimal(new BigInteger("12")));
        assertEquals(new BigDecimal("3"), Precision.toDecimal(new AtomicInteger(3)));
        assertEquals(new BigDecimal(BigInteger.TWO.pow(200)), Precision.toDecimal(BigInteger.TWO.pow(200)));
        assertEquals(new BigDecimal("0.0"), Precision.toDecimal(-0.0));
        assertEquals(new BigDecimal("4.9E-324"), Precision.toDecimal(Double.MIN_VALUE));
        assertEquals(new BigDecimal("3.4028235E38"), Precision.toDecimal(Float.MAX_VALUE));
        BigDecimal b = new BigDecimal("1.50");
        assertSame(b, Precision.toDecimal(b));
        assertThrows(ArithmeticException.class, () -> Precision.toDecimal(Double.NaN));
        assertThrows(ArithmeticException.class, () -> Precision.toDecimal(Double.POSITIVE_INFINITY));
        assertThrows(ArithmeticException.class, () -> Precision.toDecimal(Double.NEGATIVE_INFINITY));
        assertThrows(ArithmeticException.class, () -> Precision.toDecimal(Float.NaN));
        assertThrows(ArithmeticException.class, () -> Precision.toDecimal(Float.POSITIVE_INFINITY));
    }

    @Test
    void checkedLimitsTheScale() {
        BigDecimal ok = BigDecimal.ONE.scaleByPowerOfTen(-100_000);
        assertSame(ok, Precision.checked(ok));
        BigDecimal ok2 = BigDecimal.ONE.scaleByPowerOfTen(100_000);
        assertSame(ok2, Precision.checked(ok2));
        assertThrows(ArithmeticException.class, () -> Precision.checked(BigDecimal.ONE.scaleByPowerOfTen(-100_001)));
        assertThrows(ArithmeticException.class, () -> Precision.checked(BigDecimal.ONE.scaleByPowerOfTen(100_001)));
    }

    @Test
    void lowerPrecision() {
        assertSame(MathContext.DECIMAL32, Precision.lower(MathContext.DECIMAL32, MathContext.DECIMAL128));
        assertSame(MathContext.DECIMAL32, Precision.lower(MathContext.DECIMAL128, MathContext.DECIMAL32));
        assertSame(MathContext.DECIMAL64, Precision.lower(MathContext.UNLIMITED, MathContext.DECIMAL64));
        assertSame(MathContext.DECIMAL64, Precision.lower(MathContext.DECIMAL64, MathContext.UNLIMITED));
        MathContext up = new MathContext(10, RoundingMode.UP);
        MathContext down = new MathContext(10, RoundingMode.DOWN);
        assertSame(up, Precision.lower(up, down));
        assertSame(down, Precision.lower(down, up));
    }

    @Test
    void calculationContext() {
        assertSame(MathContext.DECIMAL128, Precision.calculation(MathContext.UNLIMITED));
        assertSame(MathContext.DECIMAL64, Precision.calculation(MathContext.DECIMAL64));
    }
}
