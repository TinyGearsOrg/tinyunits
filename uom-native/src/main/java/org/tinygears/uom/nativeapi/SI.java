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

import org.tinygears.uom.engine.UnitAtom;

/** The SI units of the slice, typed with this library's quantity types. */
public final class SI {

    /** The metre, {@code m}. */
    public static final Unit<Length> METRE = Unit
            .of(Length.class, org.tinygears.uom.engine.Unit.base(UnitAtom.METRE, "metre"));
    /** The kilometre, {@code km}. */
    public static final Unit<Length> KILOMETRE = METRE.times(1000, "km", "kilometre");
    /** The second, {@code s}. */
    public static final Unit<Time> SECOND = Unit
            .of(Time.class, org.tinygears.uom.engine.Unit.base(UnitAtom.SECOND, "second"));
    /** The minute, {@code min}. */
    public static final Unit<Time> MINUTE = SECOND.times(60, "min", "minute");
    /** The hour, {@code h}. */
    public static final Unit<Time> HOUR = SECOND.times(3600, "h", "hour");
    /** The metre per second, {@code m/s}. */
    public static final Unit<Speed> METRE_PER_SECOND = Unit.per(METRE, SECOND, "m/s", "metre per second");
    /** The kilometre per hour, {@code km/h}. */
    public static final Unit<Speed> KILOMETRE_PER_HOUR = Unit.per(KILOMETRE, HOUR, "km/h", "kilometre per hour");

    private SI() {
    }
}
