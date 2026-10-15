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
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.measure.Quantity;
import javax.measure.quantity.*;
import javax.measure.spi.QuantityFactory;
import javax.measure.spi.ServiceProvider;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.core.quantity.AbstractDecimalQuantity;
import org.tinygears.uom.quantity.Length;
import org.tinygears.uom.quantity.Time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UomServiceProviderTest {
    interface Unsupported extends Quantity<Unsupported> {
    }

    private static final List<Class<? extends Quantity<?>>> TYPES = List.of(
            Acceleration.class,
            AmountOfSubstance.class,
            Angle.class,
            Area.class,
            CatalyticActivity.class,
            Dimensionless.class,
            ElectricCapacitance.class,
            ElectricCharge.class,
            ElectricConductance.class,
            ElectricCurrent.class,
            ElectricInductance.class,
            ElectricPotential.class,
            ElectricResistance.class,
            Energy.class,
            Force.class,
            Frequency.class,
            Illuminance.class,
            javax.measure.quantity.Length.class,
            LuminousFlux.class,
            LuminousIntensity.class,
            MagneticFlux.class,
            MagneticFluxDensity.class,
            Mass.class,
            Power.class,
            Pressure.class,
            RadiationDoseAbsorbed.class,
            RadiationDoseEffective.class,
            Radioactivity.class,
            SolidAngle.class,
            Speed.class,
            Temperature.class,
            javax.measure.quantity.Time.class,
            Volume.class);

    @Test
    void currentIsOursAndListedOnce() {
        assertInstanceOf(UomServiceProvider.class, ServiceProvider.current());
        assertEquals(1, ServiceProvider.available().stream().filter(p -> p instanceof UomServiceProvider).count());
        assertTrue(ServiceProvider.available().stream().anyMatch(p -> p instanceof LowPriorityProvider));
        assertEquals(100, ServiceProvider.current().getPriority());
    }

    @Test
    void servicesAreShared() {
        ServiceProvider p = ServiceProvider.current();
        assertSame(p.getSystemOfUnitsService(), p.getSystemOfUnitsService());
        assertSame(p.getFormatService(), p.getFormatService());
    }

    @Test
    void serviceFileNamesTheProvider() throws IOException {
        Path file = Path.of("src/main/resources/META-INF/services/javax.measure.spi.ServiceProvider");
        assertEquals(UomServiceProvider.class.getName(), Files.readString(file).strip());
    }

    @Test
    void factoriesOfAllTypes() {
        assertEquals(33, TYPES.size());
        for (Class<? extends Quantity<?>> type : TYPES) {
            check(type);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void check(Class type) {
        QuantityFactory f = ServiceProvider.current().getQuantityFactory(type);
        assertNotNull(f, type.getName());
        for (Quantity.Scale scale : Quantity.Scale.values()) {
            Quantity<?> q = f.create(10d, f.getSystemUnit(), scale);
            assertSame(scale, q.getScale(), type.getName());
            assertTrue(type.isInstance(q), type.getName());
        }
    }

    @Test
    void unsupportedType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ServiceProvider.current().getQuantityFactory(Unsupported.class));
    }

    @Test
    void speedEndToEnd() {
        Quantity<Speed> v = Length.of(36, SI.KILOMETRE).divide(Time.of(1, SI.HOUR)).asType(Speed.class);
        assertEquals(
                10.0,
                v.to(UomUnitFormat.standard().parse("m/s").asType(Speed.class)).getValue().doubleValue(),
                1e-12);
    }

    @Test
    void factoryCreatesADecimalForABigDecimal() {
        Quantity<javax.measure.quantity.Length> q = ServiceProvider.current()
                .getQuantityFactory(javax.measure.quantity.Length.class).create(new BigDecimal("1.5"), SI.METRE);
        assertInstanceOf(Length.class, q);
        assertInstanceOf(AbstractDecimalQuantity.class, q);
        assertEquals(new BigDecimal("1.5"), q.getValue());
        assertSame(SI.METRE, q.getUnit());
    }

    @Test
    void decimalSpeedEndToEnd() {
        Quantity<Speed> v = Length.of(new BigDecimal("36"), SI.KILOMETRE).divide(Time.of(new BigDecimal("1"), SI.HOUR))
                .asType(Speed.class);
        Quantity<Speed> inMetresPerSecond = v.to(UomUnitFormat.standard().parse("m/s").asType(Speed.class));
        assertInstanceOf(AbstractDecimalQuantity.class, inMetresPerSecond);
        assertEquals(0, new BigDecimal("10").compareTo((BigDecimal) inMetresPerSecond.getValue()));
    }
}
