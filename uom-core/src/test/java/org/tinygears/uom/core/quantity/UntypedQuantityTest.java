/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.core.quantity;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Random;
import javax.measure.Quantity;
import javax.measure.UnconvertibleException;
import javax.measure.Unit;
import javax.measure.quantity.Length;
import javax.measure.quantity.Speed;
import javax.measure.spi.QuantityFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.Factor;
import org.tinygears.uom.core.TestServiceProvider;
import org.tinygears.uom.core.UomUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UntypedQuantityTest {

    private static final UomUnit<Length> METRE = BaseUnits.METRE;
    private static final UomUnit<Length> KILOMETRE = METRE.multiply(1000);
    private static final UomUnit<Length> SECOND = UomUnit.unchecked(BaseUnits.SECOND);
    private static final UomUnit<Length> HOUR = SECOND.multiply(3600);
    private static final UomUnit<Length> CELSIUS = UomUnit.unchecked(BaseUnits.KELVIN.shift(273.15));

    /** Its create fails, so a call proves that asType went to the provider. */
    private static final QuantityFactory<Length> LENGTH_FACTORY = new QuantityFactory<>() {
        @Override
        public Quantity<Length> create(Number value, Unit<Length> unit, Quantity.Scale scale) {
            throw new UnsupportedOperationException("provider called");
        }

        @Override
        public Quantity<Length> create(Number value, Unit<Length> unit) {
            throw new UnsupportedOperationException("provider called");
        }

        @Override
        public Unit<Length> getSystemUnit() {
            return METRE;
        }
    };

    @BeforeAll
    static void registerFactories() {
        TestServiceProvider.FACTORIES.put(Speed.class, TestSpeedImpl.FACTORY);
        TestServiceProvider.FACTORIES.put(Length.class, LENGTH_FACTORY);
    }

    @AfterAll
    static void removeFactories() {
        TestServiceProvider.FACTORIES.remove(Speed.class);
        TestServiceProvider.FACTORIES.remove(Length.class);
    }

    private static TestLengthImpl q(double value, UomUnit<Length> unit) {
        return new TestLengthImpl(value, unit);
    }

    @Test
    void product() {
        Quantity<?> r = q(2, METRE).multiply(q(3, SECOND));
        assertEquals(6.0, r.getValue());
        assertEquals(METRE.multiply(SECOND), r.getUnit());
        assertInstanceOf(Untyped.class, r);
        assertSame(Quantity.Scale.ABSOLUTE, r.getScale());
        assertSame(
                Quantity.Scale.ABSOLUTE,
                new TestLengthImpl(2, METRE, Quantity.Scale.RELATIVE).multiply(q(3, SECOND)).getScale());
    }

    @Test
    void resultUnitsPrintNormalized() {
        assertEquals("0.5", q(2, METRE).divide(q(4, METRE)).toString());
        assertEquals("4.0 m^2", q(2, METRE).multiply(q(2, METRE)).toString());
        assertEquals("5.0 m/s", q(10, METRE).divide(q(2, SECOND)).toString());
        assertEquals("8.0 m^3", q(2, METRE).multiply(q(2, METRE)).multiply(q(2, METRE)).toString());
        assertEquals("0.5 1/s", q(2, SECOND).inverse().toString());
    }

    @Test
    void quotientGivesSpeed() {
        Quantity<Speed> speed = q(10, METRE).divide(q(2, SECOND)).asType(Speed.class);
        assertInstanceOf(TestSpeed.class, speed);
        assertEquals(5.0, speed.getValue());
    }

    @Test
    void kilometrePerHour() {
        Quantity<?> r = q(36, KILOMETRE).divide(q(1, HOUR));
        assertEquals(10.0, r.getValue().doubleValue(), 1e-12);
        assertEquals(METRE.divide(SECOND), r.getUnit());
    }

    @Test
    void inverseOfSecond() {
        Quantity<?> r = q(2, SECOND).inverse();
        assertEquals(0.5, r.getValue());
        assertEquals(SECOND.inverse(), r.getUnit());
        assertInstanceOf(Untyped.class, r);
        TestLengthImpl relative = new TestLengthImpl(2, SECOND, Quantity.Scale.RELATIVE);
        assertSame(Quantity.Scale.ABSOLUTE, relative.inverse().getScale());
    }

    @Test
    void offsetOperand() {
        Quantity<?> r = q(2, CELSIUS).multiply(q(2, METRE));
        assertEquals(550.3, r.getValue().doubleValue(), 1e-12);
        assertEquals(BaseUnits.KELVIN.multiply(METRE), r.getUnit());
    }

    @Test
    void asTypeWrongDimension() {
        Quantity<?> speed = q(10, METRE).divide(q(2, SECOND));
        assertThrows(ClassCastException.class, () -> speed.asType(Length.class));
    }

    @Test
    void asTypeSameType() {
        TestLengthImpl length = q(1, KILOMETRE);
        assertSame(length, length.asType(Length.class));
    }

    interface Unsupported extends Quantity<Unsupported> {
    }

    @Test
    void asTypeWithoutFactoryIsUnsupported() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> q(10, METRE).divide(q(2, SECOND)).asType(Unsupported.class));
    }

    @Test
    void asTypeNull() {
        assertThrows(NullPointerException.class, () -> q(1, METRE).asType(null));
    }

    @Test
    void chainedOperations() {
        UomUnit<Length> third = UomUnit.unchecked(METRE.scaledBy(Factor.of(1, 3)));
        List<UomUnit<Length>> lengths = List.of(METRE, KILOMETRE, third);
        List<UomUnit<Length>> times = List.of(SECOND, HOUR, SECOND.divide(1000));
        Random random = new Random(42);
        for (int i = 0; i < 100; i++) {
            TestLengthImpl a = q(random.nextDouble(1e-3, 1e6), lengths.get(random.nextInt(lengths.size())));
            TestLengthImpl b = q(random.nextDouble(1e-3, 1e6), times.get(random.nextInt(times.size())));
            UntypedDoubleQuantity r = (UntypedDoubleQuantity) ((UntypedDoubleQuantity) a.multiply(b)).divide(b);
            assertEquals(METRE, r.getUnit());
            double expected = a.to(METRE).getValue().doubleValue();
            assertTrue(Math.abs(r.getValue() - expected) <= 1e-9 * expected, () -> a + " vs " + r);
        }
    }

    @Test
    void nullOperand() {
        TestLengthImpl one = q(1, METRE);
        assertThrows(NullPointerException.class, () -> one.multiply((Quantity<?>) null));
        assertThrows(NullPointerException.class, () -> one.divide((Quantity<?>) null));
    }

    private static Quantity<?> foreign(Number value, Unit<?> unit) {
        return (Quantity<?>) Proxy.newProxyInstance(
                UntypedQuantityTest.class.getClassLoader(),
                new Class<?>[] { Quantity.class },
                (p, m, a) -> switch (m.getName()) {
                    case "getValue" -> value;
                    case "getUnit" -> unit;
                    case "getScale" -> Quantity.Scale.ABSOLUTE;
                    default -> throw new UnsupportedOperationException(m.getName());
                });
    }

    @Test
    void foreignOperand() {
        Unit<?> foreignUnit = (Unit<?>) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] { Unit.class },
                (p, m, a) -> null);
        Quantity<?> foreign = foreign(1.0, foreignUnit);
        UnconvertibleException e = assertThrows(UnconvertibleException.class, () -> q(1, METRE).multiply(foreign));
        assertTrue(e.getMessage().contains("foreign unit implementation"), e.getMessage());
        assertThrows(UnconvertibleException.class, () -> q(1, METRE).divide(foreign));
    }

    @Test
    void foreignQuantityWithUomUnit() {
        Quantity<?> r = q(2, METRE).multiply(foreign(3, HOUR));
        assertEquals(21600.0, r.getValue());
        assertEquals(METRE.multiply(SECOND), r.getUnit());
        assertEquals(0.5, q(1, METRE).divide(foreign(2f, SECOND)).getValue());
    }

    @Test
    void divideByZero() {
        assertEquals(Double.POSITIVE_INFINITY, q(1, METRE).divide(q(0, SECOND)).getValue());
        assertEquals(Double.POSITIVE_INFINITY, q(0, SECOND).inverse().getValue());
        assertTrue(Double.isNaN(q(0, METRE).divide(q(0, SECOND)).getValue().doubleValue()));
    }

    @Test
    void untypedValueSemantics() {
        UntypedDoubleQuantity x = (UntypedDoubleQuantity) q(2, METRE).multiply(q(3, SECOND));
        UntypedDoubleQuantity y = (UntypedDoubleQuantity) q(3, SECOND).multiply(q(2, METRE));
        UntypedDoubleQuantity z = (UntypedDoubleQuantity) q(1, METRE).multiply(q(3, SECOND));
        assertEquals(x, y);
        assertEquals(x.hashCode(), y.hashCode());
        assertEquals(0, x.compareTo(y));
        assertTrue(x.isEquivalentTo(y));
        assertTrue(z.compareTo(x) < 0);
        assertTrue(!z.isEquivalentTo(x) && !z.equals(x));
        assertSame(x, x.asType(Untyped.class));
    }

    @Test
    void publicFactory() {
        UntypedDoubleQuantity q = UntypedDoubleQuantity.of(2.5, METRE.divide(SECOND));
        assertEquals(2.5, q.getValue());
        assertEquals(METRE.divide(SECOND), q.getUnit());
        assertSame(Quantity.Scale.ABSOLUTE, q.getScale());
        assertThrows(NullPointerException.class, () -> UntypedDoubleQuantity.of(1, null));
    }
}
