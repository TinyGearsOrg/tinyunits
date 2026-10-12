package org.tinygears.uom.core.parse;

import org.tinygears.uom.core.UomUnit;

/** One unit definition line; {@code explicitType} is null when the line names no quantity type. */
public record Definition(String symbol, String name, UomUnit<?> unit, String explicitType, String source, int line) {
}
