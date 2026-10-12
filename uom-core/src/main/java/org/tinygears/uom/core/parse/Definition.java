package org.tinygears.uom.core.parse;

import org.tinygears.uom.core.UomUnit;

/**
 * One unit definition line; {@code explicitType} is null when the line names no quantity type, and
 * {@code alternateOf} is null except for {@code ~} lines, where it is the unit the alternate was defined from.
 */
public record Definition(String symbol, String name, UomUnit<?> unit, UomUnit<?> alternateOf, String explicitType,
        String source, int line) {
}
