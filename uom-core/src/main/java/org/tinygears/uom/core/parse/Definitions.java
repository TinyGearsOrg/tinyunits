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
 * The parsed content of a definition file and its includes, in file order. {@code system}, {@code extended} and
 * {@code members} come from the root file only; {@code system} is {@code null} without an {@code @system} line.
 *
 * @param definitions the unit definitions
 * @param preferred the {@code @preferred} directives
 * @param prefixed the {@code @prefixed} directives
 * @param system the system name, or null
 * @param extended the {@code @extends} directives
 * @param members the {@code @member} symbols
 */
public record Definitions(
        List<Definition> definitions,
        List<Preferred> preferred,
        List<Prefixed> prefixed,
        String system,
        List<Extends> extended,
        List<Member> members
) {
    /**
     * Copies the lists.
     *
     * @param definitions the unit definitions
     * @param preferred the {@code @preferred} directives
     * @param prefixed the {@code @prefixed} directives
     * @param system the system name, or null
     * @param extended the {@code @extends} directives
     * @param members the {@code @member} symbols
     */
    public Definitions {
        definitions = List.copyOf(definitions);
        preferred = List.copyOf(preferred);
        prefixed = List.copyOf(prefixed);
        extended = List.copyOf(extended);
        members = List.copyOf(members);
    }
}
