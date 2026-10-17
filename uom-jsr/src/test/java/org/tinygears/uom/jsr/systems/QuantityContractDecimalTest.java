/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.jsr.systems;

import java.math.BigDecimal;
import java.math.MathContext;
import javax.measure.Quantity;
import javax.measure.quantity.Speed;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.engine.Factor;
import org.tinygears.uom.jsr.UomUnit;
import org.tinygears.uom.jsr.quantity.AbstractDecimalQuantity;
import org.tinygears.uom.jsr.quantity.Angle;
import org.tinygears.uom.jsr.quantity.Length;
import org.tinygears.uom.jsr.quantity.Temperature;
import org.tinygears.uom.jsr.quantity.Time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tinygears.uom.jsr.systems.SI.CENTIMETRE;
import static org.tinygears.uom.jsr.systems.SI.DEGREE;
import static org.tinygears.uom.jsr.systems.SI.DEGREE_CELSIUS;
import static org.tinygears.uom.jsr.systems.SI.KELVIN;
import static org.tinygears.uom.jsr.systems.SI.KILOMETRE;
import static org.tinygears.uom.jsr.systems.SI.METRE;
import static org.tinygears.uom.jsr.systems.SI.MILLISECOND;
import static org.tinygears.uom.jsr.systems.SI.RADIAN;
import static org.tinygears.uom.jsr.systems.SI.SECOND;

/** The unit-api 2.2 {@link Quantity} Javadoc clauses for decimal quantities, plus exact conversions. */
class QuantityContractDecimalTest {

    interface Unsupported extends Quantity<Unsupported> {
    }

    private static final UomUnit<javax.measure.quantity.Length> INCH = CENTIMETRE.multiply(new BigDecimal("2.54"));
    private static final UomUnit<javax.measure.quantity.Temperature> DEGREE_FAHRENHEIT = KELVIN.multiply(5).divide(9)
            .shift(new BigDecimal("459.67"));

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static Length length(String v, javax.measure.Unit<javax.measure.quantity.Length> unit) {
        return Length.of(d(v), unit);
    }

    private static Temperature celsius(String v) {
        return Temperature.of(d(v), DEGREE_CELSIUS);
    }

    private static Temperature kelvin(String v) {
        return Temperature.of(d(v), KELVIN);
    }

    /** Asserts a decimal value equal to {@code expected} by {@code compareTo}, so the scale does not matter. */
    private static void assertExactly(String expected, Quantity<?> q) {
        assertInstanceOf(AbstractDecimalQuantity.class, q);
        BigDecimal value = (BigDecimal) q.getValue();
        assertEquals(0, d(expected).compareTo(value), () -> expected + " != " + value);
    }

    @Test
    void twoThousandMetresIsEquivalentToTwoKilometres() {
        assertTrue(length("2000", METRE).isEquivalentTo(length("2", KILOMETRE)));
        assertTrue(length("2", KILOMETRE).isEquivalentTo(length("2000", METRE)));
    }

    @Test
    void twoCelsiusIsEquivalentTo275KelvinNotToTwoKelvin() {
        assertTrue(celsius("2").isEquivalentTo(kelvin("275.15")));
        assertFalse(celsius("2").isEquivalentTo(kelvin("2")));
    }

    @Test
    void twoCelsiusPlusTwoKelvinIsEquivalentTo275KelvinPlusTwoKelvin() {
        assertTrue(celsius("2").add(kelvin("2")).isEquivalentTo(kelvin("275.15").add(kelvin("2"))));
        assertTrue(celsius("2").add(kelvin("2")).isEquivalentTo(kelvin("277.15")));
    }

    @Test
    void twoCelsiusPlusOneCelsiusIsTheKnownDiscrepancy() {
        // The Javadoc says 274.15 degC, its own rule gives 276.15 degC; see docs/DESIGN.md DR-01.
        Temperature sum = celsius("2").add(celsius("1"));
        assertExactly("276.15", sum);
        assertSame(DEGREE_CELSIUS, sum.getUnit());
    }

    @Test
    void fourCentimetresPlusOneInchIsTheSystemUnitSum() {
        Length sum = length("4", CENTIMETRE).add(length("1", INCH));
        assertEquals(0, sum.compareTo(length("0.04", METRE).add(length("0.0254", METRE))));
        assertEquals(0, sum.compareTo(length("0.0654", METRE)));
        assertExactly("6.54", sum);
    }

    @Test
    void additionIsCommutative() {
        assertEquals(0, celsius("2").add(kelvin("2")).compareTo(kelvin("2").add(celsius("2"))));
    }

    @Test
    void additionIsAssociative() {
        Temperature a = celsius("2");
        Temperature b = celsius("1");
        Temperature c = kelvin("3");
        assertExactly("279.15", a.add(b).add(c));
        assertExactly("279.15", a.add(b.add(c)));
        assertEquals(0, a.add(b).add(c).compareTo(kelvin("552.3")));
    }

    @Test
    void multiplicationIsCommutativeAndAssociative() {
        Length a = length("2", KILOMETRE);
        Time b = Time.of(d("3"), SECOND);
        Length c = length("5", CENTIMETRE);
        assertExactly("6000", a.multiply(b));
        assertExactly("6000", b.multiply(a));
        assertEquals(a.multiply(b).getUnit(), b.multiply(a).getUnit());
        assertExactly("300", a.multiply(b).multiply(c));
        assertExactly("300", a.multiply(b.multiply(c)));
    }

    @Test
    void implementationsAreComparable() {
        assertInstanceOf(Comparable.class, length("1", METRE));
        assertEquals(0, length("1", KILOMETRE).compareTo(length("1000", METRE)));
        assertEquals(-1, Integer.signum(length("1", METRE).compareTo(length("1", KILOMETRE))));
        assertEquals(1, Integer.signum(length("1", KILOMETRE).compareTo(length("999.999", METRE))));
    }

    @Test
    void decimalArithmeticIsExact() {
        // Each is inexact in double: 0.30000000000000004, 3.3000000000000003, 0.19999999999999998.
        assertExactly("0.3", length("0.1", METRE).add(length("0.2", METRE)));
        assertExactly("3.3", length("1.1", KILOMETRE).multiply(3));
        assertExactly("0.2", length("0.3", METRE).subtract(length("0.1", METRE)));
    }

    @Test
    void addConvertsTheAddendAndKeepsThisUnit() {
        Length sum = length("1", KILOMETRE).add(length("500", METRE));
        assertExactly("1.5", sum);
        assertSame(KILOMETRE, sum.getUnit());
    }

    @Test
    void subtractConvertsTheSubtrahendAndKeepsThisUnit() {
        Length difference = length("1", KILOMETRE).subtract(length("250", METRE));
        assertExactly("0.75", difference);
        assertSame(KILOMETRE, difference.getUnit());
        // Literal rule for ABSOLUTE operands, docs/DESIGN.md DR-01: 275.15 K - 274.15 K = 1 K = -272.15 degC.
        assertExactly("-272.15", celsius("2").subtract(celsius("1")));
    }

    @Test
    void multiplyByQuantityIsTheProduct() {
        Quantity<?> product = length("2", METRE).multiply(Time.of(d("3"), SECOND));
        assertExactly("6", product);
        assertEquals(METRE.multiply(SECOND), product.getUnit());
    }

    @Test
    void divideByQuantityIsTheQuotient() {
        Quantity<?> quotient = length("6", METRE).divide(Time.of(d("4"), SECOND));
        assertExactly("1.5", quotient);
        assertEquals(METRE.divide(SECOND), quotient.getUnit());
    }

    @Test
    void multiplyAndDivideByNumber() {
        Length product = length("2", KILOMETRE).multiply(3);
        assertExactly("6", product);
        assertSame(KILOMETRE, product.getUnit());
        Length quotient = length("6", KILOMETRE).divide(4);
        assertExactly("1.5", quotient);
        assertSame(KILOMETRE, quotient.getUnit());
        // Literal rule for ABSOLUTE operands, docs/DESIGN.md DR-01: 275.15 K * 2 = 550.3 K = 277.15 degC.
        assertExactly("277.15", celsius("2").multiply(2));
    }

    @Test
    void inverseHasReciprocalValueAndUnit() {
        Quantity<?> inverse = Time.of(d("2"), MILLISECOND).inverse();
        // 1 / (2 ms) is stated in the system unit: 500 /s.
        assertExactly("500", inverse);
        assertEquals(SECOND.inverse(), inverse.getUnit());
    }

    @Test
    void negateIsMinusTheValueInTheSameUnit() {
        Temperature negated = celsius("2").negate();
        assertExactly("-2", negated);
        assertSame(DEGREE_CELSIUS, negated.getUnit());
    }

    @Test
    void toStatesTheQuantityInTheGivenUnit() {
        Length metres = length("2", KILOMETRE).to(METRE);
        assertExactly("2000", metres);
        assertSame(METRE, metres.getUnit());
    }

    @Test
    void toSystemUnitIsToTheSystemUnit() {
        Quantity<javax.measure.quantity.Temperature> k = celsius("2").toSystemUnit();
        assertExactly("275.15", k);
        assertEquals(KELVIN, k.getUnit());
    }

    @Test
    void asTypeSpeedOfLightExample() {
        Quantity<Speed> c = length("2", KILOMETRE).multiply(299792458).divide(Time.of(d("1"), SECOND))
                .asType(Speed.class);
        assertInstanceOf(Speed.class, c);
        assertExactly("599584916000", c);
    }

    @Test
    void asTypeThrowsForADifferentDimensionOrWithoutASystemUnit() {
        Quantity<?> speed = length("6", METRE).divide(Time.of(d("2"), SECOND));
        assertThrows(ClassCastException.class, () -> speed.asType(javax.measure.quantity.Length.class));
        assertThrows(UnsupportedOperationException.class, () -> speed.asType(Unsupported.class));
    }

    @Test
    void getScaleReturnsTheScale() {
        assertSame(Quantity.Scale.ABSOLUTE, length("1", METRE).getScale());
        assertSame(Quantity.Scale.RELATIVE, Length.of(d("1"), METRE, Quantity.Scale.RELATIVE).getScale());
    }

    @Test
    void isEquivalentToNullThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> length("1", METRE).isEquivalentTo(null));
    }

    @Test
    void hundredCelsiusIsExactlyKelvin() {
        assertExactly("373.15", celsius("100").to(KELVIN));
    }

    @Test
    void fahrenheitToCelsiusIsExact() {
        assertExactly("100", Temperature.of(d("212"), DEGREE_FAHRENHEIT).to(DEGREE_CELSIUS));
        // A double computation gives 50.00000000000001.
        assertExactly("50", Temperature.of(d("122"), DEGREE_FAHRENHEIT).to(DEGREE_CELSIUS));
    }

    @Test
    void inchToMetreIsExact() {
        assertExactly("0.0254", length("1", INCH).to(METRE));
        // A double computation gives 0.07619999999999999 for 3 in.
        assertExactly("0.0762", length("3", INCH).to(METRE));
        assertExactly("0.3048", length("12", INCH).to(METRE));
    }

    @Test
    void degreesToRadiansRoundsOnceToTheContext() {
        Quantity<?> pi = Angle.of(d("180"), DEGREE).to(RADIAN);
        assertEquals(d("3.141592653589793238462643383279503"), pi.getValue());
        assertEquals(Factor.PI.decimalValue(MathContext.DECIMAL128), pi.getValue());
    }
}
