// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core;

import java.io.InvalidObjectException;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/**
 * An irreducible unit: one of the SI base units or an alternate unit. Serializable through a proxy that carries
 * an alternate atom's definition and re-registers it on read.
 */
public record UnitAtom(String symbol, UomDimension dimension) implements Serializable {

    public UnitAtom {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(dimension, "dimension");
    }

    @Serial
    private Object writeReplace() {
        Map<UnitAtom, Integer> definition = AlternateUnits.definition(this);
        // A base atom is defined by itself; it resolves by symbol and dimension alone.
        if (definition == null || definition.equals(Map.of(this, 1))) {
            return new Proxy(symbol, dimension, null, null);
        }
        return new Proxy(symbol, dimension, definition.keySet().toArray(UnitAtom[]::new),
                definition.values().stream().mapToInt(Integer::intValue).toArray());
    }

    // A record ignores readObject; a record read without its proxy ends here.
    @Serial
    private Object readResolve() throws InvalidObjectException {
        throw new InvalidObjectException("proxy required");
    }

    /** Serialized form of an atom; package-private so tests can forge streams. */
    static final class Proxy implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String symbol;
        private final UomDimension dimension;
        private final UnitAtom[] definitionAtoms;
        private final int[] definitionExponents;

        Proxy(String symbol, UomDimension dimension, UnitAtom[] definitionAtoms, int[] definitionExponents) {
            this.symbol = symbol;
            this.dimension = dimension;
            this.definitionAtoms = definitionAtoms;
            this.definitionExponents = definitionExponents;
        }

        @Serial
        private Object readResolve() throws InvalidObjectException {
            try {
                Objects.requireNonNull(symbol, "null symbol");
                Objects.requireNonNull(dimension, "null dimension");
                Map<UnitAtom, Integer> definition = definitionAtoms == null && definitionExponents == null ? null
                        : UomUnit.atomMap(definitionAtoms, definitionExponents);
                return AlternateUnits.restore(symbol, dimension, definition);
            } catch (RuntimeException e) {
                throw new InvalidObjectException("invalid unit atom: " + e.getMessage(), e);
            }
        }
    }
}
