/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.jsr;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.measure.IncommensurableException;
import javax.measure.UnconvertibleException;
import javax.measure.Unit;
import javax.measure.quantity.Length;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.engine.Factor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tinygears.uom.jsr.BaseUnits.METRE;
import static org.tinygears.uom.jsr.BaseUnits.SECOND;

class UomUnitConversionTest {

    private static final UomUnit<Length> KILOMETRE = UomUnit
            .of(METRE.atoms(), Converters.scale(Factor.ofLong(1000)), "km", "kilometre");

    @Test
    void kilometreToMetre() {
        assertEquals(1500.0, KILOMETRE.getConverterTo(METRE).convert(1.5), 1e-9);
    }

    @Test
    void metreToKilometre() {
        assertEquals(0.0015, METRE.getConverterTo(KILOMETRE).convert(1.5), 1e-12);
    }

    @Test
    void incommensurableThrowsChecked() {
        assertThrows(IncommensurableException.class, () -> METRE.getConverterToAny(SECOND));
    }

    @Test
    void getConverterToAnyCompatible() throws Exception {
        assertEquals(1500.0, KILOMETRE.getConverterToAny(METRE).convert(1.5), 1e-9);
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void mismatchedDimensionThrowsUnconvertible() {
        assertThrows(UnconvertibleException.class, () -> METRE.getConverterTo((Unit) SECOND));
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void foreignUnitIsRejected() {
        Unit<Length> foreign = (Unit<Length>) java.lang.reflect.Proxy
                .newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Unit.class }, (p, m, a) -> null);
        assertThrows(UnconvertibleException.class, () -> METRE.getConverterTo(foreign));
        assertThrows(UnconvertibleException.class, () -> METRE.getConverterToAny(foreign));
        assertFalse(METRE.isEquivalentTo(foreign));
    }

    @Test
    void equivalenceIgnoresLabels() {
        UomUnit<Length> meter = METRE.named("m", "meter");
        assertTrue(METRE.isEquivalentTo(meter));
        assertTrue(meter.isEquivalentTo(METRE));
        assertFalse(METRE.isEquivalentTo(KILOMETRE));
        assertFalse(KILOMETRE.isEquivalentTo(METRE));
    }

    @Test
    void sameInstanceOnRepeatedLookup() {
        assertSame(KILOMETRE.getConverterTo(METRE), KILOMETRE.getConverterTo(METRE));
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void failuresAreNotCached() throws Exception {
        // a converter that cannot be built: an irrational offset against a rational one
        UomUnit<?> piShifted = BaseUnits.KELVIN.scaledBy(Factor.PI).shift(1);
        UomUnit<?> celsius = BaseUnits.KELVIN.shift(new BigDecimal("273.15"));
        assertThrows(UnconvertibleException.class, () -> piShifted.getConverterToAny(celsius));
        assertFalse(EngineProxies.cache(piShifted).contains(celsius.engine()));
        assertThrows(UnconvertibleException.class, () -> piShifted.getConverterToAny(celsius));
        assertEquals(0, EngineProxies.cache(piShifted).size());
    }

    @Test
    void cacheIsSafeUnderContention() throws Exception {
        int threads = 16;
        UomUnit<Length> source = METRE.named("m", "metre copy");
        UomUnit<Length> target = KILOMETRE.named("km", "kilometre copy");
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Object> first = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    for (int n = 0; n < 10_000; n++) {
                        Object c = source.getConverterTo(target);
                        first.compareAndSet(null, c);
                        if (c != first.get()) {
                            throw new AssertionError("different converter instance");
                        }
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            });
            workers.add(t);
            t.start();
        }
        start.countDown();
        for (Thread t : workers) {
            t.join(TimeUnit.SECONDS.toMillis(30));
            assertFalse(t.isAlive(), "worker hung");
        }
        assertNull(failure.get());
    }

    @Test
    void aConversionThatHasNoExactFactorIsUnconvertible() {
        // pi*(x + 1) K has an irrational offset, which no exact factor adds to the rational offset of degree Celsius
        UomUnit<?> piShifted = BaseUnits.KELVIN.scaledBy(Factor.PI).shift(1);
        UomUnit<?> celsius = BaseUnits.KELVIN.shift(new BigDecimal("273.15"));
        UnconvertibleException e = assertThrows(
                UnconvertibleException.class,
                () -> piShifted.getConverterToAny(celsius));
        assertTrue(e.getMessage().endsWith("exactly: cannot add 1*pi^1 and -5463/20 exactly"), e.getMessage());
        assertTrue(e.getCause() instanceof ArithmeticException);
        assertThrows(UnconvertibleException.class, () -> celsius.getConverterToAny(piShifted));
        assertFalse(piShifted.isEquivalentTo(UomUnit.unchecked(celsius)));
    }

    @Test
    void ofRejectsAZeroFactorAsTransformDoes() {
        assertEquals(
                "zero scale factor",
                assertThrows(
                        ArithmeticException.class,
                        () -> UomUnit.of(Map.of(), Converters.scale(Factor.ZERO), "z", null)).getMessage());
        assertThrows(ArithmeticException.class, () -> METRE.transform(Converters.scale(Factor.ZERO)));
    }

    @Test
    void aScaledUnitShifts() {
        // (x + 1) km: one of it is 2000 m
        UomUnit<Length> shifted = KILOMETRE.shift(1);
        assertEquals(Factor.ofLong(1000), shifted.toSystemConverter().factor());
        assertEquals(Factor.ofLong(1000), shifted.toSystemConverter().offset());
        assertEquals(2000.0, shifted.getConverterTo(METRE).convert(1.0));
        assertNull(shifted.getSymbol());
    }

    @Test
    void offsetUnitsConvertIntoEachOther() throws Exception {
        UomUnit<?> celsius = BaseUnits.KELVIN.shift(new BigDecimal("273.15"));
        UomUnit<?> fahrenheit = BaseUnits.KELVIN.scaledBy(Factor.of(5, 9)).shift(new BigDecimal("459.67"));
        assertEquals(212.0, celsius.getConverterToAny(fahrenheit).convert(100.0), 1e-12);
        assertEquals(-40.0, fahrenheit.getConverterToAny(celsius).convert(-40.0), 1e-12);
        assertEquals(
                0,
                new BigDecimal("212").compareTo(
                        celsius.getConverterToAny(fahrenheit)
                                .convert(new BigDecimal("100"), java.math.MathContext.DECIMAL128)));
    }
}
