/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.systems;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Locale;
import javax.measure.Quantity;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.quantity.Angle;
import org.tinygears.uom.quantity.Dimensionless;
import org.tinygears.uom.quantity.DimensionlessFactory;
import org.tinygears.uom.quantity.Length;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A dimensionless number is written alone, and a number alone parses as dimensionless. */
class DimensionlessTextTest {
    private static final UomQuantityFormat FORMAT = new UomQuantityFormat(UomUnitFormat.standard());

    @Test
    void aDimensionlessQuantityPrintsAsItsNumber() {
        assertEquals("5.0", Dimensionless.of(5, DimensionlessFactory.SYSTEM_UNIT).toString());
        assertEquals("1.50", Dimensionless.of(new BigDecimal("1.50"), DimensionlessFactory.SYSTEM_UNIT).toString());
        assertEquals("2.0", Length.of(2, SI.METRE).divide(Length.of(1, SI.METRE)).toString());
        // A unit of dimension one with a symbol of its own keeps it.
        assertEquals("2.0 rad", Angle.of(2, SI.RADIAN).toString());
    }

    @Test
    void theFormatWritesTheNumberAloneAndParsesItBack() {
        Quantity<?> five = Dimensionless.of(5, DimensionlessFactory.SYSTEM_UNIT);
        assertEquals("5.0", FORMAT.format(five));
        assertEquals(five, FORMAT.parse("5.0"));
        assertEquals(five, FORMAT.parse(FORMAT.format(five)));
        assertEquals(five, FORMAT.parse("5.0 1"));
        assertEquals("2.0 rad", FORMAT.format(Angle.of(2, SI.RADIAN)));
    }

    @Test
    void theLocaleAndTheDecimalsApply() {
        Quantity<?> half = Dimensionless.of(0.5, DimensionlessFactory.SYSTEM_UNIT);
        UomQuantityFormat german = FORMAT.withLocale(Locale.GERMANY);
        assertEquals("0,5", german.format(half));
        assertEquals(half, german.parse("0,5"));
        UomQuantityFormat exact = FORMAT.withDecimals(MathContext.DECIMAL128);
        Quantity<?> decimal = exact.parse("1.50");
        assertEquals("1.50", exact.format(decimal));
        assertEquals(new BigDecimal("1.50"), decimal.getValue());
    }
}
