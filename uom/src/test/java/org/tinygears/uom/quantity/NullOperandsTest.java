/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.quantity;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;
import javax.measure.Quantity;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.systems.SI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A null operand is a NullPointerException that names the parameter, for double and decimal quantities alike. */
class NullOperandsTest {

    private static void assertNames(String parameter, Consumer<Length> call, Length... quantities) {
        for (Length q : quantities) {
            assertEquals(
                    parameter,
                    assertThrows(NullPointerException.class, () -> call.accept(q)).getMessage(),
                    q.getClass().getSimpleName());
        }
    }

    @Test
    void everyArithmeticMethodNamesItsParameter() {
        List<Length> quantities = List.of(Length.of(1, SI.METRE), Length.of(new BigDecimal("1"), SI.METRE));
        Length[] all = quantities.toArray(new Length[0]);
        assertNames("addend", q -> q.add(null), all);
        assertNames("subtrahend", q -> q.subtract(null), all);
        assertNames("multiplicand", q -> q.multiply((Number) null), all);
        assertNames("divisor", q -> q.divide((Number) null), all);
        assertNames("multiplicand", q -> q.multiply((Quantity<?>) null), all);
        assertNames("divisor", q -> q.divide((Quantity<?>) null), all);
        assertNames("that", q -> q.compareTo(null), all);
        assertNames("that", q -> q.isEquivalentTo(null), all);
    }
}
