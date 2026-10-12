// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.systems;

import java.io.IOException;
import java.math.BigInteger;
import java.text.ParsePosition;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.measure.MetricPrefix;
import javax.measure.Unit;
import javax.measure.format.MeasurementParseException;
import javax.measure.format.UnitFormat;
import org.tinygears.uom.core.Factor;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.core.parse.FormulaEvaluator;
import org.tinygears.uom.core.parse.FormulaException;

/**
 * Formats units by label or symbol and parses unit formulas such as {@code kg·m/s^2} over systems of units.
 * Symbols resolve as labels, then exact symbols, then a metric prefix on a prefixable unit. Thread-safe.
 */
public final class UomUnitFormat implements UnitFormat {
    private static final String EXCLUDED = "*/()^|=#[]!~@,·+-";
    private static final List<MetricPrefix> PREFIXES = Arrays.stream(MetricPrefix.values())
            .sorted(Comparator.comparingInt((MetricPrefix p) -> p.getSymbol().length()).reversed())
            .toList();

    private final List<UomSystemOfUnits> systems;
    private final Map<String, UomUnit<?>> aliases = new ConcurrentHashMap<>();
    private final Map<UomUnit<?>, String> labels = new ConcurrentHashMap<>();

    public UomUnitFormat(List<UomSystemOfUnits> systems) {
        this.systems = List.copyOf(systems);
    }

    /** A new format over {@link UnitSystems#all()}. */
    public static UomUnitFormat standard() {
        return new UomUnitFormat(UnitSystems.all());
    }

    /**
     * The first of these texts that parses back to an equal unit: the label, the symbol, the symbol of an equal
     * unit of the systems, an exact formula such as {@code 1|1000·m/s}.
     *
     * @throws IllegalArgumentException when none does: a unit with an offset, a negative factor, a factor with a
     *     root or a formula too long to parse has no faithful form (offset units get one in milestone 6)
     */
    @Override
    public String format(Unit<?> unit) {
        String label = labels.get(Objects.requireNonNull(unit, "unit"));
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
        Factor f = u.toSystemConverter().factor();
        Factor offset = u.toSystemConverter().offset();
        if (offset.isZero() && f.signum() > 0 && f.rootIndex() == 1) {
            String formula = formula(f, u.getSystemUnit().getSymbol());
            if (parsesTo(formula, unit)) {
                return formula;
            }
        }
        throw new IllegalArgumentException(
                "unit can not be formatted: " + unit + " (factor " + f + ", offset " + offset + ")");
    }

    private boolean parsesTo(String text, Unit<?> unit) {
        try {
            return unit.equals(FormulaEvaluator.evaluate(text, this::lookup));
        } catch (FormulaException e) {
            return false;
        }
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
        if (alias != null) {
            return alias;
        }
        for (UomSystemOfUnits system : systems) {
            Optional<UomUnit<?>> unit = system.lookup(symbol);
            if (unit.isPresent()) {
                return unit.get();
            }
        }
        for (MetricPrefix prefix : PREFIXES) {
            String p = prefix.getSymbol();
            if (symbol.length() > p.length() && symbol.startsWith(p)) {
                String rest = symbol.substring(p.length());
                for (UomSystemOfUnits system : systems) {
                    Optional<UomUnit<?>> unit = system.lookup(rest);
                    if (system.isPrefixable(rest) && unit.isPresent()) {
                        UomUnit<?> u = unit.get();
                        return u.prefix(prefix).named(symbol, prefix.name().toLowerCase(Locale.ROOT) + u.getName());
                    }
                }
            }
        }
        return null;
    }

    /**
     * Adds {@code label} as an alias that parses to {@code unit} and formats it; a previous holder of the
     * same label loses it. Rejects labels that would not parse back or that name a different catalog unit.
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
        for (UomSystemOfUnits system : systems) {
            Optional<UomUnit<?>> existing = system.lookup(label);
            if (existing.isPresent() && !existing.get().equals(u)) {
                throw new IllegalArgumentException("label '" + label + "' is the symbol of " + existing.get().getName());
            }
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
