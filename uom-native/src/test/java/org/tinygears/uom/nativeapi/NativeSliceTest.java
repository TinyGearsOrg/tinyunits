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
        assertEquals(1000.0, Length.of(1, km).valueIn(SI.METRE));
    }

    @Test
    void productsAndQuotientsAreTyped() {
        Speed pace = Length.of(42.195, SI.KILOMETRE).divide(Time.of(3, SI.HOUR));
        assertEquals(SI.METRE_PER_SECOND, pace.unit());
        assertEquals(42195.0 / 10800.0, pace.value());
        assertEquals(PACE_KMH, pace.valueIn(SI.KILOMETRE_PER_HOUR));
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
