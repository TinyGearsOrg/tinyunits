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

/**
 * A service ({@link java.util.ServiceLoader}) that has the alternate units of its catalogs registered, so that an atom
 * read from a stream is checked against them; {@code org.tinygears.uom} provides it. Not API.
 */
public interface UnitCatalogs {

    /** Loads the catalogs, which registers their alternate units. */
    void loadCatalogs();
}
