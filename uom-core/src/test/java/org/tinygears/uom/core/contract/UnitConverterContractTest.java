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

import java.math.BigDecimal;
import java.util.List;
import javax.measure.UnitConverter;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.core.Converters;
import org.tinygears.uom.core.Factor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tinygears.uom.core.BaseUnits.KELVIN;
import static org.tinygears.uom.core.BaseUnits.METRE;

/** Each test states one clause of the unit-api 2.2 {@link UnitConverter} Javadoc. */
class UnitConverterContractTest {

    private static final UnitConverter TIMES_TWO = Converters.scale(Factor.ofLong(2));
    private static final UnitConverter TIMES_THREE = Converters.scale(Factor.ofLong(3));
    private static final UnitConverter PLUS_TEN = Converters.shift(Factor.ofLong(10));
    private static final UnitConverter FAHRENHEIT_TO_KELVIN = Converters.affine(Factor.of(5, 9), Factor.of(45967, 180));

    @Test
    void identityReturnsItsArgumentAndIsLinear() {
        UnitConverter identity = KELVIN.getConverterTo(KELVIN);
        assertTrue(identity.isIdentity());
        assertEquals(42.5, identity.convert(42.5));
        assertTrue(identity.isLinear());
        assertFalse(TIMES_TWO.isIdentity());
        assertFalse(PLUS_TEN.isIdentity());
    }

    @Test
    void linearMeansAdditiveAndHomogeneous() {
        assertTrue(TIMES_TWO.isLinear());
        assertEquals(TIMES_TWO.convert(3.0) + TIMES_TWO.convert(5.0), TIMES_TWO.convert(3.0 + 5.0));
        assertEquals(4 * TIMES_TWO.convert(3.0), TIMES_TWO.convert(4 * 3.0));
    }

    @Test
    void anOffsetIsNotLinear() {
        assertFalse(PLUS_TEN.isLinear());
        assertNotEquals(PLUS_TEN.convert(3.0) + PLUS_TEN.convert(5.0), PLUS_TEN.convert(3.0 + 5.0));
    }

    @Test
    void linearConvertersCommute() {
        assertEquals(TIMES_TWO.concatenate(TIMES_THREE).convert(7.0), TIMES_THREE.concatenate(TIMES_TWO).convert(7.0));
    }

    @Test
    void inverseUndoesConvert() {
        for (double x : new double[] { -40.0, 0.0, 32.0, 212.0, 1e6 }) {
            assertEquals(x, FAHRENHEIT_TO_KELVIN.inverse().convert(FAHRENHEIT_TO_KELVIN.convert(x)), 1e-9);
        }
        assertEquals(233.15, FAHRENHEIT_TO_KELVIN.convert(-40.0), 1e-12);
        assertEquals(-40.0, FAHRENHEIT_TO_KELVIN.inverse().convert(233.15), 1e-12);
    }

    @Test
    void convertNumber() {
        assertEquals(20.0, TIMES_TWO.convert((Number) 10).doubleValue());
        BigDecimal r = (BigDecimal) TIMES_TWO.convert((Number) new BigDecimal("0.1"));
        assertEquals(0, new BigDecimal("0.2").compareTo(r), r::toPlainString);
    }

    @Test
    void convertDouble() {
        assertEquals(20.0, TIMES_TWO.convert(10.0));
        assertEquals(1000.0, METRE.multiply(1000).getConverterTo(METRE).convert(1.0));
    }

    @Test
    void concatenateConvertsByTheArgumentFirst() {
        assertEquals(12.0, PLUS_TEN.concatenate(TIMES_TWO).convert(1.0));
        assertEquals(22.0, TIMES_TWO.concatenate(PLUS_TEN).convert(1.0));
    }

    @Test
    void conversionStepsOfAFundamentalConverterAreItself() {
        assertEquals(1, TIMES_TWO.getConversionSteps().size());
        assertSame(TIMES_TWO, TIMES_TWO.getConversionSteps().get(0));
    }

    @Test
    void conversionStepsOfAConcatenationAreInOrder() {
        List<? extends UnitConverter> steps = TIMES_TWO.concatenate(PLUS_TEN).getConversionSteps();
        assertEquals(2, steps.size());
        assertSame(TIMES_TWO, steps.get(0));
        assertSame(PLUS_TEN, steps.get(1));
    }
}
