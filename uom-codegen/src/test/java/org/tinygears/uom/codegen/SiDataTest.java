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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.measure.Quantity;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.tinygears.uom.core.Factor;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.core.parse.Definition;
import org.tinygears.uom.core.parse.DefinitionParser;
import org.tinygears.uom.core.parse.Definitions;
import org.tinygears.uom.core.parse.Prefixed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiDataTest {
    private static final Path DATA = Path.of("../data");
    private static final Set<String> TYPES = Set.of(
            "Acceleration",
            "AmountOfSubstance",
            "Angle",
            "Area",
            "CatalyticActivity",
            "Dimensionless",
            "ElectricCapacitance",
            "ElectricCharge",
            "ElectricConductance",
            "ElectricCurrent",
            "ElectricInductance",
            "ElectricPotential",
            "ElectricResistance",
            "Energy",
            "Force",
            "Frequency",
            "Illuminance",
            "Length",
            "LuminousFlux",
            "LuminousIntensity",
            "MagneticFlux",
            "MagneticFluxDensity",
            "Mass",
            "Power",
            "Pressure",
            "RadiationDoseAbsorbed",
            "RadiationDoseEffective",
            "Radioactivity",
            "SolidAngle",
            "Speed",
            "Temperature",
            "Time",
            "Volume");

    private static Definitions defs;
    private static List<QuantityTypeSpec> specs;
    private static final Map<String, Definition> BY_SYMBOL = new HashMap<>();

    @BeforeAll
    static void load() throws IOException {
        try (Reader in = Files.newBufferedReader(DATA.resolve("si.units"), StandardCharsets.UTF_8)) {
            defs = DefinitionParser.parse("si.units", in, null);
        }
        defs.definitions().forEach(d -> BY_SYMBOL.put(d.symbol(), d));
        try (Reader in = Files.newBufferedReader(DATA.resolve("quantity-types.txt"), StandardCharsets.UTF_8)) {
            specs = QuantityTypesParser.parse("quantity-types.txt", in, defs);
        }
    }

    private static UomUnit<?> unit(String symbol) {
        Definition d = BY_SYMBOL.get(symbol);
        assertNotNull(d, symbol);
        return d.unit();
    }

    private static boolean isPrefixed(Definition d) {
        return defs.prefixed().stream().anyMatch(p -> p.source().equals(d.source()) && p.line() == d.line());
    }

    @Test
    void symbolsAreUnique() {
        List<String> symbols = defs.definitions().stream().map(Definition::symbol).toList();
        assertEquals(symbols.size(), new HashSet<>(symbols).size(), symbols.toString());
        List<String> prefixed = defs.definitions().stream().filter(SiDataTest::isPrefixed)
                .map(Definition::symbol).toList();
        int listed = defs.prefixed().stream().mapToInt(p -> p.prefixSymbols().size()).sum();
        assertEquals(listed, prefixed.size());
        assertEquals(listed, new HashSet<>(prefixed).size());
    }

    @Test
    void codePoints() throws IOException {
        String text = Files.readString(DATA.resolve("si.units"), StandardCharsets.UTF_8);
        assertFalse(text.contains("\u03BC"), "Greek mu instead of the micro sign");
        assertFalse(text.contains("\u2126"), "ohm sign instead of Greek capital omega");
        for (String s : List.of("\u00B5m", "\u00B5s", "\u03A9", "k\u03A9", "m\u00B2", "m\u00B3", "\u00B0C")) {
            assertNotNull(BY_SYMBOL.get(s), s);
        }
        assertTrue(defs.prefixed().stream().map(Prefixed::prefixSymbols).anyMatch(l -> l.contains("\u00B5")));
        String types = Files.readString(DATA.resolve("quantity-types.txt"), StandardCharsets.UTF_8);
        assertTrue(
                types.contains("Area m\u00B2") && types.contains("Volume m\u00B3")
                        && types.contains("ElectricResistance \u03A9"),
                types);
    }

    @Test
    void explicitTypesAreStandard() {
        defs.definitions().stream().filter(d -> d.explicitType() != null)
                .forEach(d -> assertTrue(TYPES.contains(d.explicitType()), d.symbol() + " " + d.explicitType()));
    }

    @Test
    void typesFileListsThe33StandardTypes() throws Exception {
        assertEquals(33, TYPES.size());
        assertEquals(TYPES, specs.stream().map(QuantityTypeSpec::name).collect(Collectors.toSet()));
        for (String name : TYPES) {
            Class<?> c = Class.forName("javax.measure.quantity." + name);
            assertTrue(c.isInterface() && Quantity.class.isAssignableFrom(c), name);
        }
    }

    @Test
    void systemUnitsArePairwiseUnequal() {
        assertEquals(33, specs.stream().map(QuantityTypeSpec::systemUnit).collect(Collectors.toSet()).size());
    }

    private static List<QuantityTypeSpec> matching(UomUnit<?> unit) {
        return specs.stream().filter(s -> s.systemUnit().equals(unit.getSystemUnit())).toList();
    }

    @Test
    void explicitTypeMatchesSystemUnit() {
        for (Definition d : defs.definitions()) {
            if (d.explicitType() != null) {
                List<String> names = matching(d.unit()).stream().map(QuantityTypeSpec::name).toList();
                assertEquals(List.of(d.explicitType()), names, d.symbol());
            }
        }
    }

    @Test
    void untypedDefinitionsMatchExactlyOneType() {
        for (Definition d : defs.definitions()) {
            if (d.explicitType() == null) {
                assertEquals(1, matching(d.unit()).size(), d.symbol());
            }
        }
    }

    private static void assertScaled(String symbol, String ref, Factor factor) {
        UomUnit<?> u = unit(symbol);
        assertEquals(unit(ref).scaledBy(factor), u, symbol);
        assertEquals(unit(ref).toSystemConverter().factor().multiply(factor), u.toSystemConverter().factor(), symbol);
        assertEquals(Factor.ZERO, u.toSystemConverter().offset(), symbol);
    }

    @Test
    void exactDefinitions() {
        assertScaled("eV", "J", Factor.decimal("1.602176634e-19"));
        assertEquals(Factor.decimal("1.602176634e-19"), unit("eV").toSystemConverter().factor());
        assertScaled("deg", "rad", Factor.PI.divide(Factor.ofLong(180)));
        assertScaled("arcmin", "rad", Factor.PI.divide(Factor.ofLong(10800)));
        assertScaled("arcsec", "rad", Factor.PI.divide(Factor.ofLong(648000)));
        assertScaled("au", "m", Factor.ofLong(149597870700L));
        assertScaled("L", "m\u00B3", Factor.of(1, 1000));
        assertScaled("ha", "m\u00B2", Factor.ofLong(10000));
        assertScaled("t", "kg", Factor.ofLong(1000));
        assertScaled("g", "kg", Factor.of(1, 1000));
        assertScaled("d", "s", Factor.ofLong(86400));
        assertScaled("h", "s", Factor.ofLong(3600));
        assertScaled("min", "s", Factor.ofLong(60));
        assertScaled("Da", "kg", Factor.decimal("1.66053906660e-27"));
        UomUnit<?> celsius = unit("\u00B0C");
        assertEquals(unit("K").getSystemUnit(), celsius.getSystemUnit());
        assertEquals(Factor.ONE, celsius.toSystemConverter().factor());
        assertEquals(Factor.decimal("273.15"), celsius.toSystemConverter().offset());
    }

    @Test
    void prefixedExpansions() {
        assertScaled("km", "m", Factor.ofLong(1000));
        assertScaled("\u00B5m", "m", Factor.of(1, 1_000_000));
        assertScaled("MHz", "Hz", Factor.ofLong(1_000_000));
        assertScaled("kPa", "Pa", Factor.ofLong(1000));
        assertScaled("hPa", "Pa", Factor.ofLong(100));
        assertScaled("mmol", "mol", Factor.of(1, 1000));
        assertScaled("k\u03A9", "\u03A9", Factor.ofLong(1000));
        assertScaled("mS", "S", Factor.of(1, 1000));
        assertScaled("ms", "s", Factor.of(1, 1000));
        assertNotEquals(unit("mS"), unit("ms"));
        assertScaled("MJ", "J", Factor.ofLong(1_000_000));
        assertScaled("mJ", "J", Factor.of(1, 1000));
        assertScaled("dL", "L", Factor.of(1, 10));
        assertScaled("keV", "eV", Factor.ofLong(1000));
        assertScaled("mSv", "Sv", Factor.of(1, 1000));
        assertScaled("mK", "K", Factor.of(1, 1000));
        assertScaled("mcd", "cd", Factor.of(1, 1000));
        assertScaled("kDa", "Da", Factor.ofLong(1000));
        assertScaled("Mt", "t", Factor.ofLong(1_000_000));
    }

    @Test
    void prefixedSymbolsArePinned() {
        List<String> expected = List.of(
                "GBq",
                "GHz",
                "GJ",
                "GPa",
                "GW",
                "GeV",
                "Gt",
                "MBq",
                "MDa",
                "MHz",
                "MJ",
                "MPa",
                "MW",
                "MeV",
                "Mt",
                "MΩ",
                "THz",
                "TeV",
                "cL",
                "cm",
                "dL",
                "hPa",
                "kBq",
                "kDa",
                "kHz",
                "kJ",
                "kN",
                "kPa",
                "kV",
                "kW",
                "kcd",
                "keV",
                "kkat",
                "klm",
                "klx",
                "km",
                "kt",
                "kΩ",
                "mA",
                "mC",
                "mF",
                "mGy",
                "mH",
                "mJ",
                "mK",
                "mL",
                "mN",
                "mS",
                "mSv",
                "mT",
                "mV",
                "mW",
                "mWb",
                "mcd",
                "mg",
                "mkat",
                "mlm",
                "mm",
                "mmol",
                "mrad",
                "ms",
                "msr",
                "nF",
                "nH",
                "nK",
                "nT",
                "nkat",
                "nm",
                "ns",
                "pF",
                "pm",
                "ps",
                "µA",
                "µC",
                "µF",
                "µH",
                "µK",
                "µSv",
                "µT",
                "µV",
                "µg",
                "µkat",
                "µm",
                "µs");
        List<String> actual = defs.definitions().stream().filter(SiDataTest::isPrefixed).map(Definition::symbol)
                .sorted().toList();
        assertEquals(84, expected.size());
        assertEquals(expected, actual);
    }

    @Test
    void alternatesAreDistinct() {
        assertNotEquals(unit("Gy"), unit("Sv"));
        assertNotEquals(unit("Hz"), unit("Bq"));
    }
}
