/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.engine.parse;

import java.util.List;

import org.tinygears.uom.engine.format.Prefix;

/**
 * A {@code @prefixed unit prefix...} directive, with the symbols as written. A line may mix metric ({@code k}) and
 * binary ({@code Ki}) prefixes.
 *
 * @param unitSymbol the unit symbol
 * @param prefixSymbols the prefix symbols
 * @param source the file the line is in
 * @param line the 1-based line number
 */
public record Prefixed(String unitSymbol, List<String> prefixSymbols, String source, int line) {
    /**
     * Copies the prefix list.
     *
     * @param unitSymbol the unit symbol
     * @param prefixSymbols the prefix symbols
     * @param source the file the line is in
     * @param line the 1-based line number
     */
    public Prefixed {
        prefixSymbols = List.copyOf(prefixSymbols);
    }

    /**
     * Whether a prefix of the line is metric; such a unit takes metric prefixes at run time.
     *
     * @return whether a prefix is metric
     */
    public boolean metric() {
        return prefixSymbols.stream()
                .anyMatch(s -> Prefix.BY_SYMBOL.containsKey(s) && !Prefix.BY_SYMBOL.get(s).binary());
    }

    /**
     * Whether a prefix of the line is binary; such a unit takes binary prefixes at run time.
     *
     * @return whether a prefix is binary
     */
    public boolean binary() {
        return prefixSymbols.stream()
                .anyMatch(s -> Prefix.BY_SYMBOL.containsKey(s) && Prefix.BY_SYMBOL.get(s).binary());
    }
}
