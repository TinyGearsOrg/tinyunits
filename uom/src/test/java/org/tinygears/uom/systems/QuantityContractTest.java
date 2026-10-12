// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tinygears.uom.systems.SI.CENTIMETRE;
import static org.tinygears.uom.systems.SI.DEGREE_CELSIUS;
import static org.tinygears.uom.systems.SI.KELVIN;
import static org.tinygears.uom.systems.SI.KILOMETRE;
import static org.tinygears.uom.systems.SI.METRE;
import static org.tinygears.uom.systems.SI.MILLISECOND;
import static org.tinygears.uom.systems.SI.SECOND;

import javax.measure.IncommensurableException;
import javax.measure.Quantity;
import javax.measure.quantity.Speed;
import org.junit.jupiter.api.Test;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.quantity.Length;
import org.tinygears.uom.quantity.Temperature;
import org.tinygears.uom.quantity.Time;

/** Each test states one clause or example of the unit-api 2.2 {@link Quantity} Javadoc. */
class QuantityContractTest {

    interface Unsupported extends Quantity<Unsupported> {
    }

    private static final UomUnit<javax.measure.quantity.Length> INCH = CENTIMETRE.multiply(2.54);

    private static Temperature celsius(double v) {
        return Temperature.of(v, DEGREE_CELSIUS);
    }

    private static Temperature kelvin(double v) {
        return Temperature.of(v, KELVIN);
    }

    @Test
    void twoThousandMetresIsEquivalentToTwoKilometres() {
        assertTrue(Length.of(2000, METRE).isEquivalentTo(Length.of(2, KILOMETRE)));
        assertTrue(Length.of(2, KILOMETRE).isEquivalentTo(Length.of(2000, METRE)));
    }

    @Test
    void twoCelsiusIsEquivalentTo275KelvinNotToTwoKelvin() {
        assertTrue(celsius(2).isEquivalentTo(kelvin(275.15)));
        assertFalse(celsius(2).isEquivalentTo(kelvin(2)));
    }

    @Test
    void twoCelsiusPlusTwoKelvinIsEquivalentTo275KelvinPlusTwoKelvin() {
        assertTrue(celsius(2).add(kelvin(2)).isEquivalentTo(kelvin(275.15).add(kelvin(2))));
        assertTrue(celsius(2).add(kelvin(2)).isEquivalentTo(kelvin(277.15)));
    }

    @Test
    void twoCelsiusPlusOneCelsiusIsTheKnownDiscrepancy() {
        // The Javadoc says 274.15 degC, its own rule gives 276.15 degC; see docs/TODO.md item 1.
        Temperature sum = celsius(2).add(celsius(1));
        assertEquals(276.15, sum.getValue().doubleValue(), 1e-9);
        assertSame(DEGREE_CELSIUS, sum.getUnit());
    }

    @Test
    void fourCentimetresPlusOneInchIsEquivalentToTheSystemUnitSum() {
        Length sum = Length.of(4, CENTIMETRE).add(Length.of(1, INCH));
        assertTrue(sum.isEquivalentTo(Length.of(0.04, METRE).add(Length.of(0.0254, METRE))));
        assertTrue(sum.isEquivalentTo(Length.of(0.0654, METRE)));
    }

    @Test
    void additionIsCommutative() {
        assertTrue(celsius(2).add(kelvin(2)).isEquivalentTo(kelvin(2).add(celsius(2))));
    }

    @Test
    void additionIsAssociative() {
        Temperature a = celsius(2);
        Temperature b = celsius(1);
        Temperature c = kelvin(3);
        assertTrue(a.add(b).add(c).isEquivalentTo(a.add(b.add(c))));
        assertTrue(a.add(b).add(c).isEquivalentTo(kelvin(552.3)));
    }

    @Test
    void multiplicationIsCommutativeAndAssociative() throws IncommensurableException {
        Length a = Length.of(2, KILOMETRE);
        Time b = Time.of(3, SECOND);
        Length c = Length.of(5, CENTIMETRE);
        assertEquals(6000.0, a.multiply(b).getValue().doubleValue(), 1e-9);
        assertEquals(a.multiply(b).getValue().doubleValue(), b.multiply(a).getValue().doubleValue());
        assertEquals(a.multiply(b).getUnit(), b.multiply(a).getUnit());
        double left = a.multiply(b).multiply(c).getValue().doubleValue();
        double right = a.multiply(b.multiply(c)).getValue().doubleValue();
        assertEquals(300.0, left, 1e-9);
        assertEquals(left, right, 1e-9);
    }

    @Test
    void addConvertsTheAddendAndKeepsThisUnit() {
        Length sum = Length.of(1, KILOMETRE).add(Length.of(500, METRE));
        assertEquals(1.5, sum.getValue().doubleValue(), 1e-12);
        assertSame(KILOMETRE, sum.getUnit());
    }

    @Test
    void subtractConvertsTheSubtrahendAndKeepsThisUnit() {
        Length difference = Length.of(1, KILOMETRE).subtract(Length.of(250, METRE));
        assertEquals(0.75, difference.getValue().doubleValue(), 1e-12);
        assertSame(KILOMETRE, difference.getUnit());
    }

    @Test
    void subtractOffsetUnitsGoesThroughTheSystemUnit() {
        // Literal rule, docs/TODO.md item 1: 275.15 K - 274.15 K = 1 K = -272.15 degC.
        assertEquals(-272.15, celsius(2).subtract(celsius(1)).getValue().doubleValue(), 1e-9);
    }

    @Test
    void multiplyByQuantityIsTheProduct() {
        Quantity<?> product = Length.of(2, METRE).multiply(Time.of(3, SECOND));
        assertEquals(6.0, product.getValue().doubleValue());
        assertEquals(METRE.multiply(SECOND), product.getUnit());
    }

    @Test
    void divideByQuantityIsTheQuotient() {
        Quantity<?> quotient = Length.of(6, METRE).divide(Time.of(2, SECOND));
        assertEquals(3.0, quotient.getValue().doubleValue());
        assertEquals(METRE.divide(SECOND), quotient.getUnit());
    }

    @Test
    void multiplyByNumber() {
        Length product = Length.of(2, KILOMETRE).multiply(3);
        assertEquals(6.0, product.getValue());
        assertSame(KILOMETRE, product.getUnit());
        // Literal rule, docs/TODO.md item 1: 275.15 K * 2 = 550.3 K = 277.15 degC.
        assertEquals(277.15, celsius(2).multiply(2).getValue().doubleValue(), 1e-9);
    }

    @Test
    void divideByNumber() {
        Length quotient = Length.of(6, KILOMETRE).divide(4);
        assertEquals(1.5, quotient.getValue());
        assertSame(KILOMETRE, quotient.getUnit());
    }

    @Test
    void inverseHasReciprocalValueAndUnit() throws IncommensurableException {
        Quantity<?> inverse = Time.of(2, MILLISECOND).inverse();
        double perMillisecond = inverse.getUnit().getConverterToAny(MILLISECOND.inverse())
                .convert(inverse.getValue().doubleValue());
        assertEquals(0.5, perMillisecond, 1e-12);
    }

    @Test
    void negateIsMinusTheValueInTheSameUnit() {
        Temperature negated = celsius(2).negate();
        assertEquals(-2.0, negated.getValue());
        assertSame(DEGREE_CELSIUS, negated.getUnit());
    }

    @Test
    void toStatesTheQuantityInTheGivenUnit() {
        Length metres = Length.of(2, KILOMETRE).to(METRE);
        assertEquals(2000.0, metres.getValue());
        assertSame(METRE, metres.getUnit());
    }

    @Test
    void toSystemUnitIsToTheSystemUnit() {
        Quantity<javax.measure.quantity.Temperature> k = celsius(2).toSystemUnit();
        assertEquals(275.15, k.getValue().doubleValue(), 1e-12);
        assertEquals(KELVIN, k.getUnit());
    }

    @Test
    void asTypeSpeedOfLightExample() {
        Quantity<Speed> c = Length.of(2, KILOMETRE).multiply(299792458).divide(Time.of(1, SECOND))
                .asType(Speed.class);
        assertInstanceOf(Speed.class, c);
        assertEquals(599_584_916_000.0, c.getValue().doubleValue(), 1e-3);
    }

    @Test
    void asTypeThrowsClassCastExceptionForADifferentDimension() {
        Quantity<?> speed = Length.of(6, METRE).divide(Time.of(2, SECOND));
        assertThrows(ClassCastException.class, () -> speed.asType(javax.measure.quantity.Length.class));
    }

    @Test
    void asTypeThrowsUnsupportedOperationExceptionWithoutASystemUnit() {
        Quantity<?> speed = Length.of(6, METRE).divide(Time.of(2, SECOND));
        assertThrows(UnsupportedOperationException.class, () -> speed.asType(Unsupported.class));
    }

    @Test
    void getScaleReturnsTheScale() {
        assertSame(Quantity.Scale.ABSOLUTE, Length.of(1, METRE).getScale());
        assertSame(Quantity.Scale.RELATIVE, Length.of(1, METRE, Quantity.Scale.RELATIVE).getScale());
    }

    @Test
    void isEquivalentToNullThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> Length.of(1, METRE).isEquivalentTo(null));
    }

    @Test
    void implementationsAreComparable() {
        assertInstanceOf(Comparable.class, Length.of(1, METRE));
        assertEquals(0, Length.of(1, KILOMETRE).compareTo(Length.of(1000, METRE)));
        assertTrue(Length.of(1, METRE).compareTo(Length.of(1, KILOMETRE)) < 0);
    }
}
