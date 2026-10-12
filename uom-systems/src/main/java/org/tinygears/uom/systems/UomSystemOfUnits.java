// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.systems;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.measure.Dimension;
import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.spi.SystemOfUnits;
import org.tinygears.uom.core.UomUnit;

/** An immutable system of units indexed by symbol. */
public final class UomSystemOfUnits implements SystemOfUnits {

    private final String name;
    private final Map<String, UomUnit<?>> bySymbol;
    private final Set<UomUnit<?>> units;
    private final Map<Class<?>, UomUnit<?>> preferred;
    private final Set<String> prefixable;

    public UomSystemOfUnits(String name, List<UomUnit<?>> units, Map<Class<?>, UomUnit<?>> preferred,
            Set<String> prefixable) {
        this.name = Objects.requireNonNull(name, "name");
        Map<String, UomUnit<?>> index = new LinkedHashMap<>();
        for (UomUnit<?> unit : Objects.requireNonNull(units, "units")) {
            String symbol = Objects.requireNonNull(unit, "unit").getSymbol();
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
        for (String symbol : this.prefixable) {
            if (!index.containsKey(symbol)) {
                throw new IllegalArgumentException("prefixable symbol is not a unit symbol: " + symbol);
            }
        }
    }

    @Override
    public String getName() {
        return name;
    }

    /** Returns the preferred unit for the type, or null when none is defined. */
    @Override
    @SuppressWarnings("unchecked")
    public <Q extends Quantity<Q>> Unit<Q> getUnit(Class<Q> type) {
        return (Unit<Q>) preferred.get(type);
    }

    /** Returns the unit with exactly this symbol, or null when unknown. */
    @Override
    public Unit<?> getUnit(String symbol) {
        return bySymbol.get(symbol);
    }

    /** Units in list order; units equal to each other (aliases) collapse to the first one listed. */
    @Override
    public Set<? extends Unit<?>> getUnits() {
        return units;
    }

    @Override
    public Set<? extends Unit<?>> getUnits(Dimension dimension) {
        Set<UomUnit<?>> result = new LinkedHashSet<>();
        for (UomUnit<?> unit : units) {
            if (unit.getDimension().equals(dimension)) {
                result.add(unit);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public Optional<UomUnit<?>> lookup(String symbol) {
        return Optional.ofNullable(bySymbol.get(symbol));
    }

    public boolean isPrefixable(String unitSymbol) {
        return prefixable.contains(unitSymbol);
    }
}
