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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.tinygears.uom.engine.parse.Definition;
import org.tinygears.uom.engine.parse.DefinitionParser;
import org.tinygears.uom.engine.parse.Definitions;
import org.tinygears.uom.jsr.UomDimension;
import org.tinygears.uom.jsr.UomUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Dimensions derived by hand from the SI Brochure, checked against the parsed data. */
class SiDimensionTest {
    private static final Path DATA = Path.of("../data");

    // Alternates of 1 (rad, sr) carry no dimension.
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("Acceleration", "L1 T-2"),
            Map.entry("AmountOfSubstance", "N1"),
            Map.entry("Angle", ""),
            Map.entry("Area", "L2"),
            Map.entry("CatalyticActivity", "N1 T-1"),
            Map.entry("Dimensionless", ""),
            Map.entry("ElectricCapacitance", "L-2 M-1 T4 I2"),
            Map.entry("ElectricCharge", "T1 I1"),
            Map.entry("ElectricConductance", "L-2 M-1 T3 I2"),
            Map.entry("ElectricCurrent", "I1"),
            Map.entry("ElectricInductance", "L2 M1 T-2 I-2"),
            Map.entry("ElectricPotential", "L2 M1 T-3 I-1"),
            Map.entry("ElectricResistance", "L2 M1 T-3 I-2"),
            Map.entry("Energy", "L2 M1 T-2"),
            Map.entry("Force", "L1 M1 T-2"),
            Map.entry("Frequency", "T-1"),
            Map.entry("Illuminance", "L-2 J1"),
            Map.entry("Length", "L1"),
            Map.entry("LuminousFlux", "J1"),
            Map.entry("LuminousIntensity", "J1"),
            Map.entry("MagneticFlux", "L2 M1 T-2 I-1"),
            Map.entry("MagneticFluxDensity", "M1 T-2 I-1"),
            Map.entry("Mass", "M1"),
            Map.entry("Power", "L2 M1 T-3"),
            Map.entry("Pressure", "L-1 M1 T-2"),
            Map.entry("RadiationDoseAbsorbed", "L2 T-2"),
            Map.entry("RadiationDoseEffective", "L2 T-2"),
            Map.entry("Radioactivity", "T-1"),
            Map.entry("SolidAngle", ""),
            Map.entry("Speed", "L1 T-1"),
            Map.entry("Temperature", "Θ1"),
            Map.entry("Time", "T1"),
            Map.entry("Volume", "L3"));

    private static final Map<String, String> UNITS = Map.ofEntries(
            Map.entry("N", "L1 M1 T-2"),
            Map.entry("Pa", "L-1 M1 T-2"),
            Map.entry("J", "L2 M1 T-2"),
            Map.entry("W", "L2 M1 T-3"),
            Map.entry("C", "T1 I1"),
            Map.entry("V", "L2 M1 T-3 I-1"),
            Map.entry("F", "L-2 M-1 T4 I2"),
            Map.entry("Ω", "L2 M1 T-3 I-2"),
            Map.entry("S", "L-2 M-1 T3 I2"),
            Map.entry("Wb", "L2 M1 T-2 I-1"),
            Map.entry("T", "M1 T-2 I-1"),
            Map.entry("H", "L2 M1 T-2 I-2"),
            Map.entry("lm", "J1"),
            Map.entry("lx", "L-2 J1"),
            Map.entry("kat", "N1 T-1"),
            Map.entry("Gy", "L2 T-2"),
            Map.entry("Sv", "L2 T-2"));

    private static Definitions defs;
    private static List<QuantityTypeSpec> specs;

    @BeforeAll
    static void load() throws IOException {
        try (Reader in = Files.newBufferedReader(DATA.resolve("si.units"), StandardCharsets.UTF_8)) {
            defs = DefinitionParser.parse("si.units", in, null);
        }
        try (Reader in = Files.newBufferedReader(DATA.resolve("quantity-types.txt"), StandardCharsets.UTF_8)) {
            specs = QuantityTypesParser.parse("quantity-types.txt", in, defs);
        }
    }

    /** Parses {@code "L1 M1 T-2"}; the letters are L M T I Θ N J. */
    private static UomDimension dimension(String text) {
        int[] e = new int[7];
        for (String term : text.split(" ")) {
            if (!term.isEmpty()) {
                e["LMTIΘNJ".indexOf(term.charAt(0))] = Integer.parseInt(term.substring(1));
            }
        }
        return new UomDimension(e[0], e[1], e[2], e[3], e[4], e[5], e[6]);
    }

    @Test
    void parserOfTheTable() {
        assertEquals(new UomDimension(-2, -1, 4, 2, 0, 0, 0), dimension("L-2 M-1 T4 I2"));
        assertEquals(new UomDimension(0, 0, 0, 0, 1, 0, 0), dimension("Θ1"));
        assertEquals(new UomDimension(-2, 0, 0, 0, 0, 0, 1), dimension("L-2 J1"));
        assertEquals(new UomDimension(0, 0, -1, 0, 0, 1, 0), dimension("N1 T-1"));
        assertEquals(UomDimension.NONE, dimension(""));
    }

    @Test
    void systemUnitsOfTheTypes() {
        assertEquals(TYPES.keySet(), specs.stream().map(QuantityTypeSpec::name).collect(Collectors.toSet()));
        for (QuantityTypeSpec s : specs) {
            assertEquals(dimension(TYPES.get(s.name())), UomUnit.of(s.systemUnit()).getDimension(), s.name());
        }
    }

    @Test
    void namedDerivedUnits() {
        Map<String, Definition> bySymbol = defs.definitions().stream()
                .collect(Collectors.toMap(Definition::symbol, d -> d));
        UNITS.forEach((symbol, dim) -> {
            Definition d = bySymbol.get(symbol);
            assertNotNull(d, symbol);
            assertEquals(dimension(dim), UomUnit.of(d.unit()).getDimension(), symbol);
        });
    }
}
