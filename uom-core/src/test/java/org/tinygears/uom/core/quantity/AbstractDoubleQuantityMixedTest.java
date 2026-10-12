// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.quantity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import javax.measure.Quantity;
import javax.measure.quantity.Length;
import javax.measure.quantity.Speed;
import org.junit.jupiter.api.Test;
import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.UomUnit;

class AbstractDoubleQuantityMixedTest {

    private static final UomUnit<Length> METRE = BaseUnits.METRE;
    private static final UomUnit<Length> KILOMETRE = BaseUnits.METRE.multiply(1000);
    private static final UomUnit<Length> CELSIUS =
            UomUnit.unchecked(BaseUnits.KELVIN.shift(new BigDecimal("273.15")));
    private static final UomUnit<Length> KELVIN = UomUnit.unchecked(BaseUnits.KELVIN);

    private static TestLengthImpl d(double value, UomUnit<Length> unit) {
        return new TestLengthImpl(value, unit);
    }

    private static TestLengthDecimalImpl q(String value, UomUnit<Length> unit) {
        return new TestLengthDecimalImpl(new BigDecimal(value), unit);
    }

    @Test
    void addDecimalOperandGivesDouble() {
        Quantity<Length> sum = d(2.5, METRE).add(q("1.5", METRE));
        assertInstanceOf(TestLengthImpl.class, sum);
        assertEquals(4.0, sum.getValue());
        assertSame(METRE, sum.getUnit());
        assertEquals(1001.5, d(1.5, METRE).add(q("1", KILOMETRE)).getValue());
        assertEquals(276.15, d(2, CELSIUS).add(q("1", CELSIUS)).getValue().doubleValue(), 1e-12);
    }

    @Test
    void bothArgumentOrdersGiveTheSameClass() {
        Quantity<Length> doubleFirst = d(2.5, METRE).add(q("1.5", METRE));
        Quantity<Length> decimalFirst = q("1.5", METRE).add(d(2.5, METRE));
        assertSame(doubleFirst.getClass(), decimalFirst.getClass());
        assertEquals(doubleFirst, decimalFirst);
        assertSame(d(1, METRE).subtract(q("1", METRE)).getClass(), q("1", METRE).subtract(d(1, METRE)).getClass());
    }

    @Test
    void subtractDecimalOperand() {
        Quantity<Length> difference = d(1, KILOMETRE).subtract(q("500", METRE));
        assertInstanceOf(TestLengthImpl.class, difference);
        assertEquals(0.5, difference.getValue());
        assertSame(KILOMETRE, difference.getUnit());
    }

    @Test
    void isEquivalentToDecimalOperand() {
        assertTrue(d(2, KILOMETRE).isEquivalentTo(q("2000", METRE)));
        assertTrue(d(0.1, METRE).isEquivalentTo(q("0.1000000000000001", METRE)));
        assertFalse(d(0.1, METRE).isEquivalentTo(q("0.10000000001", METRE)));
        assertTrue(d(2, CELSIUS).isEquivalentTo(q("275.15", KELVIN)));
    }

    @Test
    void compareToDecimalOperand() {
        assertEquals(0, d(1, KILOMETRE).compareTo(q("1000", METRE)));
        assertEquals(1, d(1, KILOMETRE).compareTo(q("999.999", METRE)));
        assertEquals(-1, d(1, KILOMETRE).compareTo(q("1000.001", METRE)));
        // Compared as doubles: the decimal rounds to 0.1 and is not greater.
        assertEquals(0, d(0.1, METRE).compareTo(q("0.10000000000000000001", METRE)));
    }

    @Test
    void decimalOperandOutsideDoubleRangeThrows() {
        TestLengthDecimalImpl huge = q("1e400", METRE);
        TestLengthImpl a = d(1, METRE);
        assertThrows(ArithmeticException.class, () -> a.add(huge));
        assertThrows(ArithmeticException.class, () -> a.subtract(huge));
        assertThrows(ArithmeticException.class, () -> a.multiply((Quantity<?>) huge));
        assertThrows(ArithmeticException.class, () -> a.divide((Quantity<?>) huge));
        assertThrows(ArithmeticException.class, () -> a.isEquivalentTo(huge));
        assertThrows(ArithmeticException.class, () -> a.compareTo(huge));
        assertThrows(ArithmeticException.class, () -> a.add(q("-1e400", METRE)));
    }

    @Test
    void decimalOperandBelowDoubleRangeIsZero() {
        TestLengthDecimalImpl tiny = q("1e-400", METRE);
        TestLengthImpl a = d(1, METRE);
        assertEquals(1.0, a.add(tiny).getValue());
        assertEquals(1.0, a.subtract(tiny).getValue());
        assertEquals(0.0, a.multiply((Quantity<?>) tiny).getValue());
        assertEquals(Double.POSITIVE_INFINITY, a.divide((Quantity<?>) tiny).getValue());
        assertFalse(a.isEquivalentTo(tiny));
        assertEquals(1, a.compareTo(tiny));
        assertEquals(0, d(0, METRE).compareTo(tiny));
    }

    @Test
    void scalarDecimalOutsideDoubleRangeThrows() {
        TestLengthImpl a = d(2, METRE);
        assertThrows(ArithmeticException.class, () -> a.multiply(new BigDecimal("1e400")));
        assertThrows(ArithmeticException.class, () -> a.divide(new BigDecimal("1e400")));
        assertThrows(ArithmeticException.class, () -> a.multiply(BigInteger.TEN.pow(400)));
        assertThrows(ArithmeticException.class, () -> a.divide(BigInteger.TEN.pow(400).negate()));
        assertThrows(ArithmeticException.class, () -> d(2, CELSIUS).multiply(new BigDecimal("1e400")));
    }

    @Test
    void scalarDecimalBelowDoubleRangeIsZero() {
        assertEquals(0.0, d(2, METRE).multiply(new BigDecimal("1e-400")).getValue());
        assertEquals(Double.POSITIVE_INFINITY, d(2, METRE).divide(new BigDecimal("1e-400")).getValue());
        assertEquals(5.0, d(2, METRE).multiply(new BigDecimal("2.5")).getValue());
        assertEquals(6.0, d(2, METRE).multiply(BigInteger.valueOf(3)).getValue());
    }

    @Test
    void doubleScalarsKeepIeeeSemantics() {
        assertEquals(Double.POSITIVE_INFINITY, d(2, METRE).multiply(Double.POSITIVE_INFINITY).getValue());
        assertEquals(Double.POSITIVE_INFINITY, d(2, METRE).divide(0).getValue());
        assertEquals(Double.NaN, d(2, METRE).multiply(Double.NaN).getValue());
    }

    @Test
    void untypedProductsWithDecimalOperandStayDouble() {
        UomUnit<?> secondUnit = BaseUnits.SECOND;
        Quantity<?> product = d(2, METRE).multiply(UntypedDecimalQuantity.of(new BigDecimal("3"), METRE,
                MathContext.DECIMAL128));
        assertInstanceOf(UntypedDoubleQuantity.class, product);
        assertEquals(6.0, product.getValue());
        assertEquals(METRE.multiply(METRE), product.getUnit());
        Quantity<?> quotient = d(10, METRE).divide(UntypedDecimalQuantity.of(new BigDecimal("4"), secondUnit,
                MathContext.DECIMAL128));
        assertInstanceOf(UntypedDoubleQuantity.class, quotient);
        assertEquals(2.5, quotient.getValue());
        assertEquals(METRE.divide(secondUnit), quotient.getUnit());
        Quantity<?> typed = d(1, KILOMETRE).multiply(q("0.5", METRE));
        assertInstanceOf(UntypedDoubleQuantity.class, typed);
        assertEquals(500.0, typed.getValue());
        assertInstanceOf(UntypedDoubleQuantity.class, d(4, METRE).inverse());
    }

    @Test
    void toDecimalKeepsTheValueAsWritten() {
        TestLength x = d(0.1, METRE).toDecimal(MathContext.DECIMAL128);
        TestLengthDecimalImpl decimal = assertInstanceOf(TestLengthDecimalImpl.class, x);
        assertEquals(new BigDecimal("0.1"), decimal.getValue());
        assertEquals(q("0.1", METRE), decimal);
        assertSame(MathContext.DECIMAL128, decimal.mathContext());
        assertSame(METRE, decimal.getUnit());
        assertSame(Quantity.Scale.RELATIVE, new TestLengthImpl(1.5, METRE, Quantity.Scale.RELATIVE)
                .toDecimal(MathContext.DECIMAL64).getScale());
        assertEquals(new BigDecimal("0.3333333"), ((TestLengthDecimalImpl) d(1.0 / 3, METRE)
                .toDecimal(MathContext.DECIMAL32)).getValue());
        assertSame(MathContext.DECIMAL32, ((TestLengthDecimalImpl) d(1.0 / 3, METRE)
                .toDecimal(MathContext.DECIMAL32)).mathContext());
        assertSame(MathContext.DECIMAL64, ((TestLengthDecimalImpl) d(1.0 / 3, METRE)
                .toDecimal(MathContext.DECIMAL64)).mathContext());
        MathContext five = new MathContext(5, RoundingMode.HALF_UP);
        TestLengthDecimalImpl rounded = (TestLengthDecimalImpl) d(2.000049, METRE).toDecimal(five);
        assertSame(five, rounded.mathContext());
        assertEquals(new BigDecimal("2.0000"), rounded.getValue());
    }

    @Test
    void toDecimalOfNonFiniteThrows() {
        assertThrows(ArithmeticException.class, () -> d(Double.NaN, METRE).toDecimal(MathContext.DECIMAL128));
        assertThrows(ArithmeticException.class,
                () -> d(Double.POSITIVE_INFINITY, METRE).toDecimal(MathContext.DECIMAL128));
        assertThrows(ArithmeticException.class,
                () -> d(Double.NEGATIVE_INFINITY, METRE).toDecimal(MathContext.DECIMAL128));
    }

    @Test
    void toDoubleEqualsTheReceiver() {
        TestLengthImpl x = new TestLengthImpl(2.5, KILOMETRE, Quantity.Scale.RELATIVE);
        assertEquals(x, x.toDouble());
        assertInstanceOf(TestLengthImpl.class, x.toDouble());
    }

    @Test
    void fixturesImplementTheDecimalHook() {
        Quantity<Speed> speed = new TestSpeedImpl(3, UomUnit.unchecked(METRE.divide(BaseUnits.SECOND)),
                Quantity.Scale.ABSOLUTE).toDecimal(MathContext.DECIMAL64);
        assertInstanceOf(TestSpeedDecimalImpl.class, speed);
        Quantity<?> untyped = UntypedDoubleQuantity.of(1.5, METRE).toDecimal(MathContext.DECIMAL32);
        UntypedDecimalQuantity decimal = assertInstanceOf(UntypedDecimalQuantity.class, untyped);
        assertEquals(new BigDecimal("1.5"), decimal.getValue());
        assertSame(MathContext.DECIMAL32, decimal.mathContext());
        assertSame(MathContext.DECIMAL64, ((TestSpeedDecimalImpl) speed).mathContext());
        MathContext five = new MathContext(5, RoundingMode.HALF_UP);
        assertSame(five, ((UntypedDecimalQuantity) UntypedDoubleQuantity.of(1.5, METRE).toDecimal(five))
                .mathContext());
    }
}
