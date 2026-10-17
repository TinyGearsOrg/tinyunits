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
 * The hooks between the core and the service provider of {@code org.tinygears.uom}: how the core finds the quantity
 * factories and the unit texts of this implementation without depending on which provider is current.
 * Exported to {@code org.tinygears.uom} only; not API.
 */
package org.tinygears.uom.core.spi;
