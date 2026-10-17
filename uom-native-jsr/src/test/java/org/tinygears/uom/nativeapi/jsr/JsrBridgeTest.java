/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.nativeapi.jsr;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.nativeapi.Length;
import org.tinygears.uom.nativeapi.SI;
import org.tinygears.uom.nativeapi.Speed;
import org.tinygears.uom.nativeapi.Time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Native and JSR-385 quantities convert into each other over the same engine units. */
class JsrBridgeTest {

    @Test
    void nativeToJsrAndBack() {
        Length nativeLength = Length.of(42.195, SI.KILOMETRE);
        org.tinygears.uom.quantity.Length jsr = Jsr.toJsr(nativeLength);
        assertEquals(42.195, jsr.getValue());
        assertEquals("km", jsr.getUnit().getSymbol());
        // the JSR-385 side wraps the very engine unit of the native side
        assertSame(SI.KILOMETRE.engine(), ((org.tinygears.uom.core.UomUnit<?>) jsr.getUnit()).engine());
        assertEquals(42195.0, jsr.to(org.tinygears.uom.systems.SI.METRE).getValue());
        assertEquals(nativeLength, Jsr.length(jsr));
    }

    @Test
    void jsrToNativeKeepsTheUnitAndTypesTheProduct() {
        org.tinygears.uom.quantity.Length distance = org.tinygears.uom.quantity.Length
                .of(10, org.tinygears.uom.systems.SI.KILOMETRE);
        org.tinygears.uom.quantity.Time duration = org.tinygears.uom.quantity.Time
                .of(0.5, org.tinygears.uom.systems.SI.HOUR);
        Speed speed = Jsr.length(distance).divide(Jsr.time(duration));
        assertEquals(20.0, speed.valueIn(SI.KILOMETRE_PER_HOUR));
        org.tinygears.uom.quantity.Speed jsrSpeed = Jsr.toJsr(speed.to(SI.KILOMETRE_PER_HOUR));
        assertEquals(20.0, jsrSpeed.getValue());
        assertEquals("km/h", jsrSpeed.getUnit().getSymbol());
        assertEquals(Time.of(0.5, SI.HOUR), Jsr.time(Jsr.toJsr(Time.of(0.5, SI.HOUR))));
    }

    @Test
    void aQuantityOfAnotherImplementationIsRefused() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> Jsr.length(
                        tech.units.indriya.quantity.Quantities.getQuantity(1, tech.units.indriya.unit.Units.METRE)));
        assertEquals("unit of another implementation: tech.units.indriya.unit.BaseUnit", e.getMessage());
    }
}
