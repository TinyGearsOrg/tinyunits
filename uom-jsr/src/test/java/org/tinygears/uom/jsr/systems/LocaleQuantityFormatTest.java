/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.jsr.systems;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormatSymbols;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.measure.Quantity;
import javax.measure.format.MeasurementParseException;
import javax.measure.quantity.Length;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.jsr.BaseUnits;
import org.tinygears.uom.jsr.UomUnit;
import org.tinygears.uom.jsr.quantity.UntypedDecimalQuantity;
import org.tinygears.uom.jsr.quantity.UntypedDoubleQuantity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocaleQuantityFormatTest {
    private static final UomUnitFormat UNITS = UomUnitFormat.standard();
    private final UomQuantityFormat root = new UomQuantityFormat(UNITS);

    /** 100 double and 100 decimal quantities; their ROOT text is pinned in root-baseline.txt (from a315721). */
    static List<Quantity<?>> baselineSamples() {
        List<UomUnit<?>> units = List.of(
                SI.KILOMETRE,
                SI.DEGREE_CELSIUS,
                SI.MICROMETRE,
                SI.OHM,
                BaseUnits.KELVIN.shift(5),
                BaseUnits.ONE,
                SI.METRE.multiply(2),
                (UomUnit<?>) UNITS.parse("km/h"));
        List<Quantity<?>> samples = new ArrayList<>();
        double[] doubles = {
            0.0,
            -0.0,
            1.0,
            -1.0,
            0.1,
            0.5,
            1.0 / 3,
            1e10,
            1e22,
            1e-7,
            1e-3,
            1e7,
            123456789.125,
            Double.MIN_VALUE,
            Double.MAX_VALUE,
            Double.MIN_NORMAL,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            -1234.5 };
        Random random = new Random(20261012);
        List<Double> values = new ArrayList<>();
        for (double d : doubles) {
            values.add(d);
        }
        while (values.size() < 100) {
            values.add(Double.longBitsToDouble(random.nextLong()));
            values.add(random.nextDouble(-1e6, 1e6));
        }
        for (int i = 0; i < 100; i++) {
            samples.add(UntypedDoubleQuantity.of(values.get(i), units.get(i % units.size())));
        }
        String[] decimals = {
            "1E+3",
            "1.50",
            "-0.000",
            "0E-10",
            "123456789.125",
            "1E-7",
            "-1E+10",
            "0.1",
            "-12345678901234567890.5",
            "3.14159265358979323846264338327950288" };
        List<BigDecimal> bds = new ArrayList<>();
        for (String s : decimals) {
            bds.add(new BigDecimal(s));
        }
        while (bds.size() < 100) {
            bds.add(new BigDecimal(BigInteger.valueOf(random.nextLong()), random.nextInt(-20, 21)));
        }
        for (int i = 0; i < 100; i++) {
            samples.add(UntypedDecimalQuantity.of(bds.get(i), units.get(i % units.size()), MathContext.DECIMAL128));
        }
        return samples;
    }

    @Test
    void rootOutputIsIdenticalToMilestone5() throws IOException {
        List<String> expected;
        try (InputStream in = LocaleQuantityFormatTest.class.getResourceAsStream("root-baseline.txt")) {
            expected = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
        // The baseline of milestone 5, except that a dimensionless number is written alone (docs/DESIGN.md DR-07).
        List<Quantity<?>> samples = baselineSamples();
        assertEquals(200, expected.size());
        for (int i = 0; i < samples.size(); i++) {
            assertEquals(expected.get(i), root.format(samples.get(i)), "sample " + i);
        }
    }

    private static UomQuantityFormat in(String tag) {
        return new UomQuantityFormat(UNITS).withLocale(Locale.forLanguageTag(tag));
    }

    private static Quantity<?> km(double value) {
        return UntypedDoubleQuantity.of(value, SI.KILOMETRE);
    }

    private static void assertParses(UomQuantityFormat format, String text, double value, UomUnit<?> unit) {
        Quantity<?> q = format.parse(text);
        assertEquals(
                Double.doubleToRawLongBits(value),
                Double.doubleToRawLongBits(q.getValue().doubleValue()),
                text + " gave " + q.getValue());
        assertEquals(unit, q.getUnit(), text);
    }

    private static void assertFails(UomQuantityFormat format, String text, String message, int position) {
        MeasurementParseException e = assertThrows(MeasurementParseException.class, () -> format.parse(text), text);
        assertEquals(message, e.getMessage(), text);
        assertEquals(position, e.getPosition(), text);
        assertEquals(text, e.getParsedString(), text);
    }

    @Test
    void accessorsAndImmutability() {
        assertSame(Locale.ROOT, root.getLocale());
        assertFalse(root.isLocaleSensitive());
        UomQuantityFormat german = root.withLocale(Locale.GERMANY);
        assertNotSame(root, german);
        assertEquals(Locale.GERMANY, german.getLocale());
        assertTrue(german.isLocaleSensitive());
        assertSame(Locale.ROOT, root.getLocale());
        assertTrue(in("en-US").isLocaleSensitive());
        assertFalse(german.withLocale(Locale.ROOT).isLocaleSensitive());
        UomQuantityFormat grouped = german.withGrouping(true);
        assertNotSame(german, grouped);
        assertFails(german, "1.234 km", "grouping separator not allowed: '.'", 1);
        assertParses(grouped, "1.234 km", 1234, SI.KILOMETRE);
        assertEquals(Locale.GERMANY, grouped.getLocale());
        assertFails(grouped.withGrouping(false), "1.234 km", "grouping separator not allowed: '.'", 1);
        assertThrows(NullPointerException.class, () -> root.withLocale(null));
    }

    @Test
    void german() {
        UomQuantityFormat de = in("de-DE");
        assertEquals("1,5 km", de.format(km(1.5)));
        assertParses(de, "1,5 km", 1.5, SI.KILOMETRE);
        assertEquals("-1234,5 km", de.format(km(-1234.5)));
        assertEquals("-1234,5 km", de.withGrouping(true).format(km(-1234.5)));
        assertFails(de, "-1.234,5 km", "grouping separator not allowed: '.'", 2);
        assertParses(de.withGrouping(true), "-1.234,5 km", -1234.5, SI.KILOMETRE);
        assertParses(de.withGrouping(true), "1.2.3.4,5 km", 1234.5, SI.KILOMETRE);
        // '.' is never a decimal point in de-DE
        assertParses(de.withGrouping(true), "1.5 km", 15, SI.KILOMETRE);
        assertEquals("1,0E10 km", de.format(km(1e10)));
        assertEquals("1,0E-7 km", de.format(km(1e-7)));
        assertParses(de, "1,0E10 km", 1e10, SI.KILOMETRE);
        assertParses(de, "1,0e-7 km", 1e-7, SI.KILOMETRE);
        assertParses(de, "+1,5E+2 km", 150, SI.KILOMETRE);
        assertParses(de, "-0,0 km", -0.0, SI.KILOMETRE);
        assertEquals("0,30000000000000004 km", de.format(km(0.1 + 0.2)));
        assertEquals("1,23456789125E8 km", de.format(km(123456789.125)));
        assertEquals("123456,125 km", de.format(km(123456.125)));
        assertEquals("1,7976931348623157E308 km", de.format(km(Double.MAX_VALUE)));
        assertEquals("4,9E-324 km", de.format(km(Double.MIN_VALUE)));
    }

    @Test
    void french() {
        UomQuantityFormat fr = in("fr-FR");
        UomQuantityFormat grouped = fr.withGrouping(true);
        assertEquals("-1234,5 km", grouped.format(km(-1234.5)));
        assertFails(fr, "1\u202F234,5 km", "grouping separator not allowed: '\u202F'", 1);
        assertParses(grouped, "1\u202F234,5 km", 1234.5, SI.KILOMETRE);
        assertParses(grouped, "1\u202F234\u202F567 km", 1234567, SI.KILOMETRE);
        // exactly the locale's grouping separator: U+00A0 is not fr-FR's
        assertFails(grouped, "1\u00A0234,5 km", "invalid number '1\u00A0234,5'", 0);
        // a grouping separator not between digits does not join the number and the unit
        assertFails(grouped, "1\u202Fkm", "grouping separator not between digits", 1);
        assertFails(grouped, "12\u202F km", "grouping separator not between digits", 2);
        assertParses(grouped, "12 km", 12, SI.KILOMETRE);
        UomQuantityFormat ru = in("ru-RU").withGrouping(true);
        assertParses(ru, "1\u00A0234,5 km", 1234.5, SI.KILOMETRE);
        assertFails(in("ru-RU"), "1\u00A0234,5 km", "grouping separator not allowed: '\u00A0'", 1);
    }

    @Test
    void english() {
        UomQuantityFormat en = in("en-US");
        assertEquals("-1234.5 km", en.format(km(-1234.5)));
        assertFails(en, "1,234.5 km", "grouping separator not allowed: ','", 1);
        assertParses(en.withGrouping(true), "1,234.5 km", 1234.5, SI.KILOMETRE);
        assertParses(en, "1.0E10 km", 1e10, SI.KILOMETRE);
    }

    @Test
    void swiss() {
        UomQuantityFormat ch = in("de-CH");
        assertEquals("1234.5 km", ch.format(km(1234.5)));
        assertFails(ch, "1’234.5 km", "grouping separator not allowed: '’'", 1);
        assertParses(ch.withGrouping(true), "1’234.5 km", 1234.5, SI.KILOMETRE);
        assertFails(ch.withGrouping(true), "1'234.5 km", "invalid number '1'234.5'", 0);
    }

    @Test
    void arabicIndicDigits() {
        UomQuantityFormat eg = in("ar-EG");
        assertEquals("١٢٫٥ km", eg.format(km(12.5)));
        assertEquals("-١٢٣٤٫٥ km", eg.format(km(-1234.5)));
        assertParses(eg, "١٢٫٥ km", 12.5, SI.KILOMETRE);
        assertParses(eg, "12٫5 km", 12.5, SI.KILOMETRE);
        assertParses(eg, "1٢٫5 km", 12.5, SI.KILOMETRE);
        assertFails(eg, "12.5 km", "invalid number '12.5'", 0);
        assertFails(eg, "١٬٢٣٤ km", "grouping separator not allowed: '٬'", 1);
        assertParses(eg.withGrouping(true), "١٬٢٣٤ km", 1234, SI.KILOMETRE);
        // CLDR changed ar-EG's exponent separator (JDK 21 U+0627 U+0633, JDK 25 U+0623 U+0633)
        String exponent = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("ar-EG")).getExponentSeparator();
        assertEquals("١٫٠" + exponent + "١٠ km", eg.format(km(1e10)));
        assertParses(eg, "١٫٠" + exponent + "١٠ km", 1e10, SI.KILOMETRE);
        assertParses(eg, "١٫٠E١٠ km", 1e10, SI.KILOMETRE);
        // plain "ar" has Arabic-Indic digits on JDK 21 and ASCII digits on JDK 25
        char zero = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("ar")).getZeroDigit();
        UomQuantityFormat ar = in("ar");
        assertEquals("" + (char) (zero + 1) + (char) (zero + 2), ar.format(km(12.5)).substring(0, 2));
        assertParses(ar, ar.format(km(12.5)), 12.5, SI.KILOMETRE);
        UomQuantityFormat arab = in("ar-u-nu-arab");
        assertEquals("١٢٫٥ km", arab.format(km(12.5)));
    }

    @Test
    void devanagariDigits() {
        UomQuantityFormat hi = in("hi-IN-u-nu-deva");
        assertEquals("१२३.५ km", hi.format(km(123.5)));
        assertParses(hi, "१२३.५ km", 123.5, SI.KILOMETRE);
        assertParses(hi, "123.5 km", 123.5, SI.KILOMETRE);
        assertEquals(
                "१.२३E+५ km",
                hi.format(UntypedDecimalQuantity.of(new BigDecimal("1.23E+5"), SI.KILOMETRE, MathContext.DECIMAL64)));
        String pi = "3.14159265358979323846264338327950288";
        StringBuilder devanagari = new StringBuilder();
        pi.chars().forEach(c -> devanagari.append(c == '.' ? '.' : (char) (c - '0' + 0x0966)));
        assertEquals(
                devanagari + " km",
                hi.format(UntypedDecimalQuantity.of(new BigDecimal(pi), SI.KILOMETRE, MathContext.UNLIMITED)));
        assertParses(hi, devanagari + " km", Math.PI, SI.KILOMETRE);
    }

    @Test
    void nonAsciiMinusAndExponent() {
        UomQuantityFormat sv = in("sv-SE");
        assertEquals("\u22121,5×10^10 km", sv.format(km(-1.5e10)));
        assertEquals("1,0×10^\u22127 km", sv.format(km(1e-7)));
        assertParses(sv, "\u22121,5×10^10 km", -1.5e10, SI.KILOMETRE);
        assertParses(sv, "1,0×10^\u22127 km", 1e-7, SI.KILOMETRE);
        assertParses(sv, "-1,5E10 km", -1.5e10, SI.KILOMETRE);
        assertParses(sv, "\u22121,5e10 km", -1.5e10, SI.KILOMETRE);
        UomQuantityFormat fa = in("fa");
        assertEquals("\u2212۱۲٫۵ km", fa.format(km(-12.5)));
        assertEquals("۱٫۰×۱۰^۱۰ km", fa.format(km(1e10)));
        assertParses(fa, "\u2212۱۲٫۵ km", -12.5, SI.KILOMETRE);
        assertParses(fa, "-12٫5 km", -12.5, SI.KILOMETRE);
        assertParses(fa, "۱٫۰×۱۰^۱۰ km", 1e10, SI.KILOMETRE);
        assertParses(fa, "۱E۱۰ km", 1e10, SI.KILOMETRE);
        assertFails(fa, "۱×10^۱۰ km", "invalid number '۱×10^۱۰'", 0);
        assertEquals("1,0Е-7 km", in("uk-UA").format(km(1e-7)));
        assertParses(in("uk-UA"), "1,0Е-7 km", 1e-7, SI.KILOMETRE);
        assertEquals(
                "1E+3 km",
                in("de-DE").format(
                        UntypedDecimalQuantity.of(new BigDecimal("1E+3"), SI.KILOMETRE, MathContext.DECIMAL64)));
        assertEquals(
                "1×10^+3 km",
                sv.format(UntypedDecimalQuantity.of(new BigDecimal("1E+3"), SI.KILOMETRE, MathContext.DECIMAL64)));
        assertParses(sv, "1×10^+3 km", 1000, SI.KILOMETRE);
        assertEquals(
                "\u22121,50 km",
                sv.format(UntypedDecimalQuantity.of(new BigDecimal("-1.50"), SI.KILOMETRE, MathContext.DECIMAL64)));
    }

    /** The JDK's DecimalFormat writes a bidi mark (Cf) before the minus sign in these locales. */
    @Test
    void formatMarksAroundTheNumberAreSkipped() {
        assertParses(in("he-IL"), "\u200E-1.5 km", -1.5, SI.KILOMETRE);
        assertParses(in("ar-EG"), "\u061C-١٫٥ km", -1.5, SI.KILOMETRE);
        assertParses(in("fa"), "\u200E\u2212۱٫۵ km", -1.5, SI.KILOMETRE);
        assertParses(in("he-IL"), "-\u200E1.5\u200F km", -1.5, SI.KILOMETRE);
        assertParses(in("he-IL"), "\u200F1.5 km", 1.5, SI.KILOMETRE);
        assertParses(in("he-IL"), "\u200E-Infinity km", Double.NEGATIVE_INFINITY, SI.KILOMETRE);
        assertEquals("-1.5 km", in("he-IL").format(km(-1.5)));
        assertFails(in("he-IL"), "1\u200E5 km", "invalid number '1\u200E5'", 0);
        assertFails(in("he-IL"), "1\u200E.5 km", "invalid number '1\u200E.5'", 0);
        // ROOT reads numbers by the same rules
        assertParses(root, "\u200E-1.5 km", -1.5, SI.KILOMETRE);
        for (String tag : new String[] { "he-IL", "ar-EG", "ar-SA", "fa", "ps-AF", "ur-IN", "ckb", "lrc", "mzn" }) {
            Locale locale = Locale.forLanguageTag(tag);
            String jdk = java.text.NumberFormat.getInstance(locale).format(-1.5);
            assertParses(root.withLocale(locale), jdk + " km", -1.5, SI.KILOMETRE);
        }
    }

    @Test
    void groupingOnlyInTheIntegerPart() {
        UomQuantityFormat en = in("en-US").withGrouping(true);
        UomQuantityFormat de = in("de-DE").withGrouping(true);
        String fraction = "grouping separator not allowed after the decimal separator";
        String exponent = "grouping separator not allowed in the exponent";
        assertFails(en, "1.234,5 km", fraction, 5);
        assertFails(de, "1,5.3 km", fraction, 3);
        assertFails(de, "1,5E1.0 km", exponent, 5);
        assertFails(root.withGrouping(true), "1,234.5e1,0 m", exponent, 9);
        assertFails(in("sv-SE").withGrouping(true), "1×10^1\u00A00 m", exponent, 6);
        assertParses(de, "1.234,5E1 km", 12345, SI.KILOMETRE);
        assertFails(in("en-US"), "1.234,5 km", "grouping separator not allowed: ','", 5);
    }

    @Test
    void spaceGroupedNumberIsAProductUnit() {
        Quantity<?> q = in("fr-FR").withGrouping(true).parse("1 234 km");
        assertEquals(1.0, q.getValue());
        assertEquals(UNITS.parse("234 km"), q.getUnit());
    }

    @Test
    void equalsHashCodeAndToString() {
        UomQuantityFormat de = in("de-DE");
        assertEquals(de, root.withLocale(Locale.forLanguageTag("de-DE")));
        assertEquals(de.hashCode(), root.withLocale(Locale.forLanguageTag("de-DE")).hashCode());
        assertEquals(root, new UomQuantityFormat(UNITS));
        assertFalse(de.equals(de.withGrouping(true)));
        assertFalse(de.equals(in("de-AT")));
        assertFalse(root.equals(new UomQuantityFormat(UomUnitFormat.standard())));
        assertFalse(
                de.equals(de.withSymbols(Locale.forLanguageTag("de-DE"), DecimalFormatSymbols.getInstance(Locale.UK))));
        assertFalse(de.equals("de-DE"));
        assertEquals(de.withGrouping(true), de.withGrouping(true));
        assertFalse(de.equals(de.withDecimals(MathContext.DECIMAL64)));
        assertEquals(
                de.withDecimals(MathContext.DECIMAL64),
                root.withDecimals(MathContext.DECIMAL64).withLocale(Locale.forLanguageTag("de-DE")));
        assertEquals("UomQuantityFormat[locale=de-DE, grouping=false, decimals=off]", de.toString());
        assertEquals("UomQuantityFormat[locale=und, grouping=true, decimals=off]", root.withGrouping(true).toString());
    }

    @Test
    void groupingAndLocaleCompose() {
        UomQuantityFormat a = root.withGrouping(true).withLocale(Locale.GERMANY);
        UomQuantityFormat b = root.withLocale(Locale.GERMANY).withGrouping(true);
        assertEquals(a, b);
        assertParses(a, "1.234,5 km", 1234.5, SI.KILOMETRE);
        assertParses(b, "1.234,5 km", 1234.5, SI.KILOMETRE);
        assertParses(b.withLocale(Locale.US), "1,234.5 km", 1234.5, SI.KILOMETRE);
    }

    @Test
    void nanAndInfinityKeepTheirRootText() {
        for (String tag : new String[] { "de-DE", "sv-SE", "fa", "ar-EG" }) {
            UomQuantityFormat f = in(tag);
            assertEquals("NaN km", f.format(km(Double.NaN)), tag);
            assertEquals("Infinity km", f.format(km(Double.POSITIVE_INFINITY)), tag);
            assertEquals("-Infinity km", f.format(km(Double.NEGATIVE_INFINITY)), tag);
            assertParses(f, "NaN km", Double.NaN, SI.KILOMETRE);
            assertParses(f, "Infinity km", Double.POSITIVE_INFINITY, SI.KILOMETRE);
            assertParses(f, "-Infinity km", Double.NEGATIVE_INFINITY, SI.KILOMETRE);
            assertParses(f, "+Infinity km", Double.POSITIVE_INFINITY, SI.KILOMETRE);
        }
        assertParses(in("sv-SE"), "\u2212Infinity km", Double.NEGATIVE_INFINITY, SI.KILOMETRE);
        assertFails(in("de-DE"), "Inf km", "invalid number 'Inf'", 0);
    }

    @Test
    void unitSymbolsNeverChange() {
        List<UomUnit<?>> units = List.of(SI.DEGREE_CELSIUS, SI.MICROMETRE, SI.OHM, BaseUnits.KELVIN.shift(5));
        for (String tag : new String[] { "de-DE", "fr-FR", "ar-EG", "fa", "hi-IN-u-nu-deva", "sv-SE", "ja-JP" }) {
            for (UomUnit<?> unit : units) {
                Quantity<?> q = UntypedDoubleQuantity.of(2.5, unit);
                String text = in(tag).format(q);
                assertTrue(text.endsWith(" " + UNITS.format(unit)), tag + " " + text);
                assertEquals(unit, in(tag).parse(text).getUnit(), tag + " " + text);
            }
        }
        assertEquals("2,5 °C", in("de-DE").format(UntypedDoubleQuantity.of(2.5, SI.DEGREE_CELSIUS)));
        assertEquals("2,5 µm", in("de-DE").format(UntypedDoubleQuantity.of(2.5, SI.MICROMETRE)));
        assertEquals("2,5 Ω", in("de-DE").format(UntypedDoubleQuantity.of(2.5, SI.OHM)));
        assertEquals("2,5 K + 5", in("de-DE").format(UntypedDoubleQuantity.of(2.5, BaseUnits.KELVIN.shift(5))));
        assertInstanceOf(Length.class, in("de-DE").parse("2,5 km"));
    }

    @Test
    void errorsAndPositions() {
        UomQuantityFormat de = in("de-DE");
        assertFails(de, "", "missing number", 0);
        assertFails(de, "  ", "missing number", 2);
        assertFails(de, "abc m", "invalid number 'abc'", 0);
        assertFails(de, "  1,5x m", "invalid number '1,5x'", 2);
        assertFails(de, "1,5,5 m", "invalid number '1,5,5'", 0);
        assertFails(de, "1,0E m", "invalid number '1,0E'", 0);
        assertFails(de, "0x10 m", "invalid number '0x10'", 0);
        assertFails(de, "1d m", "invalid number '1d'", 0);
        assertFails(de.withGrouping(true), "1. m", "grouping separator not between digits", 1);
        assertFails(de.withGrouping(true), ".1 m", "grouping separator not between digits", 0);
        assertFails(de.withGrouping(true), "1,.5 m", "grouping separator not allowed after the decimal separator", 2);
        assertFails(de.withGrouping(true), "1.,5 m", "grouping separator not between digits", 1);
        assertFails(de, "1,5 foo", "unknown unit 'foo'", 4);

        ParsePosition pos = new ParsePosition(3);
        MeasurementParseException e = assertThrows(
                MeasurementParseException.class,
                () -> de.parse("x: -1.234,5 km", pos));
        assertEquals(5, e.getPosition());
        assertEquals(5, pos.getErrorIndex());
        ParsePosition ok = new ParsePosition(3);
        Quantity<?> q = de.withGrouping(true).parse("x: -1.234,5 km", ok);
        assertEquals(-1234.5, q.getValue());
        assertEquals(14, ok.getIndex());

        UomQuantityFormat fa = in("fa");
        assertFails(fa, "۱٬۲ m", "grouping separator not allowed: '٬'", 1);
        assertFails(fa, "  ۱۲٬۲ m", "grouping separator not allowed: '٬'", 4);
        assertFails(in("sv-SE"), "1,5 m x", "unknown unit 'x'", 6);
    }

    @Test
    void rootWithGroupingUsesComma() {
        assertFails(root, "1,234.5 m", "grouping separator not allowed: ','", 1);
        UomQuantityFormat grouped = root.withGrouping(true);
        assertFalse(grouped.isLocaleSensitive());
        assertParses(grouped, "1,234.5 m", 1234.5, SI.METRE);
        assertParses(grouped, "-1,000.5E1 m", -10005, SI.METRE);
        assertEquals("1234.5 m", grouped.format(UntypedDoubleQuantity.of(1234.5, SI.METRE)));
        assertParses(grouped, "1,234,5 m", 12345, SI.METRE);
        assertFails(grouped, "1,,234 m", "grouping separator not between digits", 1);
    }

    static double[] roundTripValues() {
        return new double[] {
            0.0,
            -0.0,
            1.0,
            -1.0,
            0.5,
            -0.5,
            0.1,
            -0.1,
            1.0 / 3,
            -2.0 / 3,
            1e22,
            -1e22,
            1e-22,
            1e10,
            1e-7,
            1e7,
            1e-3,
            123456789.125,
            -123456789.125,
            Double.MIN_VALUE,
            -Double.MIN_VALUE,
            Double.MAX_VALUE,
            -Double.MAX_VALUE,
            Double.MIN_NORMAL,
            1e300,
            -1e-300,
            42.0,
            1234.5,
            -1234.5,
            0.30000000000000004,
            9007199254740993.0,
            6.02214076e23,
            1.602176634e-19,
            299792458.0,
            2.5e-5,
            Math.PI,
            -Math.E,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY };
    }

    @Test
    void roundTripInEveryAvailableLocale() {
        double[] values = roundTripValues();
        assertEquals(40, values.length);
        List<UomUnit<?>> units = List.of(SI.KILOMETRE, SI.DEGREE_CELSIUS, BaseUnits.KELVIN.shift(5), BaseUnits.ONE);
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (Locale locale : Locale.getAvailableLocales()) {
            for (boolean grouping : new boolean[] { false, true }) {
                UomQuantityFormat f = root.withLocale(locale).withGrouping(grouping);
                for (UomUnit<?> unit : units) {
                    String unitText = UNITS.format(unit);
                    for (double v : values) {
                        Quantity<?> q = UntypedDoubleQuantity.of(v, unit);
                        String text = f.format(q);
                        checked++;
                        try {
                            Quantity<?> back = f.parse(text);
                            if (Double.doubleToRawLongBits(back.getValue().doubleValue()) != Double
                                    .doubleToRawLongBits(v) || !unit.equals(back.getUnit())
                                    || !(unitText.equals("1") ? !text.contains(" ") : text.endsWith(" " + unitText))) {
                                failures.add(locale.toLanguageTag() + " " + grouping + " " + text + " -> " + back);
                            }
                        } catch (RuntimeException e) {
                            failures.add(locale.toLanguageTag() + " " + grouping + " " + text + ": " + e);
                        }
                    }
                }
            }
        }
        assertTrue(checked >= 1000 * 40 * 4 * 2, "checked " + checked);
        assertEquals(List.of(), failures.subList(0, Math.min(20, failures.size())), failures.size() + " failures");
    }

    @Test
    void decimalRoundTripInEveryAvailableLocale() {
        List<UomUnit<?>> units = List.of(SI.KILOMETRE, SI.DEGREE_CELSIUS, BaseUnits.KELVIN.shift(5), BaseUnits.ONE);
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (Locale locale : Locale.getAvailableLocales()) {
            for (boolean grouping : new boolean[] { false, true }) {
                UomQuantityFormat f = root.withLocale(locale).withGrouping(grouping)
                        .withDecimals(MathContext.DECIMAL64);
                for (UomUnit<?> unit : units) {
                    for (String s : DecimalParseTest.DECIMALS) {
                        BigDecimal v = new BigDecimal(s);
                        String text = f.format(UntypedDecimalQuantity.of(v, unit, MathContext.DECIMAL64));
                        checked++;
                        try {
                            Quantity<?> back = f.parse(text);
                            if (!v.equals(back.getValue()) || !unit.equals(back.getUnit())) {
                                failures.add(locale.toLanguageTag() + " " + grouping + " " + text + " -> " + back);
                            }
                        } catch (RuntimeException e) {
                            failures.add(locale.toLanguageTag() + " " + grouping + " " + text + ": " + e);
                        }
                    }
                }
            }
        }
        assertTrue(checked >= 1000 * 15 * 4 * 2, "checked " + checked);
        assertEquals(List.of(), failures.subList(0, Math.min(20, failures.size())), failures.size() + " failures");
    }

    @Test
    void symbolsThatCannotRoundTripFailAtWithLocale() {
        Locale x = Locale.forLanguageTag("x-test");
        DecimalFormatSymbols same = DecimalFormatSymbols.getInstance(Locale.GERMANY);
        same.setGroupingSeparator(',');
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> root.withSymbols(x, same));
        assertEquals(
                "locale x-test cannot round trip numbers: decimal separator ',' equals grouping separator",
                e.getMessage());
        DecimalFormatSymbols digitMinus = DecimalFormatSymbols.getInstance(Locale.GERMANY);
        digitMinus.setMinusSign('7');
        assertEquals(
                "locale x-test cannot round trip numbers: minus sign '7' is a letter, digit, space, format mark or plus",
                assertThrows(IllegalArgumentException.class, () -> root.withSymbols(x, digitMinus)).getMessage());
        DecimalFormatSymbols letterDecimal = DecimalFormatSymbols.getInstance(Locale.GERMANY);
        letterDecimal.setDecimalSeparator('E');
        assertThrows(IllegalArgumentException.class, () -> root.withSymbols(x, letterDecimal));
        DecimalFormatSymbols spaceGrouping = DecimalFormatSymbols.getInstance(Locale.GERMANY);
        spaceGrouping.setGroupingSeparator(' ');
        assertThrows(IllegalArgumentException.class, () -> root.withSymbols(x, spaceGrouping));
        DecimalFormatSymbols plusDecimal = DecimalFormatSymbols.getInstance(Locale.GERMANY);
        plusDecimal.setDecimalSeparator('+');
        assertThrows(IllegalArgumentException.class, () -> root.withSymbols(x, plusDecimal));
        DecimalFormatSymbols digitExponent = DecimalFormatSymbols.getInstance(Locale.GERMANY);
        digitExponent.setExponentSeparator("10^");
        assertEquals(
                "locale x-test cannot round trip numbers: exponent separator '10^' starts with a digit, sign"
                        + " or separator",
                assertThrows(IllegalArgumentException.class, () -> root.withSymbols(x, digitExponent)).getMessage());
        DecimalFormatSymbols emptyExponent = DecimalFormatSymbols.getInstance(Locale.GERMANY);
        emptyExponent.setExponentSeparator("");
        assertThrows(IllegalArgumentException.class, () -> root.withSymbols(x, emptyExponent));
        DecimalFormatSymbols spacedExponent = DecimalFormatSymbols.getInstance(Locale.GERMANY);
        spacedExponent.setExponentSeparator("x 10^");
        assertThrows(IllegalArgumentException.class, () -> root.withSymbols(x, spacedExponent));
        DecimalFormatSymbols markMinus = DecimalFormatSymbols.getInstance(Locale.GERMANY);
        markMinus.setMinusSign('\u200E');
        assertThrows(IllegalArgumentException.class, () -> root.withSymbols(x, markMinus));
        for (char c : new char[] { ',', '.' }) {
            DecimalFormatSymbols separatorMinus = DecimalFormatSymbols.getInstance(Locale.GERMANY);
            separatorMinus.setMinusSign(c);
            assertEquals(
                    "locale x-test cannot round trip numbers: minus sign '" + c + "' collides with a separator",
                    assertThrows(IllegalArgumentException.class, () -> root.withSymbols(x, separatorMinus))
                            .getMessage());
        }
        DecimalFormatSymbols dashGroup = DecimalFormatSymbols.getInstance(Locale.GERMANY);
        dashGroup.setMinusSign('\u2212');
        dashGroup.setGroupingSeparator('-');
        assertThrows(IllegalArgumentException.class, () -> root.withSymbols(x, dashGroup));
        DecimalFormatSymbols dashDecimal = DecimalFormatSymbols.getInstance(Locale.GERMANY);
        dashDecimal.setMinusSign('\u2212');
        dashDecimal.setDecimalSeparator('-');
        assertThrows(IllegalArgumentException.class, () -> root.withSymbols(x, dashDecimal));
        for (String first : new String[] { ",10^", ".10^", "-10^", "+10^", "\u221210^" }) {
            DecimalFormatSymbols bad = DecimalFormatSymbols.getInstance(Locale.GERMANY);
            bad.setMinusSign('\u2212');
            bad.setExponentSeparator(first);
            assertEquals(
                    "locale x-test cannot round trip numbers: exponent separator '" + first
                            + "' starts with a digit, sign or separator",
                    assertThrows(IllegalArgumentException.class, () -> root.withSymbols(x, bad)).getMessage(),
                    first);
        }
        DecimalFormatSymbols arabicDigitExponent = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("ar-EG"));
        arabicDigitExponent.setExponentSeparator("\u0661x");
        assertThrows(IllegalArgumentException.class, () -> root.withSymbols(x, arabicDigitExponent));
        DecimalFormatSymbols badZero = DecimalFormatSymbols.getInstance(Locale.GERMANY);
        badZero.setZeroDigit('a');
        assertEquals(
                "locale x-test cannot round trip numbers: zero digit 'a' does not start a digit run",
                assertThrows(IllegalArgumentException.class, () -> root.withSymbols(x, badZero)).getMessage());
    }

    @Test
    void concurrentUseOfOneFormat() throws Exception {
        UomQuantityFormat fa = in("fa").withGrouping(true);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                long seed = t;
                results.add(pool.submit(() -> {
                    Random random = new Random(seed);
                    int failures = 0;
                    for (int i = 0; i < 5000; i++) {
                        double v = Double.longBitsToDouble(random.nextLong());
                        double back = fa.parse(fa.format(km(v))).getValue().doubleValue();
                        if (Double.isNaN(v)
                                ? !Double.isNaN(back)
                                : Double.doubleToRawLongBits(back) != Double.doubleToRawLongBits(v)) {
                            failures++;
                        }
                    }
                    return failures;
                }));
            }
            for (Future<Integer> result : results) {
                assertEquals(0, result.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** The compiled classes reference no rounding formatter (DecimalFormatSymbols only). */
    @Test
    void noNumberFormatInTheImplementation() throws IOException {
        for (String name : new String[] { "UomQuantityFormat.class", "UomQuantityFormat$Factories.class" }) {
            String bytes;
            try (InputStream in = UomQuantityFormat.class.getResourceAsStream(name)) {
                bytes = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            }
            assertFalse(bytes.matches("(?s).*java/text/(NumberFormat|DecimalFormat(?!Symbols)).*"), name);
        }
    }
}
