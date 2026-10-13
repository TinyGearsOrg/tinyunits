// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.parse;

/**
 * A {@code @preferred Type symbol} directive.
 *
 * @param quantityType the simple name of the quantity type
 * @param symbol the unit symbol
 * @param source the file the line is in
 * @param line the 1-based line number
 */
public record Preferred(String quantityType, String symbol, String source, int line) {
}
