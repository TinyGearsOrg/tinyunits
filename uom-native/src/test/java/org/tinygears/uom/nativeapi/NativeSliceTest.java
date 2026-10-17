/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.nativeapi;

import java.math.BigDecimal;
import java.math.MathContext;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.engine.UnitAtom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The native slice: typed units, typed products, and no JSR-385 types anywhere. */
class NativeSliceTest {

    @Test
    void unitsAreTypedWithOurQuantityTypes() {
        // the declarations JSR-385's generics rule out: our Length as the unit's type parameter
        Unit<Length> km = SI.KILOMETRE;
        Unit<Speed> kmh = SI.KILOMETRE_PER_HOUR;
        assertEquals(Length.class, km.type());
        assertEquals("km/h", kmh.symbol());
        assertEquals(1000.0, Length.of(1, km).to(SI.METRE).value());
    }

    @Test
    void productsAndQuotientsAreTyped() {
        Speed pace = Length.of(42.195, SI.KILOMETRE).divide(Time.of(3, SI.HOUR));
        assertEquals(SI.METRE_PER_SECOND, pace.unit());
        assertEquals(42195.0 / 10800.0, pace.value());
        assertEquals(PACE_KMH, pace.to(SI.KILOMETRE_PER_HOUR).value());
        Length covered = pace.multiply(Time.of(30, SI.MINUTE));
        assertEquals(SI.METRE, covered.unit());
        assertEquals(42195.0 / 10800.0 * 1800.0, covered.value());
        Time needed = Length.of(10, SI.KILOMETRE).divide(Speed.of(20, SI.KILOMETRE_PER_HOUR));
        assertEquals(SI.SECOND, needed.unit());
        assertEquals(1800.0, needed.value());
    }

    // 42.195 km / 3 h is 14.065 km/h; through m/s in double it picks up one unit in the last place
    static final double PACE_KMH = 14.065000000000001;

    @Test
    void arithmeticAndComparisonAcrossUnits() {
        Length a = Length.of(1, SI.KILOMETRE);
        Length b = Length.of(500, SI.METRE);
        assertEquals(Length.of(1.5, SI.KILOMETRE), a.add(b));
        assertEquals(Length.of(0.5, SI.KILOMETRE), a.subtract(b));
        assertEquals(Length.of(2, SI.KILOMETRE), a.multiply(2));
        assertEquals(1, a.compareTo(b));
        assertEquals(0, a.compareTo(Length.of(1000, SI.METRE)));
        assertTrue(a.isCloseTo(Length.of(1000.0000001, SI.METRE), 1e-9));
        assertFalse(a.isCloseTo(Length.of(1000.001, SI.METRE), 1e-9));
        assertEquals("1.0 km", a.toString());
    }

    @Test
    void decimalsAreExactAndRoundedOnce() {
        // the engine's arithmetic, shared with the JSR-385 quantities: exact, rounded once to the context
        Length a = Length.of(new BigDecimal("0.1"), SI.KILOMETRE);
        Length sum = a.add(Length.of(new BigDecimal("0.2"), SI.METRE));
        assertEquals(new BigDecimal("0.1002"), sum.value());
        assertEquals(MathContext.DECIMAL128, sum.mathContext());
        Speed pace = Length.of(new BigDecimal("42.195"), SI.KILOMETRE).divide(Time.of(3, SI.HOUR));
        assertEquals(new BigDecimal("3.906944444444444444444444444444444"), pace.value());
        assertEquals(new BigDecimal("14.06500000000000000000000000000000"), pace.to(SI.KILOMETRE_PER_HOUR).value());
        Speed rounded = Length.of(new BigDecimal("42.195"), SI.KILOMETRE, MathContext.DECIMAL32)
                .divide(Time.of(new BigDecimal("3"), SI.HOUR));
        assertEquals(new BigDecimal("3.906944"), rounded.value());
        assertEquals(MathContext.DECIMAL32, rounded.mathContext());
        assertEquals(new BigDecimal("0.03333333"), a.divide(3).toDecimal(MathContext.DECIMAL32).value());
    }

    @Test
    void mixedOperandsAreDecimal() {
        Length d = Length.of(new BigDecimal("0.1"), SI.METRE, MathContext.DECIMAL64);
        Length sum = Length.of(0.2, SI.METRE).add(d);
        assertEquals(new BigDecimal("0.3"), sum.value());
        assertEquals(MathContext.DECIMAL64, sum.mathContext());
        assertEquals(0.30000000000000004, Length.of(0.2, SI.METRE).add(Length.of(0.1, SI.METRE)).value());
        assertEquals(0, Length.of(0.1, SI.KILOMETRE).compareTo(Length.of(new BigDecimal("100"), SI.METRE)));
        assertEquals(1, Length.of(Double.POSITIVE_INFINITY, SI.METRE).compareTo(d));
        assertEquals(Length.of(2.0, SI.METRE), Length.of(new BigDecimal("2.00"), SI.METRE).toDouble());
        assertEquals(Length.of(new BigDecimal("2"), SI.METRE), Length.of(new BigDecimal("2.00"), SI.METRE));
        assertFalse(Length.of(2.0, SI.METRE).equals(Length.of(new BigDecimal("2"), SI.METRE)));
    }

    @Test
    void decimalComparisonIsExact() {
        Length a = Length.of(new BigDecimal("1000.0000000000001"), SI.METRE);
        assertEquals(1, a.compareTo(Length.of(1, SI.KILOMETRE)));
        assertTrue(a.isCloseTo(Length.of(1, SI.KILOMETRE), 1e-16));
        assertFalse(a.isCloseTo(Length.of(1, SI.KILOMETRE), 1e-17));
    }

    @Test
    void aUnitOfAnotherDimensionIsRefused() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> Unit.of(Length.class, org.tinygears.uom.engine.Unit.base(UnitAtom.SECOND, "second")));
        assertEquals("s (T) is not a unit of Length", e.getMessage());
    }

    @Test
    void noJsr385TypeIsReachable() {
        // the module depends on the engine only: unit-api is not even on the class path
        assertThrows(ClassNotFoundException.class, () -> Class.forName("javax.measure.Unit"));
    }
}
