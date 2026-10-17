/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.core;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

import org.tinygears.uom.engine.internal.SerialForm;

/**
 * An irreducible unit: one of the SI base units or an alternate unit. Serializable through a proxy that carries
 * an alternate atom's definition; reading checks it against the registry but never registers it.
 *
 * @param symbol the symbol
 * @param dimension the dimension
 */
public record UnitAtom(String symbol, UomDimension dimension) implements Serializable {

    /**
     * Checks the components.
     *
     * @param symbol the symbol
     * @param dimension the dimension
     */
    public UnitAtom {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(dimension, "dimension");
    }

    /**
     * The serialization proxy.
     *
     * @return the proxy
     */
    @Serial
    private Object writeReplace() {
        Map<UnitAtom, Integer> definition = AlternateUnits.definition(this);
        // A base atom is defined by itself; it resolves by symbol and dimension alone.
        if (definition == null || definition.equals(Map.of(this, 1))) {
            return new Proxy(symbol, dimension, null, null);
        }
        return new Proxy(
                symbol,
                dimension,
                definition.keySet().toArray(UnitAtom[]::new),
                definition.values().stream().mapToInt(Integer::intValue).toArray());
    }

    /**
     * Rejects a stream without the proxy; a record ignores readObject, so the check is here.
     *
     * @return never
     * @throws InvalidObjectException always
     */
    @Serial
    private Object readResolve() throws InvalidObjectException {
        throw new InvalidObjectException("proxy required");
    }

    /** Serialized form of an atom; package-private so tests can forge streams. */
    static final class Proxy implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private static final String TOO_MANY_ATOMS = "invalid unit atom: too many definition atoms";
        private static final String TOO_MANY_EXPONENTS = "invalid unit atom: too many definition exponents";

        private transient String symbol;
        private transient UomDimension dimension;
        private transient UnitAtom[] definitionAtoms;
        private transient int[] definitionExponents;

        Proxy(String symbol, UomDimension dimension, UnitAtom[] definitionAtoms, int[] definitionExponents) {
            this.symbol = symbol;
            this.dimension = dimension;
            this.definitionAtoms = definitionAtoms;
            this.definitionExponents = definitionExponents;
        }

        @Serial
        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            SerialForm.writeString(out, symbol, "invalid unit atom: symbol");
            out.writeUnshared(dimension);
            SerialForm.writeObjects(out, definitionAtoms, TOO_MANY_ATOMS);
            SerialForm.writeInts(out, definitionExponents, TOO_MANY_EXPONENTS);
        }

        @Serial
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            symbol = SerialForm.readString(in, "invalid unit atom: symbol");
            dimension = SerialForm.read(in, UomDimension.class);
            definitionAtoms = SerialForm.readObjects(in, UnitAtom.class, UnitAtom[]::new, TOO_MANY_ATOMS);
            definitionExponents = SerialForm.readInts(in, TOO_MANY_EXPONENTS);
        }

        @Serial
        private Object readResolve() throws InvalidObjectException {
            try {
                Objects.requireNonNull(symbol, "null symbol");
                Objects.requireNonNull(dimension, "null dimension");
                Map<UnitAtom, Integer> definition = definitionAtoms == null && definitionExponents == null
                        ? null
                        : UomUnit.atomMap(definitionAtoms, definitionExponents);
                return AlternateUnits.restore(symbol, dimension, definition);
            } catch (RuntimeException e) {
                throw (InvalidObjectException) new InvalidObjectException("invalid unit atom: " + e.getMessage())
                        .initCause(e);
            }
        }
    }
}
