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

import java.io.IOException;
import java.math.BigDecimal;
import java.text.ParsePosition;
import java.util.Random;
import javax.measure.Quantity;
import javax.measure.format.MeasurementParseException;
import javax.measure.quantity.Dimensionless;
import javax.measure.quantity.Frequency;
import javax.measure.quantity.Length;
import javax.measure.quantity.Speed;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.core.quantity.UntypedDoubleQuantity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantityFormatTest {
    private final UomUnitFormat units = UomUnitFormat.standard();
    private final UomQuantityFormat format = new UomQuantityFormat(units);

    @Test
    void typedQuantities() {
        Quantity<?> metre = format.parse("1 m");
        assertInstanceOf(Length.class, metre);
        assertEquals(1.0, metre.getValue());
        assertEquals(SI.METRE, metre.getUnit());
        assertSame(Quantity.Scale.ABSOLUTE, metre.getScale());

        Quantity<?> speed = format.parse("2.5 km/h");
        assertInstanceOf(Speed.class, speed);
        assertEquals(2.5, speed.getValue());
        assertEquals(units.parse("km/h"), speed.getUnit());

        Quantity<?> frequency = format.parse("1e3 Hz");
        assertInstanceOf(Frequency.class, frequency);
        assertEquals(1000.0, frequency.getValue());
        assertEquals(SI.HERTZ, frequency.getUnit());

        assertEquals(SI.METRE, format.parse("  -3   m  ").getUnit());
        assertEquals(-3.0, format.parse("  -3   m  ").getValue());
    }

    @Test
    void untypedQuantity() {
        Quantity<?> q = format.parse("1 rad/s");
        assertInstanceOf(UntypedDoubleQuantity.class, q);
        assertEquals(SI.RADIAN.divide(SI.SECOND), q.getUnit());
    }

    @Test
    void numberOnlyIsDimensionless() {
        Quantity<?> q = format.parse("5");
        assertInstanceOf(Dimensionless.class, q);
        assertEquals(5.0, q.getValue());
        assertEquals(BaseUnits.ONE, q.getUnit());
    }

    private void assertFails(String text, int position) {
        MeasurementParseException e = assertThrows(MeasurementParseException.class, () -> format.parse(text), text);
        assertEquals(position, e.getPosition(), text);
        assertEquals(text, e.getParsedString());
        assertTrue(e.getMessage() != null && !e.getMessage().isEmpty(), text);
    }

    @Test
    void errors() {
        assertFails("abc m", 0);
        assertFails("", 0);
        assertFails("  ", 2);
        assertFails("1 foo", 2);
        assertFails("1 m extra junk", 4);
        assertFails(" 1 m/", 5);
        assertThrows(NullPointerException.class, () -> format.parse(null));
    }

    @Test
    void parsePosition() {
        ParsePosition pos = new ParsePosition(3);
        Quantity<?> q = format.parse("x: 2 kg", pos);
        assertEquals(2.0, q.getValue());
        assertEquals(SI.KILOGRAM, q.getUnit());
        assertEquals(7, pos.getIndex());

        ParsePosition bad = new ParsePosition(3);
        MeasurementParseException e = assertThrows(
                MeasurementParseException.class,
                () -> format.parse("x: 2 kgg", bad));
        assertEquals(5, e.getPosition());
        assertEquals(5, bad.getErrorIndex());
        assertEquals(3, bad.getIndex());
    }

    @Test
    void formatRoundTrip() throws IOException {
        Random random = new Random(7);
        for (int i = 0; i < 30; i++) {
            UomUnit<?> unit = SI.UNITS.get(random.nextInt(SI.UNITS.size()));
            double value = random.nextDouble(-1e6, 1e6);
            Quantity<?> q = format.parse(value + " " + unit);
            String text = format.format(q);
            assertEquals(value + " " + unit.getSymbol(), text);
            Quantity<?> back = format.parse(text);
            assertEquals(q.getValue(), back.getValue(), text);
            assertEquals(q.getUnit(), back.getUnit(), text);
            assertEquals(q.getClass(), back.getClass(), text);
        }
        StringBuilder sb = new StringBuilder();
        assertSame(sb, format.format(format.parse("3 N"), sb));
        assertEquals("3.0 N", sb.toString());
        assertFalse(format.isLocaleSensitive());
    }

    @Test
    void scaledUnitRoundTrip() {
        Quantity<?> q = format.parse("1 2 m");
        assertEquals(SI.METRE.multiply(2), q.getUnit());
        assertEquals("1.0 2·m", format.format(q));
        assertEquals(q, format.parse(format.format(q)));
        Quantity<?> shifted = UntypedDoubleQuantity.of(1, BaseUnits.KELVIN.shift(5));
        assertEquals("1.0 K + 5", format.format(shifted));
        assertEquals(BaseUnits.KELVIN.shift(5), format.parse("1.0 K + 5").getUnit());
        assertThrows(
                IllegalArgumentException.class,
                () -> format.format(UntypedDoubleQuantity.of(1, BaseUnits.KELVIN.multiply(-1))));
    }

    @Test
    void everySymbolParsesAsOneOfIt() {
        for (UomUnit<?> unit : SI.UNITS) {
            Quantity<?> q = format.parse("1 " + unit);
            assertEquals(unit, q.getUnit(), unit.toString());
            assertEquals(1.0, q.getValue().doubleValue(), unit.toString());
        }
    }

    @Test
    void decimalFormatsAsBigDecimalToStringAndParsesAsDouble() {
        Quantity<?> decimal = org.tinygears.uom.quantity.Length.of(new BigDecimal("1.50"), SI.KILOMETRE);
        assertEquals("1.50 km", format.format(decimal));
        // D12: the default format parses to double; withDecimals keeps the digits (DecimalParseTest).
        Quantity<?> parsed = format.parse("1.50 km");
        assertEquals(1.5, parsed.getValue());
        assertEquals(SI.KILOMETRE, parsed.getUnit());
    }
}
