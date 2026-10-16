/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.core;

import javax.measure.Unit;
import javax.measure.quantity.Angle;
import javax.measure.quantity.Dimensionless;
import javax.measure.quantity.Frequency;
import javax.measure.quantity.Length;
import javax.measure.quantity.Radioactivity;
import javax.measure.quantity.Speed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tinygears.uom.core.BaseUnits.METRE;
import static org.tinygears.uom.core.BaseUnits.ONE;
import static org.tinygears.uom.core.BaseUnits.SECOND;

class UomUnitAsTypeTest {

    private static final UomUnit<?> HZ = ONE.divide(SECOND).alternate("Hz");
    private static final UomUnit<?> BQ = ONE.divide(SECOND).alternate("Bq");
    private static final UomUnit<?> RAD = ONE.alternate("rad");

    @Test
    void matchingSystemUnit() {
        UomUnit<Length> km = METRE.multiply(1000);
        Unit<Length> typed = km.asType(Length.class);
        assertSame(km, typed);
    }

    @Test
    void mismatchedSystemUnit() {
        assertThrows(ClassCastException.class, () -> METRE.asType(Speed.class));
    }

    @Test
    void alternateUnitsCountByDimension() {
        assertSame(HZ, HZ.asType(Frequency.class));
        UomUnit<?> khz = HZ.multiply(1000);
        assertSame(khz, khz.asType(Frequency.class));
        // 1/s, Hz and Bq have the dimension of frequency: the Unit Javadoc rule is the dimension.
        UomUnit<?> perSecond = ONE.divide(SECOND);
        assertSame(perSecond, perSecond.asType(Frequency.class));
        assertSame(perSecond, perSecond.asType(Radioactivity.class));
        assertSame(HZ, HZ.asType(Radioactivity.class));
        // The Javadoc example, and an angle times a length measures a length.
        assertSame(RAD, RAD.asType(Angle.class));
        assertSame(RAD, RAD.asType(Dimensionless.class));
        UomUnit<?> arc = RAD.multiply(METRE);
        assertSame(arc, arc.asType(Length.class));
        UomUnit<?> hertzSecond = HZ.multiply(SECOND);
        assertSame(hertzSecond, hertzSecond.asType(Dimensionless.class));
        assertThrows(ClassCastException.class, () -> HZ.asType(Length.class));
        assertThrows(ClassCastException.class, () -> RAD.asType(Frequency.class));
    }

    @Test
    void unitEqualityStaysStructural() {
        UomUnit<?> perSecond = ONE.divide(SECOND);
        assertNotEquals(HZ, perSecond);
        assertNotEquals(HZ, BQ);
        assertNotEquals(RAD, ONE);
        assertTrue(HZ.isEquivalentTo(UomUnit.unchecked(BQ)));
    }
}
