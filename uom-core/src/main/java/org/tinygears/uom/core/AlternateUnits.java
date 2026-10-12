// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.measure.MeasurementException;

/** Registry of atom symbols, so each symbol names one definition. */
final class AlternateUnits {

    private record Registered(UnitAtom atom, Map<UnitAtom, Integer> definition) {
    }

    // The only global state in the units core.
    private static final Map<String, Registered> REGISTRY = new ConcurrentHashMap<>();

    static {
        for (UomUnit<?> base : new UomUnit<?>[] {BaseUnits.METRE, BaseUnits.KILOGRAM, BaseUnits.SECOND,
                BaseUnits.AMPERE, BaseUnits.KELVIN, BaseUnits.MOLE, BaseUnits.CANDELA}) {
            UnitAtom atom = base.atoms().keySet().iterator().next();
            REGISTRY.put(atom.symbol(), new Registered(atom, base.atoms()));
        }
    }

    private AlternateUnits() {
    }

    static UnitAtom register(String symbol, UomUnit<?> definition) {
        return restore(symbol, definition.getDimension(), definition.atoms());
    }

    /** The registered definition of {@code atom}, or null if the atom is not registered. */
    static Map<UnitAtom, Integer> definition(UnitAtom atom) {
        Registered r = REGISTRY.get(atom.symbol());
        return r != null && r.atom().equals(atom) ? r.definition() : null;
    }

    /**
     * Registers {@code symbol} with {@code definition} unless that exact registration exists. Without a
     * definition only an equal registered atom resolves; an unregistered symbol gives an unregistered atom.
     */
    static UnitAtom restore(String symbol, UomDimension dimension, Map<UnitAtom, Integer> definition) {
        if (symbol.isBlank() || symbol.chars().anyMatch(Character::isWhitespace)) {
            throw new MeasurementException("invalid unit symbol: '" + symbol + "'");
        }
        UnitAtom atom = new UnitAtom(symbol, dimension);
        Registered existing;
        if (definition == null) {
            existing = REGISTRY.get(symbol);
            if (existing == null) {
                return atom;
            }
            if (existing.atom().equals(atom)) {
                return existing.atom();
            }
        } else {
            UomUnit<?> unit = UomUnit.of(definition, Converters.IDENTITY, null, null);
            if (!unit.getDimension().equals(dimension)) {
                throw new MeasurementException("dimension " + dimension + " of " + symbol
                        + " does not match its definition " + unit.getDimension());
            }
            existing = REGISTRY.putIfAbsent(symbol, new Registered(atom, unit.atoms()));
            if (existing == null) {
                return atom;
            }
            if (existing.definition().equals(unit.atoms())) {
                return existing.atom();
            }
        }
        throw new MeasurementException("symbol " + symbol + " is already associated to a different unit");
    }
}
