// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.quantity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.spi.QuantityFactory;
import org.junit.jupiter.api.Test;
import org.tinygears.uom.core.BaseUnits;

class QuantityTypesRegistryTest {

    private static final Set<Class<?>> TYPES = Set.of(
            javax.measure.quantity.Length.class, javax.measure.quantity.Mass.class,
            javax.measure.quantity.Time.class, javax.measure.quantity.Temperature.class,
            javax.measure.quantity.Area.class, javax.measure.quantity.Speed.class,
            javax.measure.quantity.Frequency.class, javax.measure.quantity.Radioactivity.class,
            javax.measure.quantity.Energy.class, javax.measure.quantity.Dimensionless.class);

    @Test
    void registry() throws Exception {
        assertEquals(TYPES, QuantityTypes.types());
        assertEquals(BaseUnits.METRE, QuantityTypes.factory(javax.measure.quantity.Length.class).getSystemUnit());
        var e = assertThrows(IllegalArgumentException.class,
                () -> QuantityTypes.factory(javax.measure.quantity.Power.class));
        assertEquals("no quantity factory for javax.measure.quantity.Power", e.getMessage());
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void check(Class<?> type) {
        QuantityFactory f = QuantityTypes.factory((Class) type);
        Unit unit = f.getSystemUnit();
        assertTrue(unit.getConverterTo(unit).isIdentity(), type.getName());
        assertSame(unit, unit.asType((Class) type));
    }
}
