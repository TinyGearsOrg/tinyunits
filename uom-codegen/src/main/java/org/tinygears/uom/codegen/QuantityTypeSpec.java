/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.codegen;

import org.tinygears.uom.engine.Unit;

/**
 * One line of the quantity types file; {@code systemSymbol} and {@code systemName} come from an explicit
 * {@code => symbol:name} ({@code explicit} is then true) or a bare unit symbol formula, else they are null.
 */
public record QuantityTypeSpec(
        String name,
        String formula,
        Unit systemUnit,
        String systemSymbol,
        String systemName,
        String source,
        int line,
        boolean explicit
) {}
