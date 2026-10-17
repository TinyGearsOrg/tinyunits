/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.jsr;

import org.tinygears.uom.engine.Converter;
import org.tinygears.uom.engine.Factor;

/** Factories of the affine converters: the JSR-385 converters over the engine's ({@link Converter}). */
public final class Converters {

    /** The converter {@code x -> x}. */
    public static final UomConverter IDENTITY = new AffineConverter(Converter.IDENTITY);

    private Converters() {
    }

    /**
     * The converter {@code x -> factor * x + offset}.
     *
     * @param factor the factor
     * @param offset the offset
     * @return the converter
     */
    public static UomConverter affine(Factor factor, Factor offset) {
        return AffineConverter.of(Converter.affine(factor, offset));
    }

    /**
     * The converter {@code x -> factor * x}.
     *
     * @param factor the factor
     * @return the converter
     */
    public static UomConverter scale(Factor factor) {
        return affine(factor, Factor.ZERO);
    }

    /**
     * The converter {@code x -> x + offset}.
     *
     * @param offset the offset
     * @return the converter
     */
    public static UomConverter shift(Factor offset) {
        return affine(Factor.ONE, offset);
    }
}
