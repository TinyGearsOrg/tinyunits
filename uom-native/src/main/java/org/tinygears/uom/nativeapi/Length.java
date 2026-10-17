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

/** A length; divided by a time it is a {@link Speed}, by a speed a {@link Time}. */
public interface Length extends Quantity<Length> {

    /**
     * A length.
     *
     * @param value the value
     * @param unit the unit
     * @return the length
     */
    static Length of(double value, Unit<Length> unit) {
        return new LengthValue(value, unit);
    }

    /**
     * The speed of covering this length in {@code time}, in metres per second.
     *
     * @param time the time
     * @return the speed
     */
    Speed divide(Time time);

    /**
     * The time to cover this length at {@code speed}, in seconds.
     *
     * @param speed the speed
     * @return the time
     */
    Time divide(Speed speed);
}
