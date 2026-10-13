// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.parse;

import org.tinygears.uom.core.UomUnit;

/**
 * One unit definition line; {@code explicitType} is null when the line names no quantity type, and
 * {@code alternateOf} is null except for {@code ~} lines, where it is the unit the alternate was defined from.
 *
 * @param symbol the symbol
 * @param name the name
 * @param unit the defined unit
 * @param alternateOf the unit an alternate was defined from, or null
 * @param explicitType the quantity type the line names, or null
 * @param source the file the line is in
 * @param line the 1-based line number
 */
public record Definition(String symbol, String name, UomUnit<?> unit, UomUnit<?> alternateOf, String explicitType,
        String source, int line) {
}
