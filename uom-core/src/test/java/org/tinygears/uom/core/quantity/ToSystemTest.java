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
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;
import javax.measure.Quantity;
import javax.measure.UnconvertibleException;
import javax.measure.Unit;
import javax.measure.quantity.Length;
import javax.measure.quantity.Speed;
import javax.measure.spi.SystemOfUnits;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.UomUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToSystemTest {

    private static final UomUnit<Length> METRE = BaseUnits.METRE;
    private static final UomUnit<Length> KILOMETRE = METRE.multiply(1000);
    private static final String UNSUPPORTED = "system Test has no unit for type ";

    /** A system with the given name whose {@code getUnit(Class)} looks up {@code units}. */
    static SystemOfUnits system(String name, Map<Class<?>, Unit<?>> units) {
        return (SystemOfUnits) Proxy.newProxyInstance(
                ToSystemTest.class.getClassLoader(),
                new Class<?>[] { SystemOfUnits.class },
                (p, m, a) -> switch (m.getName()) {
                    case "getName" -> name;
                    case "getUnit" -> a[0] instanceof Class<?> c ? units.get(c) : null;
                    default -> throw new UnsupportedOperationException(m.getName());
                });
    }

    private static final SystemOfUnits KM = system("Test", Map.of(Length.class, KILOMETRE));
    private static final SystemOfUnits EMPTY = system("Test", Map.of());

    @SuppressWarnings("unchecked")
    private static Unit<Length> foreignUnit() {
        return (Unit<Length>) Proxy.newProxyInstance(
                ToSystemTest.class.getClassLoader(),
                new Class<?>[] { Unit.class },
                (p, m, a) -> m.getName().equals("toString") ? "foreign" : null);
    }

    @Test
    void doubleConvertsToTheSystemUnitKeepingScale() {
        TestLengthImpl q = new TestLengthImpl(1500, METRE, Quantity.Scale.RELATIVE);
        TestLength r = q.toSystem(KM);
        assertEquals(q.to(KILOMETRE), r);
        assertEquals(1.5, r.getValue());
        assertEquals(KILOMETRE, r.getUnit());
        assertEquals(Quantity.Scale.RELATIVE, r.getScale());
    }

    @Test
    void decimalConvertsToTheSystemUnitKeepingScaleAndContext() {
        TestLengthDecimalImpl q = new TestLengthDecimalImpl(
                new BigDecimal("1234.5678"),
                METRE,
                Quantity.Scale.RELATIVE,
                MathContext.DECIMAL32);
        TestLength r = q.toSystem(KM);
        assertEquals(q.to(KILOMETRE), r);
        // DECIMAL32 rounds to seven digits
        assertEquals(new BigDecimal("1.234568"), r.getValue());
        assertEquals(KILOMETRE, r.getUnit());
        assertEquals(Quantity.Scale.RELATIVE, r.getScale());
        assertEquals(MathContext.DECIMAL32, ((AbstractDecimalQuantity<?, ?>) r).mathContext());
    }

    @Test
    void systemWithoutUnitForTheType() {
        assertEquals(
                UNSUPPORTED + "Length",
                assertThrows(UnsupportedOperationException.class, () -> new TestLengthImpl(1, METRE).toSystem(EMPTY))
                        .getMessage());
        assertEquals(
                UNSUPPORTED + "Length",
                assertThrows(
                        UnsupportedOperationException.class,
                        () -> new TestLengthDecimalImpl(BigDecimal.ONE, METRE).toSystem(EMPTY)).getMessage());
        // the type is the quantity's own, not Length for every class
        UomUnit<Speed> mps = UomUnit.unchecked(BaseUnits.METRE.divide(BaseUnits.SECOND));
        assertEquals(
                UNSUPPORTED + "Speed",
                assertThrows(
                        UnsupportedOperationException.class,
                        () -> new TestSpeedImpl(1, mps, Quantity.Scale.ABSOLUTE).toSystem(KM)).getMessage());
        assertEquals(
                UNSUPPORTED + "Speed",
                assertThrows(
                        UnsupportedOperationException.class,
                        () -> new TestSpeedDecimalImpl(
                                BigDecimal.ONE,
                                mps,
                                Quantity.Scale.ABSOLUTE,
                                MathContext.DECIMAL128).toSystem(KM))
                        .getMessage());
    }

    @Test
    void foreignSystemWithoutName() {
        SystemOfUnits unnamed = system(null, Map.of());
        assertEquals(
                "system null has no unit for type Length",
                assertThrows(UnsupportedOperationException.class, () -> new TestLengthImpl(1, METRE).toSystem(unnamed))
                        .getMessage());
    }

    @Test
    void untypedQuantitiesHaveNoSystemUnit() {
        SystemOfUnits all = system("Test", Map.of(Length.class, METRE, Untyped.class, METRE.multiply(METRE)));
        Quantity<?> area = new TestLengthImpl(2, METRE).multiply(new TestLengthImpl(3, METRE));
        Quantity<?> decimalArea = new TestLengthDecimalImpl(BigDecimal.valueOf(2), METRE)
                .multiply(new TestLengthDecimalImpl(BigDecimal.ONE, METRE));
        assertEquals(
                UNSUPPORTED + "Untyped",
                assertThrows(UnsupportedOperationException.class, () -> ((UntypedDoubleQuantity) area).toSystem(EMPTY))
                        .getMessage());
        assertEquals(
                UNSUPPORTED + "Untyped",
                assertThrows(
                        UnsupportedOperationException.class,
                        () -> ((UntypedDecimalQuantity) decimalArea).toSystem(EMPTY)).getMessage());
        // a system that does map Untyped is honoured, the hook is Untyped.class
        assertEquals(METRE.multiply(METRE), ((UntypedDoubleQuantity) area).toSystem(all).getUnit());
        assertEquals(METRE.multiply(METRE), ((UntypedDecimalQuantity) decimalArea).toSystem(all).getUnit());
    }

    @Test
    void foreignUnitFromTheSystem() {
        SystemOfUnits foreign = system("Test", Map.of(Length.class, foreignUnit()));
        assertThrows(UnconvertibleException.class, () -> new TestLengthImpl(1, METRE).toSystem(foreign));
        assertThrows(
                UnconvertibleException.class,
                () -> new TestLengthDecimalImpl(BigDecimal.ONE, METRE).toSystem(foreign));
    }

    @Test
    void unitOfAnotherDimensionFromTheSystem() {
        SystemOfUnits wrong = system("Test", Map.of(Length.class, BaseUnits.SECOND));
        assertThrows(UnconvertibleException.class, () -> new TestLengthImpl(1, METRE).toSystem(wrong));
        assertThrows(
                UnconvertibleException.class,
                () -> new TestLengthDecimalImpl(BigDecimal.ONE, METRE).toSystem(wrong));
    }

    @Test
    void nullSystem() {
        assertEquals(
                "system",
                assertThrows(NullPointerException.class, () -> new TestLengthImpl(1, METRE).toSystem(null))
                        .getMessage());
        assertEquals(
                "system",
                assertThrows(
                        NullPointerException.class,
                        () -> new TestLengthDecimalImpl(BigDecimal.ONE, METRE).toSystem(null)).getMessage());
    }

    @Test
    void quantityTypeOfFixtures() {
        assertEquals(Length.class, new TestLengthImpl(1, METRE).quantityType());
        assertEquals(Length.class, new TestLengthDecimalImpl(BigDecimal.ONE, METRE).quantityType());
        UomUnit<Speed> mps = UomUnit.unchecked(BaseUnits.METRE.divide(BaseUnits.SECOND));
        assertEquals(Speed.class, new TestSpeedImpl(1, mps, Quantity.Scale.ABSOLUTE).quantityType());
        assertEquals(
                Speed.class,
                new TestSpeedDecimalImpl(BigDecimal.ONE, mps, Quantity.Scale.ABSOLUTE, MathContext.DECIMAL128)
                        .quantityType());
        assertEquals(Untyped.class, UntypedDoubleQuantity.of(1, METRE).quantityType());
        assertEquals(
                Untyped.class,
                UntypedDecimalQuantity.of(BigDecimal.ONE, METRE, MathContext.DECIMAL128).quantityType());
    }
}
