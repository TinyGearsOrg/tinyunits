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
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.Supplier;

/** Finds the {@link UnitCatalogs} services once and has them load their catalogs; never throws. Not API. */
public final class Catalogs {
    private static final Catalogs DEFAULT = new Catalogs(
            () -> ServiceLoader.load(UnitCatalogs.class, Catalogs.class.getClassLoader()).stream()
                    .map(ServiceLoader.Provider::get).toList());

    private final Supplier<List<UnitCatalogs>> find;
    private volatile List<UnitCatalogs> found;

    Catalogs(Supplier<List<UnitCatalogs>> find) {
        this.find = find;
    }

    /** Has every {@link UnitCatalogs} service load its catalogs, which registers their alternate units. */
    public static void load() {
        DEFAULT.loadAll();
    }

    void loadAll() {
        try {
            List<UnitCatalogs> services = found;
            if (services == null) {
                services = find.get();
                found = services;
            }
            for (UnitCatalogs c : services) {
                c.loadCatalogs();
            }
        } catch (RuntimeException | ServiceConfigurationError e) {
            // Without the catalogs an atom is read unregistered, as before they were loaded.
        }
    }
}
