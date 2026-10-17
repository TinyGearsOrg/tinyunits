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

final class LengthValue extends AbstractQuantity<Length> implements Length {

    LengthValue(double value, BigDecimal decimal, MathContext mc, Unit<Length> unit) {
        super(value, decimal, mc, unit);
    }

    @Override
    Length create(double value, BigDecimal decimal, MathContext mc, Unit<Length> unit) {
        return new LengthValue(value, decimal, mc, unit);
    }

    @Override
    public Speed divide(Time time) {
        return product(time, true, SI.METRE_PER_SECOND, SpeedValue::new);
    }

    @Override
    public Time divide(Speed speed) {
        return product(speed, true, SI.SECOND, TimeValue::new);
    }
}
