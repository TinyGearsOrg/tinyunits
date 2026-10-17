/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.engine.parse;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.engine.Factor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The affine functions after {@code =} in a definition line. */
class AffineFunctionTest {

    @Test
    void affineFunctionParser() throws FormulaException {
        AffineFunction f = AffineFunction.parse("(x - 32) * 5|9");
        assertEquals(Factor.of(5, 9), f.a());
        assertEquals(Factor.of(-160, 9), f.b());
        f = AffineFunction.parse("2 * (3 * x + 1.5) / 4 - -1|2");
        assertEquals(Factor.of(3, 2), f.a());
        assertEquals(Factor.of(5, 4), f.b());
        assertEquals(
                "function is not affine",
                assertThrows(FormulaException.class, () -> AffineFunction.parse("x * (x + 1)")).getMessage());
        assertEquals(
                "division by zero",
                assertThrows(FormulaException.class, () -> AffineFunction.parse("x / (1 - 1)")).getMessage());
        assertEquals(
                "division by zero",
                assertThrows(FormulaException.class, () -> AffineFunction.parse("x * 1|0")).getMessage());
        FormulaException e = assertThrows(FormulaException.class, () -> AffineFunction.parse("x + y"));
        assertEquals("unexpected 'y'", e.getMessage());
        assertEquals(5, e.column());
        assertEquals(
                "missing ')'",
                assertThrows(FormulaException.class, () -> AffineFunction.parse("(x + 1")).getMessage());
        assertEquals(
                "function nested too deeply",
                assertThrows(
                        FormulaException.class,
                        () -> AffineFunction.parse("(".repeat(101) + "x" + ")".repeat(101))).getMessage());
    }
}
