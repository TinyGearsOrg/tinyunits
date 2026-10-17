/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.engine.arithmetic;

import java.math.BigDecimal;
import java.math.MathContext;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.engine.Factor;
import org.tinygears.uom.engine.Unit;
import org.tinygears.uom.engine.UnitAtom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArithmeticTest {

    private static final Unit M = Unit.base(UnitAtom.METRE, "metre");
    private static final Unit KM = M.scaledBy(Factor.ofLong(1000));
    private static final Unit PI_M = M.scaledBy(Factor.PI);
    private static final Unit K = Unit.base(UnitAtom.KELVIN, "kelvin");
    private static final Unit C = K.shiftedBy(Factor.of(27315, 100));

    @Test
    void doubleSums() {
        assertEquals(1.5, DoubleArithmetic.combine(1, KM, 500, M, M.converterTo(KM), false, false));
        assertEquals(0.5, DoubleArithmetic.combine(1, KM, 500, M, M.converterTo(KM), false, true));
        assertEquals(320.0, DoubleArithmetic.combine(20, C, 300, K, K.converterTo(C), false, false));
        assertEquals(25.0, DoubleArithmetic.combine(20, C, 5, K, K.converterTo(C), true, false));
    }

    @Test
    void doubleScalarsAndProducts() {
        assertEquals(293.15, DoubleArithmetic.scalar(10, C, false, 2, false));
        assertEquals(20.0, DoubleArithmetic.scalar(10, C, true, 2, false));
        assertEquals(0.25, DoubleArithmetic.scalar(1, KM, false, 4, true));
        assertEquals(2_000_000.0, DoubleArithmetic.product(2, KM, false, 1, KM, false, false));
        assertEquals(0.5, DoubleArithmetic.product(1, KM, false, 2, KM, false, true));
        assertEquals(10.0, DoubleArithmetic.product(10, C, true, 1, K, false, false));
    }

    @Test
    void doubleComparisons() {
        assertEquals(1, DoubleArithmetic.compare(1, KM, 999, M, false));
        assertEquals(0, DoubleArithmetic.compare(0, C, 273.15, K, false));
        assertEquals(-1, DoubleArithmetic.compare(0, C, 1, K, true));
        assertTrue(DoubleArithmetic.equivalent(1, 1 + 1e-13));
        assertFalse(DoubleArithmetic.equivalent(1, 1.000001));
        assertFalse(DoubleArithmetic.equivalent(Double.NaN, Double.NaN));
        assertTrue(DoubleArithmetic.isCloseTo(1, KM, 1001, M, false, 1e-3));
        assertFalse(DoubleArithmetic.isCloseTo(1, KM, 1002, M, false, 1e-3));
        assertTrue(DoubleArithmetic.isCloseTo(20, C, 293.65, K, false, 1, C));
        assertFalse(DoubleArithmetic.isCloseTo(20, C, 293.65, K, false, 0.4, K));
    }

    @Test
    void decimalSumsRoundOnce() {
        BigDecimal r = DecimalArithmetic
                .combine(new BigDecimal("0.1"), KM, new BigDecimal("0.2"), M, false, false, MathContext.UNLIMITED);
        assertEquals(new BigDecimal("0.1002"), r);
        assertEquals(
                new BigDecimal("320"),
                DecimalArithmetic.combine(
                        new BigDecimal("20"),
                        C,
                        new BigDecimal("300"),
                        K,
                        false,
                        false,
                        MathContext.UNLIMITED));
        assertEquals(
                new BigDecimal("4.141593"),
                DecimalArithmetic
                        .combine(BigDecimal.ONE, M, BigDecimal.ONE, PI_M, false, false, MathContext.DECIMAL32));
    }

    @Test
    void decimalScalarsProductsAndInverse() {
        assertEquals(
                new BigDecimal("0.3333333"),
                DecimalArithmetic.scalar(BigDecimal.ONE, M, false, new BigDecimal(3), true, MathContext.DECIMAL32));
        assertEquals(
                new BigDecimal("293.15"),
                DecimalArithmetic.scalar(BigDecimal.TEN, C, false, new BigDecimal(2), false, MathContext.UNLIMITED));
        assertEquals(
                new BigDecimal("6.283185"),
                DecimalArithmetic.product(
                        new BigDecimal(2),
                        PI_M,
                        false,
                        BigDecimal.ONE,
                        M,
                        false,
                        false,
                        MathContext.DECIMAL32));
        assertEquals(
                new BigDecimal("0.00025"),
                DecimalArithmetic.inverse(new BigDecimal(4), KM, false, MathContext.UNLIMITED));
    }

    @Test
    void decimalComparisonsAreExact() {
        assertEquals(
                1,
                DecimalArithmetic
                        .compare(BigDecimal.ONE, PI_M, new BigDecimal("3.14159"), M, false, MathContext.DECIMAL32));
        assertEquals(
                -1,
                DecimalArithmetic
                        .compare(BigDecimal.ONE, PI_M, new BigDecimal("3.1416"), M, false, MathContext.DECIMAL32));
        assertEquals(
                0,
                DecimalArithmetic.compare(
                        new BigDecimal("0.1"),
                        KM,
                        new BigDecimal("100.000"),
                        M,
                        false,
                        MathContext.UNLIMITED));
        BigDecimal approx = new BigDecimal("3.14159");
        assertTrue(
                DecimalArithmetic.isCloseTo(
                        BigDecimal.ONE,
                        PI_M,
                        approx,
                        M,
                        false,
                        new BigDecimal("1e-6"),
                        null,
                        null,
                        MathContext.DECIMAL64));
        assertFalse(
                DecimalArithmetic.isCloseTo(
                        BigDecimal.ONE,
                        PI_M,
                        approx,
                        M,
                        false,
                        new BigDecimal("1e-7"),
                        null,
                        null,
                        MathContext.DECIMAL64));
        assertTrue(
                DecimalArithmetic.isCloseTo(
                        new BigDecimal(20),
                        C,
                        new BigDecimal("293.65"),
                        K,
                        false,
                        null,
                        new BigDecimal("0.5"),
                        C,
                        MathContext.UNLIMITED));
    }
}
