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
import java.math.MathContext;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.jsr.quantity.AbstractDecimalQuantity;
import org.tinygears.uom.jsr.quantity.Untyped;
import org.tinygears.uom.jsr.quantity.UomQuantity;
import org.tinygears.uom.jsr.systems.SI;
import org.tinygears.uom.jsr.systems.USCustomary;
import org.tinygears.uom.jsr.systems.UomQuantityFormat;
import org.tinygears.uom.jsr.systems.UomUnitFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The additions of {@link UomQuantity} as the typed interfaces offer them. */
class UomQuantityTest {

    @Test
    void asNarrowsProductsAndParsedQuantitiesWithoutACast() {
        UomQuantity<?, ?> quotient = Length.of(90, SI.KILOMETRE).divide(Time.of(1, SI.HOUR));
        assertInstanceOf(Untyped.class, quotient);
        Speed speed = quotient.as(Speed.class);
        assertEquals(25.0, speed.getValue());
        assertEquals(SI.METRE.divide(SI.SECOND), speed.getUnit());
        // the result chains with the typed methods
        Speed inSi = speed.toSystem(SI.SYSTEM);
        assertEquals(25.0, inSi.getValue().doubleValue());

        UomQuantityFormat format = new UomQuantityFormat(UomUnitFormat.standard());
        Speed parsed = format.parse("90 km/h").as(Speed.class);
        assertEquals(90.0, parsed.getValue());
        // value, scale and context are kept
        Speed decimal = format.withDecimals(MathContext.DECIMAL32).parse("90.0 km/h").as(Speed.class);
        assertEquals(new BigDecimal("90.0"), decimal.getValue());
        assertEquals(MathContext.DECIMAL32, ((AbstractDecimalQuantity<?, ?>) decimal).mathContext());
    }

    @Test
    void asReturnsAQuantityOfThatTypeAsItIs() {
        Length length = Length.of(2, SI.METRE);
        assertSame(length, length.as(Length.class));
        Untyped untyped = Length.of(2, SI.METRE).multiply(Length.of(3, SI.METRE)).as(Untyped.class);
        assertSame(untyped, untyped.as(Untyped.class));
        assertEquals(Area.of(6, SI.SQUARE_METRE), untyped.as(Area.class));
    }

    @Test
    void asRefusesAnotherDimension() {
        UomQuantity<?, ?> area = Length.of(2, SI.METRE).multiply(Length.of(3, SI.METRE));
        assertThrows(ClassCastException.class, () -> area.as(Speed.class));
        assertThrows(NullPointerException.class, () -> area.as(null));
    }

    @Test
    void typedQuantitiesCompareWithinAToleranceTheCallerStates() {
        Length foot = Length.of(1, USCustomary.FOOT);
        assertTrue(foot.isCloseTo(Length.of(0.3048, SI.METRE), 1e-15));
        assertTrue(foot.isCloseTo(Length.of(0.305, SI.METRE), Length.of(1, SI.MILLIMETRE)));
        assertFalse(foot.isCloseTo(Length.of(0.306, SI.METRE), Length.of(1, SI.MILLIMETRE)));
        // A radian converted to degrees in DECIMAL128 is rounded: not equivalent to its source, but close to it.
        Angle radian = Angle.of(BigDecimal.ONE, SI.RADIAN);
        Angle degrees = radian.to(SI.DEGREE);
        assertEquals(new BigDecimal("57.29577951308232087679815481410517"), degrees.getValue());
        assertFalse(degrees.isEquivalentTo(radian));
        assertTrue(degrees.isCloseTo(radian, 1e-33));
        // 180/pi = 57.29577951308232087679815481410517033...: rounded by 3.3e-34, relative 5.8e-36
        assertTrue(degrees.isCloseTo(radian, 6e-36));
        assertFalse(degrees.isCloseTo(radian, 5e-36));
    }
}
