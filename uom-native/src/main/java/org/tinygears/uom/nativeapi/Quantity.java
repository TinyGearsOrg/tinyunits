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

/**
 * An amount of a quantity type {@code Q} in a unit of that type, as a {@code double} or a {@link BigDecimal}. Unlike
 * JSR-385's {@code Quantity<Q extends Quantity<Q>>}, {@code Q} is the typed interface itself ({@code Length}), so
 * operations take and return it and units are {@code Unit<Length>}.
 *
 * <p>The arithmetic is the engine's, shared with the JSR-385 quantities of uom: a decimal result is evaluated exactly
 * and rounded once to the context; an operation with one decimal operand is decimal, the {@code double} read as its
 * shortest decimal, and two decimals round to the lower context. Number arguments do not change the number type.
 *
 * @param <Q> the quantity type
 */
public interface Quantity<Q extends Quantity<Q>> extends Comparable<Q> {

    /**
     * The value in {@link #unit()}: a {@link Double} or a {@link BigDecimal}.
     *
     * @return the value
     */
    Number value();

    /**
     * The context that rounds the results of a decimal quantity; null for a {@code double} one.
     *
     * @return the context, or null
     */
    MathContext mathContext();

    /**
     * The unit.
     *
     * @return the unit
     */
    Unit<Q> unit();

    /**
     * The same amount in {@code unit}, a decimal one rounded to its context.
     *
     * @param unit the unit
     * @return the converted quantity
     */
    Q to(Unit<Q> unit);

    /**
     * The sum, in this unit.
     *
     * @param other the other quantity
     * @return the sum
     */
    Q add(Q other);

    /**
     * The difference, in this unit.
     *
     * @param other the other quantity
     * @return the difference
     */
    Q subtract(Q other);

    /**
     * This amount times a number, in the number type of this quantity.
     *
     * @param factor the number
     * @return the product
     */
    Q multiply(Number factor);

    /**
     * This amount divided by a number, in the number type of this quantity; a decimal division by zero throws
     * {@link ArithmeticException}.
     *
     * @param divisor the number
     * @return the quotient
     */
    Q divide(Number divisor);

    /**
     * The same quantity as a {@code double}.
     *
     * @return the double quantity
     */
    Q toDouble();

    /**
     * The same quantity as a decimal rounded to {@code mc}, which becomes its context.
     *
     * @param mc the context
     * @return the decimal quantity
     */
    Q toDecimal(MathContext mc);

    /**
     * Whether the amounts differ by at most {@code relativeTolerance} times the larger, in the system unit; exact if
     * either is decimal.
     *
     * @param other the other quantity
     * @param relativeTolerance the relative tolerance, finite and not negative
     * @return true if close
     */
    boolean isCloseTo(Q other, double relativeTolerance);
}
