// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core;

import java.util.Objects;

/** An irreducible unit: one of the SI base units or, later, an alternate unit. */
public record UnitAtom(String symbol, UomDimension dimension) {

    public UnitAtom {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(dimension, "dimension");
    }
}
