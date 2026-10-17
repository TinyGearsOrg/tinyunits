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
 * uom: units and quantities typed with this library's own quantity types ({@code org.tinygears.uom.quantity}), not
 * bound to the generics of JSR-385, with typed products, the unit catalogs and formats
 * ({@code org.tinygears.uom.systems}), on the engine ({@code org.tinygears.uom.engine}). The JSR-385
 * implementation is the module {@code org.tinygears.uom.jsr}.
 */
module org.tinygears.uom {
    requires transitive org.tinygears.uom.engine;

    exports org.tinygears.uom;
    exports org.tinygears.uom.quantity;
    exports org.tinygears.uom.systems;
}
