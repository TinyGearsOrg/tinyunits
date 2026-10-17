/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

/**
 * The exact affine conversion {@code x -> factor·x + offset} between two units, from {@link Unit#converterTo}.
 * Immutable.
 */
public final class Converter {
    private final org.tinygears.uom.engine.Converter engine;

    Converter(org.tinygears.uom.engine.Converter engine) {
        this.engine = engine;
    }

    /**
     * Converts a {@code double}.
     *
     * @param value the value
     * @return the converted value
     */
    public double convert(double value) {
        return engine.convert(value);
    }

    /**
     * Converts a decimal, rounded to {@code mc}; precision 0 is exact where the result terminates, else
     * {@code DECIMAL128}.
     *
     * @param value the value
     * @param mc the rounding
     * @return the converted value
     */
    public BigDecimal convert(BigDecimal value, MathContext mc) {
        return engine.convert(Objects.requireNonNull(value, "value"), Objects.requireNonNull(mc, "mc"));
    }

    /**
     * The factor.
     *
     * @return the exact factor
     */
    public org.tinygears.uom.engine.Factor factor() {
        return engine.factor();
    }

    /**
     * The offset.
     *
     * @return the exact offset
     */
    public org.tinygears.uom.engine.Factor offset() {
        return engine.offset();
    }

    /**
     * The inverse conversion.
     *
     * @return {@code x -> (x - offset) / factor}
     */
    public Converter inverse() {
        return new Converter(engine.inverse());
    }

    /**
     * Converts by {@code before} first, then by this converter.
     *
     * @param before the converter applied first
     * @return the composition
     */
    public Converter concatenate(Converter before) {
        return new Converter(engine.concatenate(before.engine));
    }

    /**
     * Whether this is {@code x -> x}.
     *
     * @return true for factor 1 and offset 0
     */
    public boolean isIdentity() {
        return engine.isIdentity();
    }

    /**
     * Whether the offset is zero.
     *
     * @return true if linear
     */
    public boolean isLinear() {
        return engine.isLinear();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Converter c && engine.equals(c.engine);
    }

    @Override
    public int hashCode() {
        return engine.hashCode();
    }

    /** The factor and offset, such as {@code x -> 1000 * x + 0}. */
    @Override
    public String toString() {
        return engine.toString();
    }
}
