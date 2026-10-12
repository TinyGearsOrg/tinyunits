// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.systems;

import java.io.IOException;
import java.text.ParsePosition;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.measure.Quantity;
import javax.measure.format.MeasurementParseException;
import javax.measure.format.QuantityFormat;
import javax.measure.spi.QuantityFactory;
import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.core.quantity.UntypedDoubleQuantity;
import org.tinygears.uom.quantity.QuantityTypes;

/**
 * Formats a quantity as its value, a space and its unit, and parses that form back. Thread-safe. Formatting
 * throws {@link IllegalArgumentException} for a unit the unit format refuses (offset units until milestone 6).
 */
public final class UomQuantityFormat implements QuantityFormat {
    private final UomUnitFormat unitFormat;

    public UomQuantityFormat(UomUnitFormat unitFormat) {
        this.unitFormat = Objects.requireNonNull(unitFormat, "unitFormat");
    }

    @Override
    public String format(Quantity<?> quantity) {
        return quantity.getValue() + " " + unitFormat.format(quantity.getUnit());
    }

    @Override
    public Appendable format(Quantity<?> quantity, Appendable destination) throws IOException {
        return destination.append(format(quantity));
    }

    /** A number alone is dimensionless; the quantity has the standard type of its unit, if any. */
    @Override
    public Quantity<?> parse(CharSequence csq) throws MeasurementParseException {
        return parseFrom(csq, 0);
    }

    @Override
    public Quantity<?> parse(CharSequence csq, ParsePosition pos) throws MeasurementParseException {
        try {
            Quantity<?> quantity = parseFrom(csq, pos.getIndex());
            pos.setIndex(csq.length());
            return quantity;
        } catch (MeasurementParseException e) {
            pos.setErrorIndex(e.getPosition());
            throw e;
        }
    }

    private Quantity<?> parseFrom(CharSequence csq, int start) {
        String s = csq.toString();
        if (start < 0 || start > s.length()) {
            throw new MeasurementParseException("index out of range: " + start, csq, start < 0 ? 0 : s.length());
        }
        int from = start;
        while (from < s.length() && Character.isWhitespace(s.charAt(from))) {
            from++;
        }
        int to = from;
        while (to < s.length() && !Character.isWhitespace(s.charAt(to))) {
            to++;
        }
        String token = s.substring(from, to);
        double value;
        try {
            value = Double.parseDouble(token);
        } catch (NumberFormatException e) {
            throw new MeasurementParseException(
                    token.isEmpty() ? "missing number" : "invalid number '" + token + "'", csq, from);
        }
        UomUnit<?> unit = s.substring(to).isBlank() ? BaseUnits.ONE : unitFormat.parseFrom(csq, to);
        return create(value, unit);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Quantity<?> create(double value, UomUnit<?> unit) {
        QuantityFactory factory = Factories.BY_SYSTEM_UNIT.get(unit.getSystemUnit());
        return factory != null ? factory.create(value, unit) : UntypedDoubleQuantity.of(value, unit);
    }

    /** The factory of each standard type by its system unit, built on first use. */
    static final class Factories {
        static final Map<UomUnit<?>, QuantityFactory<?>> BY_SYSTEM_UNIT = build();

        private Factories() {
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static Map<UomUnit<?>, QuantityFactory<?>> build() {
            Map<UomUnit<?>, QuantityFactory<?>> map = new HashMap<>();
            for (Class<?> type : QuantityTypes.types()) {
                QuantityFactory<?> factory;
                try {
                    factory = QuantityTypes.factory((Class) type);
                } catch (RuntimeException | LinkageError e) {
                    // A type whose factory fails to initialise parses as untyped (D8).
                    continue;
                }
                UomUnit<?> systemUnit = (UomUnit<?>) factory.getSystemUnit();
                if (map.put(systemUnit, factory) != null) {
                    throw new IllegalStateException("two quantity types with the system unit " + systemUnit);
                }
            }
            return Map.copyOf(map);
        }
    }
}
