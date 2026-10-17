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
 * The core of uom, a JSR-385 implementation: units with exact factors, dimensions, converters, the {@code .units}
 * definition parser and the base classes of the quantities. Most programs use the module {@code org.tinygears.uom},
 * which adds the typed quantities, the unit catalogs and the service provider.
 */
@SuppressWarnings("module") // the qualified export names org.tinygears.uom, which is compiled later
module org.tinygears.uom.core {
    requires transitive java.measure;

    exports org.tinygears.uom.core;
    exports org.tinygears.uom.core.parse;
    exports org.tinygears.uom.core.quantity;
    // The hook the provider of org.tinygears.uom implements; not API.
    exports org.tinygears.uom.core.spi to org.tinygears.uom;
    // Helpers the formats of org.tinygears.uom share; not API.
    exports org.tinygears.uom.core.internal to org.tinygears.uom;
}
