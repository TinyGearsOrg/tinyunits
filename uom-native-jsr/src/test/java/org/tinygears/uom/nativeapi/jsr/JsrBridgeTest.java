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

import java.math.BigDecimal;
import java.math.MathContext;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.core.quantity.AbstractDecimalQuantity;
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
        assertEquals(20.0, speed.to(SI.KILOMETRE_PER_HOUR).value());
        org.tinygears.uom.quantity.Speed jsrSpeed = Jsr.toJsr(speed.to(SI.KILOMETRE_PER_HOUR));
        assertEquals(20.0, jsrSpeed.getValue());
        assertEquals("km/h", jsrSpeed.getUnit().getSymbol());
        assertEquals(Time.of(0.5, SI.HOUR), Jsr.time(Jsr.toJsr(Time.of(0.5, SI.HOUR))));
    }

    @Test
    void decimalsKeepValueAndContextAndComputeAlike() {
        Length marathon = Length.of(new BigDecimal("42.195"), SI.KILOMETRE, MathContext.DECIMAL64);
        org.tinygears.uom.quantity.Length jsr = Jsr.toJsr(marathon);
        assertEquals(new BigDecimal("42.195"), jsr.getValue());
        assertEquals(MathContext.DECIMAL64, ((AbstractDecimalQuantity<?, ?>) jsr).mathContext());
        assertEquals(marathon, Jsr.length(jsr));
        // one engine kernel: the same exact, once-rounded results on both sides
        Time t = Time.of(new BigDecimal("3"), SI.HOUR);
        Speed pace = marathon.divide(t);
        assertEquals(new BigDecimal("3.906944444444444"), pace.value());
        assertEquals(pace.value(), jsr.divide(Jsr.toJsr(t)).getValue());
        Length plus = marathon.add(Length.of(new BigDecimal("195"), SI.METRE));
        assertEquals(new BigDecimal("42.390"), plus.value());
        assertEquals(plus.value(), jsr.add(Jsr.toJsr(Length.of(new BigDecimal("195"), SI.METRE))).getValue());
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
