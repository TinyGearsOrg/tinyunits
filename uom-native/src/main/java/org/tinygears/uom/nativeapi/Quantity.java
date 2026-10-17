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

/**
 * An amount of a quantity type {@code Q} in a unit of that type, as a {@code double}. Unlike JSR-385's
 * {@code Quantity<Q extends Quantity<Q>>}, {@code Q} is the typed interface itself ({@code Length}), so operations take
 * and return it and units are {@code Unit<Length>}.
 *
 * @param <Q> the quantity type
 */
public interface Quantity<Q extends Quantity<Q>> extends Comparable<Q> {

    /**
     * The value in {@link #unit()}.
     *
     * @return the value
     */
    double value();

    /**
     * The unit.
     *
     * @return the unit
     */
    Unit<Q> unit();

    /**
     * The value in {@code unit}.
     *
     * @param unit the unit
     * @return the converted value
     */
    double valueIn(Unit<Q> unit);

    /**
     * The same amount in {@code unit}.
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
     * This amount times a number.
     *
     * @param factor the number
     * @return the product
     */
    Q multiply(double factor);

    /**
     * This amount divided by a number.
     *
     * @param divisor the number
     * @return the quotient
     */
    Q divide(double divisor);

    /**
     * Whether the amounts differ by at most {@code relativeTolerance} times the larger, in the system unit.
     *
     * @param other the other quantity
     * @param relativeTolerance the relative tolerance
     * @return true if close
     */
    boolean isCloseTo(Q other, double relativeTolerance);
}
