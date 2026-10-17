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
 * The engine of uom, free of the JSR-385 API: exact factors with pi and roots, and the decimal and interval arithmetic
 * behind them. The JSR-385 implementation ({@code org.tinygears.uom.core}) builds on it.
 */
@SuppressWarnings("module") // the qualified export names modules that are compiled later
module org.tinygears.uom.engine {
    exports org.tinygears.uom.engine;
    // Helpers the other modules of uom share; not API.
    exports org.tinygears.uom.engine.internal to org.tinygears.uom.core, org.tinygears.uom;
}
