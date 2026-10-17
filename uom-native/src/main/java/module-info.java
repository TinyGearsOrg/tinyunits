/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * Prototype of a native API on the engine (docs/NATIVE-API.md): units and quantities typed with this library's own
 * quantity types, not bound to the generics of JSR-385.
 */
module org.tinygears.uom.nativeapi {
    requires transitive org.tinygears.uom.engine;

    exports org.tinygears.uom.nativeapi;
}
