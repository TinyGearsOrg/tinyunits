/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.nativeapi.jsr;

import java.io.StringReader;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import javax.measure.format.MeasurementParseException;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.core.quantity.UomQuantity;
import org.tinygears.uom.nativeapi.ParseException;
import org.tinygears.uom.nativeapi.Quantity;
import org.tinygears.uom.nativeapi.QuantityFormat;
import org.tinygears.uom.nativeapi.SystemOfUnits;
import org.tinygears.uom.nativeapi.Unit;
import org.tinygears.uom.nativeapi.UnitFormat;
import org.tinygears.uom.nativeapi.systems.UnitSystems;
import org.tinygears.uom.systems.UomQuantityFormat;
import org.tinygears.uom.systems.UomSystemOfUnits;
import org.tinygears.uom.systems.UomUnitFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The unit and quantity formats and the system loading of both APIs give the same texts, units and errors. */
class FormatParityTest {
    private static final UnitFormat NATIVE = UnitFormat.standard();
    private static final UomUnitFormat JSR = UomUnitFormat.standard();

    private static final List<String> FORMULAS = List.of(
            "km",
            "km/h",
            "kg·m/s^2",
            "m²",
            "m^2",
            "s⁻¹",
            "1|1000·m/s",
            "1000·min",
            "K + 5",
            "5|9·K + 459.67",
            "mHz",
            "KiB",
            "kB",
            "MiB/s",
            "pi·rad",
            "°C",
            "°F",
            "ft·lbf",
            "1",
            "Fr",
            "statC",
            "gal_UK",
            "foo",
            "km/",
            "m^",
            "(m",
            "dB",
            "Kim",
            "  km  ");

    @Test
    void everyCatalogUnitFormatsAlike() {
        int n = 0;
        for (SystemOfUnits system : UnitSystems.all()) {
            for (Unit<?> unit : system.units()) {
                UomUnit<?> jsr = UomUnit.of(unit.engine());
                assertEquals(JSR.format(jsr), NATIVE.format(unit), unit.toString());
                assertEquals(
                        JSR.withSuperscripts(true).format(jsr),
                        NATIVE.withSuperscripts(true).format(unit),
                        unit.toString());
                n++;
            }
        }
        assertEquals(320, n);
    }

    @Test
    void formulasParseAndFormatAlike() {
        for (String formula : FORMULAS) {
            same(formula, () -> describe(NATIVE.parse(formula)), () -> describe(JSR.parse(formula)));
        }
    }

    private static String describe(Unit<?> u) {
        return u.engine().toString() + " | " + u.engine().definition() + " | " + NATIVE.format(u);
    }

    private static String describe(javax.measure.Unit<?> u) {
        UomUnit<?> uu = (UomUnit<?>) u;
        return uu.engine().toString() + " | " + uu.engine().definition() + " | " + JSR.format(u);
    }

    private static final List<String> QUANTITIES = List.of(
            "1.5 km",
            "90 km/h",
            "-0.0 m",
            "NaN m",
            "Infinity s",
            "1E10 J",
            "2",
            "1.50 km",
            "20 °C",
            "1,5 km",
            "1.500,25 m",
            "1x km",
            "km",
            "",
            "1 foo",
            "1e-400 m",
            "1E100000 m");

    @Test
    void quantitiesFormatAndParseAlike() {
        int n = 0;
        for (Locale locale : List.of(
                Locale.ROOT,
                Locale.GERMANY,
                Locale.FRANCE,
                Locale.forLanguageTag("sv-SE"),
                Locale.forLanguageTag("ar-EG"))) {
            for (boolean grouping : new boolean[] { false, true }) {
                for (MathContext mc : new MathContext[] { null, MathContext.DECIMAL64 }) {
                    QuantityFormat nf = new QuantityFormat(NATIVE).withLocale(locale).withGrouping(grouping)
                            .withDecimals(mc);
                    UomQuantityFormat jf = new UomQuantityFormat(JSR).withLocale(locale).withGrouping(grouping)
                            .withDecimals(mc);
                    for (String text : QUANTITIES) {
                        String what = locale + " " + grouping + " " + mc + " '" + text + "'";
                        same(what, () -> describe(nf.parse(text), nf), () -> describe(jf.parse(text), jf));
                        n++;
                    }
                }
            }
        }
        assertEquals(5 * 2 * 2 * QUANTITIES.size(), n);
    }

    private static String describe(Quantity<?> q, QuantityFormat f) {
        return q.value() + " " + q.value().getClass().getSimpleName() + " " + q.mathContext() + " " + q.unit().engine()
                + " " + q.unit().type().getSimpleName() + " " + q.scale() + " | " + f.format(q);
    }

    private static String describe(UomQuantity<?, ?> q, UomQuantityFormat f) {
        String type = q instanceof org.tinygears.uom.core.quantity.Untyped
                ? "Untyped"
                : q.getClass().getSimpleName().replaceAll("(Double|Decimal)$", "");
        return q.getValue() + " " + q.getValue().getClass().getSimpleName() + " "
                + (q instanceof org.tinygears.uom.core.quantity.AbstractDecimalQuantity<?, ?> d
                        ? d.mathContext()
                        : null)
                + " " + ((UomUnit<?>) q.getUnit()).engine() + " " + type + " " + q.getScale() + " | " + f.format(q);
    }

    @Test
    void systemsLoadAlike() {
        String text = "@system Game\nleague:league 3 mi\nfurl:furlong 1|8 mi\n@prefixed league k\n"
                + "@preferred Length league\n";
        SystemOfUnits n = SystemOfUnits.load("game.units", new StringReader(text), null);
        UomSystemOfUnits j = UomSystemOfUnits.load("game.units", new StringReader(text), null);
        assertEquals(j.getName(), n.name());
        List<String> ns = new ArrayList<>();
        n.units().forEach(u -> ns.add(u.symbol() + " " + u.engine().definition()));
        List<String> js = new ArrayList<>();
        j.getUnits().forEach(u -> js.add(u.getSymbol() + " " + ((UomUnit<?>) u).engine().definition()));
        assertEquals(js, ns);
        assertEquals(
                ((UomUnit<?>) j.getUnit(javax.measure.quantity.Length.class)).engine(),
                n.unit(org.tinygears.uom.nativeapi.quantity.Length.class).engine());
        assertEquals(j.isPrefixable("league"), n.isPrefixable("league"));
        String nativeText = new QuantityFormat(NATIVE.with(n))
                .format(new QuantityFormat(NATIVE.with(n)).parse("3 kleague"));
        String jsrText = new UomQuantityFormat(JSR.with(j))
                .format(new UomQuantityFormat(JSR.with(j)).parse("3 kleague"));
        assertEquals(jsrText, nativeText);
        assertEquals("3.0 kleague", nativeText);
    }

    /** The same result, or the same error message and position on both sides. */
    private static void same(String what, Supplier<String> nativeSide, Supplier<String> jsrSide) {
        String n;
        String j;
        try {
            n = nativeSide.get();
        } catch (ParseException e) {
            n = "error " + e.getMessage() + " at " + e.position();
        } catch (IllegalArgumentException | ArithmeticException e) {
            n = e.getClass().getSimpleName() + " " + e.getMessage();
        }
        try {
            j = jsrSide.get();
        } catch (MeasurementParseException e) {
            j = "error " + e.getMessage() + " at " + e.getPosition();
        } catch (IllegalArgumentException | ArithmeticException e) {
            j = e.getClass().getSimpleName() + " " + e.getMessage();
        }
        assertEquals(j, n, what);
    }
}
