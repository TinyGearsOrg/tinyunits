// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        return FormulaEvaluator.evaluate(formula, s -> DEFS.definitions().stream()
                .filter(d -> d.symbol().equals(s)).findFirst().map(Definition::unit).orElse(null));
    }

    private UomUnit<?> run(String expression) throws Exception {
        String source = "public class E { public static Object value() { return " + expression + "; } }";
        try (URLClassLoader loader = InMemoryCompiler.compile(Map.of("E", source), tmp)) {
            return (UomUnit<?>) loader.loadClass("E").getMethod("value").invoke(null);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"m", "m / s", "m^2", "Hz", "Bq", "1"})
    void roundTrips(String formula) throws Exception {
        UomUnit<?> unit = evaluate(formula);
        UomUnit<?> result = run(UnitExpressionEmitter.emit(unit, DEFS));
        assertEquals(unit, result);
        if (unit.getSymbol() != null) {
            assertEquals(unit.getSymbol(), result.getSymbol());
        }
    }

    @Test
    void namedJoule() throws Exception {
        UomUnit<?> unit = evaluate("J");
        UomUnit<?> result = run(UnitExpressionEmitter.emit(unit, DEFS) + ".named(\"J\", \"joule\")");
        assertEquals(unit, result);
        assertEquals("J", result.getSymbol());
        assertEquals("joule", result.getName());
    }

    @Test
    void exactText() throws Exception {
        assertEquals("org.tinygears.uom.core.BaseUnits.METRE.divide(org.tinygears.uom.core.BaseUnits.SECOND)",
                UnitExpressionEmitter.emit(evaluate("m / s"), DEFS));
        assertEquals("org.tinygears.uom.core.BaseUnits.ONE.divide(org.tinygears.uom.core.BaseUnits.SECOND)"
                + ".alternate(\"Hz\")", UnitExpressionEmitter.emit(evaluate("Hz"), DEFS));
        assertEquals("org.tinygears.uom.core.BaseUnits.KILOGRAM.multiply(org.tinygears.uom.core.BaseUnits.METRE"
                + ".pow(2)).divide(org.tinygears.uom.core.BaseUnits.SECOND.pow(2))",
                UnitExpressionEmitter.emit(evaluate("J"), DEFS));
        assertEquals("org.tinygears.uom.core.BaseUnits.ONE", UnitExpressionEmitter.emit(BaseUnits.ONE, DEFS));
    }

    @Test
    void undefinedAtom() {
        UomUnit<?> unit = UomUnit.of(Map.of(new UnitAtom("x\"y", UomDimension.NONE), 1), Converters.IDENTITY,
                null, null);
        GenerationException e = assertThrows(GenerationException.class, () -> UnitExpressionEmitter.emit(unit, DEFS));
        assertEquals("no definition for alternate unit 'x\"y'", e.getMessage());
    }

    @Test
    void scaledUnit() {
        GenerationException e = assertThrows(GenerationException.class,
                () -> UnitExpressionEmitter.emit(BaseUnits.METRE.multiply(1000), DEFS));
        assertEquals("unit must be an unscaled system unit", e.getMessage());
    }

    @Test
    void escapes() {
        assertEquals("\"a\\\\b\\\"c\"", UnitExpressionEmitter.literal("a\\b\"c"));
    }

    @Test
    void cyclicDefinition() {
        UomUnit<?> x = UomUnit.of(Map.of(new UnitAtom("x", UomDimension.NONE), 1), Converters.IDENTITY, null, null);
        Definitions cyclic = new Definitions(List.of(new Definition("x", "ex", x, x, null, "f", 1)), List.of(),
                List.of());
        GenerationException e = assertThrows(GenerationException.class, () -> UnitExpressionEmitter.emit(x, cyclic));
        assertEquals("alternate unit definitions nest too deeply at 'x'", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"min", "L", "deg", "au", "eV", "Da", "°C", "kHz", "µm", "N", "1"})
    void emitsScaledUnits(String symbol) throws Exception {
        Definitions si = siDefinitions();
        UomUnit<?> unit = switch (symbol) {
            case "kHz" -> si(si, "Hz").prefix(MetricPrefix.KILO);
            case "µm" -> si(si, "m").prefix(MetricPrefix.MICRO);
            case "1" -> BaseUnits.ONE;
            default -> si(si, symbol);
        };
        String text = UnitExpressionEmitter.emitUnit(unit, si);
        assertEquals(unit, run(text));
        assertEquals(!unit.toSystemConverter().equals(Converters.IDENTITY), text.contains(".transform("), text);
        String unquoted = text.replaceAll("\"[^\"]*\"", "\"\"");
        assertFalse(DOUBLE_LITERAL.matcher(unquoted).find(), text);
    }

    @Test
    void emitUnitNamed() throws Exception {
        Definitions si = siDefinitions();
        UomUnit<?> unit = si(si, "min");
        String text = UnitExpressionEmitter.emitUnit(unit, si);
        assertTrue(text.endsWith(".transform(org.tinygears.uom.core.Converters.affine("
                + "org.tinygears.uom.core.Factor.of(60L, 1L), org.tinygears.uom.core.Factor.ZERO))"), text);
        UomUnit<?> result = run(text + ".named(\"min\", \"minute\")");
        assertEquals(unit, result);
        assertEquals("min", result.getSymbol());
        assertEquals("minute", result.getName());
    }
}
