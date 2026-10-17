/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.nativeapi;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.tinygears.uom.engine.format.UnitFormatter;
import org.tinygears.uom.engine.parse.FormulaException;
import org.tinygears.uom.nativeapi.quantity.QuantityTypes;
import org.tinygears.uom.nativeapi.systems.UnitSystems;

/**
 * Formats units by label or symbol and parses unit formulas such as {@code kg·m/s^2} over systems of units, with the
 * rules of the JSR-385 side, whose engine it shares: symbols resolve as labels, then exact symbols, then a metric or
 * binary prefix on a prefixable unit ({@code km}, {@code MiB}); a unit formats as its label, its symbol, a prefixed
 * symbol, a power of ten times a symbol ({@code 1000·min}) or an exact formula ({@code 1|1000·m/s},
 * {@code K + 5}). A parsed unit of the systems is that unit itself; another is typed with the quantity type of its
 * system unit, else {@link Untyped}. Labels belong to the instance; {@link #with} and {@link #withSuperscripts} copy
 * them. Thread-safe.
 */
public final class UnitFormat {
    private final List<SystemOfUnits> systems;
    private final UnitFormatter formatter;
    // The units parsing returns for engine units of the systems and of labels: parse("min") is SI.MINUTE.
    private volatile Map<org.tinygears.uom.engine.Unit, Unit<?>> instances;

    /**
     * A format over {@code systems}, in lookup order.
     *
     * @param systems the systems
     */
    public UnitFormat(List<SystemOfUnits> systems) {
        this(List.copyOf(systems), new UnitFormatter(
                systems.stream().<org.tinygears.uom.engine.format.UnitSystem>map(SystemOfUnits::engine).toList()),
                null);
    }

    private UnitFormat(
            List<SystemOfUnits> systems,
            UnitFormatter formatter,
            Map<org.tinygears.uom.engine.Unit, Unit<?>> labelled
    ) {
        this.systems = systems;
        this.formatter = formatter;
        Map<org.tinygears.uom.engine.Unit, Unit<?>> m = new IdentityHashMap<>();
        for (SystemOfUnits system : systems) {
            system.units().forEach(u -> m.putIfAbsent(u.engine(), u));
        }
        if (labelled != null) {
            m.putAll(labelled);
        }
        this.instances = m;
    }

    /**
     * A new format over the catalogs, {@link UnitSystems#all()}.
     *
     * @return the format
     */
    public static UnitFormat standard() {
        return new UnitFormat(UnitSystems.all());
    }

    /**
     * A new format with a snapshot of the labels that writes exponents as superscript digits ({@code m/s²}) or as
     * {@code ^n}; parsing reads both.
     *
     * @param superscripts true to write superscripts
     * @return the new format
     */
    public UnitFormat withSuperscripts(boolean superscripts) {
        return new UnitFormat(systems, formatter.withSuperscripts(superscripts), instances);
    }

    /**
     * Whether this format writes exponents as superscript digits.
     *
     * @return true for superscripts
     */
    public boolean writesSuperscripts() {
        return formatter.writesSuperscripts();
    }

    /**
     * A new format over these systems followed by {@code added}, with a snapshot of the labels; a system already in
     * the format is skipped.
     *
     * @param added the systems to add
     * @return the new format
     * @throws IllegalArgumentException when {@code added} is empty, or a symbol of an added system resolves in the
     *     systems before it (exactly or as a prefixed form) or as a label to a different unit
     */
    public UnitFormat with(SystemOfUnits... added) {
        List<SystemOfUnits> list = List.of(Objects.requireNonNull(added, "systems"));
        UnitFormatter extended = formatter
                .with(list.stream().<org.tinygears.uom.engine.format.UnitSystem>map(SystemOfUnits::engine).toList());
        List<SystemOfUnits> all = new ArrayList<>(systems);
        for (SystemOfUnits system : list) {
            if (all.stream().noneMatch(s -> s == system)) {
                all.add(system);
            }
        }
        return new UnitFormat(List.copyOf(all), extended, instances);
    }

    /**
     * The first text that parses back to an equal unit: label, symbol, a symbol of an equal unit of the systems, a
     * prefixed symbol, a power of ten times a symbol, an exact formula ({@code 5|9·K + 459.67} for an offset unit).
     *
     * @param unit the unit
     * @return the text
     * @throws IllegalArgumentException when none does: a negative factor, a factor with a root, an irrational shift
     *     or a formula too long to parse
     */
    public String format(Unit<?> unit) {
        return formatter.format(Objects.requireNonNull(unit, "unit").engine());
    }

    /**
     * Parses a unit formula; surrounding whitespace is ignored.
     *
     * @param text the text
     * @return the unit
     * @throws ParseException if the text is not a formula of known symbols
     */
    public Unit<?> parse(CharSequence text) {
        return parseFrom(text, 0);
    }

    /** Parses {@code text} from {@code start} to its end; error positions index into {@code text}. */
    Unit<?> parseFrom(CharSequence text, int start) {
        String s = text.toString();
        int lead = start;
        while (lead < s.length() && Character.isWhitespace(s.charAt(lead))) {
            lead++;
        }
        try {
            return wrap(formatter.parse(s.substring(lead).strip()));
        } catch (FormulaException e) {
            throw new ParseException(e.getMessage(), text, Math.min(lead + e.column() - 1, s.length()));
        }
    }

    /**
     * The unit of an exact symbol of the systems, else of a prefix on a prefixable unit; never a label.
     *
     * @param symbol the symbol
     * @return the unit, or empty
     */
    public Optional<Unit<?>> resolve(String symbol) {
        org.tinygears.uom.engine.Unit found = formatter.resolve(Objects.requireNonNull(symbol, "symbol"));
        return found == null ? Optional.empty() : Optional.of(wrap(found));
    }

    /**
     * Adds {@code label} as an alias that parses to {@code unit} and formats it, in this format only; a previous
     * holder of the same label loses it.
     *
     * @param unit the unit
     * @param label the label
     * @throws IllegalArgumentException if the label would not parse back or names a different unit of the systems
     */
    public synchronized void label(Unit<?> unit, String label) {
        Objects.requireNonNull(unit, "unit");
        formatter.label(unit.engine(), label);
        Map<org.tinygears.uom.engine.Unit, Unit<?>> m = new IdentityHashMap<>(instances);
        m.put(unit.engine(), unit);
        instances = m;
    }

    private Unit<?> wrap(org.tinygears.uom.engine.Unit engine) {
        Unit<?> same = instances.get(engine);
        return same != null ? same : typed(engine);
    }

    /** {@code engine} as a unit of the quantity type of its system unit, else of {@link Untyped}. */
    @SuppressWarnings({ "unchecked", "rawtypes" }) // the type is found at run time
    static Unit<?> typed(org.tinygears.uom.engine.Unit engine) {
        Unit<Untyped> unit = Unit.of(Untyped.class, engine);
        Class type = QuantityTypes.typeOf(unit);
        return type == null ? unit : unit.asType(type);
    }
}
