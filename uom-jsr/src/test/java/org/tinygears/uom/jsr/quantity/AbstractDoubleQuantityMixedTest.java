/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.jsr.quantity;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import javax.measure.Quantity;
import javax.measure.quantity.Length;
import javax.measure.quantity.Speed;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.jsr.BaseUnits;
import org.tinygears.uom.jsr.UomUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractDoubleQuantityMixedTest {

    private static final UomUnit<Length> METRE = BaseUnits.METRE;
    private static final UomUnit<Length> KILOMETRE = BaseUnits.METRE.multiply(1000);
    private static final UomUnit<Length> CELSIUS = UomUnit.unchecked(BaseUnits.KELVIN.shift(new BigDecimal("273.15")));
    private static final UomUnit<Length> KELVIN = UomUnit.unchecked(BaseUnits.KELVIN);

    private static TestLengthImpl d(double value, UomUnit<Length> unit) {
        return new TestLengthImpl(value, unit);
    }

    private static TestLengthDecimalImpl q(String value, UomUnit<Length> unit) {
        return new TestLengthDecimalImpl(new BigDecimal(value), unit);
    }

    private static void assertDecimal(String expected, Quantity<?> actual) {
        BigDecimal value = assertInstanceOf(BigDecimal.class, actual.getValue());
        assertEquals(0, new BigDecimal(expected).compareTo(value), () -> expected + " vs " + value);
    }

    // F23: the decimal wins; the double operand is read as its shortest decimal (BigDecimal.valueOf).
    @Test
    void addDecimalOperandGivesDecimal() {
        Quantity<Length> sum = d(2.5, METRE).add(q("1.5", METRE));
        assertInstanceOf(TestLengthDecimalImpl.class, sum);
        assertDecimal("4", sum);
        assertSame(METRE, sum.getUnit());
        assertDecimal("0.3", d(0.2, METRE).add(q("0.1", METRE)));
        assertDecimal("1001.5", d(1.5, METRE).add(q("1", KILOMETRE)));
        assertDecimal("276.15", d(2, CELSIUS).add(q("1", CELSIUS)));
        // The result takes the decimal operand's context.
        TestLengthDecimalImpl five = new TestLengthDecimalImpl(
                BigDecimal.ONE,
                METRE,
                Quantity.Scale.ABSOLUTE,
                new MathContext(5));
        Quantity<Length> rounded = d(1.0 / 3, METRE).add(five);
        assertDecimal("1.3333", rounded);
        assertEquals(new MathContext(5), ((TestLengthDecimalImpl) rounded).mathContext());
    }

    @Test
    void nonFiniteDoubleCannotBePromoted() {
        assertThrows(ArithmeticException.class, () -> d(Double.NaN, METRE).add(q("1", METRE)));
        assertThrows(ArithmeticException.class, () -> d(Double.POSITIVE_INFINITY, METRE).subtract(q("1", METRE)));
        assertThrows(ArithmeticException.class, () -> d(Double.NaN, METRE).multiply((Quantity<?>) q("1", METRE)));
        // compareTo keeps the Double.compare order for them.
        assertEquals(1, d(Double.NaN, METRE).compareTo(q("1", METRE)));
        assertEquals(-1, d(Double.NEGATIVE_INFINITY, METRE).compareTo(q("1", METRE)));
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
        assertInstanceOf(TestLengthDecimalImpl.class, difference);
        assertDecimal("0.5", difference);
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
        // Compared as decimals: the double reads as 0.1, which is less.
        assertEquals(-1, d(0.1, METRE).compareTo(q("0.10000000000000000001", METRE)));
        assertEquals(0, d(0.1, METRE).compareTo(q("0.1", METRE)));
    }

    @Test
    void decimalOperandOutsideDoubleRangeWorks() {
        TestLengthDecimalImpl huge = q("1e400", METRE);
        TestLengthImpl a = d(1, METRE);
        assertDecimal("1e400", a.add(huge));
        assertDecimal("-1e400", a.subtract(huge));
        assertDecimal("1e400", a.multiply((Quantity<?>) huge));
        assertDecimal("1e-400", a.divide((Quantity<?>) huge));
        assertEquals(-1, a.compareTo(huge));
        // isEquivalentTo keeps the double tolerance, compared in decimal.
        assertFalse(a.isEquivalentTo(huge));
        assertFalse(huge.isEquivalentTo(a));
    }

    @Test
    void equivalenceOfDoubleAndDecimalIsDecimalWithTheDoubleTolerance() {
        // just above the double range, within the tolerance of Double.MAX_VALUE
        BigDecimal max = new BigDecimal(Double.MAX_VALUE);
        TestLengthDecimalImpl above = new TestLengthDecimalImpl(max.add(max.scaleByPowerOfTen(-13)), METRE);
        assertTrue(d(Double.MAX_VALUE, METRE).isEquivalentTo(above));
        assertTrue(above.isEquivalentTo(d(Double.MAX_VALUE, METRE)));
        TestLengthDecimalImpl beyond = new TestLengthDecimalImpl(max.add(max.scaleByPowerOfTen(-11)), METRE);
        assertFalse(d(Double.MAX_VALUE, METRE).isEquivalentTo(beyond));
        assertFalse(beyond.isEquivalentTo(d(Double.MAX_VALUE, METRE)));
        // a NaN or infinite double is equivalent to no decimal
        for (double v : new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            assertFalse(d(v, METRE).isEquivalentTo(q("1e400", METRE)), () -> "" + v);
            assertFalse(q("1e400", METRE).isEquivalentTo(d(v, METRE)), () -> "" + v);
        }
        // in range, as before: units convert and the tolerance is relative
        assertTrue(q("2000", METRE).isEquivalentTo(d(2, KILOMETRE)));
        assertTrue(q("0.1000000000000001", METRE).isEquivalentTo(d(0.1, METRE)));
        assertFalse(q("0.10000000001", METRE).isEquivalentTo(d(0.1, METRE)));
    }

    @Test
    void decimalOperandBelowDoubleRangeIsKept() {
        TestLengthDecimalImpl tiny = q("1e-400", METRE);
        TestLengthImpl a = d(1, METRE);
        assertDecimal("1", a.add(tiny));
        assertDecimal("1e-400", a.multiply((Quantity<?>) tiny));
        assertDecimal("1e400", a.divide((Quantity<?>) tiny));
        assertFalse(a.isEquivalentTo(tiny));
        assertEquals(1, a.compareTo(tiny));
        assertEquals(-1, d(0, METRE).compareTo(tiny));
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
    void untypedProductsWithDecimalOperandAreDecimal() {
        UomUnit<?> secondUnit = BaseUnits.SECOND;
        Quantity<?> product = d(2, METRE)
                .multiply(UntypedDecimalQuantity.of(new BigDecimal("3"), METRE, MathContext.DECIMAL128));
        assertInstanceOf(UntypedDecimalQuantity.class, product);
        assertDecimal("6", product);
        assertEquals(METRE.multiply(METRE), product.getUnit());
        Quantity<?> quotient = d(10, METRE)
                .divide(UntypedDecimalQuantity.of(new BigDecimal("4"), secondUnit, MathContext.DECIMAL128));
        assertInstanceOf(UntypedDecimalQuantity.class, quotient);
        assertDecimal("2.5", quotient);
        assertEquals(METRE.divide(secondUnit), quotient.getUnit());
        Quantity<?> typed = d(1, KILOMETRE).multiply(q("0.5", METRE));
        assertInstanceOf(UntypedDecimalQuantity.class, typed);
        assertDecimal("500", typed);
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
        assertSame(
                Quantity.Scale.RELATIVE,
                new TestLengthImpl(1.5, METRE, Quantity.Scale.RELATIVE).toDecimal(MathContext.DECIMAL64).getScale());
        assertEquals(
                new BigDecimal("0.3333333"),
                ((TestLengthDecimalImpl) d(1.0 / 3, METRE).toDecimal(MathContext.DECIMAL32)).getValue());
        assertSame(
                MathContext.DECIMAL32,
                ((TestLengthDecimalImpl) d(1.0 / 3, METRE).toDecimal(MathContext.DECIMAL32)).mathContext());
        assertSame(
                MathContext.DECIMAL64,
                ((TestLengthDecimalImpl) d(1.0 / 3, METRE).toDecimal(MathContext.DECIMAL64)).mathContext());
        MathContext five = new MathContext(5, RoundingMode.HALF_UP);
        TestLengthDecimalImpl rounded = (TestLengthDecimalImpl) d(2.000049, METRE).toDecimal(five);
        assertSame(five, rounded.mathContext());
        assertEquals(new BigDecimal("2.0000"), rounded.getValue());
    }

    @Test
    void toDecimalOfNonFiniteThrows() {
        assertThrows(ArithmeticException.class, () -> d(Double.NaN, METRE).toDecimal(MathContext.DECIMAL128));
        assertThrows(
                ArithmeticException.class,
                () -> d(Double.POSITIVE_INFINITY, METRE).toDecimal(MathContext.DECIMAL128));
        assertThrows(
                ArithmeticException.class,
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
        Quantity<Speed> speed = new TestSpeedImpl(
                3,
                UomUnit.unchecked(METRE.divide(BaseUnits.SECOND)),
                Quantity.Scale.ABSOLUTE).toDecimal(MathContext.DECIMAL64);
        assertInstanceOf(TestSpeedDecimalImpl.class, speed);
        Quantity<?> untyped = UntypedDoubleQuantity.of(1.5, METRE).toDecimal(MathContext.DECIMAL32);
        UntypedDecimalQuantity decimal = assertInstanceOf(UntypedDecimalQuantity.class, untyped);
        assertEquals(new BigDecimal("1.5"), decimal.getValue());
        assertSame(MathContext.DECIMAL32, decimal.mathContext());
        assertSame(MathContext.DECIMAL64, ((TestSpeedDecimalImpl) speed).mathContext());
        MathContext five = new MathContext(5, RoundingMode.HALF_UP);
        assertSame(five, ((UntypedDecimalQuantity) UntypedDoubleQuantity.of(1.5, METRE).toDecimal(five)).mathContext());
    }
}
