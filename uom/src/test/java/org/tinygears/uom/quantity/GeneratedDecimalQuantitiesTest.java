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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import javax.measure.Quantity;
import javax.measure.Quantity.Scale;
import javax.measure.UnconvertibleException;
import javax.measure.Unit;
import javax.measure.spi.QuantityFactory;
import javax.measure.spi.SystemOfUnits;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.core.quantity.AbstractDecimalQuantity;

import static org.junit.jupiter.api.Assertions.*;

/** Decimal quantities for every generated type, driven by the {@link QuantityTypes} registry. */
class GeneratedDecimalQuantitiesTest {

    private static final BigDecimal ONE_HALF = new BigDecimal("1.5");
    private static final String PKG = "org.tinygears.uom.quantity.";

    interface Check {
        void run(Class<?> type, QuantityFactory<?> f, Unit<?> unit, Class<?> typed) throws Exception;
    }

    private static void forAllTypes(Check check) throws Exception {
        assertEquals(33, QuantityTypes.types().size());
        for (Class<?> type : QuantityTypes.types()) {
            QuantityFactory<?> f = QuantityTypes.factory((Class) type);
            Class<?> typed = Class.forName(PKG + type.getSimpleName());
            try {
                check.run(type, f, f.getSystemUnit(), typed);
            } catch (AssertionError | Exception e) {
                throw new AssertionError(type.getSimpleName() + ": " + e, e);
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Quantity<?> create(QuantityFactory<?> f, Number n, Unit<?> u) {
        return ((QuantityFactory) f).create(n, u);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Quantity<?> create(QuantityFactory<?> f, Number n, Unit<?> u, Scale s) {
        return ((QuantityFactory) f).create(n, u, s);
    }

    // The four-argument decimal factory is not on QuantityFactory, so reach it on the concrete class.
    private static Quantity<?> ofDecimal(QuantityFactory<?> f, Object value, Unit<?> u, Scale s, MathContext mc)
            throws Exception {
        Method m = f.getClass().getMethod("of", BigDecimal.class, Unit.class, Scale.class, MathContext.class);
        try {
            return (Quantity<?>) m.invoke(f, value, u, s, mc);
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof RuntimeException r ? r : e;
        }
    }

    private static Quantity<?> ofDecimal(QuantityFactory<?> f, BigDecimal v, Unit<?> u) throws Exception {
        Method m = f.getClass().getMethod("of", BigDecimal.class, Unit.class);
        return (Quantity<?>) m.invoke(f, v, u);
    }

    private static Quantity<?> typedCall(Class<?> typed, Quantity<?> q, String name, Class<?> argType, Object arg)
            throws Exception {
        return (Quantity<?>) typed.getMethod(name, argType).invoke(q, arg);
    }

    private static MathContext mc(Object q) {
        return ((AbstractDecimalQuantity<?, ?>) q).mathContext();
    }

    private static void assertDecimal(Quantity<?> q) {
        assertTrue(q.getClass().getSimpleName().endsWith("Decimal"), q.getClass().getName());
        assertInstanceOf(BigDecimal.class, q.getValue());
    }

    private static void assertDouble(Quantity<?> q) {
        assertTrue(q.getClass().getSimpleName().endsWith("Double"), q.getClass().getName());
        assertInstanceOf(Double.class, q.getValue());
    }

    @Test
    void createDispatchesOnNumberKind() throws Exception {
        forAllTypes((type, f, u, typed) -> {
            Quantity<?> d = create(f, ONE_HALF, u);
            assertDecimal(d);
            assertInstanceOf(typed, d);
            assertEquals(0, ONE_HALF.compareTo((BigDecimal) d.getValue()));
            assertEquals(MathContext.DECIMAL128, mc(d));
            assertDecimal(create(f, BigInteger.TEN, u));
            assertEquals(0, BigDecimal.TEN.compareTo((BigDecimal) create(f, BigInteger.TEN, u).getValue()));
            for (Number n : new Number[] { 1.5, 1.5f, 2, 2L }) {
                Quantity<?> q = create(f, n, u);
                assertDouble(q);
                assertInstanceOf(typed, q);
                assertEquals(n.doubleValue(), q.getValue().doubleValue());
            }
        });
    }

    @Test
    void scaleRoundTrips() throws Exception {
        forAllTypes((type, f, u, typed) -> {
            for (Scale s : Scale.values()) {
                assertEquals(s, create(f, ONE_HALF, u, s).getScale());
                assertEquals(s, create(f, 1.5, u, s).getScale());
                assertEquals(s, ofDecimal(f, ONE_HALF, u, s, MathContext.DECIMAL64).getScale());
            }
            assertEquals(Scale.ABSOLUTE, create(f, ONE_HALF, u).getScale());
            assertEquals(Scale.ABSOLUTE, ofDecimal(f, ONE_HALF, u).getScale());
        });
    }

    @Test
    void contextIsKept() throws Exception {
        forAllTypes((type, f, u, typed) -> {
            for (MathContext mc : new MathContext[] {
                MathContext.DECIMAL32,
                MathContext.DECIMAL64,
                MathContext.UNLIMITED,
                new MathContext(7, java.math.RoundingMode.DOWN) }) {
                Quantity<?> q = ofDecimal(f, ONE_HALF, u, Scale.ABSOLUTE, mc);
                assertDecimal(q);
                assertEquals(mc, mc(q));
            }
        });
    }

    @Test
    void toDecimalAndToDoubleRoundTrip() throws Exception {
        forAllTypes((type, f, u, typed) -> {
            Quantity<?> dbl = create(f, 1.5, u);
            Quantity<?> dec = (Quantity<?>) typed.getMethod("toDecimal", MathContext.class)
                    .invoke(dbl, MathContext.DECIMAL64);
            assertDecimal(dec);
            assertInstanceOf(typed, dec);
            assertEquals(MathContext.DECIMAL64, mc(dec));
            Quantity<?> back = (Quantity<?>) typed.getMethod("toDouble").invoke(dec);
            assertDouble(back);
            assertEquals(dbl, back);
            assertTrue(dec.isEquivalentTo((Quantity) dbl));
            Quantity<?> again = (Quantity<?>) typed.getMethod("toDecimal", MathContext.class)
                    .invoke(back, MathContext.DECIMAL64);
            assertEquals(dec, again);
        });
    }

    @Test
    void doubleToDoubleAndToDecimal() throws Exception {
        forAllTypes((type, f, u, typed) -> {
            Quantity<?> dbl = create(f, 1.5, u);
            Quantity<?> same = (Quantity<?>) typed.getMethod("toDouble").invoke(dbl);
            assertDouble(same);
            assertEquals(dbl.getClass(), same.getClass());
            assertEquals(dbl, same);
            MathContext mc = MathContext.DECIMAL32;
            Quantity<?> dec = (Quantity<?>) typed.getMethod("toDecimal", MathContext.class).invoke(dbl, mc);
            assertDecimal(dec);
            assertEquals(mc, mc(dec));
        });
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void conversionsKeepScaleAndContext() throws Exception {
        forAllTypes((type, f, u, typed) -> {
            MathContext mc = MathContext.DECIMAL32;
            Quantity<?> dec = ofDecimal(f, ONE_HALF, u, Scale.RELATIVE, mc);
            Quantity<?> dbl = (Quantity<?>) typed.getMethod("toDouble").invoke(dec);
            assertDouble(dbl);
            assertEquals(Scale.RELATIVE, dbl.getScale());
            assertEquals(Scale.RELATIVE, ((Quantity<?>) typed.getMethod("toDouble").invoke(dbl)).getScale());
            Quantity<?> back = (Quantity<?>) typed.getMethod("toDecimal", MathContext.class).invoke(dbl, mc);
            assertEquals(Scale.RELATIVE, back.getScale());
            assertEquals(mc, mc(back));
            Quantity<?> dec2 = (Quantity<?>) typed.getMethod("toDecimal", MathContext.class).invoke(dec, mc);
            assertEquals(Scale.RELATIVE, dec2.getScale());
            assertEquals(mc, mc(dec2));
            Quantity<?> neg = (Quantity<?>) typed.getMethod("negate").invoke(dec);
            assertEquals(Scale.RELATIVE, neg.getScale());
            assertEquals(mc, mc(neg));
            Quantity<?> to = (Quantity<?>) typed.getMethod("to", Unit.class).invoke(dec, u.multiply(1000));
            assertDecimal(to);
            assertEquals(Scale.RELATIVE, to.getScale());
            assertEquals(mc, mc(to));
        });
    }

    /** A system named Test that has {@code unit} for {@code type} only. */
    private static SystemOfUnits system(Class<?> type, Unit<?> unit) {
        return (SystemOfUnits) Proxy.newProxyInstance(
                GeneratedDecimalQuantitiesTest.class.getClassLoader(),
                new Class<?>[] { SystemOfUnits.class },
                (p, m, a) -> switch (m.getName()) {
                    case "getName" -> "Test";
                    case "getUnit" -> a[0] == type ? unit : null;
                    default -> throw new UnsupportedOperationException(m.getName());
                });
    }

    @Test
    void toSystemForAllTypes() throws Exception {
        Length km = Length.of(1, GeneratedQuantitiesTest.METRE)
                .toSystem(system(javax.measure.quantity.Length.class, GeneratedQuantitiesTest.KILOMETRE));
        assertEquals(1e-3, km.getValue());
        forAllTypes((type, f, u, typed) -> {
            SystemOfUnits sys = system(type, u);
            Method to = typed.getMethod("toSystem", SystemOfUnits.class);
            assertEquals(typed, to.getReturnType());
            Quantity<?> dbl = create(f, 1.0, u);
            assertEquals(dbl, to.invoke(dbl, sys));
            Quantity<?> dec = ofDecimal(f, BigDecimal.ONE, u);
            assertEquals(dec, to.invoke(dec, sys));
            Quantity<?> rel = ofDecimal(f, ONE_HALF, u, Scale.RELATIVE, MathContext.DECIMAL32);
            Quantity<?> r = (Quantity<?>) to.invoke(rel, sys);
            assertTrue(typed.isInstance(r));
            assertEquals(rel, r);
            assertEquals(MathContext.DECIMAL32, mc(r));
        });
    }

    @Test
    void toDecimalRoundsOnceToTheContext() throws Exception {
        BigDecimal exact = new BigDecimal("1.2345678");
        BigDecimal rounded = new BigDecimal("1.234568");
        forAllTypes((type, f, u, typed) -> {
            for (Quantity<?> src : new Quantity<?>[] {
                ofDecimal(f, exact, u, Scale.ABSOLUTE, MathContext.DECIMAL128),
                create(f, 1.2345678, u) }) {
                Quantity<?> q = (Quantity<?>) typed.getMethod("toDecimal", MathContext.class)
                        .invoke(src, MathContext.DECIMAL32);
                assertDecimal(q);
                assertEquals(rounded, q.getValue());
                assertEquals(MathContext.DECIMAL32, mc(q));
            }
        });
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void compareToDoubleInAnotherUnit() throws Exception {
        forAllTypes((type, f, u, typed) -> {
            Unit big = u.multiply(1000);
            Comparable<Quantity> one = (Comparable) ofDecimal(f, BigDecimal.ONE, big);
            assertEquals(0, one.compareTo(create(f, 1000.0, u)));
            assertEquals(1, one.compareTo(create(f, 999.0, u)));
            assertEquals(-1, one.compareTo(create(f, 1001.0, u)));
        });
    }

    @Test
    void foreignUnitAndNullsAreRejected() throws Exception {
        forAllTypes((type, f, u, typed) -> {
            Unit<?> foreign = GeneratedQuantitiesTest.foreignUnit();
            assertThrows(UnconvertibleException.class, () -> create(f, ONE_HALF, foreign));
            assertThrows(UnconvertibleException.class, () -> create(f, 1.5, foreign));
            assertThrows(
                    UnconvertibleException.class,
                    () -> ofDecimal(f, ONE_HALF, foreign, Scale.ABSOLUTE, MathContext.DECIMAL128));
            assertThrows(NullPointerException.class, () -> create(f, null, u));
            assertThrows(
                    NullPointerException.class,
                    () -> ofDecimal(f, null, u, Scale.ABSOLUTE, MathContext.DECIMAL128));
            // The value is checked before the unit.
            assertThrows(
                    NullPointerException.class,
                    () -> ofDecimal(f, null, foreign, Scale.ABSOLUTE, MathContext.DECIMAL128));
            assertThrows(NullPointerException.class, () -> create(f, null, foreign));
        });
    }

    @Test
    void mixedAdditionKeepsTheTypedInterface() throws Exception {
        forAllTypes((type, f, u, typed) -> {
            Quantity<?> dec = create(f, ONE_HALF, u);
            Quantity<?> dbl = create(f, 2.5, u);
            for (Quantity<?>[] pair : new Quantity<?>[][] { { dec, dbl }, { dbl, dec } }) {
                Quantity<?> sum = typedCall(typed, pair[0], "add", Quantity.class, pair[1]);
                assertInstanceOf(typed, sum);
                // F23: the decimal wins in either order.
                assertDecimal(sum);
                assertEquals(0, new BigDecimal("4").compareTo((BigDecimal) sum.getValue()));
            }
            Quantity<?> two = typedCall(typed, dec, "add", Quantity.class, dec);
            assertDecimal(two);
            assertEquals(0, new BigDecimal("3.0").compareTo((BigDecimal) two.getValue()));
        });
    }

    @Test
    void equalsDiffersFromEquivalence() throws Exception {
        forAllTypes((type, f, u, typed) -> {
            Quantity<?> dec = create(f, ONE_HALF, u);
            Quantity<?> dbl = create(f, 1.5, u);
            assertNotEquals(dec, dbl);
            assertNotEquals(dbl, dec);
            assertTrue(dec.isEquivalentTo((Quantity) dbl));
            assertTrue(dbl.isEquivalentTo((Quantity) dec));
            assertEquals(dec, create(f, new BigDecimal("1.50"), u));
            assertNotEquals(dec, create(f, ONE_HALF, u, Scale.RELATIVE));
        });
    }

    @Test
    void typedReturnAndOrdering() {
        Unit<javax.measure.quantity.Length> metre = LengthFactory.SYSTEM_UNIT;
        Unit<javax.measure.quantity.Length> km = GeneratedQuantitiesTest.KILOMETRE;
        Length l = Length.of(new BigDecimal("1.5"), km);
        Length m = l.to(metre);
        Length sum = l.add(Length.of(2.5, metre));
        Length dsum = Length.of(2.5, metre).add(l);
        assertEquals(0, new BigDecimal("1500.0").compareTo((BigDecimal) m.getValue()));
        assertDecimal(sum);
        assertDecimal(dsum);
        assertEquals(0, new BigDecimal("1.5025").compareTo((BigDecimal) sum.getValue()));
        assertEquals(0, new BigDecimal("1502.5").compareTo((BigDecimal) dsum.getValue()));
        assertEquals(0, Length.of(BigDecimal.ONE, km).compareTo(Length.of(new BigDecimal("1000"), metre)));
        assertTrue(Length.of(BigDecimal.ONE, km).compareTo(Length.of(new BigDecimal("999"), metre)) > 0);
        assertTrue(Length.of(BigDecimal.ONE, km).compareTo(Length.of(new BigDecimal("1001"), metre)) < 0);
    }

    @Test
    void asTypeFromProductsStaysDecimal() {
        Unit<javax.measure.quantity.Length> metre = LengthFactory.SYSTEM_UNIT;
        MathContext mc = MathContext.DECIMAL32;
        Length six = LengthFactory.INSTANCE.of(new BigDecimal("6"), metre, Scale.ABSOLUTE, mc);
        Length three = LengthFactory.INSTANCE.of(new BigDecimal("3"), metre, Scale.ABSOLUTE, mc);
        Dimensionless ratio = (Dimensionless) six.divide(three).asType(javax.measure.quantity.Dimensionless.class);
        assertDecimal(ratio);
        assertEquals(0, new BigDecimal("2").compareTo((BigDecimal) ratio.getValue()));
        assertEquals(mc, mc(ratio));

        Time t = TimeFactory.INSTANCE.of(new BigDecimal("2"), TimeFactory.SYSTEM_UNIT, Scale.ABSOLUTE, mc);
        Speed v = (Speed) six.divide(t).asType(javax.measure.quantity.Speed.class);
        assertDecimal(v);
        assertEquals(0, new BigDecimal("3").compareTo((BigDecimal) v.getValue()));
        assertEquals(mc, mc(v));
    }
}
