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

final class SpeedValue extends AbstractQuantity<Speed> implements Speed {

    SpeedValue(double value, Unit<Speed> unit) {
        super(value, unit);
    }

    @Override
    Speed create(double value, Unit<Speed> unit) {
        return new SpeedValue(value, unit);
    }

    @Override
    public Length multiply(Time time) {
        return Length.of(systemValue() * systemValue(time), SI.METRE);
    }
}
