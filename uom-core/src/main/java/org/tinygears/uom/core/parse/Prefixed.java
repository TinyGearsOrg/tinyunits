// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.parse;

import java.util.List;

/** A {@code @prefixed unit prefix...} directive, with the symbols as written. */
public record Prefixed(String unitSymbol, List<String> prefixSymbols, String source, int line) {
    public Prefixed {
        prefixSymbols = List.copyOf(prefixSymbols);
    }
}
