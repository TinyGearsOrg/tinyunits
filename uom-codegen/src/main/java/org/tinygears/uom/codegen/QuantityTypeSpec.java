// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.codegen;

import org.tinygears.uom.core.UomUnit;

/** One line of the quantity types file; {@code systemSymbol} and {@code systemName} are null unless the formula is a bare unit symbol. */
public record QuantityTypeSpec(String name, String formula, UomUnit<?> systemUnit, String systemSymbol,
        String systemName, String source, int line) {
}
