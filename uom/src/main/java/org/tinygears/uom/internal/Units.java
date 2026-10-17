/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.internal;

import java.util.Objects;

import org.tinygears.uom.Quantity;
import org.tinygears.uom.Unit;

/**
 * The engine side of units for the generated catalogs and for {@code uom-jsr}: a unit over an engine unit, and the
 * engine unit of a unit. Not API: the package is exported to {@code org.tinygears.uom.jsr} only.
 */
public final class Units {
    /** What {@link Unit} lets this class do; set once, when {@code Unit} initialises. */
    public interface Access {
        /**
         * The engine unit {@code engine} as a unit of {@code type}.
         *
         * @param <Q> the quantity type
         * @param type the quantity type
         * @param engine the engine unit
         * @return the unit
         */
        <Q extends Quantity<Q>> Unit<Q> of(Class<Q> type, org.tinygears.uom.engine.Unit engine);

        /**
         * The engine unit of {@code unit}.
         *
         * @param unit the unit
         * @return the engine unit
         */
        org.tinygears.uom.engine.Unit engine(Unit<?> unit);
    }

    private static volatile Access access;

    private Units() {
    }

    /**
     * Sets the access; called by {@code Unit} only.
     *
     * @param a the access
     * @throws IllegalStateException if it is set already
     */
    public static synchronized void setAccess(Access a) {
        if (access != null) {
            throw new IllegalStateException("access already set");
        }
        access = Objects.requireNonNull(a, "access");
    }

    /**
     * The engine unit {@code engine} as a unit of {@code type}.
     *
     * @param <Q> the quantity type
     * @param type the quantity type
     * @param engine the engine unit
     * @return the unit
     * @throws IllegalArgumentException if the unit's dimension is not the type's
     */
    public static <Q extends Quantity<Q>> Unit<Q> of(Class<Q> type, org.tinygears.uom.engine.Unit engine) {
        return access().of(type, engine);
    }

    /**
     * The engine unit of {@code unit}.
     *
     * @param unit the unit
     * @return the engine unit
     */
    public static org.tinygears.uom.engine.Unit engine(Unit<?> unit) {
        return access().engine(Objects.requireNonNull(unit, "unit"));
    }

    private static Access access() {
        Access a = access;
        if (a == null) {
            try {
                // initialising Unit sets the access
                Class.forName(Unit.class.getName(), true, Unit.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
            a = access;
        }
        return a;
    }
}
