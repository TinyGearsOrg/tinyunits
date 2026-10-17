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

/** A speed; times a time it is a {@link Length}. */
public interface Speed extends Quantity<Speed> {

    /**
     * A speed.
     *
     * @param value the value
     * @param unit the unit
     * @return the speed
     */
    static Speed of(double value, Unit<Speed> unit) {
        return new SpeedValue(value, unit);
    }

    /**
     * The length covered at this speed in {@code time}, in metres.
     *
     * @param time the time
     * @return the length
     */
    Length multiply(Time time);
}
