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
 * An {@code @extends file} directive of the root file.
 *
 * @param file the extended file
 * @param source the file the line is in
 * @param line the 1-based line number
 */
public record Extends(String file, String source, int line) {}
