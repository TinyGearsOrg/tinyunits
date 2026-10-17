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
        return new TimeValue(value, null, null, unit);
    }

    /**
     * A decimal time with context {@code DECIMAL128}; the value is kept as given.
     *
     * @param value the value
     * @param unit the unit
     * @return the time
     */
    static Time of(BigDecimal value, Unit<Time> unit) {
        return of(value, unit, MathContext.DECIMAL128);
    }

    /**
     * A decimal time with context {@code mc}; the value is kept as given.
     *
     * @param value the value
     * @param unit the unit
     * @param mc the context that rounds results
     * @return the time
     */
    static Time of(BigDecimal value, Unit<Time> unit, MathContext mc) {
        return new TimeValue(0, Objects.requireNonNull(value, "value"), mc, unit);
    }
}
