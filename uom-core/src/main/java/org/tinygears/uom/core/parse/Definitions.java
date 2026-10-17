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
import java.util.Map;

/**
 * The parsed content of a definition file and its includes, in file order. {@code system}, {@code extended} and
 * {@code members} come from the root file only; {@code system} is {@code null} without an {@code @system} line.
 * {@code files} holds those directives for every file read, the root included, by file name.
 *
 * @param definitions the unit definitions
 * @param preferred the {@code @preferred} directives
 * @param prefixed the {@code @prefixed} directives
 * @param system the system name, or null
 * @param extended the {@code @extends} directives
 * @param members the {@code @member} symbols
 * @param files the system directives of every file read, by file name
 */
public record Definitions(
        List<Definition> definitions,
        List<Preferred> preferred,
        List<Prefixed> prefixed,
        String system,
        List<Extends> extended,
        List<Member> members,
        Map<String, FileDirectives> files
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
     * @param files the system directives of every file read, by file name
     */
    public Definitions {
        definitions = List.copyOf(definitions);
        preferred = List.copyOf(preferred);
        prefixed = List.copyOf(prefixed);
        extended = List.copyOf(extended);
        members = List.copyOf(members);
        files = Map.copyOf(files);
    }
}
