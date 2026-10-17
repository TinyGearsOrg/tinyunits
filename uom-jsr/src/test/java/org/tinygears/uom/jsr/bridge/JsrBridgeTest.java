/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.jsr;

import java.math.BigDecimal;
import java.math.MathContext;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.Scale;
import org.tinygears.uom.Untyped;
import org.tinygears.uom.jsr.UomUnit;
import org.tinygears.uom.jsr.quantity.AbstractDecimalQuantity;
import org.tinygears.uom.quantity.Length;
import org.tinygears.uom.quantity.Speed;
import org.tinygears.uom.quantity.Temperature;
import org.tinygears.uom.quantity.Time;
import org.tinygears.uom.systems.SI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Native and JSR-385 quantities convert into each other over the same engine units. */
class JsrBridgeTest {

    @Test
    void nativeToJsrAndBack() {
        Length nativeLength = Length.of(42.195, SI.KILOMETRE);
        org.tinygears.uom.jsr.quantity.Length jsr = Jsr
                .toJsr(nativeLength, org.tinygears.uom.jsr.quantity.Length.class);
        assertEquals(42.195, jsr.getValue());
        assertEquals("km", jsr.getUnit().getSymbol());
        // the JSR-385 side wraps the very engine unit of the native side
        assertSame(SI.KILOMETRE.engine(), ((UomUnit<?>) jsr.getUnit()).engine());
        assertEquals(42195.0, jsr.to(org.tinygears.uom.jsr.systems.SI.METRE).getValue());
        assertEquals(nativeLength, Jsr.fromJsr(jsr, Length.class));
    }

    @Test
    void jsrToNativeKeepsTheUnitAndTypesTheProduct() {
        var distance = org.tinygears.uom.jsr.quantity.Length.of(10, org.tinygears.uom.jsr.systems.SI.KILOMETRE);
        var duration = org.tinygears.uom.jsr.quantity.Time.of(0.5, org.tinygears.uom.jsr.systems.SI.HOUR);
        Speed speed = Jsr.fromJsr(distance, Length.class).divide(Jsr.fromJsr(duration, Time.class));
        assertEquals(20 / 3.6, speed.value());
        org.tinygears.uom.jsr.quantity.Speed jsrSpeed = Jsr.toJsr(speed, org.tinygears.uom.jsr.quantity.Speed.class);
        assertEquals(20 / 3.6, jsrSpeed.getValue());
        assertEquals("m/s", jsrSpeed.getUnit().getSymbol());
    }

    @Test
    void scaleAndUntypedQuantitiesCarryOver() {
        Temperature diff = Temperature.of(5, SI.DEGREE_CELSIUS, Scale.RELATIVE);
        var jsr = Jsr.toJsr(diff);
        assertEquals(javax.measure.Quantity.Scale.RELATIVE, jsr.getScale());
        assertEquals(diff, Jsr.fromJsr(jsr, Temperature.class));
        Untyped momentum = Untyped.of(6, SI.KILOGRAM.multiply(SI.METRE_PER_SECOND));
        var jsrMomentum = Jsr.toJsr(momentum);
        assertEquals(org.tinygears.uom.jsr.quantity.UntypedDoubleQuantity.class, jsrMomentum.getClass());
        assertEquals(momentum, Jsr.fromJsr(jsrMomentum, Untyped.class));
        assertEquals(
                "an untyped JSR-385 quantity is absolute: 1.0 kg·(m/s)",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> Jsr.toJsr(Untyped.of(1, momentum.unit(), Scale.RELATIVE))).getMessage());
    }

    @Test
    void decimalsKeepValueAndContextAndComputeAlike() {
        Length marathon = Length.of(new BigDecimal("42.195"), SI.KILOMETRE, Scale.ABSOLUTE, MathContext.DECIMAL64);
        var jsr = Jsr.toJsr(marathon, org.tinygears.uom.jsr.quantity.Length.class);
        assertEquals(new BigDecimal("42.195"), jsr.getValue());
        assertEquals(MathContext.DECIMAL64, ((AbstractDecimalQuantity<?, ?>) jsr).mathContext());
        assertEquals(marathon, Jsr.fromJsr(jsr, Length.class));
        Time t = Time.of(new BigDecimal("3"), SI.HOUR);
        Speed pace = marathon.divide(t);
        assertEquals(new BigDecimal("3.906944444444444"), pace.value());
        assertEquals(pace.value(), jsr.divide(Jsr.toJsr(t)).getValue());
    }

    @Test
    void aQuantityOfAnotherImplementationIsRefused() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> Jsr.fromJsr(
                        tech.units.indriya.quantity.Quantities.getQuantity(1, tech.units.indriya.unit.Units.METRE),
                        Length.class));
        assertEquals("unit of another implementation: tech.units.indriya.unit.BaseUnit", e.getMessage());
    }
}
