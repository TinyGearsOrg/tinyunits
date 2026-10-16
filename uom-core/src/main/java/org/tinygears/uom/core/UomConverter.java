/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.core;

import java.math.BigDecimal;
import java.math.MathContext;
import javax.measure.UnitConverter;

/** An affine converter {@code x -> factor() * x + offset()}. */
public interface UomConverter extends UnitConverter {

    /**
     * Converts a decimal value.
     *
     * @param value the value
     * @param mc the rounding; precision 0 is exact where the result terminates for this value (a factor such as
     *     {@code 5000/127} included), else {@code DECIMAL128}, as for an irrational factor
     * @return the converted value
     */
    BigDecimal convert(BigDecimal value, MathContext mc);

    @Override
    UomConverter inverse();

    /** Converts by {@code other} first, then by this converter. */
    @Override
    UomConverter concatenate(UnitConverter other);

    /**
     * The factor.
     *
     * @return the factor
     */
    Factor factor();

    /**
     * The offset.
     *
     * @return the offset
     */
    Factor offset();
}
