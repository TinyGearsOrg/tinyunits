package org.tinygears.uom.core.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.tinygears.uom.core.Converters;
import org.tinygears.uom.core.Factor;
import org.tinygears.uom.core.UomUnit;

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
            °C:degree_celsius  K = x + 273.15
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
        assertEquals("degree celsius", find(d, "°C").name());
        UomUnit degC = find(d, "°C").unit();
        assertEquals(373.15, degC.getConverterTo(KELVIN).convert(100.0), 1e-12);
        UomUnit<?> hz = find(d, "Hz").unit();
        assertNotEquals(ONE.divide(SECOND), hz);
        assertTrue(hz.isCompatible(ONE.divide(SECOND)));
        UomUnit in = find(d, "in").unit();
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
        fails(BASE.lines().limit(2).map(l -> l + "\n").reduce("", String::concat) + "x:foo bar\n",
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
        assertEquals(KELVIN, find(parse("K !K\n"), "K").unit());
    }

    @Test
    void invalidAndReservedSymbols() {
        fails("1x m\n", "test.units:1: invalid symbol '1x'");
        fails(".x m\n", "test.units:1: invalid symbol '.x'");
        fails("a*b m\n", "test.units:1: invalid symbol 'a*b'");
        fails("pi:pie 3\n", "test.units:1: symbol 'pi' is reserved");
    }

    @Test
    void quantityTypes() {
        fails(BASE + "x:y m [lower]\n", "test.units:5: malformed quantity type 'lower'");
        fails(BASE + "x:y m [Fo#o]\n", "test.units:5: malformed quantity type 'Fo#o'");
        Definitions d = parse(BASE + "a m [Length] # comment\nb m [Length]# tight\nc m # [Nope]\n");
        assertEquals("Length", find(d, "a").explicitType());
        assertEquals("Length", find(d, "b").explicitType());
        assertNull(find(d, "c").explicitType());
    }

    @Test
    void affineDefinitions() {
        Definitions d = parse(BASE + "°C:degree_celsius K = x + 273.15\n"
                + "°F:degree_fahrenheit °C = (x - 32) * 5|9\n"
                + "°F2 °C = (x - 32) / 9 * 5\n"
                + "neg K = -x + 1\n");
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
        fails("\n@include a.units\n", Map.of("a.units", "@include b.units\n", "b.units", "\n\n@include a.units\n"),
                "b.units:3: include cycle: a.units -> b.units -> a.units");
        fails("@include a.units\n", Map.of("a.units", BASE + "x:foo bar\n"), "a.units:5: unknown unit 'bar'");
        DefinitionException e = assertThrows(DefinitionException.class,
                () -> DefinitionParser.parse("t.units", new StringReader("@include a\n"), null));
        assertEquals("t.units:1: cannot include 'a'", e.getMessage());
        assertEquals("t.units", e.source());
        assertEquals(1, e.line());
        e = assertThrows(DefinitionException.class, () -> DefinitionParser.parse("t.units",
                new StringReader("@include a\n"), name -> {
                    throw new IOException("boom");
                }));
        assertEquals("t.units:1: cannot include 'a'", e.getMessage());
    }

    @Test
    void diamondIncludeOnceInOrder() {
        Map<String, String> files = Map.of(
                "a", "@include b\n@include c\na1 m\n",
                "b", "@include d\nb1 m\n",
                "c", "@include d\nc1 m\n",
                "d", BASE + "d1 m\n");
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
        assertEquals(new Definitions(List.of(), List.of()), parse(""));
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
    }

    @Test
    void affineFunctionParser() throws FormulaException {
        AffineFunction f = AffineFunction.parse("(x - 32) * 5|9");
        assertEquals(Factor.of(5, 9), f.a());
        assertEquals(Factor.of(-160, 9), f.b());
        f = AffineFunction.parse("2 * (3 * x + 1.5) / 4 - -1|2");
        assertEquals(Factor.of(3, 2), f.a());
        assertEquals(Factor.of(5, 4), f.b());
        assertEquals("function is not affine", assertThrows(FormulaException.class,
                () -> AffineFunction.parse("x * (x + 1)")).getMessage());
        assertEquals("division by zero", assertThrows(FormulaException.class,
                () -> AffineFunction.parse("x / (1 - 1)")).getMessage());
        assertEquals("division by zero", assertThrows(FormulaException.class,
                () -> AffineFunction.parse("x * 1|0")).getMessage());
        FormulaException e = assertThrows(FormulaException.class, () -> AffineFunction.parse("x + y"));
        assertEquals("unexpected 'y'", e.getMessage());
        assertEquals(5, e.column());
        assertEquals("missing ')'", assertThrows(FormulaException.class,
                () -> AffineFunction.parse("(x + 1")).getMessage());
        assertEquals("function nested too deeply", assertThrows(FormulaException.class,
                () -> AffineFunction.parse("(".repeat(101) + "x" + ")".repeat(101))).getMessage());
    }

    @Test
    void hugeFactorsAreRejected() {
        assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> fails("x ((10^1000)^1000)^100\n", "test.units:1: factor too large"));
    }
}
