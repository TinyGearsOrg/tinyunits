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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.measure.BinaryPrefix;
import javax.measure.MetricPrefix;
import javax.measure.Prefix;
import javax.measure.Unit;
import javax.measure.format.MeasurementParseException;
import javax.measure.format.UnitFormat;

import org.tinygears.uom.core.Converters;
import org.tinygears.uom.core.Factor;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.core.parse.FormulaEvaluator;
import org.tinygears.uom.core.parse.FormulaException;

/**
 * Formats units by label or symbol and parses unit formulas such as {@code kg·m/s^2} over systems of units.
 * Symbols resolve as labels, then exact symbols, then a metric or binary prefix on a prefixable unit ({@code km},
 * {@code MiB}). A unit that takes binary prefixes takes only the metric prefixes from kilo up ({@code kB}, not
 * {@code dB}). Thread-safe.
 */
public final class UomUnitFormat implements UnitFormat {
    private static final String EXCLUDED = "*/()^|=#[]!~@,·+-";
    private static final int MAX_POWER = 30;
    private static final List<Prefix> PREFIXES = Stream
            .<Prefix>concat(Arrays.stream(MetricPrefix.values()), Arrays.stream(BinaryPrefix.values()))
            .sorted(Comparator.comparingInt((Prefix p) -> p.getSymbol().length()).reversed()).toList();

    private static final Pattern EXPONENT = Pattern.compile("\\^(-?)(\\d+)");
    private static final String SUPERSCRIPT_DIGITS = "\u2070\u00B9\u00B2\u00B3\u2074\u2075\u2076\u2077\u2078\u2079";
    private static final char SUPERSCRIPT_MINUS = '\u207B';

    private final List<UomSystemOfUnits> systems;
    private final boolean superscripts;
    private final Map<String, UomUnit<?>> aliases = new ConcurrentHashMap<>();
    private final Map<UomUnit<?>, String> labels = new ConcurrentHashMap<>();
    // The units that take a metric or binary prefix, and all units, of the systems by unit, built when first needed.
    private volatile Map<UomUnit<?>, String> prefixable;
    private volatile Map<UomUnit<?>, String> symbols;

    /**
     * A format over {@code systems}, in lookup order.
     *
     * @param systems the systems
     */
    public UomUnitFormat(List<UomSystemOfUnits> systems) {
        this(systems, false);
    }

    private UomUnitFormat(List<UomSystemOfUnits> systems, boolean superscripts) {
        this.systems = List.copyOf(systems);
        this.superscripts = superscripts;
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
        UomUnitFormat result = new UomUnitFormat(systems, superscripts);
        synchronized (this) {
            result.aliases.putAll(aliases);
            result.labels.putAll(labels);
        }
        return result;
    }

    /**
     * Whether this format writes exponents as superscript digits.
     *
     * @return true for superscripts, false for {@code ^n}
     */
    public boolean writesSuperscripts() {
        return superscripts;
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
        List<UomSystemOfUnits> all = new ArrayList<>(this.systems);
        UomUnitFormat result;
        synchronized (this) {
            for (UomSystemOfUnits system : systems) {
                Objects.requireNonNull(system, "system");
                if (all.stream().anyMatch(s -> s == system)) {
                    continue;
                }
                for (String symbol : system.symbols()) {
                    UomUnit<?> unit = system.lookup(symbol).orElseThrow();
                    UomUnit<?> label = aliases.get(symbol);
                    if (label != null && !label.equals(unit)) {
                        throw new IllegalArgumentException(
                                "symbol '" + symbol + "' of " + system.getName() + " is the label of " + label);
                    }
                    Found other = resolve(all, symbol);
                    if (other != null && !other.unit().equals(unit)) {
                        throw new IllegalArgumentException(
                                "symbol '" + symbol + "' of " + system.getName() + " is a different unit in "
                                        + other.system().getName());
                    }
                }
                all.add(system);
            }
            result = new UomUnitFormat(all, superscripts);
            result.aliases.putAll(aliases);
            result.labels.putAll(labels);
        }
        return result;
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
        String text = formatAscii(Objects.requireNonNull(unit, "unit"));
        return superscripts ? superscript(text) : text;
    }

    /**
     * {@code text} with each {@code ^n} written as superscript digits. One that directly follows a superscript
     * ({@code m²^2}, the square of the catalog unit {@code m²}) stays {@code ^2}: {@code m²²} would read as
     * {@code m^22}.
     */
    private static String superscript(String text) {
        Matcher m = EXPONENT.matcher(text);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(text, last, m.start());
            if (m.start() > 0 && isSuperscript(text.charAt(m.start() - 1))) {
                out.append(m.group());
            } else {
                if (!m.group(1).isEmpty()) {
                    out.append(SUPERSCRIPT_MINUS);
                }
                m.group(2).chars().forEach(c -> out.append(SUPERSCRIPT_DIGITS.charAt(c - '0')));
            }
            last = m.end();
        }
        return out.append(text, last, text.length()).toString();
    }

    private static boolean isSuperscript(char c) {
        return c == SUPERSCRIPT_MINUS || SUPERSCRIPT_DIGITS.indexOf(c) >= 0;
    }

    private String formatAscii(Unit<?> unit) {
        String label = labels.get(unit);
        if (label != null && parsesTo(label, unit)) {
            return label;
        }
        if (unit.getSymbol() != null && parsesTo(unit.getSymbol(), unit)) {
            return unit.getSymbol();
        }
        for (UomSystemOfUnits system : systems) {
            for (Unit<?> known : system.getUnits()) {
                if (known.equals(unit) && parsesTo(known.getSymbol(), unit)) {
                    return known.getSymbol();
                }
            }
        }
        if (!(unit instanceof UomUnit<?> u)) {
            throw new IllegalArgumentException("unit can not be formatted: " + unit);
        }
        String prefixed = prefixed(u);
        if (prefixed != null) {
            return prefixed;
        }
        String multiple = multiple(u);
        if (multiple != null) {
            return multiple;
        }
        Factor f = u.toSystemConverter().factor();
        Factor offset = u.toSystemConverter().offset();
        Factor shift = shiftOf(f, offset);
        if (f.signum() > 0 && f.rootIndex() == 1 && shift != null && shift.rootIndex() == 1
                && shift.piExponent() == 0) {
            String formula = formula(f, u.getSystemUnit().getSymbol()) + shift(shift);
            if (parsesTo(formula, unit)) {
                return formula;
            }
        }
        throw new IllegalArgumentException(
                "unit can not be formatted: " + shown(u) + " (factor " + f + ", offset " + offset + ")");
    }

    private boolean parsesTo(String text, Unit<?> unit) {
        try {
            return unit.equals(FormulaEvaluator.evaluate(text, this::lookup));
        } catch (FormulaException e) {
            return false;
        }
    }

    /** {@code mHz}, {@code pW} or {@code KiB}: a prefix on a prefixable unit of the systems that reads back as {@code u}. */
    private String prefixed(UomUnit<?> u) {
        if (!u.toSystemConverter().isLinear() || u.toSystemConverter().factor().signum() <= 0) {
            return null;
        }
        Map<UomUnit<?>, String> units = prefixable();
        for (Prefix prefix : PREFIXES) {
            Factor value = Factor.decimal(prefix.getValue().toString()).pow(prefix.getExponent());
            String symbol = units.get(u.transform(Converters.scale(Factor.ONE.divide(value))));
            if (symbol != null && parsesTo(prefix.getSymbol() + symbol, u)) {
                return prefix.getSymbol() + symbol;
            }
        }
        return null;
    }

    /** {@code 1000·min}: a power of ten from 10 up times a unit of the systems that reads back as {@code u}. */
    private String multiple(UomUnit<?> u) {
        if (!u.toSystemConverter().isLinear() || u.toSystemConverter().factor().signum() <= 0) {
            return null;
        }
        Map<UomUnit<?>, String> units = symbols();
        Factor ten = Factor.ofLong(10);
        for (int n = 1; n <= MAX_POWER; n++) {
            Factor power = ten.pow(n);
            String symbol = units.get(u.transform(Converters.scale(Factor.ONE.divide(power))));
            if (symbol != null && parsesTo(formula(power, symbol), u)) {
                return formula(power, symbol);
            }
        }
        return null;
    }

    private Map<UomUnit<?>, String> symbols() {
        Map<UomUnit<?>, String> units = symbols;
        if (units == null) {
            units = new HashMap<>();
            for (UomSystemOfUnits system : systems) {
                for (Unit<?> known : system.getUnits()) {
                    if (known.getSymbol() != null) {
                        units.putIfAbsent((UomUnit<?>) known, known.getSymbol());
                    }
                }
            }
            symbols = units;
        }
        return units;
    }

    private Map<UomUnit<?>, String> prefixable() {
        Map<UomUnit<?>, String> units = prefixable;
        if (units == null) {
            units = new HashMap<>();
            for (UomSystemOfUnits system : systems) {
                for (Unit<?> known : system.getUnits()) {
                    String symbol = known.getSymbol();
                    if (symbol != null && (system.isPrefixable(symbol) || system.isBinaryPrefixable(symbol))) {
                        units.putIfAbsent((UomUnit<?>) known, known.getSymbol());
                    }
                }
            }
            prefixable = units;
        }
        return units;
    }

    /** The symbol of {@code unit}, or the label of its system unit if it has none (not its definition). */
    private static String shown(UomUnit<?> unit) {
        return unit.getSymbol() != null ? unit.getSymbol() : unit.getSystemUnit().toString();
    }

    /** {@code n|d·pi^k·label}; juxtaposition binds tighter than the left-associative {@code ·} and {@code /}. */
    private static String formula(Factor f, String systemLabel) {
        StringBuilder sb = new StringBuilder();
        if (!f.numerator().equals(BigInteger.ONE) || !f.denominator().equals(BigInteger.ONE)) {
            sb.append(f.numerator());
            if (!f.denominator().equals(BigInteger.ONE)) {
                sb.append('|').append(f.denominator());
            }
            sb.append('·');
        }
        int k = f.piExponent();
        if (k != 0) {
            sb.append(k == 1 ? "pi" : "pi^" + k).append('·');
        }
        return sb.append(systemLabel).toString();
    }

    /** {@code o/f}, or null when it exceeds the factor size limit (its text would be too long anyway). */
    private static Factor shiftOf(Factor f, Factor offset) {
        try {
            return offset.divide(f);
        } catch (ArithmeticException e) {
            return null;
        }
    }

    /** {@code " + s"} or {@code " - s"}: a terminating plain decimal unless the exact {@code a|b} is shorter. */
    private static String shift(Factor s) {
        if (s.isZero()) {
            return "";
        }
        BigInteger n = s.numerator().abs();
        BigInteger d = s.denominator();
        String fraction = d.equals(BigInteger.ONE) ? n.toString() : n + "|" + d;
        String text;
        try {
            String decimal = new BigDecimal(n).divide(new BigDecimal(d)).toPlainString();
            text = decimal.length() <= fraction.length() ? decimal : fraction;
        } catch (ArithmeticException e) {
            text = fraction;
        }
        return (s.signum() < 0 ? " - " : " + ") + text;
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
            return FormulaEvaluator.evaluate(s.substring(lead).strip(), this::lookup);
        } catch (FormulaException e) {
            throw new MeasurementParseException(e.getMessage(), csq, Math.min(lead + e.column() - 1, s.length()));
        }
    }

    UomUnit<?> lookup(String symbol) {
        UomUnit<?> alias = aliases.get(symbol);
        return alias != null ? alias : resolve(symbol).orElse(null);
    }

    /**
     * The unit of an exact symbol of the systems, else of a prefix on a prefixable unit; never a label.
     *
     * @param symbol the symbol
     * @return the unit, or empty
     */
    public Optional<UomUnit<?>> resolve(String symbol) {
        Found found = resolve(systems, Objects.requireNonNull(symbol, "symbol"));
        return found == null ? Optional.empty() : Optional.of(found.unit());
    }

    private record Found(UomSystemOfUnits system, UomUnit<?> unit) {}

    private static Found resolve(List<UomSystemOfUnits> systems, String symbol) {
        for (UomSystemOfUnits system : systems) {
            Optional<UomUnit<?>> unit = system.lookup(symbol);
            if (unit.isPresent()) {
                return new Found(system, unit.get());
            }
        }
        for (Prefix prefix : PREFIXES) {
            String p = prefix.getSymbol();
            if (symbol.length() > p.length() && symbol.startsWith(p)) {
                String rest = symbol.substring(p.length());
                for (UomSystemOfUnits system : systems) {
                    Optional<UomUnit<?>> unit = system.lookup(rest);
                    if (takes(system, rest, prefix) && unit.isPresent()) {
                        UomUnit<?> u = unit.get();
                        return new Found(
                                system,
                                u.prefix(prefix)
                                        .named(symbol, prefix.getName().toLowerCase(Locale.ROOT) + u.getName()));
                    }
                }
            }
        }
        return null;
    }

    /** Whether {@code prefix} applies to the unit {@code symbol} of {@code system}; see the class comment. */
    private static boolean takes(UomSystemOfUnits system, String symbol, Prefix prefix) {
        if (prefix instanceof BinaryPrefix) {
            return system.isBinaryPrefixable(symbol);
        }
        return system.isPrefixable(symbol) && (!system.isBinaryPrefixable(symbol) || prefix.getExponent() >= 3);
    }

    /**
     * Adds {@code label} as an alias that parses to {@code unit} and formats it; a previous holder of the
     * same label loses it. Rejects labels that would not parse back or that name a different unit of the systems, exactly or as a prefix on a prefixable unit.
     */
    @Override
    public synchronized void label(Unit<?> unit, String label) {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(label, "label");
        if (!(unit instanceof UomUnit<?> u)) {
            throw new IllegalArgumentException("foreign unit implementation: " + unit);
        }
        if (label.isEmpty() || label.equals("pi") || "0123456789.".indexOf(label.charAt(0)) >= 0
                || label.chars().anyMatch(c -> Character.isWhitespace(c) || EXCLUDED.indexOf(c) >= 0)) {
            throw new IllegalArgumentException("invalid label: '" + label + "'");
        }
        // An exact symbol, or a prefix on a prefixable unit ("Gm" is the gigametre, not a label for seconds).
        Found existing = resolve(systems, label);
        if (existing != null && !existing.unit().equals(u)) {
            throw new IllegalArgumentException("label '" + label + "' is the symbol of " + existing.unit().getName());
        }
        UomUnit<?> previous = aliases.put(label, u);
        if (previous != null && !previous.equals(u)) {
            labels.remove(previous, label);
        }
        labels.put(u, label);
    }

    @Override
    public boolean isLocaleSensitive() {
        return false;
    }
}
