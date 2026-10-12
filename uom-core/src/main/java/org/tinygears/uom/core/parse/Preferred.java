// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.parse;

/** A {@code @preferred Type symbol} directive. */
public record Preferred(String quantityType, String symbol, String source, int line) {
}
