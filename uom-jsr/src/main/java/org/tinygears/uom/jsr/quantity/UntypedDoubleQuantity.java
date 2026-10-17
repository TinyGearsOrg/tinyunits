/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.jsr.quantity;

import java.io.Serial;
import java.math.BigDecimal;
import java.math.MathContext;
import javax.measure.Quantity;

import org.tinygears.uom.jsr.UomUnit;

/** A quantity of no standard type: a product, quotient or inverse, or a parsed value in an untyped unit. */
public final class UntypedDoubleQuantity extends AbstractDoubleQuantity<Untyped, Untyped> implements Untyped {

    @Serial
    private static final long serialVersionUID = 1L;

    UntypedDoubleQuantity(double value, UomUnit<Untyped> unit, Quantity.Scale scale) {
        super(value, unit, scale);
    }

    /**
     * An {@code ABSOLUTE} quantity.
     *
     * @param value the value
     * @param unit the unit
     * @return the quantity
     */
    public static UntypedDoubleQuantity of(double value, UomUnit<?> unit) {
        return new UntypedDoubleQuantity(value, UomUnit.unchecked(unit), Quantity.Scale.ABSOLUTE);
    }

    @Override
    protected Class<Untyped> quantityType() {
        return Untyped.class;
    }

    @Override
    protected Untyped create(double value, UomUnit<Untyped> unit, Quantity.Scale scale) {
        return new UntypedDoubleQuantity(value, unit, scale);
    }

    @Override
    protected Untyped createDecimal(BigDecimal value, UomUnit<Untyped> unit, Quantity.Scale scale, MathContext mc) {
        return new UntypedDecimalQuantity(value, unit, scale, mc);
    }
}
