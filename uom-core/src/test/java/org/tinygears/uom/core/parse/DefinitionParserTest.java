/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.core.parse;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.measure.BinaryPrefix;
import javax.measure.MetricPrefix;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.tinygears.uom.core.Converters;
import org.tinygears.uom.core.Factor;
import org.tinygears.uom.core.UomUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tinygears.uom.core.BaseUnits.KELVIN;
import static org.tinygears.uom.core.BaseUnits.KILOGRAM;
import static org.tinygears.uom.core.BaseUnits.METRE;
import static org.tinygears.uom.core.BaseUnits.ONE;
import static org.tinygears.uom.core.BaseUnits.SECOND;

class DefinitionParserTest {
    private static final String HAPPY = """
            # comment
            m:metre            !L
            kg:kilogram        !M
            s:second           !T
            K:kelvin           !Θ
            rad:radian         ~
            Hz:hertz           ~ 1/s   [Frequency]
            N:newton           kg m / s^2 [Force]
            in:inch            0.0254 m
            °C:degree_Celsius  K = x + 273.15
            @preferred Length m
            """;
    private static final String BASE = "m:metre !L\nkg:kilogram !M\ns:second !T\nK:kelvin !Θ\n";

    private static Definitions parse(String text) {
        return parse(text, Map.of());
    }

    private static Definitions parse(String text, Map<String, String> files) {
        return DefinitionParser.parse("test.units", new StringReader(text), name -> {
            String content = files.get(name);
            if (content == null) {
                throw new FileNotFoundException(name);
            }
            return new StringReader(content);
        });
    }

    private static Definition find(Definitions d, String symbol) {
        return d.definitions().stream().filter(x -> x.symbol().equals(symbol)).findFirst().orElseThrow();
    }

    private static List<String> symbols(Definitions d) {
        return d.definitions().stream().map(Definition::symbol).toList();
    }

    private static void fails(String text, String message) {
        fails(text, Map.of(), message);
    }

    private static void fails(String text, Map<String, String> files, String message) {
        DefinitionException e = assertThrows(DefinitionException.class, () -> parse(text, files));
        assertEquals(message, e.getMessage());
    }

    @Test
    void happyPath() {
        Definitions d = parse(HAPPY);
        assertEquals(List.of("m", "kg", "s", "K", "rad", "Hz", "N", "in", "°C"), symbols(d));
        assertEquals("Frequency", find(d, "Hz").explicitType());
        assertEquals("Force", find(d, "N").explicitType());
        assertNull(find(d, "in").explicitType());
        assertEquals("degree Celsius", find(d, "°C").name());
        UomUnit<javax.measure.quantity.Temperature> degC = UomUnit.unchecked(find(d, "°C").unit());
        assertEquals(373.15, degC.getConverterTo(KELVIN).convert(100.0), 1e-12);
        UomUnit<?> hz = find(d, "Hz").unit();
        assertNotEquals(ONE.divide(SECOND), hz);
        assertTrue(hz.isCompatible(ONE.divide(SECOND)));
        UomUnit<javax.measure.quantity.Length> in = UomUnit.unchecked(find(d, "in").unit());
        assertTrue(in.isEquivalentTo(METRE.multiply(254).divide(10000)));
        assertEquals(ONE.divide(SECOND), find(d, "Hz").alternateOf());
        assertEquals(ONE, find(d, "rad").alternateOf());
        assertNull(find(d, "in").alternateOf());
        assertEquals(List.of(new Preferred("Length", "m", "test.units", 11)), d.preferred());
        Definition m = find(d, "m");
        assertEquals("test.units", m.source());
        assertEquals(2, m.line());
        assertEquals(10, find(d, "°C").line());
        assertEquals("m", m.unit().getSymbol());
        assertEquals("metre", m.unit().getName());
        assertEquals(METRE, m.unit());
    }

    @Test
    void juxtapositionGroupsTheDenominator() {
        Definitions d = parse(BASE + "N kg m / s^2\nJ:joule N m\nx J / kg K\n");
        assertEquals(find(d, "J").unit().divide(KILOGRAM.multiply(KELVIN)), find(d, "x").unit());
    }

    @Test
    void absentNameEqualsSymbolAndSymbolsKeepUnderscores() {
        Definitions d = parse(BASE + "my_m m\n");
        assertEquals("my_m", find(d, "my_m").name());
    }

    @Test
    void unknownUnit() {
        fails(
                BASE.lines().limit(2).map(l -> l + "\n").reduce("", String::concat) + "x:foo bar\n",
                "test.units:3: unknown unit 'bar'");
    }

    @Test
    void duplicateSymbol() {
        fails(BASE + "m:metre !L\n", "test.units:5: duplicate symbol 'm'");
    }

    @Test
    void baseUnitErrors() {
        fails("m:metre !Q\n", "test.units:1: unknown base dimension 'Q'");
        fails("kg:kilogram !L\n", "test.units:1: base unit symbol must be 'm'");
        fails("m:metre ! L\n", "test.units:1: the dimension letter must follow '!' directly");
        assertEquals(KELVIN, find(parse("K !K\n"), "K").unit());
    }

    @Test
    void invalidAndReservedSymbols() {
        fails("1x m\n", "test.units:1: invalid symbol '1x'");
        fails(".x m\n", "test.units:1: invalid symbol '.x'");
        fails("a*b m\n", "test.units:1: invalid symbol 'a*b'");
        fails("a·b m\n", "test.units:1: invalid symbol 'a·b'");
        fails("a-b m\n", "test.units:1: invalid symbol 'a-b'");
        fails("a+ m\n", "test.units:1: invalid symbol 'a+'");
        fails("pi:pie 3\n", "test.units:1: symbol 'pi' is reserved");
        fails(BASE + "x: m\n", "test.units:5: empty name for 'x'");
        fails(BASE + "x:\n", "test.units:5: empty name for 'x'");
        fails(BASE + "x\n", "test.units:5: missing formula for 'x'");
        fails(BASE + "x # a comment\n", "test.units:5: missing formula for 'x'");
        fails(BASE + "x#y m\n", "test.units:5: missing formula for 'x' ('#' starts a comment)");
    }

    @Test
    void quantityTypes() {
        fails(BASE + "x:y m [lower]\n", "test.units:5: malformed quantity type 'lower'");
        fails(BASE + "x:y m [Fo#o]\n", "test.units:5: malformed quantity type 'Fo#o'");
        fails(BASE + "x[Foo] m\n", "test.units:5: the quantity type [Foo] must end the definition");
        fails(BASE + "x m [Length\n", "test.units:5: missing ']' after the quantity type");
        Definitions d = parse(BASE + "a m [Length] # comment\nb m [Length]# tight\nc m # [Nope]\n");
        assertEquals("Length", find(d, "a").explicitType());
        assertEquals("Length", find(d, "b").explicitType());
        assertNull(find(d, "c").explicitType());
    }

    @Test
    void affineDefinitions() {
        Definitions d = parse(
                BASE + "°C:degree_Celsius K = x + 273.15\n" + "°F:degree_fahrenheit °C = (x - 32) * 5|9\n"
                        + "°F2 °C = (x - 32) / 9 * 5\n" + "neg K = -x + 1\n");
        UomUnit<?> degC = find(d, "°C").unit();
        assertEquals(degC.transform(Converters.affine(Factor.of(5, 9), Factor.of(-160, 9))), find(d, "°F").unit());
        assertEquals(find(d, "°F").unit(), find(d, "°F2").unit());
        assertEquals(KELVIN.transform(Converters.affine(Factor.ofLong(-1), Factor.ONE)), find(d, "neg").unit());
        fails(BASE + "x:y m = x * x\n", "test.units:5: function is not affine");
        fails(BASE + "x:y m = x / x\n", "test.units:5: function is not affine");
    }

    @Test
    void alternateUnits() {
        fails(BASE + "x:y ~ 1000 m\n", "test.units:5: alternate unit must be an unscaled unit");
        fails(BASE + "t10a ~ m\nt10a ~ s\n", "test.units:6: duplicate symbol 't10a'");
        parse(BASE + "t10b ~ s\n");
        fails(BASE + "t10b ~ m\n", "test.units:5: symbol t10b is already associated to a different unit");
    }

    @Test
    void formulaErrorsCarryTheLine() {
        fails(BASE + "x m^\n", "test.units:5: exponent must be an integer");
        fails(BASE + "x m = x + \n", "test.units:5: unexpected end of function");
    }

    @Test
    void directives() {
        fails(BASE + "@preferred Length cm\ncm 1|100 m\n", "test.units:5: unknown unit 'cm'");
        fails(BASE + "@preferred length m\n", "test.units:5: malformed quantity type 'length'");
        fails("@unknown x\n", "test.units:1: unknown directive '@unknown'");
    }

    @Test
    void includeErrors() {
        fails("@include missing.units\n", "test.units:1: cannot include 'missing.units'");
        fails(
                "\n@include a.units\n",
                Map.of("a.units", "@include b.units\n", "b.units", "\n\n@include a.units\n"),
                "b.units:3: include cycle: a.units -> b.units -> a.units");
        fails("@include a.units\n", Map.of("a.units", BASE + "x:foo bar\n"), "a.units:5: unknown unit 'bar'");
        DefinitionException e = assertThrows(
                DefinitionException.class,
                () -> DefinitionParser.parse("t.units", new StringReader("@include a\n"), null));
        assertEquals("t.units:1: cannot include 'a'", e.getMessage());
        assertEquals("t.units", e.source());
        assertEquals(1, e.line());
        e = assertThrows(
                DefinitionException.class,
                () -> DefinitionParser.parse("t.units", new StringReader("@include a\n"), name -> {
                    throw new IOException("boom");
                }));
        assertEquals("t.units:1: cannot include 'a'", e.getMessage());
    }

    @Test
    void diamondIncludeOnceInOrder() {
        Map<String, String> files = Map.of(
                "a",
                "@include b\n@include c\na1 m\n",
                "b",
                "@include d\nb1 m\n",
                "c",
                "@include d\nc1 m\n",
                "d",
                BASE + "d1 m\n");
        Definitions d = parse("@include a\ntop m\n", files);
        assertEquals(List.of("m", "kg", "s", "K", "d1", "b1", "c1", "a1", "top"), symbols(d));
        assertEquals("d", find(d, "d1").source());
        assertEquals(5, find(d, "d1").line());
    }

    @Test
    void includedReadersAreClosedButNotTheCallers() {
        List<String> closed = new ArrayList<>();
        class Tracking extends StringReader {
            private final String name;

            Tracking(String name, String s) {
                super(s);
                this.name = name;
            }

            @Override
            public void close() {
                closed.add(name);
                super.close();
            }
        }
        DefinitionParser.parse("top", new Tracking("top", "@include a\n"), n -> new Tracking(n, BASE));
        assertEquals(List.of("a"), closed);
    }

    @Test
    void robustness() {
        Definitions plain = parse(HAPPY);
        assertEquals(plain, parse("\uFEFF" + HAPPY));
        assertEquals(plain, parse(HAPPY.replace("\n", "\r\n")));
        assertEquals(plain, parse(HAPPY.replace("\n", "\r")));
        assertEquals(plain, parse(HAPPY.replace(" ", "\t")));
        assertEquals(
                new Definitions(
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        Map.of("test.units", new FileDirectives(null, List.of(), List.of()))),
                parse(""));
        assertEquals(List.of("m"), symbols(parse("  \n\t\nm !L\n   \n")));
        Definitions u = parse(BASE + "Ω:ohm m\nµ:micro 1|1000000\nx Ω µ\n");
        assertEquals(METRE.divide(1000000), find(u, "x").unit());
        assertEquals("ohm", find(u, "Ω").name());
    }

    @Test
    void definitionsAreImmutable() {
        Definitions d = parse(HAPPY);
        assertThrows(UnsupportedOperationException.class, () -> d.definitions().clear());
        assertThrows(UnsupportedOperationException.class, () -> d.preferred().clear());
        Definitions p = parse(BASE + "@prefixed m k\n");
        assertThrows(UnsupportedOperationException.class, () -> p.prefixed().clear());
        assertThrows(UnsupportedOperationException.class, () -> p.prefixed().get(0).prefixSymbols().clear());
        Definitions s = parse("@extends a\n@member m\n", Map.of("a", BASE));
        assertThrows(UnsupportedOperationException.class, () -> s.extended().clear());
        assertThrows(UnsupportedOperationException.class, () -> s.members().clear());
    }

    @Test
    void affineFunctionParser() throws FormulaException {
        AffineFunction f = AffineFunction.parse("(x - 32) * 5|9");
        assertEquals(Factor.of(5, 9), f.a());
        assertEquals(Factor.of(-160, 9), f.b());
        f = AffineFunction.parse("2 * (3 * x + 1.5) / 4 - -1|2");
        assertEquals(Factor.of(3, 2), f.a());
        assertEquals(Factor.of(5, 4), f.b());
        assertEquals(
                "function is not affine",
                assertThrows(FormulaException.class, () -> AffineFunction.parse("x * (x + 1)")).getMessage());
        assertEquals(
                "division by zero",
                assertThrows(FormulaException.class, () -> AffineFunction.parse("x / (1 - 1)")).getMessage());
        assertEquals(
                "division by zero",
                assertThrows(FormulaException.class, () -> AffineFunction.parse("x * 1|0")).getMessage());
        FormulaException e = assertThrows(FormulaException.class, () -> AffineFunction.parse("x + y"));
        assertEquals("unexpected 'y'", e.getMessage());
        assertEquals(5, e.column());
        assertEquals(
                "missing ')'",
                assertThrows(FormulaException.class, () -> AffineFunction.parse("(x + 1")).getMessage());
        assertEquals(
                "function nested too deeply",
                assertThrows(
                        FormulaException.class,
                        () -> AffineFunction.parse("(".repeat(101) + "x" + ")".repeat(101))).getMessage());
    }

    private static final String PREFIX_BASE = BASE + "g:gram 1|1000 kg\nHz:hertz ~ 1/s [Frequency]\n"
            + "Bq:becquerel ~ 1/s [Radioactivity]\n°C:degree_Celsius K = x + 273.15\n";

    @Test
    void prefixedDirective() {
        Definitions d = parse(PREFIX_BASE + "@prefixed m k c µ # metric\nx km/s\n");
        assertEquals(List.of("m", "kg", "s", "K", "g", "Hz", "Bq", "°C", "km", "cm", "µm", "x"), symbols(d));
        assertEquals(List.of(new Prefixed("m", List.of("k", "c", "µ"), "test.units", 9)), d.prefixed());
        assertTrue(d.prefixed().get(0).metric());
        assertFalse(d.prefixed().get(0).binary());
        MetricPrefix[] prefixes = { MetricPrefix.KILO, MetricPrefix.CENTI, MetricPrefix.MICRO };
        String[] names = { "kilometre", "centimetre", "micrometre" };
        String[] syms = { "km", "cm", "µm" };
        for (int i = 0; i < 3; i++) {
            Definition def = find(d, syms[i]);
            assertEquals(names[i], def.name());
            assertEquals(METRE.prefix(prefixes[i]), def.unit());
            assertEquals(syms[i], def.unit().getSymbol());
            assertEquals(names[i], def.unit().getName());
            assertNull(def.alternateOf());
            assertNull(def.explicitType());
            assertEquals("test.units", def.source());
            assertEquals(9, def.line());
        }
        assertEquals(METRE.prefix(MetricPrefix.KILO).divide(SECOND), find(d, "x").unit());
        assertEquals("megagram", find(parse(PREFIX_BASE + "@prefixed g M\n"), "Mg").name());
    }

    @Test
    void prefixedAlternateUnitsKeepTheirAtom() {
        Definitions d = parse(PREFIX_BASE + "@prefixed Hz k M\n@prefixed Bq k\n");
        Definition khz = find(d, "kHz");
        assertNotEquals(khz.unit(), find(d, "kBq").unit());
        assertEquals("Frequency", khz.explicitType());
        assertEquals("Radioactivity", find(d, "kBq").explicitType());
        assertEquals(find(d, "Hz").unit().prefix(MetricPrefix.KILO), khz.unit());
        assertEquals(find(d, "Hz").unit().prefix(MetricPrefix.MEGA), find(d, "MHz").unit());
        assertEquals("kilohertz", khz.name());
        assertNull(khz.alternateOf());
        assertEquals(
                List.of(
                        new Prefixed("Hz", List.of("k", "M"), "test.units", 9),
                        new Prefixed("Bq", List.of("k"), "test.units", 10)),
                d.prefixed());
    }

    @Test
    void binaryPrefixes() {
        Definitions d = parse(BASE + "bit:bit ~\nB:byte 8 bit\n@prefixed B k Ki Mi\n@prefixed bit Ki\n");
        assertEquals(List.of("m", "kg", "s", "K", "bit", "B", "kB", "KiB", "MiB", "Kibit"), symbols(d));
        UomUnit<?> byteUnit = find(d, "B").unit();
        assertEquals(byteUnit.prefix(BinaryPrefix.KIBI), find(d, "KiB").unit());
        assertEquals(byteUnit.multiply(1048576), find(d, "MiB").unit());
        assertEquals(byteUnit.multiply(1000), find(d, "kB").unit());
        assertEquals("kibibyte", find(d, "KiB").name());
        assertEquals("mebibyte", find(d, "MiB").name());
        assertEquals("kibibit", find(d, "Kibit").name());
        Prefixed mixed = d.prefixed().get(0);
        assertTrue(mixed.metric());
        assertTrue(mixed.binary());
        assertFalse(d.prefixed().get(1).metric());
        assertTrue(d.prefixed().get(1).binary());
        fails(
                BASE + "bit:bit ~\n@prefixed bit Ki\n@prefixed Kibit Mi\n",
                "test.units:7: unit 'Kibit' is already prefixed");
    }

    @Test
    void prefixedErrors() {
        fails(PREFIX_BASE + "@prefixed zz k\n", "test.units:9: unknown unit 'zz'");
        fails(PREFIX_BASE + "@prefixed m x\n", "test.units:9: unknown prefix 'x'");
        fails(PREFIX_BASE + "@prefixed m ki\n", "test.units:9: unknown prefix 'ki'");
        fails(PREFIX_BASE + "@prefixed m\n", "test.units:9: expected '@prefixed unit prefix...'");
        fails(PREFIX_BASE + "@prefixed # nothing\n", "test.units:9: expected '@prefixed unit prefix...'");
        fails(PREFIX_BASE + "@prefixed °C k\n", "test.units:9: unit '°C' can not be prefixed");
        fails(PREFIX_BASE + "@prefixed m k k\n", "test.units:9: duplicate symbol 'km'");
        // A prefixed unit is not prefixed again (km would give kkm), also if it came from an included file.
        fails(PREFIX_BASE + "@prefixed m k\n@prefixed km M\n", "test.units:10: unit 'km' is already prefixed");
        fails(
                "@include a\n@prefixed km M\n",
                Map.of("a", PREFIX_BASE + "@prefixed m k\n"),
                "test.units:2: unit 'km' is already prefixed");
        fails(PREFIX_BASE + "km 1000 m\n@prefixed m k\n", "test.units:10: duplicate symbol 'km'");
        fails(PREFIX_BASE + "@prefixed m k\nkm 1000 m\n", "test.units:10: duplicate symbol 'km'");
        fails("@prefixed m k\nm:metre !L\n", "test.units:1: unknown unit 'm'");
    }

    @Test
    void prefixedInIncludedFile() {
        Definitions d = parse("@include a\n@prefixed s m\ntop km\n", Map.of("a", BASE + "@prefixed m k\n"));
        assertEquals(List.of("m", "kg", "s", "K", "km", "ms", "top"), symbols(d));
        assertEquals(
                List.of(new Prefixed("m", List.of("k"), "a", 5), new Prefixed("s", List.of("m"), "test.units", 2)),
                d.prefixed());
        assertEquals("a", find(d, "km").source());
        assertEquals(5, find(d, "km").line());
        fails("@include a\n", Map.of("a", BASE + "@prefixed m q x\n"), "a:5: unknown prefix 'x'");
    }

    @Test
    void prefixedRobustness() {
        String text = PREFIX_BASE + "@prefixed m k µ\n";
        Definitions plain = parse(text);
        assertEquals(plain, parse("\uFEFF" + text));
        assertEquals(plain, parse(text.replace("\n", "\r\n")));
        assertEquals(plain, parse(text.replace(" ", "\t")));
        assertEquals(plain, parse(text.replace("@prefixed m k µ", "  @prefixed   m  k µ  # comment")));
        assertEquals(plain, parse(text.replace("@prefixed m k µ", "@prefixed m k µ# tight")));
        assertEquals(List.of("m", "km"), symbols(parse("\uFEFFm:metre !L\r\n@prefixed\tm\tk\r\n")));
    }

    @Test
    void microSignIsTheMetricPrefixSymbol() {
        assertEquals(0xB5, MetricPrefix.MICRO.getSymbol().codePointAt(0));
        Definitions d = parse(BASE + "@prefixed m µ\n");
        assertEquals(0xB5, d.prefixed().get(0).prefixSymbols().get(0).codePointAt(0));
        assertEquals("µm", find(d, "µm").symbol());
        fails(BASE + "@prefixed m μ\n", "test.units:5: unknown prefix 'μ'");
    }

    @Test
    void systemDirective() {
        assertEquals("Foo", parse(BASE + "@system Foo # the name\n").system());
        assertNull(parse(BASE).system());
        fails("@system Foo\n\n@system Bar\n", "test.units:3: duplicate '@system'");
        fails("@system Foo\n@system Foo\n", "test.units:2: duplicate '@system'");
        fails("@system 9x\n", "test.units:1: invalid system name '9x'");
        fails("@system class\n", "test.units:1: invalid system name 'class'");
        fails("@system true\n", "test.units:1: invalid system name 'true'");
        fails("@system _\n", "test.units:1: invalid system name '_'");
        fails("@system a-b\n", "test.units:1: invalid system name 'a-b'");
        assertEquals("_Gauß$2", parse("@system _Gauß$2\n").system());
        fails("@system\n", "test.units:1: expected '@system Name'");
        fails("@system A B\n", "test.units:1: expected '@system Name'");
    }

    @Test
    void systemInIncludedFileIsNotTheRoots() {
        assertNull(parse("@include a\n", Map.of("a", "@system A\n")).system());
        assertEquals("Top", parse("@include a\n@system Top\n", Map.of("a", "@system A\n")).system());
        assertEquals("Top", parse("@system Top\n@extends a\n", Map.of("a", "@system A\n")).system());
    }

    @Test
    void extendsDirective() {
        Definitions d = parse("@extends b.units\nx 2 m\n", Map.of("b.units", BASE));
        assertEquals(List.of("m", "kg", "s", "K", "x"), symbols(d));
        assertEquals("b.units", find(d, "m").source());
        assertEquals(List.of(new Extends("b.units", "test.units", 1)), d.extended());
        assertEquals(List.of(), parse(BASE).extended());
    }

    @Test
    void extendsAfterIncludeOfTheSameFileIsRecorded() {
        Definitions d = parse("@include b\n@extends b\n", Map.of("b", BASE));
        assertEquals(List.of("m", "kg", "s", "K"), symbols(d));
        assertEquals(List.of(new Extends("b", "test.units", 2)), d.extended());
    }

    @Test
    void extendsInIncludedFileIsNotReported() {
        Definitions d = parse(
                "@include a\n@extends c\n",
                Map.of("a", "@extends b\n", "b", BASE, "c", "@include b\nc1 m\n"));
        assertEquals(List.of("m", "kg", "s", "K", "c1"), symbols(d));
        assertEquals(List.of(new Extends("c", "test.units", 2)), d.extended());
        // but kept as a directive of its own file
        assertEquals(List.of(new Extends("b", "a", 1)), d.files().get("a").extended());
    }

    @Test
    void everyFileKeepsItsOwnSystemDirectives() {
        Definitions d = parse(
                "@system Top\n@extends a\n",
                Map.of("a", "@system A\n@extends b\n@include c\n@member c1\n", "b", BASE, "c", "@include b\nc1 2 m\n"));
        assertEquals(
                Map.of(
                        "test.units",
                        new FileDirectives("Top", List.of(new Extends("a", "test.units", 2)), List.of()),
                        "a",
                        new FileDirectives("A", List.of(new Extends("b", "a", 2)), List.of(new Member("c1", "a", 4))),
                        "b",
                        new FileDirectives(null, List.of(), List.of()),
                        "c",
                        new FileDirectives(null, List.of(), List.of())),
                d.files());
        assertEquals("Top", d.system());
        assertEquals(List.of(new Extends("a", "test.units", 2)), d.extended());
        assertEquals(List.of(), d.members());
    }

    @Test
    void anIncludedFileIsCheckedLikeTheRoot() {
        fails("@include a\n", Map.of("a", "@system A\n@system B\n"), "a:2: duplicate '@system'");
        fails("@include a\n", Map.of("a", "@include b\n@member m kg m\n", "b", BASE), "a:2: duplicate member 'm'");
        fails("@include a\n", Map.of("a", "@extends b\n@extends b\n", "b", BASE), "a:2: duplicate '@extends' of 'b'");
    }

    @Test
    void aFileSeesOnlyItsOwnSymbolsAndThoseItIncludes() {
        Map<String, String> files = Map
                .of("b", BASE, "c", "c1 2 r\n", "p", "@member r\n", "q", "@preferred Length r\n");
        // r is defined by the root before c is included, but c does not include the root
        fails("@include b\nr 2 m\n@include c\n", files, "c:1: unknown unit 'r'");
        fails("@include b\nr 2 m\n@include p\n", files, "p:1: unknown unit 'r'");
        fails("@include b\nr 2 m\n@include q\n", files, "q:1: unknown unit 'r'");
        // what an included file includes is visible to the includer too
        Definitions d = parse("@include c2\nr 2 c1\n", Map.of("b", BASE, "c2", "@include b\nc1 2 m\n"));
        assertEquals(List.of("m", "kg", "s", "K", "c1", "r"), symbols(d));
    }

    @Test
    void manyMembersParseInLinearTime() {
        // each member was checked against every definition and every earlier member: 20,000 took about 5 s
        int n = 50_000;
        StringBuilder lib = new StringBuilder(BASE);
        StringBuilder root = new StringBuilder("@include lib\n");
        for (int i = 0; i < n; i++) {
            lib.append('u').append(i).append(" 2 m\n");
            root.append("@member u").append(i).append('\n');
        }
        Definitions d = assertTimeoutPreemptively(
                Duration.ofSeconds(10),
                () -> parse(root.toString(), Map.of("lib", lib.toString())));
        assertEquals(n, d.members().size());
        assertEquals(new Member("u49999", "test.units", n + 1), d.members().get(n - 1));
    }

    @Test
    void duplicateExtends() {
        fails("@extends b\n\n@extends b\n", Map.of("b", BASE), "test.units:3: duplicate '@extends' of 'b'");
        Definitions d = parse("@extends a\n", Map.of("a", "@extends b\n", "b", BASE));
        assertEquals(List.of(new Extends("a", "test.units", 1)), d.extended());
        d = parse("@extends b\n@extends a\n", Map.of("a", "@extends b\n", "b", BASE));
        assertEquals(List.of(new Extends("b", "test.units", 1), new Extends("a", "test.units", 2)), d.extended());
    }

    @Test
    void extendsErrors() {
        fails("@extends missing.units\n", "test.units:1: cannot include 'missing.units'");
        fails("\n@extends a\n", Map.of("a", "@extends b\n", "b", "@include a\n"), "b:1: include cycle: a -> b -> a");
        fails("@extends test.units\n", "test.units:1: include cycle: test.units -> test.units");
        fails("@extends\n", "test.units:1: expected '@extends name'");
        fails("@extends a b\n", "test.units:1: expected '@extends name'");
    }

    @Test
    void memberDirective() {
        Definitions d = parse(
                "@include si\n@member cm g s\n@member K\n",
                Map.of("si", PREFIX_BASE + "@prefixed m c\n"));
        assertEquals(
                List.of(
                        new Member("cm", "test.units", 2),
                        new Member("g", "test.units", 2),
                        new Member("s", "test.units", 2),
                        new Member("K", "test.units", 3)),
                d.members());
        assertEquals(List.of(), parse(BASE).members());
    }

    @Test
    void memberErrors() {
        Map<String, String> base = Map.of("b", BASE);
        fails("@include b\n@member m x s\n", base, "test.units:2: unknown unit 'x'");
        fails("@include b\n@member cm\ncm 1|100 m\n", base, "test.units:2: unknown unit 'cm'");
        fails("@include b\n@member m s\n@member kg m\n", base, "test.units:3: duplicate member 'm'");
        fails("@include b\n@member s s\n", base, "test.units:2: duplicate member 's'");
        fails("@include b\n@member\n", base, "test.units:2: expected '@member symbol...'");
        fails("@include a\n", Map.of("a", BASE + "@member q\n"), "a:5: unknown unit 'q'");
    }

    @Test
    void memberDefinedInTheSameFile() {
        fails(BASE + "@member m\n", "test.units:5: unit 'm' is defined in this file");
        fails("@include b\nx 2 m\n@member s x\n", Map.of("b", BASE), "test.units:3: unit 'x' is defined in this file");
        fails("@include a\n", Map.of("a", BASE + "@member K\n"), "a:5: unit 'K' is defined in this file");
    }

    @Test
    void memberFromExtendedFileAndNonAsciiSymbols() {
        Definitions d = parse(
                "@extends e\n@member Ω °F m\n",
                Map.of("e", BASE + "Ω:ohm m\n°F:degree_Fahrenheit K = (x + 459.67) * 5|9\n"));
        assertEquals(
                List.of(
                        new Member("Ω", "test.units", 2),
                        new Member("°F", "test.units", 2),
                        new Member("m", "test.units", 2)),
                d.members());
    }

    @Test
    void memberInIncludedFileIsNotReported() {
        Definitions d = parse("@include a\n@member m\n", Map.of("a", "@include b\n@member m s\n", "b", BASE));
        assertEquals(List.of(new Member("m", "test.units", 2)), d.members());
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "abstract",
            "assert",
            "boolean",
            "break",
            "byte",
            "case",
            "catch",
            "char",
            "class",
            "const",
            "continue",
            "default",
            "do",
            "double",
            "else",
            "enum",
            "extends",
            "final",
            "finally",
            "float",
            "for",
            "goto",
            "if",
            "implements",
            "import",
            "instanceof",
            "int",
            "interface",
            "long",
            "native",
            "new",
            "package",
            "private",
            "protected",
            "public",
            "return",
            "short",
            "static",
            "strictfp",
            "super",
            "switch",
            "synchronized",
            "this",
            "throw",
            "throws",
            "transient",
            "try",
            "void",
            "volatile",
            "while",
            "_",
            "true",
            "false",
            "null",
            "var",
            "yield",
            "record",
            "sealed",
            "permits" }
    )
    void reservedSystemNames(String name) {
        fails("@system " + name + "\n", "test.units:1: invalid system name '" + name + "'");
        assertEquals(name + "1", parse("@system " + name + "1\n").system());
    }

    @Test
    void hugeFactorsAreRejected() {
        assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> fails("x ((10^1000)^1000)^100\n", "test.units:1: factor too large"));
    }

    private static final Map<String, UomUnit<?>> KNOWN = Map
            .of("m", METRE, "km", METRE.prefix(MetricPrefix.KILO), "kg", KILOGRAM, "s", SECOND);

    private static Definitions known(String text) {
        return DefinitionParser.parse("test.units", new StringReader(text), null, KNOWN::get);
    }

    private static void knownFails(String text, String message) {
        DefinitionException e = assertThrows(DefinitionException.class, () -> known(text));
        assertEquals(message, e.getMessage());
    }

    @Test
    void knownSymbolsResolveInFormulas() {
        Definitions d = known("a 1.5 m\nb km\nN:newton kg m / s^2\nc 2 a\n");
        assertEquals(METRE.multiply(1.5), find(d, "a").unit());
        assertEquals(METRE.multiply(1000), find(d, "b").unit());
        assertEquals(KILOGRAM.multiply(METRE).divide(SECOND.pow(2)), find(d, "N").unit());
        assertEquals(METRE.multiply(3), find(d, "c").unit());
        assertEquals(List.of("a", "b", "N", "c"), symbols(d));
        knownFails("a 1.5 m\nb 2 nothere\n", "test.units:2: unknown unit 'nothere'");
    }

    @Test
    void definingAKnownSymbolIsAnError() {
        knownFails("x 2 m\nm:metre 1\n", "test.units:2: symbol 'm' is already defined");
        knownFails("km 1000 m\n", "test.units:1: symbol 'km' is already defined");
        knownFails("s:second !T\n", "test.units:1: symbol 's' is already defined");
        knownFails("kg ~ m\n", "test.units:1: symbol 'kg' is already defined");
        DefinitionException e = assertThrows(
                DefinitionException.class,
                () -> DefinitionParser.parse(
                        "t.units",
                        new StringReader("@include a\n"),
                        n -> new StringReader("\nx m\nkm m\n"),
                        KNOWN::get));
        assertEquals("a:3: symbol 'km' is already defined", e.getMessage());
        assertEquals("a", e.source());
        assertEquals(3, e.line());
    }

    @Test
    void prefixedSymbolClashingWithAKnownOneIsAnError() {
        Map<String, UomUnit<?>> k = Map.of("m", METRE, "kleague", METRE);
        DefinitionException e = assertThrows(
                DefinitionException.class,
                () -> DefinitionParser
                        .parse("t.units", new StringReader("league 3 m\n@prefixed league M k\n"), null, k::get));
        assertEquals("t.units:2: symbol 'kleague' is already defined", e.getMessage());
    }

    @Test
    void knownIsNotConsultedForSymbolsTheFileDefines() {
        List<String> calls = new ArrayList<>();
        Definitions d = DefinitionParser.parse("t.units", new StringReader("a 2 m\nb 3 a\nc a b\n"), null, s -> {
            calls.add(s);
            return KNOWN.get(s);
        });
        // once each for the symbol check of a, b and c, and once for m
        assertEquals(List.of("a", "m", "b", "c"), calls);
        assertEquals(METRE.pow(2).multiply(12), find(d, "c").unit());
    }

    @Test
    void preferredOfAKnownSymbolIsAnError() {
        knownFails("a 2 m\n@preferred Length m\n", "test.units:2: unknown unit 'm'");
        assertEquals(
                List.of(new Preferred("Length", "a", "test.units", 2)),
                known("a 2 m\n@preferred Length a\n").preferred());
    }

    @Test
    void knownNullSafety() {
        assertThrows(
                NullPointerException.class,
                () -> DefinitionParser.parse("t.units", new StringReader("a m\n"), null, null));
        List<String> calls = new ArrayList<>();
        known("a 2 m\n");
        DefinitionParser.parse("t.units", new StringReader("a 2 m\nb (a)·m\n"), null, s -> {
            calls.add(s);
            return KNOWN.get(s);
        });
        assertTrue(calls.stream().noneMatch(s -> s == null || s.isEmpty()), calls.toString());
        // the three-argument form knows nothing
        assertThrows(DefinitionException.class, () -> parse("a 2 m\n"));
    }
}
