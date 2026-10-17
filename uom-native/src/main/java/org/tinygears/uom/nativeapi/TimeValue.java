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

final class TimeValue extends AbstractQuantity<Time> implements Time {

    TimeValue(double value, Unit<Time> unit) {
        super(value, unit);
    }

    @Override
    Time create(double value, Unit<Time> unit) {
        return new TimeValue(value, unit);
    }
}
