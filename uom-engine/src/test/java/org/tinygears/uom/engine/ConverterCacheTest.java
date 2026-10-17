/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.engine;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.engine.internal.BoundedCache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bounded converter cache of a unit; {@code internal.BoundedCacheTest} covers the cache itself. */
class ConverterCacheTest {

    private static final Unit METRE = Unit.base(UnitAtom.METRE, "metre");

    @SuppressWarnings("unchecked")
    static BoundedCache<Unit, Converter> cache(Unit u) throws Exception {
        Field f = Unit.class.getDeclaredField("converterCache");
        f.setAccessible(true);
        return (BoundedCache<Unit, Converter>) f.get(u);
    }

    private static Unit scaled(long n) {
        return METRE.scaledBy(Factor.ofLong(n));
    }

    @Test
    void sizeStaysBoundedAndConversionsStayExact() throws Exception {
        Unit metre = METRE.scaledBy(Factor.ONE);
        for (long n = 1; n <= 1000; n++) {
            assertEquals(Factor.ONE.divide(Factor.ofLong(n)), metre.converterTo(scaled(n)).factor());
            assertTrue(cache(metre).size() <= Unit.CONVERTER_CACHE_SIZE);
        }
        assertEquals(Unit.CONVERTER_CACHE_SIZE, cache(metre).size());
        // An evicted target is computed again, equal to before.
        assertFalse(cache(metre).contains(scaled(1)));
        assertEquals(Factor.ONE, metre.converterTo(scaled(1)).factor());
    }

    @Test
    void aTargetUsedSinceTheLastSweepSurvivesEviction() throws Exception {
        Unit metre = METRE.scaledBy(Factor.ONE);
        for (long n = 1; n <= Unit.CONVERTER_CACHE_SIZE; n++) {
            metre.converterTo(scaled(n));
        }
        Unit hot = scaled(7);
        for (long n = 100; n < 100 + 4 * Unit.CONVERTER_CACHE_SIZE; n++) {
            metre.converterTo(hot);
            metre.converterTo(scaled(n));
            assertTrue(cache(metre).contains(hot), "evicted after " + n);
            assertEquals(Unit.CONVERTER_CACHE_SIZE, cache(metre).size());
        }
    }

    @Test
    void hitsReturnTheCachedConverter() throws Exception {
        Unit metre = METRE.scaledBy(Factor.ONE);
        Unit km = scaled(1000);
        assertTrue(metre.converterTo(km) == metre.converterTo(scaled(1000)));
        assertEquals(1, cache(metre).size());
    }

    @Test
    void concurrentUseStaysBoundedAndCorrect() throws Exception {
        Unit metre = METRE.scaledBy(Factor.ONE);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                results.add(pool.submit(() -> {
                    int wrong = 0;
                    for (int i = 0; i < 20_000; i++) {
                        long n = 1 + ThreadLocalRandom.current().nextInt(64);
                        if (!metre.converterTo(scaled(n)).factor().equals(Factor.ONE.divide(Factor.ofLong(n)))) {
                            wrong++;
                        }
                    }
                    return wrong;
                }));
            }
            for (Future<Integer> f : results) {
                assertEquals(0, f.get());
            }
        } finally {
            pool.shutdownNow();
        }
        assertTrue(cache(metre).size() <= Unit.CONVERTER_CACHE_SIZE, "size " + cache(metre).size());
    }
}
