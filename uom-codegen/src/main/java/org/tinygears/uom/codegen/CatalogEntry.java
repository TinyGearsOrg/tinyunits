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

import org.tinygears.uom.engine.parse.Definition;

/**
 * A catalog constant; {@code quantityType} is null for an untyped unit, {@code shared} names the factory system unit
 * the constant reuses ({@code org.tinygears.uom.quantity.LengthFactory.SYSTEM_UNIT}) or is null.
 */
public record CatalogEntry(Definition definition, String constantName, String quantityType, String shared) {}
