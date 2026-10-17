/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.engine.internal;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.tinygears.uom.engine.Dimension;
import org.tinygears.uom.engine.UnitAtom;

/** Products of atoms with integer exponents: pairing, normal form and dimension. Not API. */
public final class Atoms {

    private Atoms() {
    }

    /**
     * Pairs parallel arrays from a stream, rejecting a length mismatch, null and duplicate atoms.
     *
     * @param atoms the atoms
     * @param exponents their exponents
     * @return the map, unordered
     * @throws IllegalArgumentException for a mismatch or a duplicate
     */
    public static Map<UnitAtom, Integer> map(UnitAtom[] atoms, int[] exponents) {
        if (atoms == null || exponents == null || atoms.length != exponents.length) {
            throw new IllegalArgumentException("atoms and exponents do not match");
        }
        Map<UnitAtom, Integer> m = new HashMap<>();
        for (int i = 0; i < atoms.length; i++) {
            if (m.put(Objects.requireNonNull(atoms[i], "null atom"), exponents[i]) != null) {
                throw new IllegalArgumentException("duplicate atom " + atoms[i].symbol());
            }
        }
        return m;
    }

    /**
     * The normal form: zero exponents dropped, sorted by symbol, unmodifiable.
     *
     * @param atoms the atoms and their exponents
     * @return the normal form
     */
    public static Map<UnitAtom, Integer> normalize(Map<UnitAtom, Integer> atoms) {
        Map<UnitAtom, Integer> normalized = new LinkedHashMap<>();
        atoms.entrySet().stream().filter(e -> Objects.requireNonNull(e.getValue(), "exponent") != 0)
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(UnitAtom::symbol)))
                .forEach(e -> normalized.put(Objects.requireNonNull(e.getKey(), "atom"), e.getValue()));
        return Collections.unmodifiableMap(normalized);
    }

    /**
     * The dimension of the product.
     *
     * @param atoms the atoms and their exponents
     * @return the dimension
     */
    public static Dimension dimension(Map<UnitAtom, Integer> atoms) {
        Dimension d = Dimension.NONE;
        for (Map.Entry<UnitAtom, Integer> e : atoms.entrySet()) {
            d = d.multiply(e.getKey().dimension().pow(e.getValue()));
        }
        return d;
    }
}
