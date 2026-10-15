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

import java.util.ArrayList;
import java.util.List;
import javax.measure.Unit;
import javax.measure.format.MeasurementParseException;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.quantity.Speed;
import org.tinygears.uom.quantity.Time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Formulas read superscript exponents; a format writes them on request, ASCII exponents by default. */
class SuperscriptTest {
    private static final UomUnitFormat ASCII = UomUnitFormat.standard();
    private static final UomUnitFormat UNICODE = ASCII.withSuperscripts(true);

    @Test
    void superscriptsAreExponentsInFormulas() {
        assertEquals(ASCII.parse("m/s^2"), ASCII.parse("m/s²"));
        assertEquals(ASCII.parse("kg m^2/s^3"), ASCII.parse("kg·m²/s³"));
        assertEquals(ASCII.parse("s^-1"), ASCII.parse("s⁻¹"));
        assertEquals(ASCII.parse("(m/s)^2"), ASCII.parse("(m/s)²"));
        assertEquals(ASCII.parse("m^-2"), ASCII.parse("m⁻²"));
        // A prefixed unit is raised as a whole: km² is (km)^2, 10^6 m^2.
        assertEquals(ASCII.parse("(km)^2"), ASCII.parse("km²"));
        assertEquals(SI.METRE.multiply(1000).pow(2), ASCII.parse("km²"));
        // Units named with a superscript keep their meaning; 1 cm² is 10^-4 m² either way.
        assertEquals(ASCII.parse("cm^2"), ASCII.parse("cm²"));
        assertEquals("cm²", ASCII.parse("cm²").getSymbol());
        assertEquals(ASCII.parse("in^2"), ASCII.parse("in²"));
    }

    @Test
    void badSuperscriptsAreRefused() {
        assertThrows(MeasurementParseException.class, () -> ASCII.parse("x²"));
        assertThrows(MeasurementParseException.class, () -> ASCII.parse("s⁻"));
        assertThrows(MeasurementParseException.class, () -> ASCII.parse("²"));
        assertEquals(
                "exponent 9999 exceeds 1000",
                assertThrows(MeasurementParseException.class, () -> ASCII.parse("m⁹⁹⁹⁹")).getMessage());
    }

    @Test
    void exponentsAreWrittenAsAsciiByDefault() {
        assertFalse(ASCII.writesSuperscripts());
        assertEquals("m/s^2", ASCII.format(ASCII.parse("m/s²")));
        assertEquals("s^-1", ASCII.format(SI.SECOND.inverse()));
        assertEquals("(m/s)^2", ASCII.format(SI.METRE.divide(SI.SECOND).pow(2)));
    }

    @Test
    void exponentsAreWrittenAsSuperscriptsOnRequest() {
        assertTrue(UNICODE.writesSuperscripts());
        assertEquals("m/s²", UNICODE.format(ASCII.parse("m/s^2")));
        assertEquals("s⁻¹", UNICODE.format(SI.SECOND.inverse()));
        assertEquals("(m/s)²", UNICODE.format(SI.METRE.divide(SI.SECOND).pow(2)));
        assertEquals("m¹⁰", UNICODE.format(SI.METRE.pow(10)));
        assertEquals("kg·m²/s³", UNICODE.format(ASCII.parse("kg m^2/s^3").getSystemUnit()).replace("W", "kg·m²/s³"));
        // A catalog symbol with a superscript is written as it is, and units without an exponent are unchanged.
        assertEquals("km", UNICODE.format(SI.KILOMETRE));
        assertEquals("mHz", UNICODE.format(SI.HERTZ.prefix(javax.measure.MetricPrefix.MILLI)));
        // The format that was asked is unchanged, and the new one can go back.
        assertFalse(ASCII.writesSuperscripts());
        assertFalse(UNICODE.withSuperscripts(false).writesSuperscripts());
        assertEquals("m/s^2", UNICODE.withSuperscripts(false).format(ASCII.parse("m/s²")));
        assertTrue(UNICODE.with(SI.SYSTEM).writesSuperscripts());
    }

    @Test
    void aPowerOfAUnitWithASuperscriptSymbolKeepsItsCaret() {
        // m²² would read as m^22, so the square of m² stays m²^2, which reads back as m^4.
        UomUnit<?> squareOfSquare = ((UomUnit<?>) ASCII.parse("m²")).pow(2);
        assertEquals("m²^2", UNICODE.format(squareOfSquare));
        assertEquals(SI.METRE.pow(4), ASCII.parse("m²^2"));
        assertEquals(squareOfSquare, ASCII.parse(UNICODE.format(squareOfSquare)));
        assertEquals(SI.METRE.pow(4), ASCII.parse("(m²)²"));
    }

    @Test
    void theNewFormatKeepsTheLabelsOfTheOldOne() {
        UomUnitFormat f = UomUnitFormat.standard();
        UomUnit<?> furlong = SI.METRE.multiply(201.168);
        f.label(furlong, "fur");
        UomUnitFormat unicode = f.withSuperscripts(true);
        assertEquals("fur", unicode.format(furlong));
        f.label(SI.METRE.multiply(3), "yard3");
        assertNotEquals("yard3", unicode.format(SI.METRE.multiply(3)));
    }

    @Test
    void whatIsWrittenWithSuperscriptsIsReadBack() {
        List<String> bad = new ArrayList<>();
        int checked = 0;
        for (UomSystemOfUnits system : UnitSystems.all()) {
            for (Unit<?> known : system.getUnits()) {
                for (int n : new int[] { -3, -2, 2, 3, 10 }) {
                    UomUnit<?> unit;
                    try {
                        unit = ((UomUnit<?>) known).pow(n);
                    } catch (ArithmeticException e) {
                        continue;
                    }
                    String text;
                    try {
                        text = UNICODE.format(unit);
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                    checked++;
                    if (!unit.equals(ASCII.parse(text))) {
                        bad.add(known.getSymbol() + "^" + n + ": " + text);
                    }
                }
            }
        }
        assertEquals(List.of(), bad.subList(0, Math.min(bad.size(), 10)));
        assertTrue(checked > 500, "checked " + checked);
    }

    @Test
    void aQuantityFormatOverTheNewFormatWritesAndReadsSuperscripts() {
        UomQuantityFormat f = new UomQuantityFormat(UNICODE);
        var acceleration = Speed.of(1, (Unit) SI.METRE.divide(SI.SECOND)).divide(Time.of(1, SI.SECOND));
        assertEquals("1.0 m/s²", f.format(acceleration));
        // The parse gives the quantity of the standard type of its unit.
        assertEquals(acceleration.getValue(), f.parse("1.0 m/s²").getValue());
        assertEquals(acceleration.getUnit(), f.parse("1.0 m/s²").getUnit());
        assertEquals(f.parse("1.0 m/s²"), f.parse("1.0 m/s^2"));
        // toString is not a format: it stays ASCII.
        assertEquals("1.0 m/s^2", acceleration.toString());
        assertEquals("1/s", UNICODE.format(ASCII.parse("1/s")));
    }
}
