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

import java.util.Map;
import java.util.Objects;

import org.tinygears.uom.engine.Converter;
import org.tinygears.uom.engine.Dimension;
import org.tinygears.uom.engine.Factor;

/**
 * A unit of the quantity type {@code Q} over an engine unit; the type is checked against the unit's dimension. Unlike
 * JSR-385's {@code Unit<Q>}, {@code Q} is this library's typed interface: {@code Unit<Length>}.
 *
 * @param <Q> the quantity type
 */
public final class Unit<Q extends Quantity<Q>> {
    private static final Map<Class<?>, Dimension> DIMENSIONS = Map.of(
            Length.class,
            Dimension.LENGTH,
            Time.class,
            Dimension.TIME,
            Speed.class,
            Dimension.LENGTH.divide(Dimension.TIME));

    private final Class<Q> type;
    private final org.tinygears.uom.engine.Unit engine;

    private Unit(Class<Q> type, org.tinygears.uom.engine.Unit engine) {
        this.type = type;
        this.engine = engine;
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
        Dimension d = DIMENSIONS.get(Objects.requireNonNull(type, "type"));
        if (d == null || !d.equals(engine.dimension())) {
            throw new IllegalArgumentException(
                    engine + " (" + engine.dimension() + ") is not a unit of " + type.getSimpleName());
        }
        return new Unit<>(type, engine);
    }

    /**
     * The quantity type.
     *
     * @return the type
     */
    public Class<Q> type() {
        return type;
    }

    /**
     * The engine unit.
     *
     * @return the engine unit
     */
    public org.tinygears.uom.engine.Unit engine() {
        return engine;
    }

    /**
     * The symbol, or null.
     *
     * @return the symbol
     */
    public String symbol() {
        return engine.symbol();
    }

    /**
     * This unit times an exact factor, labelled.
     *
     * @param factor the factor
     * @param symbol the symbol
     * @param name the name
     * @return the new unit of the same type
     */
    public Unit<Q> times(long factor, String symbol, String name) {
        return new Unit<>(type, engine.scaledBy(Factor.ofLong(factor)).named(symbol, name));
    }

    /**
     * The converter from this unit to {@code that}.
     *
     * @param that the target unit
     * @return the converter
     */
    public Converter converterTo(Unit<Q> that) {
        return engine.converterTo(that.engine);
    }

    /**
     * The unit of speed of a length per a time, such as {@code km/h}.
     *
     * @param length the length unit
     * @param time the time unit
     * @param symbol the symbol
     * @param name the name
     * @return the speed unit
     */
    public static Unit<Speed> per(Unit<Length> length, Unit<Time> time, String symbol, String name) {
        return of(Speed.class, length.engine.divide(time.engine).named(symbol, name));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Unit<?> u && type == u.type && engine.equals(u.engine);
    }

    @Override
    public int hashCode() {
        return 31 * type.hashCode() + engine.hashCode();
    }

    @Override
    public String toString() {
        return engine.toString();
    }
}
