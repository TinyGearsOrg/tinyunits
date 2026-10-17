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

final class LengthValue extends AbstractQuantity<Length> implements Length {

    LengthValue(double value, Unit<Length> unit) {
        super(value, unit);
    }

    @Override
    Length create(double value, Unit<Length> unit) {
        return new LengthValue(value, unit);
    }

    @Override
    public Speed divide(Time time) {
        return Speed.of(systemValue() / systemValue(time), SI.METRE_PER_SECOND);
    }

    @Override
    public Time divide(Speed speed) {
        return Time.of(systemValue() / systemValue(speed), SI.SECOND);
    }
}
