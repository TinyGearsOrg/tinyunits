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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.quantity.AccelerationFactory;
import org.tinygears.uom.quantity.DimensionlessFactory;
import org.tinygears.uom.quantity.QuantityTypes;
import org.tinygears.uom.quantity.SpeedFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The system unit of every quantity type is a constant of SI: the same instance as the factory's. */
class SiSystemUnitsTest {

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void everyQuantityTypeHasItsSystemUnitAsAConstantOfSi() throws Exception {
        List<String> missing = new ArrayList<>();
        for (Class<?> type : QuantityTypes.types()) {
            Object unit = QuantityTypes.factory((Class) type).getSystemUnit();
            boolean found = false;
            for (Field f : SI.class.getFields()) {
                if (Modifier.isStatic(f.getModifiers()) && f.getType() == UomUnit.class && f.get(null) == unit) {
                    found = true;
                }
            }
            if (!found) {
                missing.add(type.getSimpleName());
            }
        }
        assertEquals(List.of(), missing);
        assertEquals(33, QuantityTypes.types().size());
    }

    @Test
    void theSystemUnitsWithoutADefinitionAreConstantsToo() {
        assertSame(SpeedFactory.SYSTEM_UNIT, SI.METRE_PER_SECOND);
        assertEquals("m/s", SI.METRE_PER_SECOND.getSymbol());
        assertEquals("metre per second", SI.METRE_PER_SECOND.getName());
        assertSame(AccelerationFactory.SYSTEM_UNIT, SI.METRE_PER_SECOND_SQUARED);
        assertEquals("m/s^2", SI.METRE_PER_SECOND_SQUARED.getSymbol());
        assertSame(DimensionlessFactory.SYSTEM_UNIT, SI.ONE);
        assertEquals(BaseUnits.ONE, SI.ONE);
        assertEquals(SI.METRE.divide(SI.SECOND), SI.METRE_PER_SECOND);
        // They are constants, not units of the catalog: the system keeps its 128 units.
        assertFalse(SI.UNITS.contains(SI.METRE_PER_SECOND));
        assertEquals(128, SI.UNITS.size());
        assertTrue(SI.SYSTEM.getUnit("m/s") == null);
    }
}
