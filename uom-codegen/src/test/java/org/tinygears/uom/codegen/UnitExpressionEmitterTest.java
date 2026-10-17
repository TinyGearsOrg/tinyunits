/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.codegen;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.measure.MetricPrefix;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.Converters;
import org.tinygears.uom.core.UnitAtom;
import org.tinygears.uom.core.UomDimension;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.core.parse.Definition;
import org.tinygears.uom.core.parse.DefinitionParser;
import org.tinygears.uom.core.parse.Definitions;
import org.tinygears.uom.core.parse.FormulaEvaluator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitExpressionEmitterTest {
    static final String BASE = """
            m:metre !L
            kg:kilogram !M
            s:second !T
            A:ampere !I
            K:kelvin !Θ
            mol:mole !N
            cd:candela !J
            Hz:hertz ~ 1/s [Frequency]
            Bq:becquerel ~ 1/s [Radioactivity]
            J:joule kg m^2 / s^2 [Energy]
            """;
    static final Definitions DEFS = DefinitionParser.parse("test.units", new StringReader(BASE), null);

    private static final Pattern DOUBLE_LITERAL = Pattern.compile("\\d+\\.\\d+|\\b\\d+[dDfF]\\b");

    @TempDir
    Path tmp;

    static Definitions siDefinitions() throws IOException {
        try (Reader in = Files.newBufferedReader(Path.of("../data/si.units"), StandardCharsets.UTF_8)) {
            return DefinitionParser.parse("si.units", in, null);
        }
    }

    static UomUnit<?> si(Definitions defs, String symbol) {
        return defs.definitions().stream().filter(d -> d.symbol().equals(symbol)).findFirst().orElseThrow().unit();
    }

    static UomUnit<?> evaluate(String formula) throws Exception {
        return FormulaEvaluator.evaluate(
                formula,
                s -> DEFS.definitions().stream().filter(d -> d.symbol().equals(s)).findFirst().map(Definition::unit)
                        .orElse(null));
    }

    /** Imports for a class {@code E} in the unnamed package. */
    static Imports imports() {
        return new Imports("", List.of("E"));
    }

    private UomUnit<?> run(String expression, Imports imports) throws Exception {
        String source = imports.block() + "public class E { public static Object value() { return " + expression
                + "; } }";
        try (URLClassLoader loader = InMemoryCompiler.compile(Map.of("E", source), tmp)) {
            return (UomUnit<?>) loader.loadClass("E").getMethod("value").invoke(null);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "m", "m / s", "m^2", "Hz", "Bq", "1" })
    void roundTrips(String formula) throws Exception {
        UomUnit<?> unit = evaluate(formula);
        Imports imports = imports();
        UomUnit<?> result = run(UnitExpressionEmitter.emit(unit, DEFS, imports), imports);
        assertEquals(unit, result);
        if (unit.getSymbol() != null) {
            assertEquals(unit.getSymbol(), result.getSymbol());
        }
    }

    @Test
    void namedJoule() throws Exception {
        UomUnit<?> unit = evaluate("J");
        Imports imports = imports();
        UomUnit<?> result = run(UnitExpressionEmitter.emit(unit, DEFS, imports) + ".named(\"J\", \"joule\")", imports);
        assertEquals(unit, result);
        assertEquals("J", result.getSymbol());
        assertEquals("joule", result.getName());
    }

    @Test
    void exactText() throws Exception {
        Imports imports = imports();
        assertEquals("", imports.block());
        assertEquals(
                "BaseUnits.METRE.divide(BaseUnits.SECOND)",
                UnitExpressionEmitter.emit(evaluate("m / s"), DEFS, imports));
        assertEquals(
                "BaseUnits.ONE.divide(BaseUnits.SECOND).alternate(\"Hz\")",
                UnitExpressionEmitter.emit(evaluate("Hz"), DEFS, imports));
        assertEquals(
                "BaseUnits.KILOGRAM.multiply(BaseUnits.METRE.pow(2)).divide(BaseUnits.SECOND.pow(2))",
                UnitExpressionEmitter.emit(evaluate("J"), DEFS, imports));
        assertEquals("BaseUnits.ONE", UnitExpressionEmitter.emit(BaseUnits.ONE, DEFS, imports));
        assertEquals("import org.tinygears.uom.core.BaseUnits;\n\n", imports.block());
        Imports clash = new Imports("", List.of("E", "BaseUnits"));
        assertEquals("org.tinygears.uom.core.BaseUnits.ONE", UnitExpressionEmitter.emit(BaseUnits.ONE, DEFS, clash));
        assertEquals("", clash.block());
    }

    @Test
    void undefinedAtom() {
        UomUnit<?> unit = UomUnit
                .of(Map.of(new UnitAtom("x\"y", UomDimension.NONE), 1), Converters.IDENTITY, null, null);
        GenerationException e = assertThrows(
                GenerationException.class,
                () -> UnitExpressionEmitter.emit(unit, DEFS, imports()));
        assertEquals("no definition for alternate unit 'x\"y'", e.getMessage());
    }

    @Test
    void scaledUnit() {
        GenerationException e = assertThrows(
                GenerationException.class,
                () -> UnitExpressionEmitter.emit(BaseUnits.METRE.multiply(1000), DEFS, imports()));
        assertEquals("unit must be an unscaled system unit", e.getMessage());
    }

    @Test
    void escapes() {
        assertEquals("\"a\\\\b\\\"c\"", UnitExpressionEmitter.literal("a\\b\"c"));
        // control characters as octal escapes: a unicode escape of a line break would end the literal
        assertEquals("\"a\\012b\\011c\\177\"", UnitExpressionEmitter.literal("a\nb\tc\u007f"));
        assertEquals("\"µm°\"", UnitExpressionEmitter.literal("µm°"));
    }

    @Test
    void cyclicDefinition() {
        UomUnit<?> x = UomUnit.of(Map.of(new UnitAtom("x", UomDimension.NONE), 1), Converters.IDENTITY, null, null);
        Definitions cyclic = new Definitions(
                List.of(new Definition("x", "ex", x, x, null, "f", 1)),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                Map.of());
        GenerationException e = assertThrows(
                GenerationException.class,
                () -> UnitExpressionEmitter.emit(x, cyclic, imports()));
        assertEquals("alternate unit definitions nest too deeply at 'x'", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = { "min", "L", "deg", "au", "eV", "Da", "°C", "kHz", "µm", "N", "1" })
    void emitsScaledUnits(String symbol) throws Exception {
        Definitions si = siDefinitions();
        UomUnit<?> unit = switch (symbol) {
            case "kHz" -> si(si, "Hz").prefix(MetricPrefix.KILO);
            case "µm" -> si(si, "m").prefix(MetricPrefix.MICRO);
            case "1" -> BaseUnits.ONE;
            default -> si(si, symbol);
        };
        Imports imports = imports();
        String text = UnitExpressionEmitter.emitUnit(unit, si, imports);
        assertEquals(unit, run(text, imports));
        assertEquals(!unit.toSystemConverter().equals(Converters.IDENTITY), text.contains(".transform("), text);
        String unquoted = text.replaceAll("\"[^\"]*\"", "\"\"");
        assertFalse(DOUBLE_LITERAL.matcher(unquoted).find(), text);
    }

    @Test
    void emitUnitNamed() throws Exception {
        Definitions si = siDefinitions();
        UomUnit<?> unit = si(si, "min");
        Imports imports = imports();
        String text = UnitExpressionEmitter.emitUnit(unit, si, imports);
        assertTrue(text.endsWith(".transform(Converters.affine(Factor.of(60L, 1L), Factor.ZERO))"), text);
        assertEquals(
                "import org.tinygears.uom.core.BaseUnits;\nimport org.tinygears.uom.core.Converters;\n"
                        + "import org.tinygears.uom.engine.Factor;\n\n",
                imports.block());
        UomUnit<?> result = run(text + ".named(\"min\", \"minute\")", imports);
        assertEquals(unit, result);
        assertEquals("min", result.getSymbol());
        assertEquals("minute", result.getName());
    }
}
