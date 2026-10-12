// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.tinygears.uom.core.parse.DefinitionParser;
import org.tinygears.uom.core.parse.Definitions;
import org.tinygears.uom.core.parse.Member;
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

    private static final String FACTORY = "org.tinygears.uom.quantity.%sFactory.SYSTEM_UNIT";

    // @system goes last so that error lines of the extra text stay at 19.
    private static CatalogBuilder.Catalog catalog(String extra) {
        Definitions d = defs(BASE + extra + "@system T\n");
        return CatalogBuilder.catalogs(Map.of("test.units", d), types(d)).get("test.units");
    }

    @Test
    void preferredUnits() {
        Map<String, String> p = catalog("@preferred Length km\n@preferred Frequency kHz\n").preferred();
        assertEquals(types(defs(BASE)).stream().map(QuantityTypeSpec::name).toList(), List.copyOf(p.keySet()));
        assertEquals("T.KILOMETRE", p.get("Length"));
        assertEquals("T.KILOHERTZ", p.get("Frequency"));
        assertEquals(FACTORY.formatted("Time"), p.get("Time"));
        assertEquals(FACTORY.formatted("Length"), catalog("").preferred().get("Length"));
        fails(() -> catalog("@preferred Speed m\n"), 19, "unknown quantity type 'Speed'");
        fails(() -> catalog("@preferred Length s\n"), 19, "unit 's' is not a unit of Length");
        fails(() -> catalog("@preferred Length m\n@preferred Length km\n"), 20,
                "duplicate preferred unit for 'Length'");
    }

    @Test
    void preferredUnknownSymbol() {
        Definitions parsed = defs(BASE);
        Definitions d = new Definitions(parsed.definitions(),
                List.of(new Preferred("Length", "nope", "test.units", 7)), parsed.prefixed(), "T", List.of(),
                List.of());
        fails(() -> CatalogBuilder.catalogs(Map.of("test.units", d), types(d)), 7, "unknown unit 'nope'");
    }

    @Test
    void prefixable() {
        Set<String> p = catalog("@prefixed s m\n@prefixed m c\n").prefixable();
        assertEquals(Set.of("m", "Hz", "s"), p);
        assertThrows(UnsupportedOperationException.class, () -> p.add("g"));
    }

    @Test
    void catalogNeedsASystemAndAUniqueName() {
        Definitions d = defs(BASE);
        fails(() -> CatalogBuilder.catalogs(Map.of("test.units", d), types(d)), 0, "no @system directive");
        Definitions t = defs(BASE + "@system T\n");
        Map<String, Definitions> two = new LinkedHashMap<>();
        two.put("a.units", t);
        two.put("test.units", t);
        fails(() -> CatalogBuilder.catalogs(two, types(t)), 0, "duplicate system 'T' (also in a.units)");
    }

    @Test
    void unknownMemberIsReportedAtItsLine() {
        Definitions parsed = defs(BASE);
        Definitions d = new Definitions(parsed.definitions(), List.of(), List.of(), "T", List.of(),
                List.of(new Member("zz", "test.units", 5)));
        fails(() -> CatalogBuilder.catalogs(Map.of("test.units", d), types(d)), 5, "unknown unit 'zz'");
    }

    // base.units is BASE as a catalog; parent.units and child.units exercise @extends, @member and @prefixed.
    private static final Map<String, String> SAMPLE = Map.of(
            "base.units", BASE + "@system Base\n@preferred Time min\n",
            "parent.units", """
                    @system Parent
                    @include base.units
                    ft:foot 0.3048 m
                    yd:yard 3 ft
                    @member m s
                    @prefixed yd k
                    @preferred Length ft
                    """,
            "child.units", """
                    @system Child
                    @extends parent.units
                    @include base.units
                    @member m kg Hz s
                    fur:foot 201.168 m
                    @prefixed s m
                    @prefixed J k
                    @preferred Mass kg
                    """,
            "orphan.units", """
                    @system Orphan
                    @include parent.units
                    @member yd
                    """,
            "second.units", """
                    @system Second
                    @include base.units
                    mi:mile 1609.344 m
                    @preferred Length mi
                    """,
            "twins.units", """
                    @system Twins
                    @extends parent.units
                    @extends second.units
                    """,
            "member.units", """
                    @system Mem
                    @include parent.units
                    @member ft
                    """);

    private static Definitions sample(String file) {
        DefinitionParser.Resolver resolver = name -> new StringReader(SAMPLE.get(name));
        return DefinitionParser.parse(file, new StringReader(SAMPLE.get(file)), resolver);
    }

    private static Map<String, CatalogBuilder.Catalog> sampleCatalogs(String... files) {
        Map<String, Definitions> parsed = new LinkedHashMap<>();
        for (String f : files) {
            parsed.put(f, sample(f));
        }
        return CatalogBuilder.catalogs(parsed, types(parsed.get("base.units")));
    }

    @Test
    void ownEntriesExcludeIncludedDefinitions() {
        CatalogBuilder.Catalog child = sampleCatalogs("base.units", "parent.units", "child.units").get("child.units");
        assertEquals("Child", child.className());
        assertEquals(List.of("fur", "ms", "kJ"), child.entries().stream().map(e -> e.definition().symbol()).toList());
        // A constant may repeat a parent's constant name: they live in different classes.
        assertEquals(List.of("FOOT", "MILLISECOND", "KILOJOULE"),
                child.entries().stream().map(CatalogEntry::constantName).toList());
    }

    @Test
    void unitsArePrefixedOwnThenExtendedThenMembers() {
        Map<String, CatalogBuilder.Catalog> c = sampleCatalogs("base.units", "parent.units", "child.units");
        assertEquals(List.of("Parent.FOOT", "Parent.YARD", "Parent.KILOYARD", "Base.METRE", "Base.SECOND"),
                List.copyOf(c.get("parent.units").units().values()));
        Map<String, String> units = c.get("child.units").units();
        assertEquals(List.of("fur", "ms", "kJ", "ft", "yd", "kyd", "m", "s", "kg", "Hz"), List.copyOf(units.keySet()));
        assertEquals(List.of("Child.FOOT", "Child.MILLISECOND", "Child.KILOJOULE", "Parent.FOOT", "Parent.YARD",
                "Parent.KILOYARD", "Base.METRE", "Base.SECOND", "Base.KILOGRAM", "Base.HERTZ"),
                List.copyOf(units.values()));
    }

    @Test
    void prefixableIsOwnExtendedAndPrefixedMembersRestrictedToUnits() {
        Map<String, CatalogBuilder.Catalog> c = sampleCatalogs("base.units", "parent.units", "child.units");
        assertEquals(Set.of("Hz", "m"), c.get("base.units").prefixable());
        assertEquals(Set.of("yd", "m"), c.get("parent.units").prefixable());
        // J is prefixed by the child but is not one of its units; kg is a member that base.units does not prefix.
        assertEquals(Set.of("s", "yd", "m", "Hz"), c.get("child.units").prefixable());
    }

    @Test
    void preferredIsOwnThenFirstParentThenFirstCatalog() {
        Map<String, CatalogBuilder.Catalog> c = sampleCatalogs("base.units", "parent.units", "child.units");
        Map<String, String> child = c.get("child.units").preferred();
        assertEquals("Base.KILOGRAM", child.get("Mass"));
        assertEquals("Parent.FOOT", child.get("Length"));
        assertEquals(FACTORY.formatted("Energy"), child.get("Energy"));
        // Without @extends the first catalog's preferred units apply; the child inherits them through its parent.
        assertEquals("Base.MINUTE", c.get("parent.units").preferred().get("Time"));
        assertEquals("Base.MINUTE", child.get("Time"));
        assertEquals(FACTORY.formatted("Mass"), c.get("parent.units").preferred().get("Mass"));
    }

    @Test
    void firstExtendedSystemWinsThePreferredUnit() {
        Map<String, CatalogBuilder.Catalog> c = sampleCatalogs("base.units", "parent.units", "second.units",
                "twins.units");
        assertEquals("Second.MILE", c.get("second.units").preferred().get("Length"));
        assertEquals("Parent.FOOT", c.get("twins.units").preferred().get("Length"));
        assertEquals("Second.MILE", c.get("twins.units").units().get("mi"));
    }

    @Test
    void preferredLineOfAnIncludedFileIsIgnoredForAMember() {
        CatalogBuilder.Catalog mem = sampleCatalogs("base.units", "parent.units", "member.units").get("member.units");
        assertEquals("Parent.FOOT", mem.units().get("ft"));
        assertEquals(FACTORY.formatted("Length"), mem.preferred().get("Length"));
        assertEquals("Base.MINUTE", mem.preferred().get("Time"));
    }

    @Test
    void parentsAndMemberOwnersMustBeEarlierCatalogs() {
        GenerationException e = assertThrows(GenerationException.class,
                () -> sampleCatalogs("base.units", "child.units"));
        assertEquals("child.units:2: 'parent.units' is not a catalog generated before 'child.units'",
                e.getMessage());
        e = assertThrows(GenerationException.class, () -> sampleCatalogs("base.units", "orphan.units"));
        assertEquals("orphan.units:3: unit 'yd' is defined in 'parent.units', which is not a catalog generated"
                + " before 'orphan.units'", e.getMessage());
    }

    @Test
    void preferredMustBeAUnitOfTheSystem() {
        Definitions d = defs(BASE + "@system T\n");
        Definitions orphan = DefinitionParser.parse("o.units", new StringReader("@system O\n@include test.units\n"
                + "@member s\n@preferred Length m\n"), name -> new StringReader(BASE + "@system T\n"));
        Map<String, Definitions> parsed = new LinkedHashMap<>();
        parsed.put("test.units", d);
        parsed.put("o.units", orphan);
        GenerationException e = assertThrows(GenerationException.class, () -> CatalogBuilder.catalogs(parsed,
                types(d)));
        assertEquals("o.units:4: unit 'm' is not a unit of O", e.getMessage());
    }

    private static Map<String, Definitions> parseData() throws IOException {
        Path data = Path.of("../data");
        DefinitionParser.Resolver resolver = name -> Files.newBufferedReader(data.resolve(name),
                StandardCharsets.UTF_8);
        Map<String, Definitions> parsed = new LinkedHashMap<>();
        for (String file : Generate.CATALOGS) {
            try (Reader in = resolver.open(file)) {
                parsed.put(file, DefinitionParser.parse(file, in, resolver));
            }
        }
        return parsed;
    }

    private static List<QuantityTypeSpec> realTypes(Definitions si) throws IOException {
        try (Reader in = Files.newBufferedReader(Path.of("../data/quantity-types.txt"), StandardCharsets.UTF_8)) {
            return QuantityTypesParser.parse("quantity-types.txt", in, si);
        }
    }

    @Test
    void realData() throws IOException {
        Map<String, Definitions> parsed = parseData();
        Definitions d = parsed.get("si.units");
        List<QuantityTypeSpec> types = realTypes(d);
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
        CatalogBuilder.Catalog si = CatalogBuilder.catalogs(Map.of("si.units", d), types).get("si.units");
        assertEquals(entries, si.entries());
        types.forEach(t -> assertEquals(FACTORY.formatted(t.name()), si.preferred().get(t.name())));
    }

    /** Independent of the builder: own lines of the file, else the first extended file's, else SI's. */
    private static Map<String, Map<String, String>> expectedPreferredSymbols(Map<String, Definitions> parsed,
            List<QuantityTypeSpec> types) {
        Map<String, Map<String, String>> result = new HashMap<>();
        for (String file : Generate.CATALOGS) {
            Definitions d = parsed.get(file);
            Map<String, String> own = new HashMap<>();
            d.preferred().stream().filter(p -> p.source().equals(file))
                    .forEach(p -> own.put(p.quantityType(), p.symbol()));
            Map<String, String> inherited = !d.extended().isEmpty() ? result.get(d.extended().get(0).file())
                    : file.equals("si.units") ? Map.of() : result.get("si.units");
            Map<String, String> all = new HashMap<>();
            for (QuantityTypeSpec t : types) {
                all.put(t.name(), own.getOrDefault(t.name(), inherited.getOrDefault(t.name(), "factory")));
            }
            result.put(file, all);
        }
        return result;
    }

    @Test
    void preferredLayeringOfEverySystem() throws IOException {
        Map<String, Definitions> parsed = parseData();
        List<QuantityTypeSpec> types = realTypes(parsed.get("si.units"));
        assertEquals(33, types.size());
        Map<String, CatalogBuilder.Catalog> catalogs = CatalogBuilder.catalogs(parsed, types);
        Map<String, String> symbolByRef = new HashMap<>();
        catalogs.values().forEach(c -> c.entries().forEach(e -> symbolByRef.put(c.className() + "."
                + e.constantName(), e.definition().symbol())));
        Map<String, Map<String, String>> expected = expectedPreferredSymbols(parsed, types);
        for (String file : Generate.CATALOGS) {
            Definitions d = parsed.get(file);
            Map<String, String> actual = catalogs.get(file).preferred();
            assertEquals(types.stream().map(QuantityTypeSpec::name).toList(), List.copyOf(actual.keySet()), file);
            for (QuantityTypeSpec t : types) {
                String symbol = expected.get(file).get(t.name());
                String ref = actual.get(t.name());
                if (symbol.equals("factory")) {
                    assertEquals(FACTORY.formatted(t.name()), ref, file + " " + t.name());
                } else {
                    String owner = parsed.get(d.definitions().stream().filter(x -> x.symbol().equals(symbol))
                            .findFirst().orElseThrow().source()).system();
                    assertTrue(ref.startsWith(owner + "."), file + " " + t.name() + ": " + ref);
                    assertEquals(symbol, symbolByRef.get(ref), file + " " + t.name());
                }
            }
        }
        Map<String, String> imperial = catalogs.get("imperial.units").preferred();
        assertEquals("Imperial.GALLON_UK", imperial.get("Volume"));
        assertEquals("USCustomary.FOOT", imperial.get("Length"));
        assertEquals(FACTORY.formatted("Time"), catalogs.get("cgs.units").preferred().get("Time"));
        assertEquals("CGS.DYNE", catalogs.get("cgs-esu.units").preferred().get("Force"));
        assertEquals(FACTORY.formatted("MagneticFluxDensity"),
                catalogs.get("cgs-esu.units").preferred().get("MagneticFluxDensity"));
        Map<String, String> gaussian = catalogs.get("cgs-gaussian.units").preferred();
        assertEquals("Gaussian.FRANKLIN", gaussian.get("ElectricCharge"));
        assertEquals("ESU.STATAMPERE", gaussian.get("ElectricCurrent"));
        assertEquals("EMU.GAUSS", gaussian.get("MagneticFluxDensity"));
        assertEquals("EMU.MAXWELL", gaussian.get("MagneticFlux"));
        assertEquals("SI.CENTIMETRE", gaussian.get("Length"));
    }

    private static Set<String> classesOf(CatalogBuilder.Catalog c) {
        return c.units().values().stream().map(r -> r.substring(0, r.indexOf('.'))).collect(Collectors.toSet());
    }

    @Test
    void membershipOfEverySystem() throws IOException {
        Map<String, Definitions> parsed = parseData();
        Map<String, CatalogBuilder.Catalog> c = CatalogBuilder.catalogs(parsed, realTypes(parsed.get("si.units")));
        CatalogBuilder.Catalog cgs = c.get("cgs.units");
        assertEquals(List.of("SI.CENTIMETRE", "SI.GRAM", "SI.SECOND"), cgs.units().values().stream()
                .filter(r -> r.startsWith("SI.")).toList());
        assertEquals(Set.of("CGS", "SI"), classesOf(cgs));
        assertEquals(Set.of("Gal", "P", "St", "g", "s"), cgs.prefixable());
        assertEquals(cgs.prefixable(), c.get("cgs-esu.units").prefixable());
        Set<String> withGauss = new HashSet<>(cgs.prefixable());
        withGauss.add("G");
        assertEquals(withGauss, c.get("cgs-emu.units").prefixable());
        assertEquals(withGauss, c.get("cgs-gaussian.units").prefixable());
        assertEquals(Set.of(), c.get("us-customary.units").prefixable());
        assertEquals(Set.of(), c.get("imperial.units").prefixable());
        CatalogBuilder.Catalog gaussian = c.get("cgs-gaussian.units");
        assertEquals(Set.of("Gaussian", "ESU", "CGS", "SI", "EMU"), classesOf(gaussian));
        assertEquals(List.of("EMU.GAUSS", "EMU.MAXWELL", "EMU.OERSTED", "EMU.GILBERT"), gaussian.units().values()
                .stream().filter(r -> r.startsWith("EMU.")).toList());
        List<String> expected = new ArrayList<>(List.of("Gaussian.FRANKLIN"));
        expected.addAll(c.get("cgs-esu.units").units().values());
        expected.addAll(List.of("EMU.GAUSS", "EMU.MAXWELL", "EMU.OERSTED", "EMU.GILBERT"));
        assertEquals(expected, List.copyOf(gaussian.units().values()));
        CatalogBuilder.Catalog imperial = c.get("imperial.units");
        assertEquals(Set.of("Imperial", "USCustomary"), classesOf(imperial));
        assertEquals(imperial.entries().size() + c.get("us-customary.units").entries().size(),
                imperial.units().size());
        for (CatalogBuilder.Catalog x : c.values()) {
            assertEquals(Set.copyOf(x.units().values()).size(), x.units().size(), x.className());
        }
    }
}
