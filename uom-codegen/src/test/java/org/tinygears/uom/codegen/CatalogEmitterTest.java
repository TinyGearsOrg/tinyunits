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

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.WildcardType;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.engine.parse.Definition;
import org.tinygears.uom.engine.parse.DefinitionParser;
import org.tinygears.uom.engine.parse.Definitions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogEmitterTest {
    private static final Definitions DEFS = DefinitionParser.parse("si.units", new StringReader("""
            m:metre !L
            s:second !T
            K:kelvin !Θ
            Hz:hertz ~ 1/s
            min:minute 60 s
            °C:degree_Celsius K = x + 273.15
            @prefixed m k
            @system SI
            """), null);
    private static final List<QuantityTypeSpec> TYPES = QuantityTypesParser
            .parse("types.txt", new StringReader("Length m\nTime s\n"), DEFS);
    private static final List<CatalogEntry> ENTRIES = CatalogBuilder.build(DEFS, TYPES);

    // Stand-in for the real class in uom, which uom-codegen must not depend on.
    private static final String SYSTEM_STAND_IN = """
            package org.tinygears.uom.systems;

            public final class UomSystemOfUnits {
                public final String name;
                public final java.util.List<org.tinygears.uom.core.UomUnit<?>> units;
                public final java.util.Map<Class<?>, org.tinygears.uom.core.UomUnit<?>> preferred;
                public final java.util.Set<String> prefixable;

                public UomSystemOfUnits(String name, java.util.List<org.tinygears.uom.core.UomUnit<?>> units,
                        java.util.Map<Class<?>, org.tinygears.uom.core.UomUnit<?>> preferred,
                        java.util.Set<String> prefixable) {
                    this.name = name;
                    this.units = units;
                    this.preferred = preferred;
                    this.prefixable = prefixable;
                }
            }
            """;

    @TempDir
    Path tmp;

    private static final CatalogBuilder.Catalog SI = CatalogBuilder.catalogs(Map.of("si.units", DEFS), TYPES)
            .get("si.units");

    private static String catalog() {
        return CatalogEmitter.catalog(SI, DEFS);
    }

    private static String golden(String name) throws Exception {
        try (InputStream in = CatalogEmitterTest.class.getResourceAsStream("/golden/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void matchesGolden() throws Exception {
        assertEquals(golden("SI.java"), catalog());
    }

    @Test
    void deterministicAndWellFormed() {
        String text = catalog();
        assertEquals(text, catalog());
        assertFalse(text.contains("\r"));
        assertFalse(text.contains("\t"));
        assertTrue(text.endsWith("}\n"));
        assertEquals(List.of(), ImportCheck.problems(text, Set.of()));
        assertTrue(text.startsWith(Headers.java("data/si.units and data/quantity-types.txt") + """

                package org.tinygears.uom.systems;
                """));
    }

    @Test
    void referencesToOtherCatalogsKeepTheirClass() {
        CatalogBuilder.Catalog c = new CatalogBuilder.Catalog(
                "si.units",
                "SI",
                ENTRIES,
                new TreeMap<>(Map.of("a", "SI.KILOMETRE", "b", "Other.FOOT")),
                Set.of(),
                Set.of(),
                Map.of("Length", "Other.FOOT", "Time", "SI.MINUTE"));
        String text = CatalogEmitter.catalog(c, DEFS);
        assertTrue(text.contains("Map.entry(Length.class, Other.FOOT)"), text);
        assertTrue(text.contains("Map.entry(Time.class, MINUTE)"), text);
        assertTrue(text.contains("of(\n                    KILOMETRE,\n                    Other.FOOT);"), text);
        assertTrue(text.contains("PREFIXABLE =\n            Set.of();\n"), text);
        assertFalse(text.contains("import org.tinygears.uom.systems."), text);
    }

    // A catalog class named Set and a constant named Map: java.util.Set and java.util.Map stay qualified.
    @Test
    void clashingNamesKeepTheImportedClassQualified() throws Exception {
        List<CatalogEntry> entries = ENTRIES.stream()
                .map(
                        e -> e.constantName().equals("METRE")
                                ? new CatalogEntry(e.definition(), "Map", e.quantityType(), e.shared())
                                : e)
                .toList();
        Map<String, String> units = new LinkedHashMap<>();
        entries.forEach(e -> units.put(e.definition().symbol(), "Set." + e.constantName()));
        CatalogBuilder.Catalog c = new CatalogBuilder.Catalog(
                "si.units",
                "Set",
                entries,
                units,
                Set.of("m"),
                Set.of(),
                Map.of("Length", "Set.Map", "Time", "org.tinygears.uom.quantity.TimeFactory.SYSTEM_UNIT"));
        String text = CatalogEmitter.catalog(c, DEFS);
        assertEquals(List.of(), ImportCheck.problems(text, Set.of()));
        assertFalse(text.contains("import java.util.Set;"), text);
        assertFalse(text.contains("import java.util.Map;"), text);
        assertTrue(text.contains("public static final java.util.Set<String> PREFIXABLE"), text);
        assertTrue(text.contains("java.util.Map.entry(Length.class, Map)"), text);
        assertTrue(text.contains("java.util.Map.entry(Time.class, TimeFactory.SYSTEM_UNIT)"), text);

        Map<String, String> sources = quantitySources();
        sources.put("org.tinygears.uom.systems.UomSystemOfUnits", SYSTEM_STAND_IN);
        sources.put("org.tinygears.uom.systems.Set", text);
        try (URLClassLoader loader = InMemoryCompiler.compile(sources, tmp)) {
            Class<?> set = loader.loadClass("org.tinygears.uom.systems.Set");
            Map<?, ?> preferred = (Map<?, ?>) set.getField("PREFERRED").get(null);
            assertSame(set.getField("Map").get(null), preferred.get(javax.measure.quantity.Length.class));
            assertEquals(Set.of("m"), set.getField("PREFIXABLE").get(null));
        }
    }

    private static Map<String, String> quantitySources() {
        Map<String, String> sources = new LinkedHashMap<>();
        for (QuantityTypeSpec s : TYPES) {
            String q = "org.tinygears.uom.quantity." + s.name();
            sources.put(q, SourceEmitter.typedInterface(s));
            sources.put(q + "Double", SourceEmitter.doubleImplementation(s));
            sources.put(q + "Decimal", SourceEmitter.decimalImplementation(s));
            sources.put(q + "Factory", SourceEmitter.factory(s, DEFS));
        }
        // The typed interfaces call QuantityTypes.narrow.
        sources.put("org.tinygears.uom.quantity.QuantityTypes", SourceEmitter.registry(TYPES));
        return sources;
    }

    @Test
    void coherentUnitReferencesTheFactory() {
        Definitions d = DefinitionParser.parse("si.units", new StringReader("""
                m:metre !L
                kg:kilogram !M
                s:second !T
                N:newton kg m / s^2
                kN:kilonewton 1000 N
                @system SI
                """), null);
        List<QuantityTypeSpec> types = QuantityTypesParser.parse("types.txt", new StringReader("Force N\n"), d);
        String text = CatalogEmitter.catalog(CatalogBuilder.catalogs(Map.of("si.units", d), types).get("si.units"), d);
        assertTrue(text.contains("NEWTON =\n            UomUnit.unchecked(ForceFactory.SYSTEM_UNIT);\n"), text);
        assertTrue(text.contains("import org.tinygears.uom.quantity.ForceFactory;\n"), text);
        assertFalse(text.contains(".named(\"N\""), text);
        assertTrue(text.contains(".named(\"kN\", \"kilonewton\")"), text);
    }

    @Test
    void prefixedUnitIsEmittedThroughItsBase() throws Exception {
        String text = catalog();
        assertTrue(
                text.contains(
                        "KILOMETRE =\n            UomUnit.unchecked(METRE\n                    .prefix(MetricPrefix.KILO)\n"
                                + "                    .named(\"km\", \"kilometre\"));"),
                text);
        assertTrue(text.contains("import javax.measure.MetricPrefix;\n"), text);
        assertFalse(text.contains("Factor.of(1000L, 1L)"), text);

        Map<String, String> sources = quantitySources();
        sources.put("org.tinygears.uom.systems.UomSystemOfUnits", SYSTEM_STAND_IN);
        sources.put("org.tinygears.uom.systems.SI", text);
        try (URLClassLoader loader = InMemoryCompiler.compile(sources, tmp)) {
            Class<?> si = loader.loadClass("org.tinygears.uom.systems.SI");
            UomUnit<?> metre = (UomUnit<?>) si.getField("METRE").get(null);
            UomUnit<?> km = (UomUnit<?>) si.getField("KILOMETRE").get(null);
            assertEquals(metre.prefix(javax.measure.MetricPrefix.KILO).named("km", "kilometre"), km);
            assertEquals("km", km.getSymbol());
            assertEquals("kilometre", km.getName());
        }
    }

    @Test
    void prefixedUnitFallsBackToTheDecomposedFormWithoutAnInitialisedBase() {
        Map<String, String> noBase = new LinkedHashMap<>(SI.units());
        noBase.remove("m");
        String missing = CatalogEmitter.catalog(
                new CatalogBuilder.Catalog(
                        "si.units",
                        "SI",
                        ENTRIES,
                        noBase,
                        SI.prefixable(),
                        SI.binaryPrefixable(),
                        SI.preferred()),
                DEFS);
        assertTrue(missing.contains(".transform(Converters.affine(Factor.of(1000L, 1L), Factor.ZERO))"), missing);
        assertFalse(missing.contains(".prefix("), missing);

        // A field is initialised in textual order, so a base declared after its prefixed unit is not usable.
        List<CatalogEntry> late = new java.util.ArrayList<>(ENTRIES);
        CatalogEntry km = late.stream().filter(e -> e.constantName().equals("KILOMETRE")).findFirst().orElseThrow();
        late.remove(km);
        late.add(0, km);
        String after = CatalogEmitter.catalog(
                new CatalogBuilder.Catalog(
                        "si.units",
                        "SI",
                        late,
                        SI.units(),
                        SI.prefixable(),
                        SI.binaryPrefixable(),
                        SI.preferred()),
                DEFS);
        assertTrue(after.contains(".transform(Converters.affine(Factor.of(1000L, 1L), Factor.ZERO))"), after);
        assertFalse(after.contains(".prefix("), after);
    }

    @Test
    void aSystemUnitWithoutADefinitionGetsAConstant() {
        List<QuantityTypeSpec> extras = QuantityTypesParser
                .parse("types.txt", new StringReader("Speed m / s => m/s:metre_per_second\n"), DEFS);
        String text = CatalogEmitter.catalog(SI, DEFS, extras);
        assertTrue(text.contains("/** The metre per second, {@code m/s}, the system unit of Speed. */"), text);
        assertTrue(
                text.contains(
                        "public static final UomUnit<Speed> METRE_PER_SECOND =\n"
                                + "            UomUnit.unchecked(SpeedFactory.SYSTEM_UNIT);"),
                text);
        assertTrue(text.contains("import org.tinygears.uom.quantity.SpeedFactory;\n"), text);
        assertEquals(List.of(), ImportCheck.problems(text, Set.of()));
        // It is a constant of the class only, not one of its units, and only when asked for.
        assertFalse(
                text.contains("UNITS =\n            List.<UomUnit<?>>of(\n                    METRE_PER_SECOND"),
                text);
        assertFalse(catalog().contains("METRE_PER_SECOND"));
        // A name that a definition already has is refused.
        QuantityTypeSpec clash = new QuantityTypeSpec(
                "Speed",
                "m / s",
                BaseUnits.METRE.divide(BaseUnits.SECOND),
                "m/s",
                "metre",
                "types.txt",
                1,
                true);
        assertEquals(
                "types.txt:1: duplicate constant name 'METRE'",
                assertThrows(GenerationException.class, () -> CatalogEmitter.catalog(SI, DEFS, List.of(clash)))
                        .getMessage());
    }

    @Test
    void binaryPrefixesEmitTheirSetAndConstants() {
        Definitions d = DefinitionParser.parse(
                "information.units",
                new StringReader("@system Information\nbit:bit ~\nB:byte 8 bit\n@prefixed B k Ki\n"),
                name -> null);
        CatalogBuilder.Catalog c = CatalogBuilder.catalogs(Map.of("information.units", d), TYPES)
                .get("information.units");
        String text = CatalogEmitter.catalog(c, d);
        assertTrue(
                text.contains(
                        "BYTE\n                    .prefix(BinaryPrefix.KIBI)\n"
                                + "                    .named(\"KiB\", \"kibibyte\")"),
                text);
        assertTrue(text.contains("BYTE\n                    .prefix(MetricPrefix.KILO)\n"), text);
        assertTrue(
                text.contains(
                        "public static final Set<String> BINARY_PREFIXABLE =\n            Set.of(\n"
                                + "                    \"B\");"),
                text);
        assertTrue(
                text.contains(
                        "new UomSystemOfUnits(\"Information\", UNITS, PREFERRED, PREFIXABLE," + " BINARY_PREFIXABLE);"),
                text);
        assertEquals(List.of(), ImportCheck.problems(text, Set.of()));
        assertFalse(catalog().contains("BINARY_PREFIXABLE"));
    }

    @Test
    void headerNamesTheFilesRead() {
        Definitions child = DefinitionParser.parse(
                "b-child.units",
                new StringReader("@system Child\n@include z.units\n@include a.units\n"),
                name -> new StringReader(
                        name.equals("z.units") ? "m:metre !L\n" : "@include z.units\n@prefixed m k\n"));
        assertEquals(
                List.of("b-child.units", "a.units", "z.units", "quantity-types.txt"),
                CatalogEmitter.dataFiles("b-child.units", child));
        String text = CatalogEmitter.catalog(
                new CatalogBuilder.Catalog("b-child.units", "Child", List.of(), Map.of(), Set.of(), Set.of(), Map.of()),
                child);
        assertTrue(
                text.startsWith(
                        Headers.java("data/b-child.units, data/a.units, data/z.units and data/quantity-types.txt")),
                text);
        assertTrue(
                text.contains(
                        "/** The Child units; generated from {@code data/b-child.units},"
                                + " {@code data/a.units}, {@code data/z.units} and {@code data/quantity-types.txt}. */"),
                text);
        assertEquals(List.of("si.units", "quantity-types.txt"), CatalogEmitter.dataFiles("si.units", DEFS));
    }

    @Test
    void compilesAndRuns() throws Exception {
        Map<String, String> sources = quantitySources();
        sources.put("org.tinygears.uom.systems.UomSystemOfUnits", SYSTEM_STAND_IN);
        sources.put("org.tinygears.uom.systems.SI", catalog());
        try (URLClassLoader loader = InMemoryCompiler.compile(sources, tmp)) {
            Class<?> si = loader.loadClass("org.tinygears.uom.systems.SI");
            for (CatalogEntry e : ENTRIES) {
                Definition d = e.definition();
                Field f = si.getField(e.constantName());
                assertTrue(Modifier.isFinal(f.getModifiers()) && Modifier.isStatic(f.getModifiers()), f.getName());
                UomUnit<?> u = (UomUnit<?>) f.get(null);
                assertEquals(d.unit(), u.engine(), d.symbol());
                assertEquals(d.symbol(), u.getSymbol());
                assertEquals(d.name(), u.getName());
                Object typeArg = ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[0];
                if (e.quantityType() == null) {
                    assertTrue(typeArg instanceof WildcardType, f.getName());
                } else {
                    assertEquals(Class.forName("javax.measure.quantity." + e.quantityType()), typeArg);
                }
            }
            List<?> units = (List<?>) si.getField("UNITS").get(null);
            assertEquals(
                    ENTRIES.stream().map(e -> e.definition().symbol()).toList(),
                    units.stream().map(u -> ((UomUnit<?>) u).getSymbol()).toList());
            Map<?, ?> preferred = (Map<?, ?>) si.getField("PREFERRED").get(null);
            assertEquals(
                    Set.of(javax.measure.quantity.Length.class, javax.measure.quantity.Time.class),
                    preferred.keySet());
            Object metre = loader.loadClass("org.tinygears.uom.quantity.LengthFactory").getField("SYSTEM_UNIT")
                    .get(null);
            assertSame(metre, preferred.get(javax.measure.quantity.Length.class));
            assertSame(metre, si.getField("METRE").get(null));
            assertSame(BaseUnits.METRE, metre);
            assertSame(BaseUnits.KELVIN, si.getField("KELVIN").get(null));
            assertEquals(Set.of("m"), si.getField("PREFIXABLE").get(null));
            Object system = si.getField("SYSTEM").get(null);
            assertEquals("SI", system.getClass().getField("name").get(system));
            assertSame(units, system.getClass().getField("units").get(system));
            assertTrue(Modifier.isPrivate(si.getDeclaredConstructor().getModifiers()));
        }
    }

    // A parent and a child catalog on top of the real SI data, compiled with the real UomSystemOfUnits.
    private static final Map<String, String> SAMPLE = Map.of("par.units", """
            @system Par
            @include si.units
            @member m s kg
            ft:foot 0.3048 m
            @prefixed ft k
            @preferred Length ft
            """, "kid.units", """
            @system Kid
            @extends par.units
            @include si.units
            @member g J m
            st:stone 6.35029318 kg
            @preferred Mass st
            @preferred Energy J
            """);
    private static final Path DATA = Path.of("../data");

    private static Definitions parseSample(String file) throws Exception {
        DefinitionParser.Resolver resolver = name -> SAMPLE.containsKey(name)
                ? new StringReader(SAMPLE.get(name))
                : Files.newBufferedReader(DATA.resolve(name), StandardCharsets.UTF_8);
        try (Reader in = resolver.open(file)) {
            return DefinitionParser.parse(file, in, resolver);
        }
    }

    @Test
    void childCatalogCompilesWithItsParents() throws Exception {
        Map<String, Definitions> parsed = new LinkedHashMap<>();
        for (String file : List.of("si.units", "par.units", "kid.units")) {
            parsed.put(file, parseSample(file));
        }
        List<QuantityTypeSpec> types;
        try (Reader in = Files.newBufferedReader(DATA.resolve("quantity-types.txt"), StandardCharsets.UTF_8)) {
            types = QuantityTypesParser.parse("quantity-types.txt", in, parsed.get("si.units"));
        }
        Map<String, CatalogBuilder.Catalog> catalogs = CatalogBuilder.catalogs(parsed, types);
        String kid = CatalogEmitter.catalog(catalogs.get("kid.units"), parsed.get("kid.units"));
        assertEquals(golden("Kid.java"), kid);
        assertEquals(List.of(), ImportCheck.problems(kid, Set.of()));

        Map<String, String> sources = new LinkedHashMap<>();
        for (QuantityTypeSpec s : types) {
            String q = "org.tinygears.uom.quantity." + s.name();
            sources.put(q, SourceEmitter.typedInterface(s));
            sources.put(q + "Double", SourceEmitter.doubleImplementation(s));
            sources.put(q + "Decimal", SourceEmitter.decimalImplementation(s));
            sources.put(q + "Factory", SourceEmitter.factory(s, parsed.get("si.units")));
        }
        sources.put("org.tinygears.uom.quantity.QuantityTypes", SourceEmitter.registry(types));
        sources.put(
                "org.tinygears.uom.systems.UomSystemOfUnits",
                Files.readString(Path.of("../uom/src/main/java/org/tinygears/uom/systems/UomSystemOfUnits.java")));
        // UomSystemOfUnits.load needs these; the hand-written format is not under test here.
        sources.put("org.tinygears.uom.systems.UomUnitFormat", """
                package org.tinygears.uom.systems;
                final class UomUnitFormat {
                    static UomUnitFormat standard() {
                        return new UomUnitFormat();
                    }
                    java.util.Optional<org.tinygears.uom.core.UomUnit<?>> resolve(String symbol) {
                        return java.util.Optional.empty();
                    }
                }
                """);
        catalogs.forEach(
                (file, c) -> sources.put(
                        "org.tinygears.uom.systems." + c.className(),
                        CatalogEmitter.catalog(c, parsed.get(file))));
        try (URLClassLoader loader = InMemoryCompiler.compile(sources, tmp)) {
            Class<?> si = loader.loadClass("org.tinygears.uom.systems.SI");
            Class<?> par = loader.loadClass("org.tinygears.uom.systems.Par");
            Class<?> k = loader.loadClass("org.tinygears.uom.systems.Kid");
            List<?> units = (List<?>) k.getField("UNITS").get(null);
            assertEquals(
                    List.of("st", "ft", "kft", "m", "s", "kg", "g", "J"),
                    units.stream().map(u -> ((UomUnit<?>) u).getSymbol()).toList());
            assertSame(par.getField("FOOT").get(null), units.get(1));
            assertSame(si.getField("GRAM").get(null), units.get(6));
            assertEquals(Set.of("J", "ft", "g", "m", "s"), k.getField("PREFIXABLE").get(null));
            Object system = k.getField("SYSTEM").get(null);
            java.lang.reflect.Method getUnit = system.getClass().getMethod("getUnit", Class.class);
            for (QuantityTypeSpec t : types) {
                assertNotNull(getUnit.invoke(system, Class.forName("javax.measure.quantity." + t.name())), t.name());
            }
            assertSame(k.getField("STONE").get(null), getUnit.invoke(system, javax.measure.quantity.Mass.class));
            assertSame(par.getField("FOOT").get(null), getUnit.invoke(system, javax.measure.quantity.Length.class));
            assertSame(
                    loader.loadClass("org.tinygears.uom.quantity.TimeFactory").getField("SYSTEM_UNIT").get(null),
                    getUnit.invoke(system, javax.measure.quantity.Time.class));
            assertEquals(8, ((Set<?>) system.getClass().getMethod("getUnits").invoke(system)).size());
        }
    }
}
