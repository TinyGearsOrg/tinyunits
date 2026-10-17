/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.engine.format;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.tinygears.uom.engine.Unit;

/**
 * A system of units indexed by symbol, with the symbols that take metric and binary prefixes and the preferred unit
 * of each quantity type by its simple name ({@code Length}); both APIs wrap it. Immutable.
 */
public final class UnitSystem {
    private final String name;
    private final Map<String, Unit> bySymbol;
    private final Set<Unit> units;
    private final Map<String, Unit> preferred;
    private final Set<String> prefixable;
    private final Set<String> binaryPrefixable;

    /**
     * A system; a unit without a symbol, a duplicate symbol or a prefixable symbol that is not a unit symbol throws
     * IllegalArgumentException.
     *
     * @param name the name
     * @param units the units, in lookup and listing order
     * @param preferred the preferred unit of each quantity type, by the type's simple name
     * @param prefixable the symbols that take a metric prefix
     * @param binaryPrefixable the symbols that take a binary prefix
     */
    public UnitSystem(
            String name,
            List<Unit> units,
            Map<String, Unit> preferred,
            Set<String> prefixable,
            Set<String> binaryPrefixable
    ) {
        this.name = Objects.requireNonNull(name, "name");
        Map<String, Unit> index = new LinkedHashMap<>();
        for (Unit unit : Objects.requireNonNull(units, "units")) {
            String symbol = Objects.requireNonNull(unit, "unit").symbol();
            if (symbol == null || symbol.isEmpty()) {
                throw new IllegalArgumentException("unit without a symbol: " + unit);
            }
            if (index.put(symbol, unit) != null) {
                throw new IllegalArgumentException("duplicate unit symbol: " + symbol);
            }
        }
        this.bySymbol = Collections.unmodifiableMap(index);
        this.units = Collections.unmodifiableSet(new LinkedHashSet<>(index.values()));
        this.preferred = Map.copyOf(Objects.requireNonNull(preferred, "preferred"));
        this.prefixable = Set.copyOf(Objects.requireNonNull(prefixable, "prefixable"));
        this.binaryPrefixable = Set.copyOf(Objects.requireNonNull(binaryPrefixable, "binaryPrefixable"));
        for (Set<String> symbols : List.of(this.prefixable, this.binaryPrefixable)) {
            for (String symbol : symbols) {
                if (!index.containsKey(symbol)) {
                    throw new IllegalArgumentException("prefixable symbol is not a unit symbol: " + symbol);
                }
            }
        }
    }

    /**
     * The name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * The unit with exactly this symbol; no prefixed forms.
     *
     * @param symbol the symbol
     * @return the unit, or null
     */
    public Unit lookup(String symbol) {
        return bySymbol.get(symbol);
    }

    /**
     * Every symbol, in list order, aliases included.
     *
     * @return the symbols
     */
    public Collection<String> symbols() {
        return bySymbol.keySet();
    }

    /**
     * The units in list order; equal units (aliases) collapse to the first one listed.
     *
     * @return the units
     */
    public Set<Unit> units() {
        return units;
    }

    /**
     * The preferred units by the simple name of their quantity type.
     *
     * @return the preferred units
     */
    public Map<String, Unit> preferred() {
        return preferred;
    }

    /**
     * Whether metric prefixes apply to the unit with this symbol.
     *
     * @param symbol the unit symbol
     * @return true if prefixable
     */
    public boolean isPrefixable(String symbol) {
        return prefixable.contains(symbol);
    }

    /**
     * Whether binary prefixes apply to the unit with this symbol; such a unit takes only the metric prefixes from
     * kilo up in a format, as IEC 80000-13 uses them ({@code kB}, not {@code dB}).
     *
     * @param symbol the unit symbol
     * @return true if binary-prefixable
     */
    public boolean isBinaryPrefixable(String symbol) {
        return binaryPrefixable.contains(symbol);
    }

    @Override
    public String toString() {
        return name;
    }
}
