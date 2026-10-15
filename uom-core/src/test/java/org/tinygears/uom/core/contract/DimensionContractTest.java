/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.core.contract;

import java.util.Map;
import javax.measure.Dimension;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tinygears.uom.core.BaseUnits.KILOGRAM;
import static org.tinygears.uom.core.BaseUnits.METRE;
import static org.tinygears.uom.core.BaseUnits.SECOND;

/** Each test states one clause of the unit-api 2.2 {@link Dimension} Javadoc. */
class DimensionContractTest {

    private static final Dimension L = METRE.getDimension();
    private static final Dimension M = KILOGRAM.getDimension();
    private static final Dimension T = SECOND.getDimension();

    @Test
    void multiplyIsTheProduct() {
        Dimension lm = L.multiply(M);
        assertEquals(Map.of(L, 1, M, 1), lm.getBaseDimensions());
        assertEquals(METRE.multiply(KILOGRAM).getDimension(), lm);
    }

    @Test
    void divideIsTheQuotient() {
        Dimension speed = L.divide(T);
        assertEquals(Map.of(L, 1, T, -1), speed.getBaseDimensions());
        assertEquals(METRE.divide(SECOND).getDimension(), speed);
    }

    @Test
    void powRaisesToTheExponent() {
        assertEquals(Map.of(L, 3), L.pow(3).getBaseDimensions());
        assertEquals(Map.of(T, -2), T.pow(-2).getBaseDimensions());
    }

    @Test
    void rootTakesTheRoot() {
        assertEquals(L, L.pow(2).root(2));
        assertEquals(L.multiply(T.pow(-1)), L.pow(3).divide(T.pow(3)).root(3));
    }

    @Test
    void rootZeroThrowsArithmeticException() {
        assertThrows(ArithmeticException.class, () -> L.pow(2).root(0));
    }

    @Test
    void getBaseDimensionsIsNullForABaseDimension() {
        assertNull(L.getBaseDimensions());
        assertNull(M.getBaseDimensions());
        assertNull(T.getBaseDimensions());
    }

    @Test
    void unitsAreCompatibleIffTheirDimensionsAreEqual() {
        assertTrue(METRE.isCompatible(METRE.multiply(1000)));
        assertEquals(L, METRE.multiply(1000).getDimension());
        assertFalse(METRE.isCompatible(SECOND));
        assertNotEquals(L, T);
    }
}
