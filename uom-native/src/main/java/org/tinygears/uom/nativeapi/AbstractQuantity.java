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

import java.util.Objects;

/**
 * The {@code double} quantities of the slice: a value and a unit, compared and added in the system unit.
 *
 * @param <Q> the quantity type
 */
abstract class AbstractQuantity<Q extends Quantity<Q>> implements Quantity<Q> {
    private final double value;
    private final Unit<Q> unit;

    AbstractQuantity(double value, Unit<Q> unit) {
        this.value = value;
        this.unit = Objects.requireNonNull(unit, "unit");
    }

    /** A quantity of this type. */
    abstract Q create(double value, Unit<Q> unit);

    @Override
    public double value() {
        return value;
    }

    @Override
    public Unit<Q> unit() {
        return unit;
    }

    /** The value in the system unit. */
    double systemValue() {
        return unit.engine().toSystem().convert(value);
    }

    static <Q extends Quantity<Q>> double systemValue(Q q) {
        return q.unit().engine().toSystem().convert(q.value());
    }

    @Override
    public double valueIn(Unit<Q> target) {
        return unit.converterTo(target).convert(value);
    }

    @Override
    public Q to(Unit<Q> target) {
        return create(valueIn(target), target);
    }

    @Override
    public Q add(Q other) {
        return create(value + other.valueIn(unit), unit);
    }

    @Override
    public Q subtract(Q other) {
        return create(value - other.valueIn(unit), unit);
    }

    @Override
    public Q multiply(double factor) {
        return create(value * factor, unit);
    }

    @Override
    public Q divide(double divisor) {
        return create(value / divisor, unit);
    }

    @Override
    public boolean isCloseTo(Q other, double relativeTolerance) {
        double a = systemValue();
        double b = systemValue(other);
        return a == b || Math.abs(a - b) <= relativeTolerance * Math.max(Math.abs(a), Math.abs(b));
    }

    @Override
    public int compareTo(Q other) {
        return Double.compare(systemValue(), systemValue(other));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AbstractQuantity<?> q && getClass() == q.getClass() && Double.compare(value, q.value) == 0
                && unit.equals(q.unit);
    }

    @Override
    public int hashCode() {
        return 31 * Double.hashCode(value) + unit.hashCode();
    }

    @Override
    public String toString() {
        return value + " " + unit;
    }
}
