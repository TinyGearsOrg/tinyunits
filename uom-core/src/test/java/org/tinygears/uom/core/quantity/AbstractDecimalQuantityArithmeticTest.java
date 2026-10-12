// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.quantity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import javax.measure.Quantity;
import javax.measure.UnconvertibleException;
import javax.measure.Unit;
import javax.measure.quantity.Length;
import org.junit.jupiter.api.Test;
import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.UomUnit;

class AbstractDecimalQuantityArithmeticTest {

    private static final UomUnit<Length> METRE = BaseUnits.METRE;
    private static final UomUnit<Length> KILOMETRE = BaseUnits.METRE.multiply(1000);
    private static final UomUnit<Length> CELSIUS =
            UomUnit.unchecked(BaseUnits.KELVIN.shift(new BigDecimal("273.15")));
    private static final UomUnit<Length> KELVIN = UomUnit.unchecked(BaseUnits.KELVIN);
    private static final MathContext UNLIMITED = MathContext.UNLIMITED;

    private static TestLengthDecimalImpl q(String value, UomUnit<Length> unit) {
        return new TestLengthDecimalImpl(new BigDecimal(value), unit);
    }

    private static TestLengthDecimalImpl q(String value, UomUnit<Length> unit, MathContext mc) {
        return new TestLengthDecimalImpl(new BigDecimal(value), unit, Quantity.Scale.ABSOLUTE, mc);
    }

    private static TestLengthImpl d(double value, UomUnit<Length> unit) {
        return new TestLengthImpl(value, unit);
    }

    /** Asserts a decimal result: numerically equal value, unit and context. */
    private static void assertDecimal(String value, UomUnit<Length> unit, MathContext mc, Quantity<Length> actual) {
        TestLengthDecimalImpl r = assertInstanceOf(TestLengthDecimalImpl.class, actual);
        assertEquals(0, new BigDecimal(value).compareTo(r.getValue()), () -> value + " vs " + r.getValue());
        assertEquals(unit, r.getUnit());
        assertEquals(mc, r.mathContext());
    }

    private static void assertDecimal(String value, UomUnit<Length> unit, Quantity<Length> actual) {
        assertDecimal(value, unit, MathContext.DECIMAL128, actual);
    }

    @Test
    void add() {
        assertDecimal("3.75", METRE, q("1.5", METRE).add(q("2.25", METRE)));
        assertDecimal("1.001", KILOMETRE, q("1", KILOMETRE).add(q("1", METRE)));
        assertDecimal("1001", METRE, q("1", METRE).add(q("1", KILOMETRE)));
    }

    @Test
    void addIsExactInDecimal() {
        assertEquals(new BigDecimal("0.3"), q("0.1", METRE).add(q("0.2", METRE)).getValue());
    }

    @Test
    void subtract() {
        assertDecimal("9.75", METRE, q("10", METRE).subtract(q("0.25", METRE)));
        assertDecimal("0.999", KILOMETRE, q("1", KILOMETRE).subtract(q("1", METRE)));
    }

    @Test
    void offsetUnitsGoThroughTheSystemUnitExactly() {
        assertEquals(new BigDecimal("276.15"), q("2", CELSIUS).add(q("1", CELSIUS)).getValue());
        assertEquals(new BigDecimal("-272.15"), q("2", CELSIUS).subtract(q("1", CELSIUS)).getValue());
        assertEquals(new BigDecimal("277.15"), q("2", CELSIUS).multiply(2).getValue());
        assertDecimal("277.15", KELVIN, q("2", KELVIN).add(q("2", CELSIUS)));
        assertDecimal("4", CELSIUS, q("2", CELSIUS).add(q("2", KELVIN)));
    }

    @Test
    void scalarMultiply() {
        assertDecimal("4", METRE, q("2", METRE).multiply(2));
        assertEquals(new BigDecimal("1.5"), q("3", METRE).multiply(0.5).getValue());
        assertEquals(new BigDecimal("0.75"), q("3", METRE).multiply(0.25).getValue());
        assertEquals(new BigDecimal("0.3"), q("3", METRE).multiply(0.1f).getValue());
        assertEquals(new BigDecimal("2.2"), q("2", METRE).multiply(new BigDecimal("1.1")).getValue());
        assertDecimal("2.2", METRE, MathContext.DECIMAL32,
                q("2", METRE, MathContext.DECIMAL32).multiply(new BigDecimal("1.1")));
        assertThrows(ArithmeticException.class, () -> q("2", METRE).multiply(Double.NaN));
        assertThrows(ArithmeticException.class, () -> q("2", METRE).multiply(Double.POSITIVE_INFINITY));
    }

    @Test
    void scalarAdoptsTheReceiverContext() {
        assertDecimal("2.469", METRE, new MathContext(4), q("1.2345", METRE, new MathContext(4)).multiply(2));
        assertDecimal("1.235", METRE, new MathContext(4),
                q("1", METRE, new MathContext(4)).multiply(new BigDecimal("1.2345")));
    }

    @Test
    void exactOperandsStayExact() {
        BigInteger huge = BigInteger.TEN.pow(400);
        assertDecimal("2e400", METRE, UNLIMITED, q("2", METRE, UNLIMITED).multiply(huge));
        assertDecimal("1", METRE, UNLIMITED, q("2e400", METRE, UNLIMITED).divide(new BigDecimal(huge).multiply(
                BigDecimal.valueOf(2))));
        assertDecimal("9007199254740993", METRE, q("0", METRE).add(new Foreign<>(9007199254740993L, METRE)));
        assertDecimal("1e400", METRE, UNLIMITED, q("0", METRE, UNLIMITED).add(new Foreign<>(huge, METRE)));
    }

    @Test
    void scalarDivide() {
        assertDecimal("0.5", METRE, q("1", METRE).divide(2));
        assertEquals(new BigDecimal("0.3333333333333333333333333333333333"), q("1", METRE).divide(3).getValue());
        TestLength unlimited = q("1", METRE, UNLIMITED).divide(3);
        assertDecimal("0.3333333333333333333333333333333333", METRE, UNLIMITED, unlimited);
        assertEquals(new BigDecimal("0.33333"), q("1", METRE, new MathContext(5)).divide(3).getValue());
        assertEquals(new BigDecimal("-135.575"), q("2", CELSIUS).divide(2).getValue());
    }

    @Test
    void divisionByZeroThrows() {
        assertThrows(ArithmeticException.class, () -> q("1", METRE).divide(0));
        assertThrows(ArithmeticException.class, () -> q("0", METRE).divide(0));
        assertThrows(ArithmeticException.class, () -> q("1", METRE, UNLIMITED).divide(BigDecimal.ZERO));
        assertThrows(ArithmeticException.class, () -> q("1", CELSIUS).divide(0.0));
    }

    @Test
    void doubleOperandGivesDouble() {
        Quantity<Length> sum = q("1.5", METRE).add(d(2.5, METRE));
        assertInstanceOf(TestLengthImpl.class, sum);
        assertEquals(4.0, sum.getValue());
        assertEquals(METRE, sum.getUnit());
        Quantity<Length> difference = q("1", KILOMETRE).subtract(d(500, METRE));
        assertInstanceOf(TestLengthImpl.class, difference);
        assertEquals(0.5, difference.getValue());
        assertEquals(276.15, q("2", CELSIUS).add(d(1, CELSIUS)).getValue().doubleValue(), 1e-12);
        assertInstanceOf(TestLengthImpl.class, q("1", METRE).add(new Foreign<>(2.5f, METRE)));
    }

    @Test
    void decimalOutsideDoubleRangeThrowsAgainstDouble() {
        assertThrows(ArithmeticException.class, () -> q("1e400", METRE).add(d(1, METRE)));
        assertThrows(ArithmeticException.class, () -> q("1e400", METRE).subtract(d(1, METRE)));
    }

    @Test
    void foreignExactValueStaysDecimal() {
        assertDecimal("3.5", METRE, q("1.5", METRE).add(new Foreign<>(2, METRE)));
        assertDecimal("-0.5", METRE, q("1.5", METRE).subtract(new Foreign<>(2, METRE)));
    }

    @Test
    void foreignDecimalCountsAsUnlimited() {
        assertDecimal("3.5", METRE, MathContext.DECIMAL64,
                q("1.5", METRE, MathContext.DECIMAL64).add(new Foreign<>(new BigDecimal("2"), METRE)));
        assertDecimal("3.5", METRE, UNLIMITED,
                q("1.5", METRE, UNLIMITED).add(new Foreign<>(new BigDecimal("2"), METRE)));
    }

    @Test
    void lowerPrecisionWins() {
        TestLengthDecimalImpl d32 = q("1", METRE, MathContext.DECIMAL32);
        TestLengthDecimalImpl d128 = q("1", METRE, MathContext.DECIMAL128);
        assertEquals(7, ((TestLengthDecimalImpl) d32.add(d128)).mathContext().getPrecision());
        assertEquals(7, ((TestLengthDecimalImpl) d128.add(d32)).mathContext().getPrecision());
        assertEquals(7, ((TestLengthDecimalImpl) d128.subtract(d32)).mathContext().getPrecision());
        TestLengthDecimalImpl unlimited = q("1", METRE, UNLIMITED);
        TestLengthDecimalImpl d64 = q("1", METRE, MathContext.DECIMAL64);
        assertEquals(16, ((TestLengthDecimalImpl) unlimited.add(d64)).mathContext().getPrecision());
        assertEquals(16, ((TestLengthDecimalImpl) d64.add(unlimited)).mathContext().getPrecision());
        TestLengthDecimalImpl halfDown = q("1", METRE, new MathContext(7, java.math.RoundingMode.HALF_DOWN));
        assertSame(halfDown.mathContext(), ((TestLengthDecimalImpl) halfDown.add(d32)).mathContext());
    }

    @Test
    void resultIsRoundedToTheResultContext() {
        Quantity<Length> r = q("1.2345678", METRE, MathContext.DECIMAL32).add(q("0", METRE));
        assertEquals(new BigDecimal("1.234568"), r.getValue());
        assertEquals(7, ((BigDecimal) r.getValue()).precision());
        assertEquals(new BigDecimal("1.234568"), q("0", METRE).add(q("1.2345678", METRE, MathContext.DECIMAL32))
                .getValue());
    }

    @Test
    void scaleIsKept() {
        TestLengthDecimalImpl relative = new TestLengthDecimalImpl(
                BigDecimal.ONE, KILOMETRE, Quantity.Scale.RELATIVE, MathContext.DECIMAL128);
        assertSame(Quantity.Scale.RELATIVE, relative.add(q("1", METRE)).getScale());
        assertSame(Quantity.Scale.RELATIVE, relative.subtract(q("1", METRE)).getScale());
        assertSame(Quantity.Scale.RELATIVE, relative.multiply(2).getScale());
        assertSame(Quantity.Scale.RELATIVE, relative.divide(2).getScale());
        assertSame(Quantity.Scale.RELATIVE, relative.add(d(1, METRE)).getScale());
        assertSame(Quantity.Scale.ABSOLUTE, q("1", METRE).add(relative).getScale());
    }

    @Test
    void foreignUnitFails() {
        Quantity<Length> foreign = new Foreign<>(BigDecimal.ONE, foreignUnit());
        UnconvertibleException e = assertThrows(UnconvertibleException.class, () -> q("1", METRE).add(foreign));
        assertTrue(e.getMessage().contains("foreign unit implementation"), e.getMessage());
        assertThrows(UnconvertibleException.class, () -> q("1", METRE).subtract(foreign));
        assertThrows(UnconvertibleException.class, () -> q("1", METRE).compareTo(foreign));
        assertThrows(UnconvertibleException.class, () -> q("1", METRE).isEquivalentTo(foreign));
    }

    @Test
    void incompatibleDimensionFails() {
        assertThrows(UnconvertibleException.class, () -> q("1", METRE).add(q("1", KELVIN)));
        assertThrows(UnconvertibleException.class, () -> q("1", METRE).subtract(q("1", KELVIN)));
        assertThrows(UnconvertibleException.class, () -> q("1", METRE).add(d(1, KELVIN)));
    }

    @Test
    void nullFails() {
        TestLengthDecimalImpl one = q("1", METRE);
        assertThrows(NullPointerException.class, () -> one.add(null));
        assertThrows(NullPointerException.class, () -> one.subtract(null));
        assertThrows(NullPointerException.class, () -> one.multiply((Number) null));
        assertThrows(NullPointerException.class, () -> one.divide((Number) null));
        assertThrows(NullPointerException.class, () -> one.compareTo(null));
        assertThrows(NullPointerException.class, () -> one.isEquivalentTo(null));
    }

    @Test
    void compareTo() {
        assertEquals(0, q("1", KILOMETRE).compareTo(q("1000", METRE)));
        assertEquals(1, q("1", KILOMETRE).compareTo(q("999.999", METRE)));
        assertEquals(-1, q("999.999", METRE).compareTo(q("1", KILOMETRE)));
        assertEquals(0, q("2", CELSIUS).compareTo(q("275.15", KELVIN)));
        assertEquals(0, q("1.0", METRE).compareTo(q("1.00", METRE)));
        assertEquals(1, q("1.000000000000000000000000000000000000001", METRE, UNLIMITED)
                .compareTo(q("1", METRE, UNLIMITED)));
        assertEquals(1, q("2", METRE).compareTo(new Foreign<>(1, METRE)));
    }

    @Test
    void compareToDoubleComparesAsDoubles() {
        // 0.1 as a double is above the decimal 0.1, but both compare as the same double.
        assertEquals(0, q("0.1", METRE).compareTo(d(0.1, METRE)));
        assertEquals(-1, q("1", METRE).compareTo(d(1.5, METRE)));
        assertEquals(1, q("1", KILOMETRE).compareTo(d(999.5, METRE)));
        assertThrows(ArithmeticException.class, () -> q("1e400", METRE).compareTo(d(1, METRE)));
    }

    @Test
    void compareToIncompatibleDimensionThrows() {
        assertThrows(UnconvertibleException.class, () -> q("1", METRE).compareTo(q("1", KELVIN)));
        assertThrows(UnconvertibleException.class, () -> q("1", METRE).compareTo(d(1, KELVIN)));
    }

    @Test
    void equivalentAcrossUnits() {
        assertTrue(q("2", KILOMETRE).isEquivalentTo(q("2000", METRE)));
        assertTrue(q("2", CELSIUS).isEquivalentTo(q("275.15", KELVIN)));
        assertFalse(q("2", CELSIUS).isEquivalentTo(q("2", KELVIN)));
        assertFalse(q("1", METRE).isEquivalentTo(q("1", KELVIN)));
        assertFalse(q("1", METRE).isEquivalentTo(d(1, KELVIN)));
        assertTrue(q("0", METRE).isEquivalentTo(q("0.000", METRE)));
        assertTrue(q("1", METRE).isEquivalentTo(new Foreign<>(1, METRE)));
    }

    @Test
    void equivalenceToleranceFollowsTheLowestPrecision() {
        assertTrue(q("1", METRE).isEquivalentTo(q("1.0000000000000000000000000000000001", METRE)));
        // Unlimited contexts use p = 34: a 5e-34 difference is within 1e-33 relative, 2e-33 is not.
        TestLengthDecimalImpl one = q("1.00000000000000000000000000000000000000", METRE, UNLIMITED);
        assertTrue(one.isEquivalentTo(q("1.00000000000000000000000000000000050000", METRE, UNLIMITED)));
        assertFalse(one.isEquivalentTo(q("1.00000000000000000000000000000000200000", METRE, UNLIMITED)));
        assertFalse(q("1", METRE, UNLIMITED).isEquivalentTo(q("1.000000000000000000000000000000002", METRE)));
        // DECIMAL32 on either side gives p = 7: relative 1e-6.
        assertTrue(q("1", METRE).isEquivalentTo(q("1.000001", METRE, MathContext.DECIMAL32)));
        assertTrue(q("1.000001", METRE, MathContext.DECIMAL32).isEquivalentTo(q("1", METRE)));
        assertFalse(q("1", METRE).isEquivalentTo(q("1.000002", METRE, MathContext.DECIMAL32)));
        assertFalse(q("1", METRE).isEquivalentTo(q("1.000001", METRE)));
    }

    @Test
    void equivalenceAgainstDoubleUsesTheDoubleTolerance() {
        assertTrue(q("1", METRE).isEquivalentTo(d(1 + 1e-13, METRE)));
        assertFalse(q("1", METRE).isEquivalentTo(d(1 + 1e-11, METRE)));
        assertTrue(q("0.1", METRE).isEquivalentTo(d(0.1, METRE)));
        assertTrue(q("2", KILOMETRE).isEquivalentTo(d(2000, METRE)));
    }

    @SuppressWarnings("unchecked")
    private static Unit<Length> foreignUnit() {
        return (Unit<Length>) Proxy.newProxyInstance(AbstractDecimalQuantityArithmeticTest.class.getClassLoader(),
                new Class<?>[] {Unit.class}, (p, m, a) -> null);
    }

    /** A Quantity from another implementation carrying any Number. */
    private record Foreign<Q extends Quantity<Q>>(Number value, Unit<Q> unit) implements Quantity<Q> {
        @Override public Number getValue() { return value; }
        @Override public Unit<Q> getUnit() { return unit; }
        @Override public Scale getScale() { return Scale.ABSOLUTE; }
        @Override public Quantity<Q> add(Quantity<Q> addend) { throw new UnsupportedOperationException(); }
        @Override public Quantity<Q> subtract(Quantity<Q> s) { throw new UnsupportedOperationException(); }
        @Override public Quantity<?> divide(Quantity<?> divisor) { throw new UnsupportedOperationException(); }
        @Override public Quantity<Q> divide(Number divisor) { throw new UnsupportedOperationException(); }
        @Override public Quantity<?> multiply(Quantity<?> m) { throw new UnsupportedOperationException(); }
        @Override public Quantity<Q> multiply(Number m) { throw new UnsupportedOperationException(); }
        @Override public Quantity<?> inverse() { throw new UnsupportedOperationException(); }
        @Override public Quantity<Q> negate() { throw new UnsupportedOperationException(); }
        @Override public Quantity<Q> to(Unit<Q> u) { throw new UnsupportedOperationException(); }
        @Override public boolean isEquivalentTo(Quantity<Q> that) { throw new UnsupportedOperationException(); }
        @Override public <T extends Quantity<T>> Quantity<T> asType(Class<T> type) {
            throw new UnsupportedOperationException();
        }
    }
}
