// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;
import javax.measure.Quantity;
import javax.measure.UnconvertibleException;
import javax.measure.Unit;
import javax.measure.spi.QuantityFactory;
import javax.measure.spi.SystemOfUnits;
import org.junit.jupiter.api.Test;
import org.tinygears.uom.core.quantity.AbstractDecimalQuantity;
import org.tinygears.uom.core.quantity.AbstractDoubleQuantity;
import org.tinygears.uom.core.quantity.UntypedDecimalQuantity;
import org.tinygears.uom.core.quantity.UntypedDoubleQuantity;
import org.tinygears.uom.quantity.ElectricCharge;
import org.tinygears.uom.quantity.Force;
import org.tinygears.uom.quantity.Length;
import org.tinygears.uom.quantity.Mass;
import org.tinygears.uom.quantity.Pressure;
import org.tinygears.uom.quantity.QuantityTypes;
import org.tinygears.uom.quantity.Temperature;
import org.tinygears.uom.quantity.Time;
import org.tinygears.uom.quantity.Volume;

class ToSystemTest {

    private static Quantity<?> to(Quantity<?> q, SystemOfUnits system) {
        return q instanceof AbstractDoubleQuantity<?, ?> d ? (Quantity<?>) d.to(system)
                : (Quantity<?>) ((AbstractDecimalQuantity<?, ?>) q).to(system);
    }

    private static SystemOfUnits foreign(Map<Class<?>, Unit<?>> units) {
        return (SystemOfUnits) Proxy.newProxyInstance(ToSystemTest.class.getClassLoader(),
                new Class<?>[] {SystemOfUnits.class}, (p, m, a) -> switch (m.getName()) {
                    case "getName" -> "Foreign";
                    case "getUnit" -> a[0] instanceof Class<?> c ? units.get(c) : null;
                    default -> throw new UnsupportedOperationException(m.getName());
                });
    }

    private static void assertExact(String expected, Quantity<?> q) {
        assertEquals(0, new BigDecimal(expected).compareTo((BigDecimal) q.getValue()), q.toString());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void everyTypeInEverySystem() {
        assertEquals(7, UnitSystems.all().size());
        assertEquals(33, QuantityTypes.types().size());
        for (UomSystemOfUnits system : UnitSystems.all()) {
            for (Class<?> type : QuantityTypes.types()) {
                QuantityFactory f = QuantityTypes.factory((Class) type);
                Unit<?> target = system.getUnit((Class) type);
                assertNotNull(target, system.getName() + " " + type.getSimpleName());
                for (Number one : new Number[] {1, new BigDecimal("1")}) {
                    Quantity q = f.create(one, f.getSystemUnit());
                    Quantity r = to(q, system);
                    String where = system.getName() + " " + type.getSimpleName() + " " + one.getClass();
                    assertSame(target, r.getUnit(), where);
                    assertEquals(f.getSystemUnit().getDimension(), r.getUnit().getDimension(), where);
                    assertTrue(type.isInstance(r), where);
                    assertTrue(r.isEquivalentTo(q), where);
                }
            }
        }
    }

    @Test
    void metreToCgs() {
        Length d = Length.of(1, SI.METRE).to(CGS.SYSTEM);
        assertSame(SI.CENTIMETRE, d.getUnit());
        assertEquals(100.0, d.getValue().doubleValue(), 1e-12);
        assertExact("100", Length.of(BigDecimal.ONE, SI.METRE).to(CGS.SYSTEM));
    }

    @Test
    void footToSi() {
        assertEquals(0.3048, Length.of(1, USCustomary.FOOT).to(SI.SYSTEM).getValue().doubleValue(), 1e-12);
        Length m = Length.of(BigDecimal.ONE, USCustomary.FOOT).to(SI.SYSTEM);
        assertEquals(SI.METRE, m.getUnit());
        assertExact("0.3048", m);
    }

    @Test
    void celsiusToUsCustomary() {
        Temperature d = Temperature.of(100, SI.DEGREE_CELSIUS).to(USCustomary.SYSTEM);
        assertSame(USCustomary.DEGREE_FAHRENHEIT, d.getUnit());
        assertEquals(212.0, d.getValue().doubleValue(), 1e-12);
        Temperature f = Temperature.of(new BigDecimal("100"), SI.DEGREE_CELSIUS, Quantity.Scale.ABSOLUTE)
                .to(USCustomary.SYSTEM);
        assertExact("212", f);
        assertEquals(Quantity.Scale.ABSOLUTE, f.getScale());
    }

    @Test
    void kilogramToImperial() {
        Mass d = Mass.of(1, SI.KILOGRAM).to(Imperial.SYSTEM);
        assertSame(USCustomary.POUND, d.getUnit());
        assertEquals(1 / 0.45359237, d.getValue().doubleValue(), 1e-12);
        // non-terminating, so the DECIMAL128 default context rounds it
        BigDecimal expected = BigDecimal.ONE.divide(new BigDecimal("0.45359237"), MathContext.DECIMAL128);
        assertEquals(expected, Mass.of(BigDecimal.ONE, SI.KILOGRAM).to(Imperial.SYSTEM).getValue());
    }

    @Test
    void secondInGaussianFallsBackToSi() {
        Time t = Time.of(5, SI.SECOND).to(Gaussian.SYSTEM);
        assertEquals(SI.SECOND, t.getUnit());
        assertEquals(5.0, t.getValue());
        assertExact("5", Time.of(new BigDecimal("5"), SI.SECOND).to(Gaussian.SYSTEM));
    }

    @Test
    void coulombToFranklin() {
        ElectricCharge d = ElectricCharge.of(1, SI.COULOMB).to(Gaussian.SYSTEM);
        assertSame(Gaussian.FRANKLIN, d.getUnit());
        assertEquals(2997924580.0, d.getValue().doubleValue(), 1e-3);
        assertExact("2997924580", ElectricCharge.of(BigDecimal.ONE, SI.COULOMB).to(Gaussian.SYSTEM));
    }

    @Test
    void pascalAndNewtonToCgs() {
        Pressure p = Pressure.of(BigDecimal.ONE, SI.PASCAL).to(CGS.SYSTEM);
        assertSame(CGS.BARYE, p.getUnit());
        assertExact("10", p);
        assertEquals(10.0, Pressure.of(1, SI.PASCAL).to(CGS.SYSTEM).getValue().doubleValue(), 1e-12);
        Force n = Force.of(BigDecimal.ONE, SI.NEWTON).to(CGS.SYSTEM);
        assertSame(CGS.DYNE, n.getUnit());
        assertExact("100000", n);
        assertEquals(100000.0, Force.of(1, SI.NEWTON).to(CGS.SYSTEM).getValue().doubleValue(), 1e-9);
    }

    @Test
    void litreToImperialGallon() {
        Volume d = Volume.of(1, SI.LITRE).to(Imperial.SYSTEM);
        assertSame(Imperial.GALLON_UK, d.getUnit());
        assertEquals(1 / 4.54609, d.getValue().doubleValue(), 1e-12);
        assertEquals(BigDecimal.ONE.divide(new BigDecimal("4.54609"), MathContext.DECIMAL128),
                Volume.of(BigDecimal.ONE, SI.LITRE).to(Imperial.SYSTEM).getValue());
    }

    @Test
    void untypedQuantitiesHaveNoSystemUnit() {
        String message = "system SI has no unit for type Untyped";
        Quantity<?> speed = Length.of(1, SI.METRE).divide(Time.of(1, SI.SECOND));
        assertEquals(message, assertThrows(UnsupportedOperationException.class,
                () -> ((UntypedDoubleQuantity) speed).to(SI.SYSTEM)).getMessage());
        Quantity<?> decimal = Length.of(BigDecimal.ONE, SI.METRE).divide(Time.of(BigDecimal.ONE, SI.SECOND));
        assertEquals(message, assertThrows(UnsupportedOperationException.class,
                () -> ((UntypedDecimalQuantity) decimal).to(SI.SYSTEM)).getMessage());
    }

    @Test
    void foreignSystems() {
        SystemOfUnits empty = foreign(Map.of());
        assertEquals("system Foreign has no unit for type Length", assertThrows(UnsupportedOperationException.class,
                () -> Length.of(1, SI.METRE).to(empty)).getMessage());
        assertEquals("system Foreign has no unit for type Mass", assertThrows(UnsupportedOperationException.class,
                () -> Mass.of(BigDecimal.ONE, SI.KILOGRAM).to(empty)).getMessage());
        Unit<?> alien = (Unit<?>) Proxy.newProxyInstance(ToSystemTest.class.getClassLoader(),
                new Class<?>[] {Unit.class}, (p, m, a) -> m.getName().equals("toString") ? "alien" : null);
        SystemOfUnits withAlien = foreign(Map.of(javax.measure.quantity.Length.class, alien));
        assertThrows(UnconvertibleException.class, () -> Length.of(1, SI.METRE).to(withAlien));
        assertThrows(UnconvertibleException.class, () -> Length.of(BigDecimal.ONE, SI.METRE).to(withAlien));
        assertThrows(NullPointerException.class, () -> Length.of(1, SI.METRE).to((SystemOfUnits) null));
    }

    @Test
    void scaleAndContextAreKept() {
        Length rel = Length.of(new BigDecimal("1.23456789"), USCustomary.FOOT, Quantity.Scale.RELATIVE,
                MathContext.DECIMAL32).to(SI.SYSTEM);
        // 0.376296292872 rounded to seven digits
        assertEquals(new BigDecimal("0.3762963"), rel.getValue());
        assertEquals(Quantity.Scale.RELATIVE, rel.getScale());
        assertEquals(MathContext.DECIMAL32, ((AbstractDecimalQuantity<?, ?>) (Object) rel).mathContext());
        Length dbl = Length.of(2, USCustomary.FOOT, Quantity.Scale.RELATIVE).to(SI.SYSTEM);
        assertEquals(Quantity.Scale.RELATIVE, dbl.getScale());
        assertEquals(0.6096, dbl.getValue().doubleValue(), 1e-12);
    }
}
