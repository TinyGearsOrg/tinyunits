// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.codegen;

import org.tinygears.uom.core.UomUnit;

/**
 * One line of the quantity types file; {@code systemSymbol} and {@code systemName} come from an explicit
 * {@code => symbol:name} ({@code explicit} is then true) or a bare unit symbol formula, else they are null.
 */
public record QuantityTypeSpec(String name, String formula, UomUnit<?> systemUnit, String systemSymbol,
        String systemName, String source, int line, boolean explicit) {
}
