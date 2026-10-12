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
        if (symbol.isBlank() || symbol.chars().anyMatch(Character::isWhitespace)) {
            throw new MeasurementException("invalid unit symbol: '" + symbol + "'");
        }
        Registered fresh = new Registered(new UnitAtom(symbol, definition.getDimension()), definition.atoms());
        Registered existing = REGISTRY.putIfAbsent(symbol, fresh);
        if (existing == null) {
            return fresh.atom();
        }
        if (!existing.definition().equals(definition.atoms())) {
            throw new MeasurementException("symbol " + symbol + " is already associated to a different unit");
        }
        return existing.atom();
    }
}
