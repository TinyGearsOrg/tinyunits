/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.core.parse;

/**
 * A {@code @preferred Type symbol} directive.
 *
 * @param quantityType the simple name of the quantity type
 * @param symbol the unit symbol
 * @param source the file the line is in
 * @param line the 1-based line number
 */
public record Preferred(String quantityType, String symbol, String source, int line) {}
