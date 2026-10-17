/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.core.internal;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A cache of at most {@code capacity} entries whose eviction approximates least recently used (second chance, as in
 * the CLOCK algorithm): a hit marks its entry used without a lock; a miss on a full cache sweeps the entries, clears
 * their marks and evicts the first one not used since the previous sweep. Values are computed outside the lock; racing
 * threads may each compute one, the first stored wins and all of them return it. Thread-safe; null values are not
 * allowed.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public final class BoundedCache<K, V> {
    private static final class Entry<V> {
        final V value;
        volatile boolean used;

        Entry(V value) {
            this.value = value;
        }
    }

    private final int capacity;
    private final Map<K, Entry<V>> map = new ConcurrentHashMap<>();

    /**
     * A cache of at most {@code capacity} entries.
     *
     * @param capacity the capacity, positive
     */
    public BoundedCache(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity " + capacity);
        }
        this.capacity = capacity;
    }

    /**
     * The cached value for {@code key}, else {@code compute}'s, which then is cached.
     *
     * @param key the key
     * @param compute computes the value of a missing key
     * @return the value
     */
    public V get(K key, Function<? super K, ? extends V> compute) {
        Entry<V> entry = map.get(key);
        if (entry != null) {
            // Read first: a hot entry is written once per sweep, not on every hit.
            if (!entry.used) {
                entry.used = true;
            }
            return entry.value;
        }
        V value = Objects.requireNonNull(compute.apply(key), "computed value");
        synchronized (this) {
            Entry<V> stored = map.get(key);
            if (stored != null) {
                return stored.value;
            }
            if (map.size() >= capacity) {
                evict();
            }
            map.put(key, new Entry<>(value));
        }
        return value;
    }

    /** Evicts the first entry not used since the last sweep, clearing the marks it passes; one exists by the second pass. */
    private void evict() {
        for (int pass = 0; pass < 2; pass++) {
            for (Map.Entry<K, Entry<V>> e : map.entrySet()) {
                if (!e.getValue().used) {
                    map.remove(e.getKey());
                    return;
                }
                e.getValue().used = false;
            }
        }
        // Every entry was marked again by concurrent hits during both passes.
        map.remove(map.keySet().iterator().next());
    }

    /**
     * The number of entries.
     *
     * @return the size
     */
    public int size() {
        return map.size();
    }

    /**
     * Whether {@code key} is cached.
     *
     * @param key the key
     * @return true if cached
     */
    public boolean contains(K key) {
        return map.containsKey(key);
    }

    /** Removes every entry. */
    public synchronized void clear() {
        map.clear();
    }
}
