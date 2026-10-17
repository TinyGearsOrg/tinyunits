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

import java.util.Objects;

import org.tinygears.uom.engine.Converter;
import org.tinygears.uom.engine.Dimension;
import org.tinygears.uom.engine.Factor;
import org.tinygears.uom.engine.UnitException;
import org.tinygears.uom.nativeapi.quantity.QuantityTypes;

/**
 * A unit of the quantity type {@code Q} over an engine unit; the type is checked against the unit's dimension, and
 * {@link Untyped} takes any. Unlike JSR-385's {@code Unit<Q>}, {@code Q} is this library's typed interface:
 * {@code Unit<Length>}. Two units are equal if their engine units are (the same atoms and converter), whatever their
 * type and labels. Immutable.
 *
 * @param <Q> the quantity type
 */
public final class Unit<Q extends Quantity<Q>> {
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
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(engine, "engine");
        if (type != Untyped.class) {
            Dimension d = QuantityTypes.dimension(type);
            if (d == null) {
                throw new IllegalArgumentException("not a quantity type: " + type.getName());
            }
            if (!d.equals(engine.dimension())) {
                throw new IllegalArgumentException(
                        engine + " (" + engine.dimension() + ") is not a unit of " + type.getSimpleName());
            }
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
     * The name, or null.
     *
     * @return the name
     */
    public String name() {
        return engine.name();
    }

    /**
     * The dimension.
     *
     * @return the dimension
     */
    public Dimension dimension() {
        return engine.dimension();
    }

    /**
     * The unscaled unit of the same atoms, of this type.
     *
     * @return the system unit
     */
    public Unit<Q> systemUnit() {
        return new Unit<>(type, engine.systemUnit());
    }

    /**
     * Whether {@code that} has this dimension.
     *
     * @param that the other unit
     * @return true if the dimensions are equal
     */
    public boolean isCompatible(Unit<?> that) {
        return engine.isCompatible(that.engine);
    }

    /**
     * The converter from this unit to {@code that}.
     *
     * @param that the target unit
     * @return the converter
     * @throws UnitException if the dimensions differ or no exact converter exists
     */
    public Converter converterTo(Unit<?> that) {
        if (!isCompatible(that)) {
            throw new UnitException("incompatible dimensions: " + this + " and " + that);
        }
        try {
            return engine.converterTo(that.engine);
        } catch (ArithmeticException e) {
            throw (UnitException) new UnitException(
                    "cannot convert " + this + " to " + that + " exactly: " + e.getMessage()).initCause(e);
        }
    }

    /**
     * The product; an offset takes part by its factor only.
     *
     * @param that the other unit
     * @return the product, untyped
     */
    public Unit<Untyped> multiply(Unit<?> that) {
        return new Unit<>(Untyped.class, engine.multiply(that.engine));
    }

    /**
     * The quotient; an offset takes part by its factor only.
     *
     * @param that the divisor
     * @return the quotient, untyped
     */
    public Unit<Untyped> divide(Unit<?> that) {
        return new Unit<>(Untyped.class, engine.divide(that.engine));
    }

    /**
     * The {@code n}-th power.
     *
     * @param n the exponent
     * @return the power, untyped
     */
    public Unit<Untyped> pow(int n) {
        return new Unit<>(Untyped.class, engine.pow(n));
    }

    /**
     * The reciprocal.
     *
     * @return the reciprocal, untyped
     */
    public Unit<Untyped> inverse() {
        return new Unit<>(Untyped.class, engine.inverse());
    }

    /**
     * This unit times an exact factor, unlabelled.
     *
     * @param factor the factor
     * @return the scaled unit of the same type
     */
    public Unit<Q> scaledBy(Factor factor) {
        return new Unit<>(type, engine.scaledBy(factor));
    }

    /**
     * This unit shifted by an exact offset, unlabelled.
     *
     * @param offset the offset
     * @return the shifted unit of the same type
     */
    public Unit<Q> shiftedBy(Factor offset) {
        return new Unit<>(type, engine.shiftedBy(offset));
    }

    /**
     * This unit with a symbol and a name.
     *
     * @param symbol the symbol
     * @param name the name
     * @return the labelled unit, equal to this one
     */
    public Unit<Q> named(String symbol, String name) {
        return new Unit<>(type, engine.named(symbol, name));
    }

    /**
     * This unit as a unit of {@code type}.
     *
     * @param <T> the quantity type
     * @param type the quantity type
     * @return the unit
     * @throws IllegalArgumentException if the dimension is not the one of {@code type}
     */
    @SuppressWarnings("unchecked") // the type is Class<T>, so the unit is one of T
    public <T extends Quantity<T>> Unit<T> asType(Class<T> type) {
        return this.type == type ? (Unit<T>) this : of(type, engine);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Unit<?> u && engine.equals(u.engine);
    }

    @Override
    public int hashCode() {
        return engine.hashCode();
    }

    /** The symbol, else the definition ({@code 1|1000·m}). */
    @Override
    public String toString() {
        return engine.toString();
    }
}
