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

final class SpeedValue extends AbstractQuantity<Speed> implements Speed {

    SpeedValue(double value, BigDecimal decimal, MathContext mc, Unit<Speed> unit) {
        super(value, decimal, mc, unit);
    }

    @Override
    Speed create(double value, BigDecimal decimal, MathContext mc, Unit<Speed> unit) {
        return new SpeedValue(value, decimal, mc, unit);
    }

    @Override
    public Length multiply(Time time) {
        return product(time, false, SI.METRE, LengthValue::new);
    }
}
