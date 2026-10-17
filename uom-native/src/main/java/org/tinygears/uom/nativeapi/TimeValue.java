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

final class TimeValue extends AbstractQuantity<Time> implements Time {

    TimeValue(double value, BigDecimal decimal, MathContext mc, Unit<Time> unit) {
        super(value, decimal, mc, unit);
    }

    @Override
    Time create(double value, BigDecimal decimal, MathContext mc, Unit<Time> unit) {
        return new TimeValue(value, decimal, mc, unit);
    }
}
