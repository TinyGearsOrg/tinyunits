/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.core.spi;

/**
 * Implemented by the service provider of this implementation, so that the core can have the alternate units of its
 * catalogs registered before it reads an atom from a stream; exported to {@code org.tinygears.uom} only, not API.
 */
public interface UnitCatalogs {

    /** Loads the catalogs, which registers their alternate units. */
    void loadCatalogs();
}
