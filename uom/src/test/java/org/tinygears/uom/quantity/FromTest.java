/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.quantity;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.MathContext;
import javax.measure.Quantity;
import javax.measure.Unit;

import org.junit.jupiter.api.Test;
import tech.units.indriya.quantity.Quantities;
import tech.units.indriya.unit.Units;

import org.tinygears.uom.systems.SI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code X.from(Quantity<?>)} gives a quantity of this implementation, which {@code asType} cannot take as a type. */
class FromTest {

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void everyTypedInterfaceReturnsItsOwnQuantitiesAsTheyAre() throws Exception {
        assertEquals(33, QuantityTypes.types().size());
        for (Class<?> standard : QuantityTypes.types()) {
            Class<?> ours = Class.forName("org.tinygears.uom.quantity." + standard.getSimpleName());
            Method from = ours.getMethod("from", Quantity.class);
            Unit unit = QuantityTypes.factory((Class) standard).getSystemUnit();
            for (Number value : new Number[] { 1.5, new BigDecimal("1.5") }) {
                Quantity<?> q = QuantityTypes.factory((Class) standard).create(value, unit);
                Object narrowed = from.invoke(null, q);
                assertSame(q, narrowed, ours.getSimpleName() + " " + value.getClass().getSimpleName());
                assertTrue(ours.isInstance(narrowed));
            }
        }
    }

    @Test
    void theProductOfTwoQuantitiesIsNarrowed() {
        Quantity<?> product = Length.of(36, SI.KILOMETRE).divide(Time.of(1, SI.HOUR));
        Speed speed = Speed.from(product);
        assertEquals(10.0, speed.to(SI.METRE_PER_SECOND).getValue().doubleValue(), 1e-12);
        assertEquals(product.getUnit(), speed.getUnit());
        assertInstanceOf(Speed.class, speed);
        // The value, unit, scale and the decimal kind stay.
        Quantity<?> decimal = Length
                .of(new BigDecimal("36"), SI.KILOMETRE, Quantity.Scale.RELATIVE, MathContext.DECIMAL32)
                .divide(Time.of(new BigDecimal("1"), SI.HOUR));
        Speed exact = Speed.from(decimal);
        assertInstanceOf(BigDecimal.class, exact.getValue());
        assertEquals(decimal.getValue(), exact.getValue());
        assertEquals(decimal.getScale(), exact.getScale());
    }

    @Test
    void aUnitOfTheRightDimensionNarrowsEvenWithAlternateUnits() {
        // asType goes by dimension (docs/DESIGN.md DR-02), so from does too.
        assertInstanceOf(Frequency.class, Frequency.from(Time.of(2, SI.SECOND).inverse()));
        assertInstanceOf(
                Dimensionless.class,
                Dimensionless.from(Frequency.of(2, SI.HERTZ).multiply(Time.of(3, SI.SECOND))));
        Length arc = Length.from(Angle.of(2, SI.RADIAN).multiply(Length.of(3, SI.METRE)));
        assertEquals(6.0, arc.to(SI.METRE).getValue().doubleValue(), 1e-12);
    }

    @Test
    void whatItCannotNarrowIsRefused() {
        assertThrows(ClassCastException.class, () -> Speed.from(Length.of(1, SI.METRE)));
        assertThrows(ClassCastException.class, () -> Length.from(Time.of(1, SI.SECOND)));
        assertEquals("quantity", assertThrows(NullPointerException.class, () -> Length.from(null)).getMessage());
        // A quantity of another implementation is not one of this implementation, so it is refused.
        Quantity<javax.measure.quantity.Length> foreign = Quantities.getQuantity(5, Units.METRE);
        String message = assertThrows(ClassCastException.class, () -> Length.from(foreign)).getMessage();
        assertTrue(message.endsWith(" is not a quantity of this implementation"), message);
    }
}
