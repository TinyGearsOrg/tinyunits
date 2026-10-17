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

import java.util.List;

/**
 * The system directives of one file: its {@code @system} name and its {@code @extends} and {@code @member} lines, in
 * file order.
 *
 * @param system the system name, or null without an {@code @system} line
 * @param extended the {@code @extends} directives
 * @param members the {@code @member} symbols
 */
public record FileDirectives(String system, List<Extends> extended, List<Member> members) {

    /**
     * Copies the lists.
     *
     * @param system the system name, or null
     * @param extended the {@code @extends} directives
     * @param members the {@code @member} symbols
     */
    public FileDirectives {
        extended = List.copyOf(extended);
        members = List.copyOf(members);
    }
}
