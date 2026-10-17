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

/**
 * A quantity of no named type: a product, quotient or inverse without a declared relation, or a value in a unit of
 * any dimension. {@link Quantity#as(Class)} narrows it to a typed one of its dimension.
 */
public interface Untyped extends Quantity<Untyped> {

    /**
     * An absolute {@code double} quantity.
     *
     * @param value the value
     * @param unit the unit, of any dimension
     * @return the quantity
     */
    static Untyped of(double value, Unit<?> unit) {
        return of(value, unit, Scale.ABSOLUTE);
    }

    /**
     * An absolute decimal quantity with context {@code mc}; the value is kept as given.
     *
     * @param value the value
     * @param unit the unit, of any dimension
     * @param mc the context that rounds results
     * @return the quantity
     */
    static Untyped of(BigDecimal value, Unit<?> unit, MathContext mc) {
        return of(value, unit, Scale.ABSOLUTE, mc);
    }

    /**
     * A {@code double} quantity.
     *
     * @param value the value
     * @param unit the unit, of any dimension
     * @param scale the scale
     * @return the quantity
     */
    static Untyped of(double value, Unit<?> unit, Scale scale) {
        return new UntypedValue(value, null, null, unit.asType(Untyped.class), scale);
    }

    /**
     * A decimal quantity with context {@code mc}; the value is kept as given.
     *
     * @param value the value
     * @param unit the unit, of any dimension
     * @param scale the scale
     * @param mc the context that rounds results
     * @return the quantity
     */
    static Untyped of(BigDecimal value, Unit<?> unit, Scale scale, MathContext mc) {
        return new UntypedValue(
                0,
                java.util.Objects.requireNonNull(value, "value"),
                mc,
                unit.asType(Untyped.class),
                scale);
    }
}
