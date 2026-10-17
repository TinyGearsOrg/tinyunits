/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.core.parse;

import java.util.function.Function;

import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.engine.Unit;
import org.tinygears.uom.engine.parse.FormulaEvaluator;
import org.tinygears.uom.engine.parse.FormulaException;

/** Between the engine units the parser works on and the JSR-385 units the tests compare with. */
final class ParseAdapters {

    private ParseAdapters() {
    }

    /** The JSR-385 unit of an engine unit, or null. */
    static UomUnit<?> u(Unit unit) {
        return unit == null ? null : UomUnit.of(unit);
    }

    /** The engine unit of a JSR-385 unit, or null. */
    static Unit e(UomUnit<?> unit) {
        return unit == null ? null : unit.engine();
    }

    /** A lookup of JSR-385 units as the parser's lookup of engine units. */
    static Function<String, Unit> e(Function<String, ? extends UomUnit<?>> lookup) {
        return s -> e(lookup.apply(s));
    }

    /** {@link FormulaEvaluator#evaluate} with a lookup of JSR-385 units, as a JSR-385 unit. */
    static UomUnit<?> jsr(String formula, Function<String, ? extends UomUnit<?>> lookup) throws FormulaException {
        return u(FormulaEvaluator.evaluate(formula, e(lookup)));
    }
}
