/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.systems;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.measure.MetricPrefix;
import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Length;
import javax.measure.quantity.Temperature;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.core.parse.Definition;
import org.tinygears.uom.core.parse.DefinitionParser;
import org.tinygears.uom.core.parse.Definitions;
import org.tinygears.uom.core.parse.Prefixed;
import org.tinygears.uom.quantity.QuantityTypes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The generated catalog agrees with {@code data/si.units} (F17). */
class SiCatalogTest {
    private static final List<String> TYPES = List.of(
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

    @BeforeAll
    static void load() throws IOException {
        try (Reader in = Files.newBufferedReader(Path.of("../data/si.units"), StandardCharsets.UTF_8)) {
            defs = DefinitionParser.parse("si.units", in, null);
        }
    }

    @Test
    void everyDefinitionIsInTheCatalog() {
        for (Definition d : defs.definitions()) {
            UomUnit<?> u = SI.SYSTEM.lookup(d.symbol()).orElseThrow(() -> new AssertionError(d.symbol()));
            assertEquals(d.unit(), u, d.symbol());
            assertEquals(d.symbol(), u.getSymbol());
            assertEquals(d.name(), u.getName());
        }
        assertEquals(defs.definitions().size(), SI.UNITS.size());
        List<String> symbols = SI.UNITS.stream().map(UomUnit::getSymbol).toList();
        assertEquals(symbols.size(), new HashSet<>(symbols).size());
    }

    @Test
    void preferredUnits() throws Exception {
        assertEquals("m", SI.SYSTEM.getUnit(Length.class).getSymbol());
        assertEquals(BaseUnits.METRE, SI.SYSTEM.getUnit(Length.class));
        assertEquals(BaseUnits.KELVIN, SI.SYSTEM.getUnit(Temperature.class));
        assertEquals(33, TYPES.size());
        for (String name : TYPES) {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            Class<Q> type = (Class) Class.forName("javax.measure.quantity." + name);
            Unit<?> preferred = SI.SYSTEM.getUnit(type);
            assertNotNull(preferred, name);
            assertEquals(QuantityTypes.factory(type).getSystemUnit().getSymbol(), preferred.getSymbol(), name);
        }
    }

    // Lets the loop above name a type variable for getUnit(Class<Q>).
    private interface Q extends Quantity<Q> {
    }

    @Test
    void prefixedConstants() {
        assertEquals(SI.METRE.prefix(MetricPrefix.KILO), SI.KILOMETRE);
        assertEquals(SI.METRE.prefix(MetricPrefix.MICRO), SI.MICROMETRE);
        assertEquals(SI.PASCAL.prefix(MetricPrefix.KILO), SI.KILOPASCAL);
        assertEquals(SI.ELECTRONVOLT.prefix(MetricPrefix.MEGA), SI.MEGAELECTRONVOLT);
        assertEquals(SI.HERTZ.prefix(MetricPrefix.KILO), SI.KILOHERTZ);
        assertEquals(SI.SECOND.multiply(60), SI.MINUTE);
        assertEquals(SI.CUBIC_METRE.divide(1000), SI.LITRE);
        assertEquals(Math.PI, SI.DEGREE.getConverterTo(SI.RADIAN).convert(180.0), 1e-12);
        assertEquals("µm", SI.MICROMETRE.getSymbol());
    }

    @Test
    void degreeCelsius() {
        assertEquals("degree Celsius", SI.DEGREE_CELSIUS.getName());
        assertEquals(373.15, SI.DEGREE_CELSIUS.getConverterTo(SI.KELVIN).convert(100.0), 1e-12);
    }

    @Test
    void prefixable() {
        Set<String> expected = defs.prefixed().stream().map(Prefixed::unitSymbol).collect(Collectors.toSet());
        assertEquals(expected, SI.PREFIXABLE);
        assertTrue(SI.SYSTEM.isPrefixable("m"));
        expected.forEach(s -> assertTrue(SI.SYSTEM.isPrefixable(s), s));
    }

    @Test
    void constantsArePublicStaticFinal() throws Exception {
        // The system units without a definition (m/s) are constants of the class, not units of the system.
        Set<String> withoutDefinition = Set.of("METRE_PER_SECOND", "METRE_PER_SECOND_SQUARED", "ONE");
        int units = 0;
        for (Field f : SI.class.getDeclaredFields()) {
            int m = f.getModifiers();
            assertTrue(Modifier.isPublic(m) && Modifier.isStatic(m) && Modifier.isFinal(m), f.getName());
            if (f.getType() == UomUnit.class && !withoutDefinition.contains(f.getName())) {
                units++;
                assertSame(SI.SYSTEM.getUnit(((UomUnit<?>) f.get(null)).getSymbol()), f.get(null), f.getName());
            }
        }
        assertEquals(SI.UNITS.size(), units);
    }
}
