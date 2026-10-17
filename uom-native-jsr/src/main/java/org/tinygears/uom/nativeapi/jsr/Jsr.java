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

import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.nativeapi.Length;
import org.tinygears.uom.nativeapi.Quantity;
import org.tinygears.uom.nativeapi.Speed;
import org.tinygears.uom.nativeapi.Time;
import org.tinygears.uom.nativeapi.Unit;

/**
 * Converts between the native quantities and the JSR-385 quantities of uom. Both sides wrap the same engine units, so
 * a conversion copies the value and wraps the unit; nothing is converted. A decimal JSR-385 quantity becomes a
 * {@code double} (the slice has no decimals).
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
        return org.tinygears.uom.quantity.Length.of(length.value(), jsrUnit(length));
    }

    /**
     * The JSR-385 time of a native one.
     *
     * @param time the native time
     * @return the JSR-385 time
     */
    public static org.tinygears.uom.quantity.Time toJsr(Time time) {
        return org.tinygears.uom.quantity.Time.of(time.value(), jsrUnit(time));
    }

    /**
     * The JSR-385 speed of a native one.
     *
     * @param speed the native speed
     * @return the JSR-385 speed
     */
    public static org.tinygears.uom.quantity.Speed toJsr(Speed speed) {
        return org.tinygears.uom.quantity.Speed.of(speed.value(), jsrUnit(speed));
    }

    /**
     * The native length of a JSR-385 one of this implementation.
     *
     * @param length the JSR-385 length
     * @return the native length
     * @throws IllegalArgumentException if the unit is of another implementation
     */
    public static Length length(javax.measure.Quantity<javax.measure.quantity.Length> length) {
        return Length.of(length.getValue().doubleValue(), Unit.of(Length.class, engine(length.getUnit())));
    }

    /**
     * The native time of a JSR-385 one of this implementation.
     *
     * @param time the JSR-385 time
     * @return the native time
     * @throws IllegalArgumentException if the unit is of another implementation
     */
    public static Time time(javax.measure.Quantity<javax.measure.quantity.Time> time) {
        return Time.of(time.getValue().doubleValue(), Unit.of(Time.class, engine(time.getUnit())));
    }

    /**
     * The native speed of a JSR-385 one of this implementation.
     *
     * @param speed the JSR-385 speed
     * @return the native speed
     * @throws IllegalArgumentException if the unit is of another implementation
     */
    public static Speed speed(javax.measure.Quantity<javax.measure.quantity.Speed> speed) {
        return Speed.of(speed.getValue().doubleValue(), Unit.of(Speed.class, engine(speed.getUnit())));
    }

    private static <J extends javax.measure.Quantity<J>> UomUnit<J> jsrUnit(Quantity<?> q) {
        return UomUnit.unchecked(UomUnit.of(q.unit().engine()));
    }

    private static org.tinygears.uom.engine.Unit engine(javax.measure.Unit<?> unit) {
        if (unit instanceof UomUnit<?> u) {
            return u.engine();
        }
        throw new IllegalArgumentException("unit of another implementation: " + unit.getClass().getName());
    }
}
