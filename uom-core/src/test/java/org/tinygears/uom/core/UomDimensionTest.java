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

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tinygears.uom.core.UomDimension.LENGTH;
import static org.tinygears.uom.core.UomDimension.NONE;
import static org.tinygears.uom.core.UomDimension.TIME;

class UomDimensionTest {

    @Test
    void baseDimensionHasNullBaseDimensions() {
        assertNull(LENGTH.getBaseDimensions());
    }

    @Test
    void derivedDimensionMapsBaseConstantsToExponents() {
        assertEquals(Map.of(LENGTH, 1, TIME, -1), LENGTH.divide(TIME).getBaseDimensions());
    }

    @Test
    void noneHasEmptyBaseDimensions() {
        assertTrue(NONE.getBaseDimensions().isEmpty());
    }

    @Test
    void powAndRootRoundTrip() {
        assertEquals(LENGTH, LENGTH.pow(2).root(2));
    }

    @Test
    void rootRejectsZeroAndIndivisibleExponents() {
        assertThrows(ArithmeticException.class, () -> LENGTH.root(2));
        assertThrows(ArithmeticException.class, () -> LENGTH.root(0));
    }

    @Test
    void noneIsMultiplicativeIdentity() {
        assertEquals(LENGTH, NONE.multiply(LENGTH));
    }

    @Test
    void toStringListsNonzeroExponents() {
        assertEquals("L·T^-1", LENGTH.divide(TIME).toString());
        assertEquals("1", NONE.toString());
    }

    @Test
    void foreignDimensionIsConvertedThroughBaseDimensions() {
        javax.measure.Dimension foreign = new javax.measure.Dimension() {
            public javax.measure.Dimension multiply(javax.measure.Dimension m) {
                throw new UnsupportedOperationException();
            }
            public javax.measure.Dimension divide(javax.measure.Dimension d) {
                throw new UnsupportedOperationException();
            }
            public javax.measure.Dimension pow(int n) {
                throw new UnsupportedOperationException();
            }
            public javax.measure.Dimension root(int n) {
                throw new UnsupportedOperationException();
            }
            public Map<? extends javax.measure.Dimension, Integer> getBaseDimensions() {
                return Map.of(TIME, 1);
            }
        };
        assertEquals(LENGTH.multiply(TIME), LENGTH.multiply(foreign));
    }

    private static final UomDimension MAX_L = new UomDimension(Integer.MAX_VALUE, 0, 0, 0, 0, 0, 0);
    private static final UomDimension MIN_L = new UomDimension(Integer.MIN_VALUE, 0, 0, 0, 0, 0, 0);

    private static javax.measure.Dimension foreign(Map<javax.measure.Dimension, Integer> base) {
        return new javax.measure.Dimension() {
            public javax.measure.Dimension multiply(javax.measure.Dimension m) {
                throw new UnsupportedOperationException();
            }
            public javax.measure.Dimension divide(javax.measure.Dimension d) {
                throw new UnsupportedOperationException();
            }
            public javax.measure.Dimension pow(int n) {
                throw new UnsupportedOperationException();
            }
            public javax.measure.Dimension root(int n) {
                throw new UnsupportedOperationException();
            }
            public Map<? extends javax.measure.Dimension, Integer> getBaseDimensions() {
                return base;
            }
        };
    }

    @Test
    void powEdgeCases() {
        assertEquals(NONE, LENGTH.pow(0));
        assertEquals(NONE.divide(LENGTH), LENGTH.pow(-1));
        assertThrows(ArithmeticException.class, () -> LENGTH.pow(Integer.MAX_VALUE).pow(2));
        assertThrows(ArithmeticException.class, () -> LENGTH.pow(2).pow(Integer.MAX_VALUE));
        assertThrows(ArithmeticException.class, () -> MIN_L.pow(-1));
    }

    @Test
    void multiplyAndDivideOverflowThrow() {
        assertThrows(ArithmeticException.class, () -> MAX_L.multiply(LENGTH));
        assertThrows(ArithmeticException.class, () -> LENGTH.divide(MIN_L));
        assertThrows(ArithmeticException.class, () -> NONE.divide(MIN_L));
    }

    @Test
    void rootEdgeCases() {
        assertThrows(ArithmeticException.class, () -> MIN_L.root(-1));
        assertEquals(NONE.divide(LENGTH), LENGTH.pow(2).root(-2));
    }

    @Test
    void unsupportedForeignDimensionsAreRejected() {
        var badKey = foreign(Map.of(foreign(Map.of()), 1));
        assertThrows(IllegalArgumentException.class, () -> LENGTH.multiply(badKey));
        assertThrows(IllegalArgumentException.class, () -> LENGTH.divide(badKey));
        var nullExp = new java.util.HashMap<javax.measure.Dimension, Integer>();
        nullExp.put(TIME, null);
        assertThrows(IllegalArgumentException.class, () -> LENGTH.multiply(foreign(nullExp)));
    }
}
