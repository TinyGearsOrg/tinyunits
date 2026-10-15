/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.quantity;

import java.util.Set;
import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.spi.QuantityFactory;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.core.BaseUnits;

import static org.junit.jupiter.api.Assertions.*;

class QuantityTypesRegistryTest {

    private static final Set<Class<?>> TYPES = Set.of(
            javax.measure.quantity.Acceleration.class,
            javax.measure.quantity.AmountOfSubstance.class,
            javax.measure.quantity.Angle.class,
            javax.measure.quantity.Area.class,
            javax.measure.quantity.CatalyticActivity.class,
            javax.measure.quantity.Dimensionless.class,
            javax.measure.quantity.ElectricCapacitance.class,
            javax.measure.quantity.ElectricCharge.class,
            javax.measure.quantity.ElectricConductance.class,
            javax.measure.quantity.ElectricCurrent.class,
            javax.measure.quantity.ElectricInductance.class,
            javax.measure.quantity.ElectricPotential.class,
            javax.measure.quantity.ElectricResistance.class,
            javax.measure.quantity.Energy.class,
            javax.measure.quantity.Force.class,
            javax.measure.quantity.Frequency.class,
            javax.measure.quantity.Illuminance.class,
            javax.measure.quantity.Length.class,
            javax.measure.quantity.LuminousFlux.class,
            javax.measure.quantity.LuminousIntensity.class,
            javax.measure.quantity.MagneticFlux.class,
            javax.measure.quantity.MagneticFluxDensity.class,
            javax.measure.quantity.Mass.class,
            javax.measure.quantity.Power.class,
            javax.measure.quantity.Pressure.class,
            javax.measure.quantity.RadiationDoseAbsorbed.class,
            javax.measure.quantity.RadiationDoseEffective.class,
            javax.measure.quantity.Radioactivity.class,
            javax.measure.quantity.SolidAngle.class,
            javax.measure.quantity.Speed.class,
            javax.measure.quantity.Temperature.class,
            javax.measure.quantity.Time.class,
            javax.measure.quantity.Volume.class);

    /** Not a standard type, so no factory exists for it. */
    interface Probe extends Quantity<Probe> {
    }

    @Test
    void registry() throws Exception {
        assertEquals(TYPES, QuantityTypes.types());
        assertEquals(BaseUnits.METRE, QuantityTypes.factory(javax.measure.quantity.Length.class).getSystemUnit());
        var e = assertThrows(
                IllegalArgumentException.class,
                () -> QuantityTypes.factory(Probe.class));
        assertEquals("no quantity factory for " + Probe.class.getName(), e.getMessage());
        assertThrows(UnsupportedOperationException.class, () -> QuantityTypes.types().clear());
    }

    @Test
    void everySystemUnitIsIdentityAndTypeable() throws Exception {
        for (Class<?> type : QuantityTypes.types()) {
            check(type);
            Class<?> typed = Class.forName("org.tinygears.uom.quantity." + type.getSimpleName());
            assertTrue(type.isAssignableFrom(typed), typed.getName());
            assertTrue(Comparable.class.isAssignableFrom(typed), typed.getName());
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void check(Class<?> type) {
        QuantityFactory f = QuantityTypes.factory((Class) type);
        Unit unit = f.getSystemUnit();
        assertTrue(unit.getConverterTo(unit).isIdentity(), type.getName());
        assertSame(unit, unit.asType((Class) type));
    }
}
