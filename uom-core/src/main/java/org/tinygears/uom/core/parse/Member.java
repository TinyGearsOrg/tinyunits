// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.parse;

/** One symbol of an {@code @member symbol...} directive of the root file. */
public record Member(String symbol, String source, int line) {
}
