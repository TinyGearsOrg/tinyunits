// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.measure.Unit;
import org.junit.jupiter.api.Test;
import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.quantity.AmountOfSubstanceFactory;
import org.tinygears.uom.quantity.DimensionlessFactory;
import org.tinygears.uom.quantity.ElectricCurrentFactory;
import org.tinygears.uom.quantity.LengthFactory;
import org.tinygears.uom.quantity.LuminousIntensityFactory;
import org.tinygears.uom.quantity.MassFactory;
import org.tinygears.uom.quantity.QuantityTypes;
import org.tinygears.uom.quantity.TemperatureFactory;
import org.tinygears.uom.quantity.TimeFactory;

/** A unit reachable through a base unit, a factory, a catalog constant or a preferred map is one object. */
class SharedUnitsTest {
    private static final List<Class<?>> CATALOGS = List.of(SI.class, USCustomary.class, Imperial.class, CGS.class,
            ESU.class, EMU.class, Gaussian.class);

    @Test
    void baseUnitsAreTheCatalogAndFactoryUnits() {
        assertSame(BaseUnits.METRE, SI.METRE);
        assertSame(BaseUnits.METRE, LengthFactory.SYSTEM_UNIT);
        assertSame(BaseUnits.KILOGRAM, SI.KILOGRAM);
        assertSame(BaseUnits.KILOGRAM, MassFactory.SYSTEM_UNIT);
        assertSame(BaseUnits.SECOND, SI.SECOND);
        assertSame(BaseUnits.SECOND, TimeFactory.SYSTEM_UNIT);
        assertSame(BaseUnits.AMPERE, SI.AMPERE);
        assertSame(BaseUnits.AMPERE, ElectricCurrentFactory.SYSTEM_UNIT);
        assertSame(BaseUnits.KELVIN, SI.KELVIN);
        assertSame(BaseUnits.KELVIN, TemperatureFactory.SYSTEM_UNIT);
        assertSame(BaseUnits.MOLE, SI.MOLE);
        assertSame(BaseUnits.MOLE, AmountOfSubstanceFactory.SYSTEM_UNIT);
        assertSame(BaseUnits.CANDELA, SI.CANDELA);
        assertSame(BaseUnits.CANDELA, LuminousIntensityFactory.SYSTEM_UNIT);
        // SI has no constant for one.
        assertSame(BaseUnits.ONE, DimensionlessFactory.SYSTEM_UNIT);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void everySystemUnitIsOneInstance() throws Exception {
        assertEquals(33, QuantityTypes.types().size());
        for (Class<?> type : QuantityTypes.types()) {
            Unit<?> unit = QuantityTypes.factory((Class) type).getSystemUnit();
            String name = type.getSimpleName();
            assertSame(unit, Class.forName("org.tinygears.uom.quantity." + name + "Factory").getField("SYSTEM_UNIT")
                    .get(null), name);
            assertSame(unit, SI.PREFERRED.get(type), name);
            assertSame(unit, SI.SYSTEM.getUnit((Class) type), name);
        }
    }

    // Resolved by unit equality over the catalog fields, independently of the generator's matching.
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void catalogConstantsEqualToASystemUnitAreThatUnit() throws Exception {
        Set<String> siTypes = new TreeSet<>();
        for (Class<?> catalog : CATALOGS) {
            UomSystemOfUnits system = (UomSystemOfUnits) catalog.getField("SYSTEM").get(null);
            for (Field f : constants(catalog)) {
                UomUnit<?> constant = (UomUnit<?>) f.get(null);
                for (Class<?> type : QuantityTypes.types()) {
                    Unit<?> unit = QuantityTypes.factory((Class) type).getSystemUnit();
                    if (!constant.equals(unit) || !constant.getSymbol().equals(unit.getSymbol())
                            || !constant.getName().equals(unit.getName())) {
                        continue;
                    }
                    String where = catalog.getSimpleName() + "." + f.getName();
                    assertSame(unit, constant, where);
                    assertSame(unit, system.getUnit(constant.getSymbol()), where);
                    if (catalog == SI.class) {
                        siTypes.add(type.getSimpleName());
                    }
                }
            }
        }
        assertEquals(30, siTypes.size(), siTypes.toString());
        Set<String> withoutConstant = new TreeSet<>();
        QuantityTypes.types().forEach(t -> withoutConstant.add(t.getSimpleName()));
        withoutConstant.removeAll(siTypes);
        assertEquals(Set.of("Acceleration", "Dimensionless", "Speed"), withoutConstant);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void preferredUnitsEqualToASystemUnitAreThatUnit() throws Exception {
        for (Class<?> catalog : CATALOGS) {
            Map<Class<?>, UomUnit<?>> preferred = (Map<Class<?>, UomUnit<?>>) catalog.getField("PREFERRED").get(null);
            assertEquals(QuantityTypes.types(), preferred.keySet(), catalog.getSimpleName());
            for (Class<?> type : QuantityTypes.types()) {
                Unit<?> unit = QuantityTypes.factory((Class) type).getSystemUnit();
                if (preferred.get(type).equals(unit)) {
                    assertSame(unit, preferred.get(type), catalog.getSimpleName() + " " + type.getSimpleName());
                }
            }
        }
    }

    private static List<Field> constants(Class<?> catalog) {
        List<Field> result = new ArrayList<>();
        for (Field f : catalog.getFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == UomUnit.class) {
                result.add(f);
            }
        }
        return result;
    }
}
