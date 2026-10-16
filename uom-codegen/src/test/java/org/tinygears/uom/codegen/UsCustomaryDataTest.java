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
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.lang.model.SourceVersion;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.tinygears.uom.core.Factor;
import org.tinygears.uom.core.UomConverter;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.core.parse.Definition;
import org.tinygears.uom.core.parse.DefinitionParser;
import org.tinygears.uom.core.parse.Definitions;
import org.tinygears.uom.core.parse.Extends;
import org.tinygears.uom.core.parse.Preferred;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsCustomaryDataTest {
    private static final Path DATA = Path.of("../data");
    private static final String US = "us-customary.units";
    private static final String UK = "imperial.units";

    // Defining constants (NIST SP 811 appendix B, NIST Handbook 44 appendix C), independent of the data files.
    private static final BigDecimal INCH = new BigDecimal("0.0254");
    private static final BigDecimal FOOT = INCH.multiply(BigDecimal.valueOf(12));
    private static final BigDecimal POUND = new BigDecimal("0.45359237");
    private static final BigDecimal LBF = POUND.multiply(new BigDecimal("9.80665"));
    private static final BigDecimal GAL_US = INCH.pow(3).multiply(BigDecimal.valueOf(231));
    private static final BigDecimal GAL_UK = new BigDecimal("0.00454609");
    private static final Factor SURVEY_FOOT = Factor.of(1200, 3937);

    private static Definitions us;
    private static Definitions uk;
    private static List<QuantityTypeSpec> specs;
    private static Map<String, CatalogEntry> usEntries;
    private static Map<String, CatalogEntry> ukEntries;

    @BeforeAll
    static void load() throws IOException {
        us = parse(US);
        uk = parse(UK);
        try (Reader in = Files.newBufferedReader(DATA.resolve("quantity-types.txt"), StandardCharsets.UTF_8)) {
            specs = QuantityTypesParser.parse("quantity-types.txt", in, us);
        }
        usEntries = bySymbol(CatalogBuilder.build(us, specs));
        ukEntries = bySymbol(CatalogBuilder.build(uk, specs));
    }

    private static Definitions parse(String file) throws IOException {
        DefinitionParser.Resolver resolver = name -> Files
                .newBufferedReader(DATA.resolve(name), StandardCharsets.UTF_8);
        try (Reader in = resolver.open(file)) {
            return DefinitionParser.parse(file, in, resolver);
        }
    }

    private static Map<String, CatalogEntry> bySymbol(List<CatalogEntry> entries) {
        return entries.stream().collect(Collectors.toMap(e -> e.definition().symbol(), Function.identity()));
    }

    private static UomUnit<?> unit(String symbol) {
        CatalogEntry e = ukEntries.get(symbol);
        assertNotNull(e, symbol);
        return e.definition().unit();
    }

    private static Factor exact(BigDecimal value) {
        return Factor.decimal(value.toPlainString());
    }

    private static BigDecimal times(BigDecimal value, long n) {
        return value.multiply(BigDecimal.valueOf(n));
    }

    /** {@code symbol} is {@code factor} times the system unit, which is the SI unit {@code si}. */
    private static void assertFactor(String symbol, String si, Factor factor) {
        UomUnit<?> u = unit(symbol);
        assertEquals(unit(si), u.getSystemUnit(), symbol);
        assertEquals(factor, u.toSystemConverter().factor(), symbol);
        assertEquals(Factor.ZERO, u.toSystemConverter().offset(), symbol);
    }

    private static void assertFactor(String symbol, String si, BigDecimal factor) {
        assertFactor(symbol, si, exact(factor));
    }

    private static Factor toKelvin(String symbol, String value) {
        UomConverter c = unit(symbol).toSystemConverter();
        return Factor.decimal(value).multiply(c.factor()).add(c.offset());
    }

    @Test
    void lengths() {
        assertFactor("in", "m", INCH);
        assertFactor("ft", "m", FOOT);
        assertFactor("yd", "m", times(FOOT, 3));
        assertFactor("mi", "m", times(FOOT, 5280));
        assertFactor("nmi", "m", BigDecimal.valueOf(1852));
        assertFactor("fathom", "m", times(FOOT, 6));
        assertFactor("ft_US", "m", SURVEY_FOOT);
        assertFactor("mi_US", "m", SURVEY_FOOT.multiply(Factor.ofLong(5280)));
        assertEquals(Factor.of(6336000, 3937), unit("mi_US").toSystemConverter().factor());
    }

    @Test
    void surveyFootDiffersFromInternationalFoot() {
        assertNotEquals(unit("ft"), unit("ft_US"));
        Factor ratio = unit("ft_US").toSystemConverter().factor().divide(unit("ft").toSystemConverter().factor());
        // (1200/3937) / (3048/10000) = 12000000 / 11999976
        assertEquals(Factor.of(12_000_000, 11_999_976), ratio);
        assertEquals(Factor.of(1200, 3937).divide(Factor.decimal("0.3048")), ratio);
    }

    @Test
    void areasAndVolumes() {
        assertFactor("in²", "m²", INCH.pow(2));
        assertFactor("ft²", "m²", FOOT.pow(2));
        assertFactor("ac", "m²", times(FOOT.pow(2), 43560));
        assertFactor("ac_US", "m²", SURVEY_FOOT.pow(2).multiply(Factor.ofLong(43560)));
        assertFactor("in³", "m³", INCH.pow(3));
        assertFactor("ft³", "m³", FOOT.pow(3));
        assertFactor("gal_US", "m³", GAL_US);
        assertEquals(Factor.decimal("3.785411784e-3"), unit("gal_US").toSystemConverter().factor());
        Factor gal = exact(GAL_US);
        assertFactor("qt_US", "m³", gal.divide(Factor.ofLong(4)));
        assertFactor("pt_US", "m³", gal.divide(Factor.ofLong(8)));
        assertFactor("cup_US", "m³", gal.divide(Factor.ofLong(16)));
        assertFactor("floz_US", "m³", gal.divide(Factor.ofLong(128)));
        assertFactor("tbsp_US", "m³", gal.divide(Factor.ofLong(256)));
        assertFactor("tsp_US", "m³", gal.divide(Factor.ofLong(768)));
    }

    @Test
    void imperialVolumes() {
        assertFactor("gal_UK", "m³", GAL_UK);
        Factor gal = exact(GAL_UK);
        assertFactor("qt_UK", "m³", gal.divide(Factor.ofLong(4)));
        assertFactor("pt_UK", "m³", gal.divide(Factor.ofLong(8)));
        assertFactor("gi_UK", "m³", gal.divide(Factor.ofLong(32)));
        assertFactor("floz_UK", "m³", gal.divide(Factor.ofLong(160)));
        assertNotEquals(unit("gal_US"), unit("gal_UK"));
        assertNotEquals(unit("floz_US"), unit("floz_UK"));
    }

    @Test
    void masses() {
        Factor lb = exact(POUND);
        assertFactor("lb", "kg", POUND);
        assertFactor("oz", "kg", lb.divide(Factor.ofLong(16)));
        assertFactor("gr", "kg", lb.divide(Factor.ofLong(7000)));
        assertFactor("ton_US", "kg", times(POUND, 2000));
        assertFactor("st", "kg", times(POUND, 14));
        assertFactor("ton_UK", "kg", times(POUND, 2240));
    }

    @Test
    void mechanics() {
        assertFactor("lbf", "N", LBF);
        assertEquals(Factor.decimal("4.4482216152605"), unit("lbf").toSystemConverter().factor());
        Factor psi = exact(LBF).divide(exact(INCH.pow(2)));
        assertFactor("psi", "Pa", psi);
        assertEquals(new BigDecimal("6894.757293168"), psi.decimalValue(new MathContext(13)));
        assertFactor("hp", "W", times(FOOT.multiply(LBF), 550));
        assertEquals(Factor.decimal("745.69987158227022"), unit("hp").toSystemConverter().factor());
        assertFactor("hp_M", "W", new BigDecimal("735.49875"));
        assertFactor("BTU_IT", "J", new BigDecimal("1055.05585262"));
        Factor btuTh = exact(new BigDecimal("453.59237").multiply(new BigDecimal("4.184"))).multiply(Factor.of(5, 9));
        assertFactor("BTU_th", "J", btuTh);
        assertEquals(
                new BigDecimal("1054.350264488889"),
                unit("BTU_th").toSystemConverter().factor().decimalValue(new MathContext(16)));
        UomUnit<?> mps = unit("m").divide(unit("s"));
        assertEquals(mps, unit("mph").getSystemUnit());
        assertEquals(exact(times(FOOT, 5280)).divide(Factor.ofLong(3600)), unit("mph").toSystemConverter().factor());
        assertEquals(mps, unit("kn").getSystemUnit());
        assertEquals(Factor.of(1852, 3600), unit("kn").toSystemConverter().factor());
    }

    @Test
    void temperatures() {
        assertEquals(unit("K"), unit("°F").getSystemUnit());
        assertEquals(Factor.of(5, 9), unit("°F").toSystemConverter().factor());
        assertEquals(Factor.decimal("459.67").multiply(Factor.of(5, 9)), unit("°F").toSystemConverter().offset());
        assertEquals(Factor.decimal("373.15"), toKelvin("°F", "212"));
        assertEquals(toKelvin("°C", "-40"), toKelvin("°F", "-40"));
        assertEquals(Factor.decimal("273.15"), toKelvin("°F", "32"));
        assertFactor("°R", "K", Factor.of(5, 9));
        assertEquals(Factor.decimal("273.15"), toKelvin("°R", "491.67"));
    }

    @Test
    void types() {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("ft", "Length"),
                Map.entry("ft_US", "Length"),
                Map.entry("nmi", "Length"),
                Map.entry("ac", "Area"),
                Map.entry("ft²", "Area"),
                Map.entry("gal_US", "Volume"),
                Map.entry("gal_UK", "Volume"),
                Map.entry("lb", "Mass"),
                Map.entry("st", "Mass"),
                Map.entry("lbf", "Force"),
                Map.entry("psi", "Pressure"),
                Map.entry("hp", "Power"),
                Map.entry("hp_M", "Power"),
                Map.entry("BTU_IT", "Energy"),
                Map.entry("BTU_th", "Energy"),
                Map.entry("mph", "Speed"),
                Map.entry("kn", "Speed"),
                Map.entry("°F", "Temperature"),
                Map.entry("°R", "Temperature"));
        expected.forEach((symbol, type) -> assertEquals(type, ukEntries.get(symbol).quantityType(), symbol));
        for (Definition d : uk.definitions()) {
            if (!d.source().equals("si.units")) {
                assertNotNull(ukEntries.get(d.symbol()).quantityType(), d.symbol());
            }
        }
    }

    @Test
    void symbolsAndConstantsAreUnique() {
        for (Definitions defs : List.of(us, uk)) {
            List<String> symbols = defs.definitions().stream().map(Definition::symbol).toList();
            assertEquals(symbols.size(), new HashSet<>(symbols).size(), symbols.toString());
            List<String> constants = CatalogBuilder.build(defs, specs).stream().map(CatalogEntry::constantName)
                    .toList();
            assertEquals(constants.size(), new HashSet<>(constants).size(), constants.toString());
            constants.forEach(c -> assertTrue(SourceVersion.isName(c), c));
        }
        assertEquals("GALLON_US", ukEntries.get("gal_US").constantName());
        assertEquals("GALLON_UK", ukEntries.get("gal_UK").constantName());
        assertEquals("FOOT_US", ukEntries.get("ft_US").constantName());
    }

    private static Map<String, String> preferred(Definitions defs, String source) {
        return defs.preferred().stream().filter(p -> p.source().equals(source))
                .collect(Collectors.toMap(Preferred::quantityType, Preferred::symbol));
    }

    @Test
    void systemsAndPreferredUnits() {
        assertEquals("USCustomary", us.system());
        assertEquals(List.of(), us.extended());
        assertEquals("Imperial", uk.system());
        assertEquals(List.of("us-customary.units"), uk.extended().stream().map(Extends::file).toList());
        assertEquals(UK, uk.extended().get(0).source());
        Map<String, String> usPreferred = Map.of(
                "Length",
                "ft",
                "Area",
                "ft²",
                "Volume",
                "gal_US",
                "Mass",
                "lb",
                "Force",
                "lbf",
                "Pressure",
                "psi",
                "Energy",
                "BTU_IT",
                "Power",
                "hp",
                "Speed",
                "mph",
                "Temperature",
                "°F");
        assertEquals(usPreferred, preferred(us, US));
        assertEquals(usPreferred, preferred(uk, US));
        assertEquals(Map.of("Volume", "gal_UK"), preferred(uk, UK));
        assertNull(preferred(uk, UK).get("Length"));
        for (Preferred p : uk.preferred()) {
            assertEquals(p.quantityType(), ukEntries.get(p.symbol()).quantityType(), p.symbol());
        }
    }
}
