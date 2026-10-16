/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.core.quantity;

import java.io.Serial;
import java.math.BigDecimal;
import java.math.MathContext;
import javax.measure.Quantity;
import javax.measure.quantity.Length;

import org.tinygears.uom.core.UomUnit;

/** Stand-in for a generated double implementation. */
class TestLengthImpl extends AbstractDoubleQuantity<Length, TestLength> implements TestLength {
    @Serial
    private static final long serialVersionUID = 1L;

    TestLengthImpl(double value, UomUnit<Length> unit, Quantity.Scale scale) {
        super(value, unit, scale);
    }

    TestLengthImpl(double value, UomUnit<Length> unit) {
        this(value, unit, Quantity.Scale.ABSOLUTE);
    }

    @Override
    protected Class<Length> quantityType() {
        return Length.class;
    }

    @Override
    protected TestLength create(double value, UomUnit<Length> unit, Quantity.Scale scale) {
        return new TestLengthImpl(value, unit, scale);
    }

    @Override
    protected TestLength createDecimal(BigDecimal value, UomUnit<Length> unit, Quantity.Scale scale, MathContext mc) {
        return new TestLengthDecimalImpl(value, unit, scale, mc);
    }
}
