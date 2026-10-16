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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.measure.MeasurementException;

import org.tinygears.uom.core.spi.Providers;

/** Registry of atom symbols, so each symbol names one definition. */
final class AlternateUnits {

    private record Registered(UnitAtom atom, Map<UnitAtom, Integer> definition) {}

    // The only global state in the units core.
    private static final Map<String, Registered> REGISTRY = new ConcurrentHashMap<>();

    static {
        for (UomUnit<?> base : new UomUnit<?>[] {
            BaseUnits.METRE,
            BaseUnits.KILOGRAM,
            BaseUnits.SECOND,
            BaseUnits.AMPERE,
            BaseUnits.KELVIN,
            BaseUnits.MOLE,
            BaseUnits.CANDELA }) {
            UnitAtom atom = base.atoms().keySet().iterator().next();
            REGISTRY.put(atom.symbol(), new Registered(atom, base.atoms()));
        }
    }

    private AlternateUnits() {
    }

    static UnitAtom register(String symbol, UomUnit<?> definition) {
        checkSymbol(symbol);
        Registered fresh = new Registered(new UnitAtom(symbol, definition.getDimension()), definition.atoms());
        Registered existing = REGISTRY.putIfAbsent(symbol, fresh);
        return existing == null ? fresh.atom() : matching(existing, fresh.atom(), definition.atoms());
    }

    /** The registered definition of {@code atom}, or null if the atom is not registered. */
    static Map<UnitAtom, Integer> definition(UnitAtom atom) {
        Registered r = REGISTRY.get(atom.symbol());
        return r != null && r.atom().equals(atom) ? r.definition() : null;
    }

    /**
     * Resolves an atom read from a stream without registering it, so untrusted data cannot bind a symbol: a
     * registered symbol must match (same definition, or the same atom when none is given) and gives the
     * registered atom; an unregistered one gives an equal, unregistered atom. A symbol is unregistered only once the
     * catalogs of our provider are loaded, so a fresh JVM checks a catalog atom as well. Without a definition (a base atom,
     * or an unregistered atom written again) only symbol and dimension are compared: a weaker check, but
     * harmless since atoms are equal by symbol and dimension.
     */
    static UnitAtom restore(String symbol, UomDimension dimension, Map<UnitAtom, Integer> definition) {
        checkSymbol(symbol);
        UnitAtom atom = new UnitAtom(symbol, dimension);
        Map<UnitAtom, Integer> atoms = null;
        if (definition != null) {
            UomUnit<?> unit = UomUnit.of(definition, Converters.IDENTITY, null, null);
            if (!unit.getDimension().equals(dimension)) {
                throw new MeasurementException(
                        "dimension " + dimension + " of " + symbol + " does not match its definition "
                                + unit.getDimension());
            }
            atoms = unit.atoms();
        }
        Registered existing = REGISTRY.get(symbol);
        if (existing == null) {
            Providers.loadCatalogs();
            existing = REGISTRY.get(symbol);
        }
        return existing == null ? atom : matching(existing, atom, atoms);
    }

    static int size() {
        return REGISTRY.size();
    }

    private static UnitAtom matching(Registered existing, UnitAtom atom, Map<UnitAtom, Integer> definition) {
        if (definition == null ? existing.atom().equals(atom) : existing.definition().equals(definition)) {
            return existing.atom();
        }
        throw new MeasurementException("symbol " + atom.symbol() + " is already associated to a different unit");
    }

    private static void checkSymbol(String symbol) {
        if (symbol.isBlank() || symbol.chars().anyMatch(Character::isWhitespace)) {
            throw new MeasurementException("invalid unit symbol: '" + symbol + "'");
        }
    }
}
