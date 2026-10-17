/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.engine.arithmetic;

import org.tinygears.uom.engine.Converter;

/** How a difference converts: by the factor only, without the offset. */
public final class Deltas {

    private Deltas() {
    }

    /**
     * {@code c} without its offset. A linear converter is returned as is.
     *
     * @param c the converter
     * @return {@code c}'s factor as a converter
     */
    public static Converter converter(Converter c) {
        return c.isLinear() ? c : Converter.scale(c.factor());
    }

    /**
     * {@code c}, or {@code c} without its offset if {@code delta}.
     *
     * @param c the converter
     * @param delta whether the value is a difference
     * @return the converter for the value
     */
    static Converter of(Converter c, boolean delta) {
        return delta ? converter(c) : c;
    }
}
