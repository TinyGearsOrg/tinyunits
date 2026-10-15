/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.measure.IncommensurableException;
import javax.measure.UnconvertibleException;
import javax.measure.Unit;
import javax.measure.quantity.Length;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tinygears.uom.core.BaseUnits.METRE;
import static org.tinygears.uom.core.BaseUnits.SECOND;

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
        Unit<Length> foreign = (Unit<Length>) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] { Unit.class },
                (p, m, a) -> null);
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
    void failuresAreNotCached() {
        assertThrows(UnconvertibleException.class, () -> METRE.getConverterTo((Unit) SECOND));
        assertThrows(UnconvertibleException.class, () -> METRE.getConverterTo((Unit) SECOND));
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
}
