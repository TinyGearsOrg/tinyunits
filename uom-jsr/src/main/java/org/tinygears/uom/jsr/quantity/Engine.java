/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.jsr.quantity;

import javax.measure.Quantity;
import javax.measure.UnconvertibleException;

import org.tinygears.uom.engine.Converter;
import org.tinygears.uom.jsr.UomUnit;

/** The engine side of the units the quantities compute with. */
final class Engine {

    private Engine() {
    }

    /** The engine converter, with the exceptions of {@link UomUnit#getConverterTo}. */
    static Converter converter(UomUnit<?> from, UomUnit<?> to) {
        if (!from.isCompatible(to)) {
            throw new UnconvertibleException("incompatible dimensions: " + from + " and " + to);
        }
        try {
            return from.engine().converterTo(to.engine());
        } catch (ArithmeticException e) {
            throw (UnconvertibleException) new UnconvertibleException(
                    "cannot convert " + from + " to " + to + " exactly: " + e.getMessage()).initCause(e);
        }
    }

    /** The scale of a sum or difference: {@code ABSOLUTE} unless both operands are {@code RELATIVE}. */
    static Quantity.Scale sumScale(Quantity.Scale a, Quantity.Scale b) {
        return a == Quantity.Scale.RELATIVE && b == Quantity.Scale.RELATIVE
                ? Quantity.Scale.RELATIVE
                : Quantity.Scale.ABSOLUTE;
    }
}
