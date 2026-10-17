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

final class UntypedValue extends AbstractQuantity<Untyped> implements Untyped {

    UntypedValue(double value, BigDecimal decimal, MathContext mc, Unit<Untyped> unit, Scale scale) {
        super(value, decimal, mc, unit, scale);
    }

    @Override
    protected Untyped create(double value, BigDecimal decimal, MathContext mc, Unit<Untyped> unit, Scale scale) {
        return new UntypedValue(value, decimal, mc, unit, scale);
    }
}
