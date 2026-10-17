/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.engine.internal;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The {@link UnitCatalogs} services are found once and asked to load each time; failures never escape. */
class CatalogsTest {

    private static class Counting implements UnitCatalogs {
        final AtomicInteger loads = new AtomicInteger();

        @Override
        public void loadCatalogs() {
            loads.incrementAndGet();
        }
    }

    @Test
    void servicesAreFoundOnceAndLoadEachTime() {
        Counting counting = new Counting();
        AtomicInteger found = new AtomicInteger();
        Catalogs catalogs = new Catalogs(() -> {
            found.incrementAndGet();
            return List.of(counting);
        });
        catalogs.loadAll();
        catalogs.loadAll();
        assertEquals(2, counting.loads.get());
        assertEquals(1, found.get());
    }

    @Test
    void loadingNeverThrows() {
        new Catalogs(() -> {
            throw new IllegalStateException("no services");
        }).loadAll();
        Counting failing = new Counting() {
            @Override
            public void loadCatalogs() {
                super.loadCatalogs();
                throw new IllegalStateException("broken");
            }
        };
        new Catalogs(() -> List.of(failing)).loadAll();
        assertEquals(1, failing.loads.get());
    }
}
