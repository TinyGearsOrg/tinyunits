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

/** A time. */
public interface Time extends Quantity<Time> {

    /**
     * A time.
     *
     * @param value the value
     * @param unit the unit
     * @return the time
     */
    static Time of(double value, Unit<Time> unit) {
        return new TimeValue(value, unit);
    }
}
