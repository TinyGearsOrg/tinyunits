// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.parse;

import java.util.List;

/**
 * A {@code @prefixed unit prefix...} directive, with the symbols as written.
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
}
