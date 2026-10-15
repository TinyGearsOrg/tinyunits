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
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.lang.model.SourceVersion;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.Factor;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.core.parse.Definition;
import org.tinygears.uom.core.parse.DefinitionParser;
import org.tinygears.uom.core.parse.Definitions;
import org.tinygears.uom.core.parse.Extends;
import org.tinygears.uom.core.parse.Member;
import org.tinygears.uom.core.parse.Preferred;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CgsDataTest {
    private static final Path DATA = Path.of("../data");
    private static final String CGS = "cgs.units";
    private static final String ESU = "cgs-esu.units";
    private static final String EMU = "cgs-emu.units";
    private static final String GAUSSIAN = "cgs-gaussian.units";
    private static final List<String> FILES = List.of(CGS, ESU, EMU, GAUSSIAN);

    // Speed of light in m/s (SI defining constant); the ESU/EMU ratio is c in cm/s, i.e. 100 c.
    private static final BigInteger C = BigInteger.valueOf(299_792_458);
    private static final Factor C_SI = Factor.of(C, BigInteger.ONE);

    private static final Map<String, Definitions> parsed = new HashMap<>();
    private static final Map<String, Map<String, CatalogEntry>> entries = new HashMap<>();
    private static List<QuantityTypeSpec> specs;

    @BeforeAll
    static void load() throws IOException {
        for (String file : FILES) {
            parsed.put(file, parse(file));
        }
        try (Reader in = Files.newBufferedReader(DATA.resolve("quantity-types.txt"), StandardCharsets.UTF_8)) {
            specs = QuantityTypesParser.parse("quantity-types.txt", in, parsed.get(CGS));
        }
        for (String file : FILES) {
            entries.put(
                    file,
                    CatalogBuilder.build(parsed.get(file), specs).stream()
                            .collect(Collectors.toMap(e -> e.definition().symbol(), Function.identity())));
        }
    }

    private static Definitions parse(String file) throws IOException {
        DefinitionParser.Resolver resolver = name -> Files.newBufferedReader(
                DATA.resolve(name),
                StandardCharsets.UTF_8);
        try (Reader in = resolver.open(file)) {
            return DefinitionParser.parse(file, in, resolver);
        }
    }

    /** The Gaussian parse sees every definition of the four files. */
    private static CatalogEntry entry(String symbol) {
        CatalogEntry e = entries.get(GAUSSIAN).get(symbol);
        assertNotNull(e, symbol);
        return e;
    }

    private static UomUnit<?> unit(String symbol) {
        return entry(symbol).definition().unit();
    }

    private static Factor pow10(int n) {
        return n >= 0
                ? Factor.of(BigInteger.TEN.pow(n), BigInteger.ONE)
                : Factor.of(
                        BigInteger.ONE,
                        BigInteger.TEN.pow(-n));
    }

    private static void assertFactor(String symbol, UomUnit<?> system, Factor factor) {
        UomUnit<?> u = unit(symbol);
        assertEquals(system.getSystemUnit(), u.getSystemUnit(), symbol);
        assertEquals(factor, u.toSystemConverter().factor(), symbol);
        assertEquals(Factor.ZERO, u.toSystemConverter().offset(), symbol);
    }

    private static void assertFactor(String symbol, String si, Factor factor) {
        assertFactor(symbol, unit(si), factor);
    }

    private static void assertSame(UomUnit<?> expected, UomUnit<?> actual, String what) {
        assertEquals(expected.getSystemUnit(), actual.getSystemUnit(), what);
        assertEquals(expected.toSystemConverter().factor(), actual.toSystemConverter().factor(), what);
    }

    @Test
    void mechanicalUnits() {
        assertFactor("dyn", "N", Factor.of(1, 100_000));
        assertFactor("erg", "J", Factor.of(1, 10_000_000));
        assertFactor("Ba", "Pa", Factor.of(1, 10));
        assertFactor("Gal", unit("m").divide(unit("s").pow(2)), Factor.of(1, 100));
        assertFactor("cm²", "m²", Factor.of(1, 10_000));
        assertFactor("cm³", "m³", Factor.of(1, 1_000_000));
        UomUnit<?> kgPerMs = unit("kg").divide(unit("m").multiply(unit("s")));
        assertFactor("P", kgPerMs, Factor.of(1, 10));
        assertFactor("St", unit("m").pow(2).divide(unit("s")), Factor.of(1, 10_000));
    }

    @Test
    void esuUnits() {
        Factor statC = Factor.ONE.divide(C_SI.multiply(Factor.ofLong(10)));
        Factor statV = C_SI.multiply(pow10(-6));
        assertFactor("statC", "C", statC);
        assertEquals(Factor.of(1, 2_997_924_580L), statC);
        assertFactor("statA", "A", statC);
        assertFactor("statV", "V", statV);
        assertEquals(Factor.decimal("299.792458"), statV);
        Factor statOhm = C_SI.pow(2).multiply(pow10(-5));
        assertEquals(Factor.decimal("299.792458").multiply(Factor.ofLong(2_997_924_580L)), statOhm);
        assertFactor("statΩ", "Ω", statOhm);
        assertFactor("statF", "F", Factor.ONE.divide(statOhm));
        assertFactor("statH", "H", statOhm);
    }

    @Test
    void emuUnits() {
        assertFactor("abA", "A", Factor.ofLong(10));
        assertFactor("abC", "C", Factor.ofLong(10));
        assertFactor("abV", "V", pow10(-8));
        assertFactor("abΩ", "Ω", pow10(-9));
        assertFactor("abF", "F", pow10(9));
        assertFactor("abH", "H", pow10(-9));
        assertFactor("G", "T", pow10(-4));
        assertFactor("Mx", "Wb", pow10(-8));
        Factor oe = Factor.of(BigInteger.valueOf(250), BigInteger.ONE, -1, 1);
        assertFactor("Oe", unit("A").divide(unit("m")), oe);
        assertEquals(BigInteger.valueOf(250), unit("Oe").toSystemConverter().factor().numerator());
        assertEquals(-1, unit("Oe").toSystemConverter().factor().piExponent());
        // 1 Oe = 1000 / (4 pi) A/m and 1 Gb = 10 / (4 pi) A
        assertEquals(Factor.ofLong(1000).divide(Factor.ofLong(4).multiply(Factor.PI)), oe);
        assertFactor("Gb", "A", Factor.ofLong(10).divide(Factor.ofLong(4).multiply(Factor.PI)));
        assertSame(unit("abV").divide(unit("abA")), unit("abΩ"), "abΩ = abV / abA");
        assertSame(unit("abC").divide(unit("abV")), unit("abF"), "abF = abC / abV");
        assertSame(unit("abV").multiply(unit("s")).divide(unit("abA")), unit("abH"), "abH = abV s / abA");
    }

    @Test
    void identities() {
        assertSame(unit("erg"), unit("statC").multiply(unit("statV")), "statC statV = erg");
        assertSame(unit("erg"), unit("abC").multiply(unit("abV")), "abC abV = erg");
        assertSame(unit("Mx"), unit("G").multiply(unit("cm²")), "G cm² = Mx");
        assertSame(unit("erg").divide(unit("s")), unit("statA").multiply(unit("statV")), "statA statV = erg/s");
        assertSame(unit("dyn").multiply(unit("cm")), unit("erg"), "dyn cm = erg");
        assertSame(unit("dyn").divide(unit("cm²")), unit("Ba"), "dyn / cm² = Ba");
        UomUnit<?> ratio = unit("abC").divide(unit("statC"));
        assertEquals(BaseUnits.ONE, ratio.getSystemUnit());
        assertEquals(Factor.ofLong(29_979_245_800L), ratio.toSystemConverter().factor());
        assertEquals(C_SI.multiply(Factor.ofLong(100)), ratio.toSystemConverter().factor());
        assertNotEquals(unit("statC"), unit("abC"));
        assertNotEquals(unit("statA"), unit("abA"));
    }

    @Test
    void franklinIsTheStatcoulomb() {
        assertEquals(unit("statC"), unit("Fr"));
        assertEquals("franklin", unit("Fr").getName());
        assertEquals("ElectricCharge", entry("Fr").quantityType());
    }

    @Test
    void prefixedUnits() {
        Map<String, Object[]> expected = new LinkedHashMap<>();
        expected.put("cP", new Object[] { "centipoise", pow10(-3), null });
        expected.put("mP", new Object[] { "millipoise", pow10(-4), null });
        expected.put("cSt", new Object[] { "centistokes", pow10(-6), null });
        expected.put("mGal", new Object[] { "milligalileo", pow10(-5), "Acceleration" });
        expected.put("µGal", new Object[] { "microgalileo", pow10(-8), "Acceleration" });
        expected.put("mG", new Object[] { "milligauss", pow10(-7), "MagneticFluxDensity" });
        expected.put("µG", new Object[] { "microgauss", pow10(-10), "MagneticFluxDensity" });
        expected.put("nG", new Object[] { "nanogauss", pow10(-13), "MagneticFluxDensity" });
        expected.forEach((symbol, e) -> {
            String base = symbol.substring(1);
            assertFactor(symbol, unit(base), (Factor) e[1]);
            assertEquals(e[0], unit(symbol).getName(), symbol);
            assertEquals(e[2], entry(symbol).quantityType(), symbol);
        });
    }

    @Test
    void types() {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("dyn", "Force"),
                Map.entry("erg", "Energy"),
                Map.entry("Ba", "Pressure"),
                Map.entry("Gal", "Acceleration"),
                Map.entry("cm²", "Area"),
                Map.entry("cm³", "Volume"),
                Map.entry("statC", "ElectricCharge"),
                Map.entry("statA", "ElectricCurrent"),
                Map.entry("statV", "ElectricPotential"),
                Map.entry("statΩ", "ElectricResistance"),
                Map.entry("statF", "ElectricCapacitance"),
                Map.entry("statH", "ElectricInductance"),
                Map.entry("abC", "ElectricCharge"),
                Map.entry("abA", "ElectricCurrent"),
                Map.entry("abV", "ElectricPotential"),
                Map.entry("abΩ", "ElectricResistance"),
                Map.entry("abF", "ElectricCapacitance"),
                Map.entry("abH", "ElectricInductance"),
                Map.entry("G", "MagneticFluxDensity"),
                Map.entry("Mx", "MagneticFlux"),
                Map.entry("Gb", "ElectricCurrent"),
                Map.entry("Fr", "ElectricCharge"));
        expected.forEach((symbol, type) -> assertEquals(type, entry(symbol).quantityType(), symbol));
        for (String untyped : List.of("P", "St", "Oe")) {
            assertNull(entry(untyped).quantityType(), untyped);
            assertNull(entry(untyped).definition().explicitType(), untyped);
        }
    }

    @Test
    void names() {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("dyn", "dyne"),
                Map.entry("erg", "erg"),
                Map.entry("Ba", "barye"),
                Map.entry("Gal", "galileo"),
                Map.entry("cm²", "square centimetre"),
                Map.entry("cm³", "cubic centimetre"),
                Map.entry("P", "poise"),
                Map.entry("St", "stokes"),
                Map.entry("statC", "statcoulomb"),
                Map.entry("statA", "statampere"),
                Map.entry("statV", "statvolt"),
                Map.entry("statΩ", "statohm"),
                Map.entry("statF", "statfarad"),
                Map.entry("statH", "stathenry"),
                Map.entry("abA", "abampere"),
                Map.entry("abC", "abcoulomb"),
                Map.entry("abV", "abvolt"),
                Map.entry("abΩ", "abohm"),
                Map.entry("abF", "abfarad"),
                Map.entry("abH", "abhenry"),
                Map.entry("G", "gauss"),
                Map.entry("Mx", "maxwell"),
                Map.entry("Oe", "oersted"),
                Map.entry("Gb", "gilbert"),
                Map.entry("Fr", "franklin"));
        Map<String, String> actual = new HashMap<>();
        for (Definition d : parsed.get(GAUSSIAN).definitions()) {
            if (FILES.contains(d.source()) && !isPrefixed(d)) {
                actual.put(d.symbol(), d.unit().getName());
            }
        }
        assertEquals(expected, actual);
        assertEquals("Area", entry("cm²").definition().explicitType());
        assertEquals("Volume", entry("cm³").definition().explicitType());
    }

    private static boolean isPrefixed(Definition d) {
        return parsed.get(GAUSSIAN).prefixed().stream().anyMatch(
                p -> p.source().equals(d.source())
                        && p.line() == d.line());
    }

    @Test
    void noAlternateUnits() {
        for (Definition d : parsed.get(GAUSSIAN).definitions()) {
            if (!d.source().equals("si.units")) {
                assertNull(d.alternateOf(), d.symbol());
            }
        }
    }

    @Test
    void symbolsAndConstantsAreUnique() {
        for (String file : FILES) {
            Definitions defs = parsed.get(file);
            List<String> symbols = defs.definitions().stream().map(Definition::symbol).toList();
            assertEquals(symbols.size(), new HashSet<>(symbols).size(), symbols.toString());
            List<String> constants = CatalogBuilder.build(defs, specs).stream().map(CatalogEntry::constantName)
                    .toList();
            assertEquals(constants.size(), new HashSet<>(constants).size(), constants.toString());
            constants.forEach(c -> assertTrue(SourceVersion.isName(c), c));
        }
        Map<String, String> names = Map.of(
                "statΩ",
                "STATOHM",
                "abΩ",
                "ABOHM",
                "cm²",
                "SQUARE_CENTIMETRE",
                "cm³",
                "CUBIC_CENTIMETRE",
                "Gal",
                "GALILEO",
                "Fr",
                "FRANKLIN",
                "µG",
                "MICROGAUSS");
        names.forEach((symbol, constant) -> assertEquals(constant, entry(symbol).constantName(), symbol));
    }

    /** Own definitions, then the members of the extended files, then the {@code @member} units (D3). */
    private static Set<String> members(String file) {
        Definitions defs = parsed.get(file);
        Set<String> result = new HashSet<>();
        defs.definitions().stream().filter(d -> d.source().equals(file)).forEach(d -> result.add(d.symbol()));
        defs.extended().forEach(e -> result.addAll(members(e.file())));
        defs.members().forEach(m -> result.add(m.symbol()));
        return result;
    }

    private static Set<String> own(String file) {
        return parsed.get(file).definitions().stream().filter(d -> d.source().equals(file))
                .map(Definition::symbol).collect(Collectors.toSet());
    }

    @Test
    void systemsAndMembers() {
        assertEquals("CGS", parsed.get(CGS).system());
        assertEquals("ESU", parsed.get(ESU).system());
        assertEquals("EMU", parsed.get(EMU).system());
        assertEquals("Gaussian", parsed.get(GAUSSIAN).system());
        assertEquals(List.of(), parsed.get(CGS).extended());
        assertEquals(List.of(CGS), parsed.get(ESU).extended().stream().map(Extends::file).toList());
        assertEquals(List.of(CGS), parsed.get(EMU).extended().stream().map(Extends::file).toList());
        assertEquals(List.of(ESU), parsed.get(GAUSSIAN).extended().stream().map(Extends::file).toList());
        assertEquals(List.of("cm", "g", "s"), parsed.get(CGS).members().stream().map(Member::symbol).toList());
        assertEquals(List.of(), parsed.get(ESU).members());
        assertEquals(List.of(), parsed.get(EMU).members());
        assertEquals(
                List.of("G", "Mx", "Oe", "Gb"),
                parsed.get(GAUSSIAN).members().stream().map(Member::symbol).toList());
        assertTrue(entries.get(GAUSSIAN).containsKey("abA"), "cgs-emu.units is included");
        assertFalse(entries.get(ESU).containsKey("abA"));
        assertFalse(entries.get(EMU).containsKey("statA"));

        Set<String> cgs = new HashSet<>(own(CGS));
        cgs.addAll(List.of("cm", "g", "s"));
        assertEquals(cgs, members(CGS));
        assertTrue(
                own(CGS).containsAll(
                        List.of(
                                "dyn",
                                "erg",
                                "Ba",
                                "Gal",
                                "cm²",
                                "cm³",
                                "P",
                                "St",
                                "cP",
                                "mP",
                                "cSt",
                                "mGal",
                                "µGal")));
        Set<String> esu = new HashSet<>(cgs);
        esu.addAll(List.of("statC", "statA", "statV", "statΩ", "statF", "statH"));
        assertEquals(esu, members(ESU));
        Set<String> emu = new HashSet<>(cgs);
        emu.addAll(List.of("abA", "abC", "abV", "abΩ", "abF", "abH", "G", "Mx", "Oe", "Gb", "mG", "µG", "nG"));
        assertEquals(emu, members(EMU));
        Set<String> gaussian = new HashSet<>(esu);
        gaussian.addAll(List.of("G", "Mx", "Oe", "Gb", "Fr"));
        assertEquals(gaussian, members(GAUSSIAN));
        assertTrue(members(GAUSSIAN).stream().noneMatch(s -> s.startsWith("ab")));
    }

    /** Own {@code @preferred}, else the first extended system's (D4); SI's fallback is the generator's job. */
    private static Map<String, String> preferred(String file) {
        Map<String, String> result = new HashMap<>();
        Definitions defs = parsed.get(file);
        List<Extends> extended = defs.extended();
        for (int i = extended.size() - 1; i >= 0; i--) {
            result.putAll(preferred(extended.get(i).file()));
        }
        defs.preferred().stream().filter(p -> p.source().equals(file))
                .forEach(p -> result.put(p.quantityType(), p.symbol()));
        return result;
    }

    @Test
    void preferredUnits() {
        Map<String, String> cgs = Map.of(
                "Length",
                "cm",
                "Mass",
                "g",
                "Force",
                "dyn",
                "Energy",
                "erg",
                "Pressure",
                "Ba",
                "Acceleration",
                "Gal",
                "Area",
                "cm²",
                "Volume",
                "cm³");
        assertEquals(cgs, preferred(CGS));
        Map<String, String> esu = new HashMap<>(cgs);
        esu.putAll(
                Map.of(
                        "ElectricCharge",
                        "statC",
                        "ElectricCurrent",
                        "statA",
                        "ElectricPotential",
                        "statV",
                        "ElectricResistance",
                        "statΩ",
                        "ElectricCapacitance",
                        "statF",
                        "ElectricInductance",
                        "statH"));
        assertEquals(esu, preferred(ESU));
        Map<String, String> emu = new HashMap<>(cgs);
        emu.putAll(
                Map.of(
                        "ElectricCharge",
                        "abC",
                        "ElectricCurrent",
                        "abA",
                        "ElectricPotential",
                        "abV",
                        "ElectricResistance",
                        "abΩ",
                        "ElectricCapacitance",
                        "abF",
                        "ElectricInductance",
                        "abH",
                        "MagneticFluxDensity",
                        "G",
                        "MagneticFlux",
                        "Mx"));
        assertEquals(emu, preferred(EMU));
        Map<String, String> gaussian = new HashMap<>(esu);
        gaussian.putAll(Map.of("ElectricCharge", "Fr", "MagneticFluxDensity", "G", "MagneticFlux", "Mx"));
        assertEquals(gaussian, preferred(GAUSSIAN));
        assertEquals(
                Map.of("ElectricCharge", "Fr", "MagneticFluxDensity", "G", "MagneticFlux", "Mx"),
                parsed.get(GAUSSIAN).preferred().stream().filter(p -> p.source().equals(GAUSSIAN))
                        .collect(Collectors.toMap(Preferred::quantityType, Preferred::symbol)));
        for (String file : FILES) {
            for (Preferred p : parsed.get(file).preferred()) {
                assertEquals(p.quantityType(), entries.get(file).get(p.symbol()).quantityType(), p.symbol());
            }
        }
    }
}
