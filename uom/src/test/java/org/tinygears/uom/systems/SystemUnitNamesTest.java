// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import javax.measure.Unit;
import javax.measure.format.UnitFormat;
import javax.measure.spi.ServiceProvider;
import org.junit.jupiter.api.Test;
import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.quantity.Acceleration;
import org.tinygears.uom.quantity.AccelerationFactory;
import org.tinygears.uom.quantity.Dimensionless;
import org.tinygears.uom.quantity.DimensionlessFactory;
import org.tinygears.uom.quantity.QuantityTypes;
import org.tinygears.uom.quantity.Speed;
import org.tinygears.uom.quantity.SpeedFactory;

/** Every factory system unit has a symbol and a name; naming does not change formatting. */
class SystemUnitNamesTest {
    private static final UnitFormat FORMAT = ServiceProvider.current().getFormatService().getUnitFormat();

    @Test
    void aNamedSystemUnitIsItsOwnSystemUnit() {
        assertSame(SI.NEWTON, SI.NEWTON.getSystemUnit());
        assertSame(SI.METRE, SI.METRE.getSystemUnit());
        assertSame(SI.HERTZ, SI.HERTZ.getSystemUnit());
        assertEquals("N", SI.NEWTON.getSystemUnit().getSymbol());
        assertEquals("newton", SI.NEWTON.getSystemUnit().getName());
    }

    @Test
    void otherUnitsHaveTheLabelOfTheirAtomsAsSystemUnit() {
        UomUnit<?> km = SI.KILOMETRE.getSystemUnit();
        assertEquals(SI.METRE, km);
        assertEquals("m", km.getSymbol());
        assertNull(km.getName());
        // A symbol built from other units is not a name: the product of two lengths is m^2.
        assertEquals("m^2", SI.METRE.multiply(SI.METRE).getSystemUnit().getSymbol());
        assertEquals("kg·m/s^2", SI.NEWTON.multiply(2).getSystemUnit().getSymbol());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void everySystemUnitIsNamedAndRoundTrips() {
        assertEquals(33, QuantityTypes.types().size());
        for (Class<?> type : QuantityTypes.types()) {
            Unit<?> unit = QuantityTypes.factory((Class) type).getSystemUnit();
            String name = type.getSimpleName();
            assertNotNull(unit.getSymbol(), name);
            assertNotNull(unit.getName(), name);
            assertEquals(unit, FORMAT.parse(FORMAT.format(unit)), name);
        }
    }

    @Test
    void explicitlyNamedUnits() {
        assertEquals("m/s", SpeedFactory.SYSTEM_UNIT.getSymbol());
        assertEquals("metre per second", SpeedFactory.SYSTEM_UNIT.getName());
        assertEquals("m/s^2", AccelerationFactory.SYSTEM_UNIT.getSymbol());
        assertEquals("metre per second squared", AccelerationFactory.SYSTEM_UNIT.getName());
        assertEquals("1", DimensionlessFactory.SYSTEM_UNIT.getSymbol());
        assertEquals("one", DimensionlessFactory.SYSTEM_UNIT.getName());
        assertSame(BaseUnits.ONE, DimensionlessFactory.SYSTEM_UNIT);
    }

    @Test
    void formatIsUnchanged() {
        assertEquals("m/s", FORMAT.format(SpeedFactory.SYSTEM_UNIT));
        assertEquals("m/s^2", FORMAT.format(AccelerationFactory.SYSTEM_UNIT));
        assertEquals("1", FORMAT.format(DimensionlessFactory.SYSTEM_UNIT));
        assertEquals(SpeedFactory.SYSTEM_UNIT, FORMAT.parse("m/s"));
        assertEquals(AccelerationFactory.SYSTEM_UNIT, FORMAT.parse("m/s^2"));
        assertEquals(DimensionlessFactory.SYSTEM_UNIT, FORMAT.parse("1"));
        assertEquals("10.0 m/s", Speed.of(10, SpeedFactory.SYSTEM_UNIT).toString());
        assertEquals("9.81 m/s^2", Acceleration.of(9.81, AccelerationFactory.SYSTEM_UNIT).toString());
        assertEquals("0.5", Dimensionless.of(0.5, DimensionlessFactory.SYSTEM_UNIT).toString());
        assertEquals("10.0 m/s", ServiceProvider.current().getFormatService().getQuantityFormat()
                .format(Speed.of(10, SpeedFactory.SYSTEM_UNIT)));
    }
}
