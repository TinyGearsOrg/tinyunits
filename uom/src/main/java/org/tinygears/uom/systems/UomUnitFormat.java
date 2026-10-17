/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.systems;

import java.io.IOException;
import java.text.ParsePosition;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.measure.Unit;
import javax.measure.format.MeasurementParseException;
import javax.measure.format.UnitFormat;

import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.engine.format.UnitFormatter;
import org.tinygears.uom.engine.parse.FormulaException;

/**
 * Formats units by label or symbol and parses unit formulas such as {@code kg·m/s^2} over systems of units.
 * Symbols resolve as labels, then exact symbols, then a metric or binary prefix on a prefixable unit ({@code km},
 * {@code MiB}). A unit that takes binary prefixes takes only the metric prefixes from kilo up ({@code kB}, not
 * {@code dB}). Thread-safe.
 *
 * <p>Labels ({@link #label}) belong to the format instance: {@link #standard()} returns a new format each time, and
 * {@link #with} and {@link #withSuperscripts} copy the labels the format has then. The unit format of the format
 * service is one shared instance, so its labels apply to every user of the service. Labels never change
 * {@code toString} of units and quantities, which print the same whatever any format was told.
 */
public final class UomUnitFormat implements UnitFormat {
    private final List<UomSystemOfUnits> systems;
    private final UnitFormatter formatter;
    // The instances parsing returns for engine units of the systems and of labels: parse("min") is SI.MINUTE.
    private volatile Map<org.tinygears.uom.engine.Unit, UomUnit<?>> instances;

    /**
     * A format over {@code systems}, in lookup order.
     *
     * @param systems the systems
     */
    public UomUnitFormat(List<UomSystemOfUnits> systems) {
        this(List.copyOf(systems), new UnitFormatter(
                systems.stream().<org.tinygears.uom.engine.format.UnitSystem>map(UomSystemOfUnits::engine).toList()),
                null);
    }

    private UomUnitFormat(
            List<UomSystemOfUnits> systems,
            UnitFormatter formatter,
            Map<org.tinygears.uom.engine.Unit, UomUnit<?>> labelled
    ) {
        this.systems = systems;
        this.formatter = formatter;
        Map<org.tinygears.uom.engine.Unit, UomUnit<?>> m = new IdentityHashMap<>();
        for (UomSystemOfUnits system : systems) {
            for (String symbol : system.symbols()) {
                UomUnit<?> u = system.lookup(symbol).orElseThrow();
                m.putIfAbsent(u.engine(), u);
            }
        }
        if (labelled != null) {
            m.putAll(labelled);
        }
        this.instances = m;
    }

    /**
     * A new format over the same systems with a snapshot of this format's labels, that writes an exponent as
     * superscript digits ({@code m/s²}, {@code s⁻¹}) instead of {@code ^n} ({@code m/s^2}, {@code s^-1}); this format
     * is unchanged. Parsing reads both. A {@link UomQuantityFormat} over the new format writes quantities the same
     * way. {@code toString} of units and quantities is not affected.
     *
     * @param superscripts true to write superscripts, false for ASCII exponents (the default)
     * @return the new format
     */
    public UomUnitFormat withSuperscripts(boolean superscripts) {
        return new UomUnitFormat(systems, formatter.withSuperscripts(superscripts), instances);
    }

    /**
     * Whether this format writes exponents as superscript digits.
     *
     * @return true for superscripts, false for {@code ^n}
     */
    public boolean writesSuperscripts() {
        return formatter.writesSuperscripts();
    }

    /**
     * A new format over this format's systems followed by {@code systems}, with a snapshot of this format's
     * labels; this format is unchanged. A system already in the format is skipped. Not detected: a prefixed
     * reading of a new prefixable unit that equals an existing exact symbol; the existing symbol wins and
     * formatting still round-trips.
     *
     * @param systems the systems to add
     * @return the new format
     * @throws IllegalArgumentException when {@code systems} is empty, or a symbol of a given system resolves in
     *     the systems before it (exactly or as a prefixed form) or as a label to a different unit
     */
    public UomUnitFormat with(UomSystemOfUnits... systems) {
        if (Objects.requireNonNull(systems, "systems").length == 0) {
            throw new IllegalArgumentException("no systems");
        }
        List<UomSystemOfUnits> added = List.of(systems);
        UnitFormatter extended = formatter.with(
                added.stream().<org.tinygears.uom.engine.format.UnitSystem>map(UomSystemOfUnits::engine).toList());
        List<UomSystemOfUnits> all = new java.util.ArrayList<>(this.systems);
        for (UomSystemOfUnits system : added) {
            if (all.stream().noneMatch(s -> s == system)) {
                all.add(system);
            }
        }
        return new UomUnitFormat(List.copyOf(all), extended, instances);
    }

    /**
     * A new format over {@link UnitSystems#all()}.
     *
     * @return the format
     */
    public static UomUnitFormat standard() {
        return new UomUnitFormat(UnitSystems.all());
    }

    /**
     * The first of these texts that parses back to an equal unit: the label, the symbol, the symbol of an equal
     * unit of the systems, a prefix on a prefixable unit of the systems ({@code mHz}, {@code pW}, {@code KiB}), a power of ten from
     * 10 up times a unit of the systems ({@code 1000·min}), an exact formula such as {@code 1|1000·m/s}. An offset unit with factor {@code f} and
     * offset {@code o} formats as the formula of {@code f} shifted by {@code o/f}: a plain decimal if it terminates
     * and is not longer than the exact fraction, else the fraction: {@code K + 5}, {@code 5|9·K + 459.67},
     * {@code 7|9·K - 1|3}, {@code K + 1|1024}.
     *
     * @throws IllegalArgumentException when none does: a unit with a negative factor, a factor with a root, an
     *     irrational shift {@code o/f} (with a power of pi or a root) or a formula too long to parse has no
     *     faithful form
     */
    @Override
    public String format(Unit<?> unit) {
        if (!(Objects.requireNonNull(unit, "unit") instanceof UomUnit<?> u)) {
            throw new IllegalArgumentException(
                    "foreign unit implementation " + unit.getClass().getName() + " can not be formatted: " + unit);
        }
        return formatter.format(u.engine());
    }

    @Override
    public Appendable format(Unit<?> unit, Appendable appendable) throws IOException {
        return appendable.append(format(unit));
    }

    @Override
    public Unit<?> parse(CharSequence csq) throws MeasurementParseException {
        return parseFrom(csq, 0);
    }

    @Override
    public Unit<?> parse(CharSequence csq, ParsePosition pos) throws MeasurementParseException {
        try {
            UomUnit<?> unit = parseFrom(csq, pos.getIndex());
            pos.setIndex(csq.length());
            return unit;
        } catch (MeasurementParseException e) {
            pos.setErrorIndex(e.getPosition());
            throw e;
        }
    }

    /** Parses {@code csq} from {@code start} to its end; exception positions index into {@code csq}. */
    UomUnit<?> parseFrom(CharSequence csq, int start) {
        String s = csq.toString();
        if (start < 0 || start > s.length()) {
            throw new MeasurementParseException("index out of range: " + start, csq, start < 0 ? 0 : s.length());
        }
        int lead = start;
        while (lead < s.length() && Character.isWhitespace(s.charAt(lead))) {
            lead++;
        }
        try {
            return wrap(formatter.parse(s.substring(lead).strip()));
        } catch (FormulaException e) {
            throw new MeasurementParseException(e.getMessage(), csq, Math.min(lead + e.column() - 1, s.length()));
        }
    }

    /** The unit of the systems or the labels that is {@code engine} itself, else a new one over it. */
    private UomUnit<?> wrap(org.tinygears.uom.engine.Unit engine) {
        UomUnit<?> same = instances.get(engine);
        return same != null ? same : UomUnit.of(engine);
    }

    /**
     * The unit of an exact symbol of the systems, else of a prefix on a prefixable unit; never a label.
     *
     * @param symbol the symbol
     * @return the unit, or empty
     */
    public Optional<UomUnit<?>> resolve(String symbol) {
        org.tinygears.uom.engine.Unit found = formatter.resolve(Objects.requireNonNull(symbol, "symbol"));
        return found == null ? Optional.empty() : Optional.of(wrap(found));
    }

    /**
     * Adds {@code label} as an alias that parses to {@code unit} and formats it; a previous holder of the
     * same label loses it. Rejects labels that would not parse back or that name a different unit of the systems, exactly or as a prefix on a prefixable unit.
     * The label applies to this format only, not to other instances or to {@code toString} (see the class comment).
     */
    @Override
    public synchronized void label(Unit<?> unit, String label) {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(label, "label");
        if (!(unit instanceof UomUnit<?> u)) {
            throw new IllegalArgumentException("foreign unit implementation: " + unit);
        }
        formatter.label(u.engine(), label);
        Map<org.tinygears.uom.engine.Unit, UomUnit<?>> m = new IdentityHashMap<>(instances);
        m.put(u.engine(), u);
        instances = m;
    }

    @Override
    public boolean isLocaleSensitive() {
        return false;
    }
}
