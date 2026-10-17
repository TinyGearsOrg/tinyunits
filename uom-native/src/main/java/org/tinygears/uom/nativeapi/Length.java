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
import java.util.Objects;

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
        return new LengthValue(value, null, null, unit);
    }

    /**
     * A decimal length with context {@code DECIMAL128}; the value is kept as given.
     *
     * @param value the value
     * @param unit the unit
     * @return the length
     */
    static Length of(BigDecimal value, Unit<Length> unit) {
        return of(value, unit, MathContext.DECIMAL128);
    }

    /**
     * A decimal length with context {@code mc}; the value is kept as given.
     *
     * @param value the value
     * @param unit the unit
     * @param mc the context that rounds results
     * @return the length
     */
    static Length of(BigDecimal value, Unit<Length> unit, MathContext mc) {
        return new LengthValue(0, Objects.requireNonNull(value, "value"), mc, unit);
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
