package org.tinygears.uom.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tinygears.uom.core.UomDimension.LENGTH;
import static org.tinygears.uom.core.UomDimension.NONE;
import static org.tinygears.uom.core.UomDimension.TIME;

import java.util.Map;
import org.junit.jupiter.api.Test;

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
            public javax.measure.Dimension multiply(javax.measure.Dimension m) { throw new UnsupportedOperationException(); }
            public javax.measure.Dimension divide(javax.measure.Dimension d) { throw new UnsupportedOperationException(); }
            public javax.measure.Dimension pow(int n) { throw new UnsupportedOperationException(); }
            public javax.measure.Dimension root(int n) { throw new UnsupportedOperationException(); }
            public Map<? extends javax.measure.Dimension, Integer> getBaseDimensions() { return Map.of(TIME, 1); }
        };
        assertEquals(LENGTH.multiply(TIME), LENGTH.multiply(foreign));
    }
}
