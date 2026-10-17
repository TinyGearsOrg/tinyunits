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

import org.junit.jupiter.api.Test;

import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.engine.Factor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractDoubleQuantityArithmeticTest {

    private static final UomUnit<Length> METRE = BaseUnits.METRE;
    private static final UomUnit<Length> KILOMETRE = BaseUnits.METRE.multiply(1000);
    private static final UomUnit<Length> CELSIUS = UomUnit.unchecked(BaseUnits.KELVIN.shift(273.15));
    private static final UomUnit<Length> KELVIN = UomUnit.unchecked(BaseUnits.KELVIN);
    private static final double INF = Double.POSITIVE_INFINITY;

    private static TestLengthImpl q(double value, UomUnit<Length> unit) {
        return new TestLengthImpl(value, unit);
    }

    private static void assertQuantity(double value, UomUnit<Length> unit, Quantity<Length> actual) {
        assertEquals(value, actual.getValue().doubleValue(), 1e-12);
        assertEquals(unit, actual.getUnit());
    }

    @Test
    void addAcrossUnits() {
        assertQuantity(1.5, KILOMETRE, q(1, KILOMETRE).add(q(500, METRE)));
        assertQuantity(1500, METRE, q(500, METRE).add(q(1, KILOMETRE)));
    }

    @Test
    void subtractAcrossUnits() {
        assertQuantity(0.5, KILOMETRE, q(1, KILOMETRE).subtract(q(500, METRE)));
        assertQuantity(-500, METRE, q(500, METRE).subtract(q(1, KILOMETRE)));
    }

    @Test
    void noSystemRoundTripForLinearUnits() {
        assertEquals(1.5, q(1, KILOMETRE).add(q(500, METRE)).getValue());
    }

    @Test
    void negate() {
        Quantity<Length> r = new TestLengthImpl(2, KILOMETRE, Quantity.Scale.RELATIVE).negate();
        assertQuantity(-2, KILOMETRE, r);
        assertSame(Quantity.Scale.RELATIVE, r.getScale());
        assertQuantity(-2, CELSIUS, q(2, CELSIUS).negate());
        assertEquals(0, Double.compare(-0.0, q(0.0, METRE).negate().getValue().doubleValue()));
        assertEquals(0, Double.compare(0.0, q(-0.0, METRE).negate().getValue().doubleValue()));
        assertTrue(Double.isNaN(q(Double.NaN, METRE).negate().getValue().doubleValue()));
    }

    @Test
    void offsetAddsThroughSystemUnit() {
        assertQuantity(4, CELSIUS, q(2, CELSIUS).add(q(2, KELVIN)));
        assertQuantity(276.15, CELSIUS, q(2, CELSIUS).add(q(1, CELSIUS)));
    }

    @Test
    void subtractOffset() {
        // Follows the Quantity Javadoc rule: (275.15 K - 274.15 K) back in degC.
        assertQuantity(-272.15, CELSIUS, q(2, CELSIUS).subtract(q(1, CELSIUS)));
        assertQuantity(0, CELSIUS, q(2, CELSIUS).subtract(q(2, KELVIN)));
    }

    @Test
    void kelvinPlusCelsiusStaysKelvin() {
        assertQuantity(277.15, KELVIN, q(2, KELVIN).add(q(2, CELSIUS)));
        assertQuantity(-273.15, KELVIN, q(2, KELVIN).subtract(q(2, CELSIUS)));
    }

    @Test
    void scalarOperations() {
        assertQuantity(6, KILOMETRE, q(2, KILOMETRE).multiply(3));
        assertQuantity(0.5, KILOMETRE, q(2, KILOMETRE).divide(4));
        assertQuantity(5, KILOMETRE, q(2, KILOMETRE).multiply(2.5f));
        assertTrue(Double.isNaN(q(2, KILOMETRE).multiply(Double.NaN).getValue().doubleValue()));
        assertEquals(INF, q(2, KILOMETRE).divide(0).getValue());
        assertEquals(Double.NEGATIVE_INFINITY, q(-2, KILOMETRE).divide(0).getValue());
        assertTrue(Double.isNaN(q(0, KILOMETRE).divide(0).getValue().doubleValue()));
    }

    @Test
    void offsetScalar() {
        assertQuantity(277.15, CELSIUS, q(2, CELSIUS).multiply(2));
        assertQuantity(-135.575, CELSIUS, q(2, CELSIUS).divide(2));
    }

    @Test
    void commutativeAndAssociative() {
        UomUnit<Length> third = UomUnit.unchecked(METRE.scaledBy(Factor.of(1, 3)));
        UomUnit<Length> piOver180 = UomUnit.unchecked(METRE.scaledBy(Factor.PI.divide(Factor.ofLong(180))));
        List<UomUnit<Length>> units = List.of(METRE, KILOMETRE, third, piOver180);
        Random random = new Random(42);
        for (int i = 0; i < 200; i++) {
            TestLengthImpl a = q(random.nextDouble(1e-3, 1e6), units.get(random.nextInt(units.size())));
            TestLengthImpl b = q(random.nextDouble(1e-3, 1e6), units.get(random.nextInt(units.size())));
            TestLengthImpl c = q(random.nextDouble(1e-3, 1e6), units.get(random.nextInt(units.size())));
            assertClose(a.add(b), b.add(a));
            assertClose(a.add(b).add(c), a.add(b.add(c)));
        }
    }

    private static void assertClose(Quantity<Length> x, Quantity<Length> y) {
        double a = x.to(METRE).getValue().doubleValue();
        double b = y.to(METRE).getValue().doubleValue();
        assertTrue(Math.abs(a - b) <= 1e-9 * Math.max(Math.abs(a), Math.abs(b)), () -> x + " vs " + y);
    }

    @Test
    void incompatibleUnitFails() {
        assertThrows(UnconvertibleException.class, () -> q(1, METRE).add(q(1, KELVIN)));
        assertThrows(UnconvertibleException.class, () -> q(1, METRE).subtract(q(1, KELVIN)));
    }

    @Test
    void foreignQuantityFails() {
        @SuppressWarnings("unchecked")
        Unit<Length> foreignUnit = (Unit<Length>) Proxy
                .newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Unit.class }, (p, m, a) -> null);
        @SuppressWarnings("unchecked")
        Quantity<Length> foreign = (Quantity<Length>) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] { Quantity.class },
                (p, m, a) -> switch (m.getName()) {
                    case "getValue" -> 1.0;
                    case "getUnit" -> foreignUnit;
                    case "getScale" -> Quantity.Scale.ABSOLUTE;
                    default -> throw new UnsupportedOperationException(m.getName());
                });
        UnconvertibleException e = assertThrows(UnconvertibleException.class, () -> q(1, METRE).add(foreign));
        assertTrue(e.getMessage().contains("foreign unit implementation"), e.getMessage());
        assertThrows(UnconvertibleException.class, () -> q(1, METRE).subtract(foreign));
    }

    @Test
    void foreignQuantityWithUomUnitWorks() {
        Quantity<Length> foreign = new ForeignLength(500, METRE);
        assertQuantity(1.5, KILOMETRE, q(1, KILOMETRE).add(foreign));
        assertQuantity(0.5, KILOMETRE, q(1, KILOMETRE).subtract(foreign));
    }

    @Test
    void nullFails() {
        TestLengthImpl one = q(1, METRE);
        assertThrows(NullPointerException.class, () -> one.add(null));
        assertThrows(NullPointerException.class, () -> one.subtract(null));
        assertThrows(NullPointerException.class, () -> one.multiply((Number) null));
        assertThrows(NullPointerException.class, () -> one.divide((Number) null));
    }

    @Test
    void infinities() {
        assertTrue(Double.isNaN(q(INF, METRE).add(q(Double.NEGATIVE_INFINITY, METRE)).getValue().doubleValue()));
        assertEquals(INF, q(INF, METRE).add(q(1, METRE)).getValue());
        assertEquals(INF, q(INF, CELSIUS).add(q(1, KELVIN)).getValue());
    }

    @Test
    void nanPropagates() {
        assertTrue(Double.isNaN(q(Double.NaN, METRE).add(q(1, KILOMETRE)).getValue().doubleValue()));
        assertTrue(Double.isNaN(q(1, METRE).subtract(q(Double.NaN, KILOMETRE)).getValue().doubleValue()));
        assertTrue(Double.isNaN(q(Double.NaN, CELSIUS).add(q(1, KELVIN)).getValue().doubleValue()));
        assertTrue(Double.isNaN(q(Double.NaN, METRE).multiply(2).getValue().doubleValue()));
    }

    @Test
    void largeValuesDoNotOverflowSilently() {
        assertEquals(INF, q(1e308, KILOMETRE).add(q(1e308, KILOMETRE)).getValue());
    }

    @Test
    void scaleIsKeptByArithmetic() {
        TestLengthImpl relative = new TestLengthImpl(2, KILOMETRE, Quantity.Scale.RELATIVE);
        TestLengthImpl delta = new TestLengthImpl(1, METRE, Quantity.Scale.RELATIVE);
        assertSame(Quantity.Scale.RELATIVE, relative.add(delta).getScale());
        assertSame(Quantity.Scale.RELATIVE, relative.subtract(delta).getScale());
        assertSame(Quantity.Scale.RELATIVE, relative.negate().getScale());
        assertSame(Quantity.Scale.RELATIVE, relative.multiply(2).getScale());
        assertSame(Quantity.Scale.RELATIVE, relative.divide(2).getScale());
        // A sum or difference is a difference only if both operands are.
        assertSame(Quantity.Scale.ABSOLUTE, relative.add(q(1, METRE)).getScale());
        assertSame(Quantity.Scale.ABSOLUTE, relative.subtract(q(1, METRE)).getScale());
        assertSame(Quantity.Scale.ABSOLUTE, q(1, METRE).add(relative).getScale());
        assertSame(Quantity.Scale.ABSOLUTE, q(1, METRE).subtract(delta).getScale());
        assertSame(Quantity.Scale.ABSOLUTE, q(1, METRE).multiply(2).getScale());
    }

    /** A Quantity from another implementation that uses a UomUnit. */
    private record ForeignLength(double value, Unit<Length> unit) implements Quantity<Length> {
        @Override
        public Number getValue() {
            return value;
        }
        @Override
        public Unit<Length> getUnit() {
            return unit;
        }
        @Override
        public Scale getScale() {
            return Scale.ABSOLUTE;
        }
        @Override
        public Quantity<Length> add(Quantity<Length> addend) {
            throw new UnsupportedOperationException();
        }
        @Override
        public Quantity<Length> subtract(Quantity<Length> s) {
            throw new UnsupportedOperationException();
        }
        @Override
        public Quantity<?> divide(Quantity<?> divisor) {
            throw new UnsupportedOperationException();
        }
        @Override
        public Quantity<Length> divide(Number divisor) {
            throw new UnsupportedOperationException();
        }
        @Override
        public Quantity<?> multiply(Quantity<?> m) {
            throw new UnsupportedOperationException();
        }
        @Override
        public Quantity<Length> multiply(Number m) {
            throw new UnsupportedOperationException();
        }
        @Override
        public Quantity<?> inverse() {
            throw new UnsupportedOperationException();
        }
        @Override
        public Quantity<Length> negate() {
            throw new UnsupportedOperationException();
        }
        @Override
        public Quantity<Length> to(Unit<Length> u) {
            throw new UnsupportedOperationException();
        }
        @Override
        public boolean isEquivalentTo(Quantity<Length> that) {
            throw new UnsupportedOperationException();
        }
        @Override
        public <T extends Quantity<T>> Quantity<T> asType(Class<T> type) {
            throw new UnsupportedOperationException();
        }
    }
}
