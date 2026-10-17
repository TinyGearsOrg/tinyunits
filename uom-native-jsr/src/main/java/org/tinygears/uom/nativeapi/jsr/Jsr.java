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

import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.core.quantity.AbstractDecimalQuantity;
import org.tinygears.uom.nativeapi.Length;
import org.tinygears.uom.nativeapi.Quantity;
import org.tinygears.uom.nativeapi.Speed;
import org.tinygears.uom.nativeapi.Time;
import org.tinygears.uom.nativeapi.Unit;

/**
 * Converts between the native quantities and the JSR-385 quantities of uom. Both sides wrap the same engine units, so
 * a conversion copies the value, its number type and context, and wraps the unit; nothing is converted or rounded. A
 * decimal of another implementation's quantity has no context and counts as unlimited. The slice has no offset units,
 * so the scale is not carried.
 */
public final class Jsr {

    private Jsr() {
    }

    /**
     * The JSR-385 length of a native one.
     *
     * @param length the native length
     * @return the JSR-385 length, a {@code javax.measure.quantity.Length}
     */
    public static org.tinygears.uom.quantity.Length toJsr(Length length) {
        UomUnit<javax.measure.quantity.Length> unit = jsrUnit(length);
        return length.value() instanceof BigDecimal d
                ? org.tinygears.uom.quantity.Length
                        .of(d, unit, javax.measure.Quantity.Scale.ABSOLUTE, length.mathContext())
                : org.tinygears.uom.quantity.Length.of(length.value().doubleValue(), unit);
    }

    /**
     * The JSR-385 time of a native one.
     *
     * @param time the native time
     * @return the JSR-385 time
     */
    public static org.tinygears.uom.quantity.Time toJsr(Time time) {
        UomUnit<javax.measure.quantity.Time> unit = jsrUnit(time);
        return time.value() instanceof BigDecimal d
                ? org.tinygears.uom.quantity.Time.of(d, unit, javax.measure.Quantity.Scale.ABSOLUTE, time.mathContext())
                : org.tinygears.uom.quantity.Time.of(time.value().doubleValue(), unit);
    }

    /**
     * The JSR-385 speed of a native one.
     *
     * @param speed the native speed
     * @return the JSR-385 speed
     */
    public static org.tinygears.uom.quantity.Speed toJsr(Speed speed) {
        UomUnit<javax.measure.quantity.Speed> unit = jsrUnit(speed);
        return speed.value() instanceof BigDecimal d
                ? org.tinygears.uom.quantity.Speed
                        .of(d, unit, javax.measure.Quantity.Scale.ABSOLUTE, speed.mathContext())
                : org.tinygears.uom.quantity.Speed.of(speed.value().doubleValue(), unit);
    }

    /**
     * The native length of a JSR-385 one of this implementation.
     *
     * @param length the JSR-385 length
     * @return the native length
     * @throws IllegalArgumentException if the unit is of another implementation
     */
    public static Length length(javax.measure.Quantity<javax.measure.quantity.Length> length) {
        Unit<Length> unit = Unit.of(Length.class, engine(length.getUnit()));
        return length.getValue() instanceof BigDecimal d
                ? Length.of(d, unit, context(length))
                : Length.of(length.getValue().doubleValue(), unit);
    }

    /**
     * The native time of a JSR-385 one of this implementation.
     *
     * @param time the JSR-385 time
     * @return the native time
     * @throws IllegalArgumentException if the unit is of another implementation
     */
    public static Time time(javax.measure.Quantity<javax.measure.quantity.Time> time) {
        Unit<Time> unit = Unit.of(Time.class, engine(time.getUnit()));
        return time.getValue() instanceof BigDecimal d
                ? Time.of(d, unit, context(time))
                : Time.of(time.getValue().doubleValue(), unit);
    }

    /**
     * The native speed of a JSR-385 one of this implementation.
     *
     * @param speed the JSR-385 speed
     * @return the native speed
     * @throws IllegalArgumentException if the unit is of another implementation
     */
    public static Speed speed(javax.measure.Quantity<javax.measure.quantity.Speed> speed) {
        Unit<Speed> unit = Unit.of(Speed.class, engine(speed.getUnit()));
        return speed.getValue() instanceof BigDecimal d
                ? Speed.of(d, unit, context(speed))
                : Speed.of(speed.getValue().doubleValue(), unit);
    }

    private static <J extends javax.measure.Quantity<J>> UomUnit<J> jsrUnit(Quantity<?> q) {
        return UomUnit.unchecked(UomUnit.of(q.unit().engine()));
    }

    private static MathContext context(javax.measure.Quantity<?> q) {
        return q instanceof AbstractDecimalQuantity<?, ?> d ? d.mathContext() : MathContext.UNLIMITED;
    }

    private static org.tinygears.uom.engine.Unit engine(javax.measure.Unit<?> unit) {
        if (unit instanceof UomUnit<?> u) {
            return u.engine();
        }
        throw new IllegalArgumentException("unit of another implementation: " + unit.getClass().getName());
    }
}
