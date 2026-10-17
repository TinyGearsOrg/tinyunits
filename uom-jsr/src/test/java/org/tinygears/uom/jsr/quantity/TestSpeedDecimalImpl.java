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
import javax.measure.Unit;
import javax.measure.quantity.Speed;
import javax.measure.spi.QuantityFactory;

import org.tinygears.uom.engine.arithmetic.Precision;
import org.tinygears.uom.jsr.UomUnit;

/** Stand-in for a generated decimal speed, with a factory that follows D11 (DECIMAL128 for decimal values). */
class TestSpeedDecimalImpl extends AbstractDecimalQuantity<Speed, TestSpeed> implements TestSpeed {
    @Serial
    private static final long serialVersionUID = 1L;

    static final QuantityFactory<Speed> FACTORY = new QuantityFactory<>() {
        @Override
        public Quantity<Speed> create(Number value, Unit<Speed> unit, Quantity.Scale scale) {
            if (Precision.kind(value) == Precision.Kind.DOUBLE) {
                return TestSpeedImpl.FACTORY.create(value, unit, scale);
            }
            return new TestSpeedDecimalImpl(
                    Precision.toDecimal(value),
                    UomUnit.unchecked((UomUnit<?>) unit),
                    scale,
                    MathContext.DECIMAL128);
        }

        @Override
        public Quantity<Speed> create(Number value, Unit<Speed> unit) {
            return create(value, unit, Quantity.Scale.ABSOLUTE);
        }

        @Override
        public Unit<Speed> getSystemUnit() {
            return TestSpeedImpl.FACTORY.getSystemUnit();
        }
    };

    TestSpeedDecimalImpl(BigDecimal value, UomUnit<Speed> unit, Quantity.Scale scale, MathContext mc) {
        super(value, unit, scale, mc);
    }

    @Override
    protected Class<Speed> quantityType() {
        return Speed.class;
    }

    @Override
    protected TestSpeed create(BigDecimal value, UomUnit<Speed> unit, Quantity.Scale scale, MathContext mc) {
        return new TestSpeedDecimalImpl(value, unit, scale, mc);
    }

    @Override
    protected TestSpeed createDouble(double value, UomUnit<Speed> unit, Quantity.Scale scale) {
        return new TestSpeedImpl(value, unit, scale);
    }
}
