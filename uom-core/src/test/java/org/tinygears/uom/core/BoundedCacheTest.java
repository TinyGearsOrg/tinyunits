// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BoundedCacheTest {

    @Test
    void computesOnceAndReturnsTheCachedValue() {
        BoundedCache<Integer, String> cache = new BoundedCache<>(4);
        AtomicInteger computed = new AtomicInteger();
        String first = cache.get(1, k -> "v" + k + computed.incrementAndGet());
        assertEquals("v11", first);
        assertSame(first, cache.get(1, k -> "v" + k + computed.incrementAndGet()));
        assertEquals(1, computed.get());
    }

    @Test
    void neverExceedsItsCapacity() {
        BoundedCache<Integer, Integer> cache = new BoundedCache<>(4);
        for (int i = 0; i < 100; i++) {
            assertEquals(i * 2, cache.get(i, k -> k * 2));
            assertTrue(cache.size() <= 4, "size " + cache.size());
        }
        assertEquals(4, cache.size());
        assertTrue(cache.contains(99));
    }

    @Test
    void anEntryUsedSinceTheLastSweepSurvives() {
        BoundedCache<Integer, Integer> cache = new BoundedCache<>(4);
        for (int i = 0; i < 4; i++) {
            cache.get(i, k -> k);
        }
        for (int i = 4; i < 40; i++) {
            cache.get(2, k -> -1);
            cache.get(i, k -> k);
            assertTrue(cache.contains(2), "evicted at " + i);
        }
        assertEquals(4, cache.size());
    }

    @Test
    void clearAndArguments() {
        BoundedCache<Integer, Integer> cache = new BoundedCache<>(1);
        cache.get(1, k -> k);
        cache.clear();
        assertEquals(0, cache.size());
        assertThrows(IllegalArgumentException.class, () -> new BoundedCache<Integer, Integer>(0));
        assertThrows(NullPointerException.class, () -> cache.get(1, k -> null));
        assertEquals(0, cache.size());
    }
}
