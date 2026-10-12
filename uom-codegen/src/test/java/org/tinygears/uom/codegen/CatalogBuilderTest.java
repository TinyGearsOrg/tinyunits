// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.tinygears.uom.core.parse.DefinitionParser;
import org.tinygears.uom.core.parse.Definitions;
import org.tinygears.uom.core.parse.Preferred;

class CatalogBuilderTest {
    // 18 lines; error cases append at line 19.
    private static final String BASE = """
            m:metre !L
            kg:kilogram !M
            s:second !T
            A:ampere !I
            K:kelvin !Θ
            mol:mole !N
            cd:candela !J
            rad:radian ~ [Angle]
            Hz:hertz ~ 1/s [Frequency]
            Bq:becquerel ~ 1/s [Radioactivity]
            N:newton kg m / s^2 [Force]
            J:joule N m [Energy]
            °C:degree_Celsius K = x + 273.15 [Temperature]
            min:minute 60 s
            au:astronomical_unit 149597870700 m
            x:angular_velocity rad / s
            @prefixed m k
            @prefixed Hz k
            """;
    private static final String TYPES = """
            Length m
            Mass kg
            Time s
            Temperature K
            Angle rad
            Frequency Hz
            Radioactivity Bq
            Force N
            Energy J
            """;

    private static Definitions defs(String text) {
        return DefinitionParser.parse("test.units", new StringReader(text), null);
    }

    private static List<QuantityTypeSpec> types(Definitions d) {
        return QuantityTypesParser.parse("types.txt", new StringReader(TYPES), d);
    }

    private static List<CatalogEntry> build(String text) {
        Definitions d = defs(text);
        return CatalogBuilder.build(d, types(d));
    }

    private static Map<String, CatalogEntry> bySymbol(List<CatalogEntry> entries) {
        return entries.stream().collect(Collectors.toMap(e -> e.definition().symbol(), Function.identity()));
    }

    private static void fails(Executable call, int line, String text) {
        GenerationException e = assertThrows(GenerationException.class, call);
        assertEquals("test.units", e.source());
        assertEquals(line, e.line());
        assertEquals("test.units:" + line + ": " + text, e.getMessage());
    }

    private static void fails(String extra, String text) {
        fails(() -> build(BASE + extra), 19, text);
    }

    @Test
    void resolvesTypesAndNames() {
        List<CatalogEntry> entries = build(BASE);
        assertEquals(List.of("m", "kg", "s", "A", "K", "mol", "cd", "rad", "Hz", "Bq", "N", "J", "°C", "min", "au",
                "x", "km", "kHz"), entries.stream().map(e -> e.definition().symbol()).toList());
        Map<String, CatalogEntry> e = bySymbol(entries);
        assertEquals("METRE", e.get("m").constantName());
        assertEquals("Length", e.get("m").quantityType());
        assertEquals("DEGREE_CELSIUS", e.get("°C").constantName());
        assertEquals("Temperature", e.get("°C").quantityType());
        assertEquals("ASTRONOMICAL_UNIT", e.get("au").constantName());
        assertEquals("Length", e.get("au").quantityType());
        assertEquals("Time", e.get("min").quantityType());
        assertEquals("Frequency", e.get("Hz").quantityType());
        assertEquals("Radioactivity", e.get("Bq").quantityType());
        assertEquals("Energy", e.get("J").quantityType());
        assertEquals("ANGULAR_VELOCITY", e.get("x").constantName());
        assertNull(e.get("x").quantityType());
        assertNull(e.get("A").quantityType());
        assertEquals("KILOMETRE", e.get("km").constantName());
        assertEquals("Length", e.get("km").quantityType());
        assertEquals("KILOHERTZ", e.get("kHz").constantName());
        assertEquals("Frequency", e.get("kHz").quantityType());
    }

    @Test
    void keywordSpelledInLowerCaseIsAValidConstant() {
        assertEquals("CLASS", bySymbol(build(BASE + "y:class 2 s\n")).get("y").constantName());
    }

    @Test
    void errors() {
        fails("y:metre 1000 m\n", "duplicate constant name 'METRE' ('m' and 'y')");
        fails("y:other 2 s [Length]\n", "unit 'y' is not a unit of Length");
        fails("y:other 2 s [Speed]\n", "unknown quantity type 'Speed'");
        fails("y:2nd 2 s\n", "invalid constant name '2ND' for 'y'");
        fails("y:_ 2 s\n", "invalid constant name '_' for 'y'");
    }

    @Test
    void ambiguousType() {
        Definitions d = defs(BASE);
        List<QuantityTypeSpec> types = new ArrayList<>(types(d));
        QuantityTypeSpec time = types.get(2);
        types.add(new QuantityTypeSpec("Duration", "s", time.systemUnit(), "s", "second", "types.txt", 10));
        fails(() -> CatalogBuilder.build(d, types), 3, "ambiguous quantity type for 's': Time, Duration (add a [Type])");
    }

    private static Map<String, String> preferred(String extra) {
        Definitions d = defs(BASE + extra);
        List<QuantityTypeSpec> types = types(d);
        return CatalogBuilder.preferred(d, CatalogBuilder.build(d, types), types);
    }

    @Test
    void preferredUnits() {
        assertEquals(Map.of("Length", "KILOMETRE", "Frequency", "KILOHERTZ"),
                preferred("@preferred Length km\n@preferred Frequency kHz\n"));
        assertEquals(Map.of(), preferred(""));
        fails(() -> preferred("@preferred Speed m\n"), 19, "unknown quantity type 'Speed'");
        fails(() -> preferred("@preferred Length s\n"), 19, "unit 's' is not a unit of Length");
        fails(() -> preferred("@preferred Length m\n@preferred Length km\n"), 20,
                "duplicate preferred unit for 'Length'");
    }

    @Test
    void preferredUnknownSymbol() {
        Definitions parsed = defs(BASE);
        Definitions d = new Definitions(parsed.definitions(),
                List.of(new Preferred("Length", "nope", "test.units", 7)), parsed.prefixed());
        List<QuantityTypeSpec> types = types(d);
        fails(() -> CatalogBuilder.preferred(d, CatalogBuilder.build(d, types), types), 7, "unknown unit 'nope'");
    }

    @Test
    void prefixable() {
        Set<String> p = CatalogBuilder.prefixable(defs(BASE + "@prefixed s m\n@prefixed m c\n"));
        assertEquals(List.of("m", "Hz", "s"), List.copyOf(p));
        assertThrows(UnsupportedOperationException.class, () -> p.add("g"));
    }

    @Test
    void realData() throws IOException {
        Path data = Path.of("../data");
        Definitions d;
        try (Reader in = Files.newBufferedReader(data.resolve("si.units"), StandardCharsets.UTF_8)) {
            d = DefinitionParser.parse("si.units", in, null);
        }
        List<QuantityTypeSpec> types;
        try (Reader in = Files.newBufferedReader(data.resolve("quantity-types.txt"), StandardCharsets.UTF_8)) {
            types = QuantityTypesParser.parse("quantity-types.txt", in, d);
        }
        List<CatalogEntry> entries = CatalogBuilder.build(d, types);
        assertEquals(d.definitions().size(), entries.size());
        assertEquals(0, entries.stream().filter(e -> e.quantityType() == null).count());
        assertEquals(entries.size(), new HashSet<>(entries.stream().map(CatalogEntry::constantName).toList()).size());
        Map<String, String> symbols = entries.stream()
                .collect(Collectors.toMap(CatalogEntry::constantName, e -> e.definition().symbol()));
        Map<String, String> expected = Map.ofEntries(Map.entry("METRE", "m"), Map.entry("KILOGRAM", "kg"),
                Map.entry("DEGREE_CELSIUS", "°C"), Map.entry("SQUARE_METRE", "m²"), Map.entry("KILOMETRE", "km"),
                Map.entry("MICROMETRE", "µm"), Map.entry("KILOHERTZ", "kHz"), Map.entry("ASTRONOMICAL_UNIT", "au"),
                Map.entry("ARCMINUTE", "arcmin"), Map.entry("MILLISIEMENS", "mS"), Map.entry("MILLISECOND", "ms"));
        expected.forEach((name, symbol) -> assertEquals(symbol, symbols.get(name), name));
        assertEquals(Map.of(), CatalogBuilder.preferred(d, entries, types));
    }
}
