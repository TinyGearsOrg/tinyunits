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
import javax.measure.Unit;
import javax.measure.quantity.Speed;
import javax.measure.spi.QuantityFactory;

import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.UomUnit;

/** Stand-in for a generated double implementation, with its factory. */
class TestSpeedImpl extends AbstractDoubleQuantity<Speed, TestSpeed> implements TestSpeed {
    @Serial
    private static final long serialVersionUID = 1L;

    static final QuantityFactory<Speed> FACTORY = new QuantityFactory<>() {
        @Override
        public Quantity<Speed> create(Number value, Unit<Speed> unit, Quantity.Scale scale) {
            return new TestSpeedImpl(value.doubleValue(), UomUnit.unchecked((UomUnit<?>) unit), scale);
        }

        @Override
        public Quantity<Speed> create(Number value, Unit<Speed> unit) {
            return create(value, unit, Quantity.Scale.ABSOLUTE);
        }

        @Override
        public Unit<Speed> getSystemUnit() {
            return UomUnit.unchecked(BaseUnits.METRE.divide(BaseUnits.SECOND));
        }
    };

    TestSpeedImpl(double value, UomUnit<Speed> unit, Quantity.Scale scale) {
        super(value, unit, scale);
    }

    @Override
    protected Class<Speed> quantityType() {
        return Speed.class;
    }

    @Override
    protected TestSpeed create(double value, UomUnit<Speed> unit, Quantity.Scale scale) {
        return new TestSpeedImpl(value, unit, scale);
    }

    @Override
    protected TestSpeed createDecimal(BigDecimal value, UomUnit<Speed> unit, Quantity.Scale scale, MathContext mc) {
        return new TestSpeedDecimalImpl(value, unit, scale, mc);
    }
}
