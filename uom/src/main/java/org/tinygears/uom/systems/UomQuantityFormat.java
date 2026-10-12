// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.systems;

import java.io.IOException;
import java.text.DecimalFormatSymbols;
import java.text.ParsePosition;
import java.util.HashMap;
import java.util.Locale;
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
 * Formats a quantity as its value ({@code BigDecimal.toString()} for a decimal quantity), a space and its unit,
 * and parses the same form; parsing always gives a {@code double} quantity, so trailing zeros and digits beyond
 * double precision are not preserved. Immutable and thread-safe. An offset unit without a symbol formats as a
 * shifted formula ({@code 1.0 K + 5}); catalog offset units print their symbols.
 * Formatting throws {@link IllegalArgumentException} for a unit the unit format refuses: a negative factor, a
 * factor with a root, an irrational shift or a formula too long to parse.
 *
 * <p>The default locale is {@link Locale#ROOT}: the value text is exactly {@code Double.toString} or
 * {@code BigDecimal.toString} and parsing uses {@link Double#parseDouble}. With {@link #withLocale} only the
 * number is localized, never the unit: the same text is written with each ASCII digit, {@code .}, {@code -} and
 * {@code E} replaced by the locale's digit, decimal separator, minus sign and exponent separator of
 * {@link DecimalFormatSymbols} ({@code -1,5E10 km} in {@code de-DE}, {@code \u22121,5×10^10 km} in {@code sv-SE});
 * an exponent {@code +} stays ASCII, {@code NaN}, {@code Infinity} and {@code -Infinity} keep their ROOT text and
 * no grouping separator is written. Parsing accepts the locale's digits and ASCII digits, only the locale's
 * decimal separator, the locale's minus sign, {@code -} or {@code +}, and the locale's exponent separator,
 * {@code E} or {@code e}. A grouping separator (exactly the locale's) is an error unless {@link #withGrouping}
 * is on, in which case it is skipped between two digits; {@code withGrouping(true)} on ROOT skips {@code ,}.
 * The number ends at the first whitespace ({@link Character#isWhitespace}, which excludes the no-break spaces
 * some locales group with). A locale whose symbols would make numbers ambiguous is refused by {@code withLocale}.
 */
public final class UomQuantityFormat implements QuantityFormat {
    private static final DecimalFormatSymbols ROOT_SYMBOLS = DecimalFormatSymbols.getInstance(Locale.ROOT);

    private final UomUnitFormat unitFormat;
    private final Locale locale;
    private final boolean grouping;
    private final char zero;
    private final char decimal;
    private final char group;
    private final char minus;
    private final String exponent;

    /** A ROOT format without grouping. */
    public UomQuantityFormat(UomUnitFormat unitFormat) {
        this(Objects.requireNonNull(unitFormat, "unitFormat"), Locale.ROOT, ROOT_SYMBOLS, false);
    }

    private UomQuantityFormat(UomUnitFormat unitFormat, Locale locale, DecimalFormatSymbols symbols,
            boolean grouping) {
        this.unitFormat = unitFormat;
        this.locale = locale;
        this.grouping = grouping;
        this.zero = symbols.getZeroDigit();
        this.decimal = symbols.getDecimalSeparator();
        this.group = symbols.getGroupingSeparator();
        this.minus = symbols.getMinusSign();
        this.exponent = symbols.getExponentSeparator();
        check();
    }

    /**
     * This format with numbers in {@code locale}; throws {@link IllegalArgumentException} if the locale's number
     * symbols collide so that a number could not round trip (no available locale of JDK 25 does).
     */
    public UomQuantityFormat withLocale(Locale locale) {
        return withSymbols(locale, DecimalFormatSymbols.getInstance(Objects.requireNonNull(locale, "locale")));
    }

    UomQuantityFormat withSymbols(Locale locale, DecimalFormatSymbols symbols) {
        return new UomQuantityFormat(unitFormat, locale, symbols, grouping);
    }

    /** This format accepting (never writing) the locale's grouping separator between digits when parsing. */
    public UomQuantityFormat withGrouping(boolean grouping) {
        return new UomQuantityFormat(this, grouping);
    }

    private UomQuantityFormat(UomQuantityFormat base, boolean grouping) {
        this.unitFormat = base.unitFormat;
        this.locale = base.locale;
        this.grouping = grouping;
        this.zero = base.zero;
        this.decimal = base.decimal;
        this.group = base.group;
        this.minus = base.minus;
        this.exponent = base.exponent;
    }

    public Locale getLocale() {
        return locale;
    }

    /** True unless the locale is ROOT. */
    @Override
    public boolean isLocaleSensitive() {
        return !Locale.ROOT.equals(locale);
    }

    private void check() {
        for (int i = 0; i < 10; i++) {
            if (Character.digit(zero + i, 10) != i) {
                throw unsafe("zero digit '" + zero + "' does not start a digit run");
            }
        }
        if (decimal == group) {
            throw unsafe("decimal separator '" + decimal + "' equals grouping separator");
        }
        checkSymbol("decimal separator", decimal);
        checkSymbol("grouping separator", group);
        checkSymbol("minus sign", minus);
        if (minus == decimal || minus == group || decimal == '-' || group == '-') {
            throw unsafe("minus sign '" + minus + "' collides with a separator");
        }
        if (exponent.isEmpty() || exponent.codePoints().anyMatch(Character::isWhitespace)) {
            throw unsafe("exponent separator '" + exponent + "' is empty or has a space");
        }
        char first = exponent.charAt(0);
        if (isDigit(first) || first == decimal || first == group || first == minus || first == '-' || first == '+') {
            throw unsafe("exponent separator '" + exponent + "' starts with a digit, sign or separator");
        }
    }

    private void checkSymbol(String what, char c) {
        if (Character.isLetterOrDigit(c) || Character.isWhitespace(c) || c == '+') {
            throw unsafe(what + " '" + c + "' is a letter, digit or space");
        }
    }

    private IllegalArgumentException unsafe(String reason) {
        return new IllegalArgumentException(
                "locale " + locale.toLanguageTag() + " cannot round trip numbers: " + reason);
    }

    @Override
    public String format(Quantity<?> quantity) {
        String value = String.valueOf(quantity.getValue());
        if (isLocaleSensitive() && !value.endsWith("NaN") && !value.endsWith("Infinity")) {
            value = localize(value);
        }
        return value + " " + unitFormat.format(quantity.getUnit());
    }

    /** Maps the ASCII number text character by character; the digits are never rounded. */
    private String localize(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append((char) (zero + c - '0'));
            } else if (c == '.') {
                sb.append(decimal);
            } else if (c == '-') {
                sb.append(minus);
            } else if (c == 'E') {
                sb.append(exponent);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
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
            value = Double.parseDouble(isLocaleSensitive() || grouping ? normalize(csq, from, to) : token);
        } catch (NumberFormatException e) {
            throw new MeasurementParseException(
                    token.isEmpty() ? "missing number" : "invalid number '" + token + "'", csq, from);
        }
        UomUnit<?> unit = s.substring(to).isBlank() ? BaseUnits.ONE : unitFormat.parseFrom(csq, to);
        return create(value, unit);
    }

    /**
     * The number in {@code csq[from, to)} as ASCII text for {@link Double#parseDouble} or {@code new BigDecimal};
     * throws {@link NumberFormatException} for a character outside the locale's number symbols.
     */
    private String normalize(CharSequence csq, int from, int to) {
        String s = csq.toString();
        String token = s.substring(from, to);
        int signed = token.isEmpty() || !isSign(token.charAt(0)) ? 0 : 1;
        String rest = token.substring(signed);
        if (rest.equals("NaN") || rest.equals("Infinity")) {
            return (signed == 1 && token.charAt(0) != '+' ? "-" : "") + rest;
        }
        StringBuilder sb = new StringBuilder(token.length());
        int i = from;
        while (i < to) {
            char c = s.charAt(i);
            if (isDigit(c)) {
                sb.append((char) ('0' + (c >= '0' && c <= '9' ? c - '0' : c - zero)));
            } else if (c == decimal) {
                sb.append('.');
            } else if (c == group) {
                if (!grouping) {
                    throw new MeasurementParseException("grouping separator not allowed: '" + c + "'", csq, i);
                }
                if (i == from || !isDigit(s.charAt(i - 1)) || i + 1 >= to || !isDigit(s.charAt(i + 1))) {
                    throw new MeasurementParseException("grouping separator not between digits", csq, i);
                }
            } else if (isSign(c)) {
                sb.append(c == '+' ? '+' : '-');
            } else if (s.startsWith(exponent, i)) {
                sb.append('E');
                i += exponent.length();
                continue;
            } else if (c == 'E' || c == 'e') {
                sb.append('E');
            } else {
                throw new NumberFormatException();
            }
            i++;
        }
        return sb.toString();
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9' || c >= zero && c < zero + 10;
    }

    private boolean isSign(char c) {
        return c == minus || c == '-' || c == '+';
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
