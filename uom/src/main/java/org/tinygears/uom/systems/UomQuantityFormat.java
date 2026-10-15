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
import java.math.MathContext;
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
import org.tinygears.uom.core.quantity.AbstractDecimalQuantity;
import org.tinygears.uom.core.quantity.UntypedDecimalQuantity;
import org.tinygears.uom.core.quantity.UntypedDoubleQuantity;
import org.tinygears.uom.quantity.QuantityTypes;

/**
 * Formats a quantity as its value ({@code BigDecimal.toString()} for a decimal quantity), a space and its unit,
 * and parses the same form; parsing gives a {@code double} quantity unless {@link #withDecimals} is set, which
 * keeps every digit and the scale of the text. Immutable and thread-safe. An offset unit without a symbol formats as a
 * shifted formula ({@code 1.0 K + 5}); catalog offset units print their symbols.
 * Formatting throws {@link IllegalArgumentException} for a unit the unit format refuses: a negative factor, a
 * factor with a root, an irrational shift or a formula too long to parse.
 *
 * <p>The default locale is {@link Locale#ROOT}: the value text is exactly {@code Double.toString} or
 * {@code BigDecimal.toString} and parsing uses {@link Double#parseDouble} (with decimals: {@code new BigDecimal},
 * ASCII digits only). With {@link #withLocale} only the
 * number is localized, never the unit: the same text is written with each ASCII digit, {@code .}, {@code -} and
 * {@code E} replaced by the locale's digit, decimal separator, minus sign and exponent separator of
 * {@link DecimalFormatSymbols} ({@code -1,5E10 km} in {@code de-DE}, {@code \u22121,5×10^10 km} in {@code sv-SE});
 * an exponent {@code +} stays ASCII, {@code NaN}, {@code Infinity} and {@code -Infinity} keep their ROOT text, and
 * neither grouping separators nor the bidi marks that {@code DecimalFormat} puts around a minus sign are written.
 *
 * <p>Parsing in a locale accepts the locale's digits and ASCII digits, mixed freely in one number; only the
 * locale's decimal separator; the locale's minus sign, {@code -} or {@code +}; and the locale's exponent separator,
 * {@code E} or {@code e} (inside a multi-character separator such as {@code fa}'s {@code ×۱۰^} the characters must
 * match exactly, so {@code ۱×10^۱۰} is invalid while {@code ۱E۱۰} is accepted). Format characters (Unicode
 * {@code Cf}, such as bidi marks) are skipped at the start and end of the number and after a leading sign. Only
 * the exact grouping character of the locale is recognised ({@code fr-FR} groups with U+202F, so U+00A0 is
 * rejected). A grouping separator is an error unless {@link #withGrouping} is on; then it is skipped between two
 * digits of the integer part, without checking group sizes ({@code 1.5 km} in {@code de-DE} is 15 km), and is
 * an error after the decimal separator or in the exponent. The number ends at the first whitespace
 * ({@link Character#isWhitespace}, which excludes no-break spaces): {@code 1 234 km} with ASCII spaces is 1 of
 * the unit {@code 234 km} (the unit grammar's product), so space-grouping locales need a no-break grouping
 * character. {@code withGrouping(true)} on ROOT groups with {@code ,} and no longer accepts the extra forms of
 * {@code Double.parseDouble} (hexadecimal floats, a {@code d} or {@code f} suffix). A locale whose symbols would
 * make numbers ambiguous is refused by {@code withLocale}.
 */
public final class UomQuantityFormat implements QuantityFormat {
    private static final DecimalFormatSymbols ROOT_SYMBOLS = DecimalFormatSymbols.getInstance(Locale.ROOT);
    /** The value-scale bound of the decimal quantities, checked before parsing so huge texts fail fast. */
    private static final int MAX_SCALE = 100_000;

    private final UomUnitFormat unitFormat;
    private final Locale locale;
    private final boolean grouping;
    private final char zero;
    private final char decimal;
    private final char group;
    private final char minus;
    private final String exponent;
    private final MathContext decimals;

    /**
     * A ROOT format without grouping, parsing to {@code double}.
     *
     * @param unitFormat the format of the units
     */
    public UomQuantityFormat(UomUnitFormat unitFormat) {
        this(Objects.requireNonNull(unitFormat, "unitFormat"), Locale.ROOT, ROOT_SYMBOLS, false, null);
    }

    private UomQuantityFormat(
            UomUnitFormat unitFormat,
            Locale locale,
            DecimalFormatSymbols symbols,
            boolean grouping,
            MathContext decimals
    ) {
        this.unitFormat = unitFormat;
        this.locale = locale;
        this.grouping = grouping;
        this.decimals = decimals;
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
     *
     * @param locale the locale of the numbers
     * @return the new format
     */
    public UomQuantityFormat withLocale(Locale locale) {
        return withSymbols(locale, DecimalFormatSymbols.getInstance(Objects.requireNonNull(locale, "locale")));
    }

    UomQuantityFormat withSymbols(Locale locale, DecimalFormatSymbols symbols) {
        return new UomQuantityFormat(unitFormat, locale, symbols, grouping, decimals);
    }

    /**
     * This format accepting (never writing) the locale's grouping separator between digits when parsing.
     *
     * @param grouping whether to accept the separator
     * @return the new format
     */
    public UomQuantityFormat withGrouping(boolean grouping) {
        return new UomQuantityFormat(this, grouping, decimals);
    }

    /**
     * This format parsing to decimal quantities with context {@code mc}; formatting is unchanged. The number text,
     * read with the same locale and grouping rules (in ROOT too, so only ASCII digits there), becomes
     * {@code new BigDecimal(text)} with every digit and its scale kept, never rounded to {@code mc} ({@code 1.50 km}
     * has scale 2). The quantity has scale {@code ABSOLUTE} and the standard type of its unit, else it is an
     * {@link UntypedDecimalQuantity}. NaN, infinity, the forms only {@link Double#parseDouble} accepts (hexadecimal,
     * a {@code d} or {@code f} suffix) and a value scale beyond ±100000 are {@link MeasurementParseException}s,
     * reported before any unit error. Throws {@link ArithmeticException}, as the quantities do, if the precision of
     * {@code mc} exceeds 100000.
     *
     * @param mc the context of parsed quantities
     * @return the new format
     */
    public UomQuantityFormat withDecimals(MathContext mc) {
        // the quantities' own precision check
        UntypedDecimalQuantity.of(BigDecimal.ZERO, BaseUnits.ONE, Objects.requireNonNull(mc, "mc"));
        return new UomQuantityFormat(this, grouping, mc);
    }

    private UomQuantityFormat(UomQuantityFormat base, boolean grouping, MathContext decimals) {
        this.unitFormat = base.unitFormat;
        this.locale = base.locale;
        this.grouping = grouping;
        this.decimals = decimals;
        this.zero = base.zero;
        this.decimal = base.decimal;
        this.group = base.group;
        this.minus = base.minus;
        this.exponent = base.exponent;
    }

    /**
     * The locale of the numbers; {@link Locale#ROOT} unless set by {@link #withLocale}.
     *
     * @return the locale
     */
    public Locale getLocale() {
        return locale;
    }

    /**
     * The context of parsed decimal quantities, or null if parsing gives {@code double} quantities.
     *
     * @return the context, or null
     */
    public MathContext getDecimalContext() {
        return decimals;
    }

    /** True unless the locale is ROOT. */
    @Override
    public boolean isLocaleSensitive() {
        return !Locale.ROOT.equals(locale);
    }

    /** Equal formats have the same locale symbols, grouping and decimal context and the same unit format instance. */
    @Override
    public boolean equals(Object o) {
        return o instanceof UomQuantityFormat f && f.unitFormat == unitFormat && f.locale.equals(locale)
                && f.grouping == grouping && f.zero == zero && f.decimal == decimal && f.group == group
                && f.minus == minus && f.exponent.equals(exponent) && Objects.equals(f.decimals, decimals);
    }

    @Override
    public int hashCode() {
        return Objects.hash(System.identityHashCode(unitFormat), locale, grouping, decimals);
    }

    @Override
    public String toString() {
        return "UomQuantityFormat[locale=" + locale.toLanguageTag() + ", grouping=" + grouping + ", decimals="
                + (decimals == null
                        ? "off"
                        : decimals.equals(MathContext.UNLIMITED)
                                ? "unlimited"
                                : decimals.getPrecision() + "/" + decimals.getRoundingMode())
                + "]";
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
        if (Character.isLetterOrDigit(c) || Character.isWhitespace(c) || isFormatMark(c) || c == '+') {
            throw unsafe(what + " '" + c + "' is a letter, digit, space, format mark or plus");
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
        String unit = unitFormat.format(quantity.getUnit());
        // A number alone is dimensionless when parsed (parse).
        return unit.equals("1") ? value : value + " " + unit;
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
        double value = 0;
        BigDecimal decimal = null;
        try {
            // plain ROOT doubles keep Double.parseDouble's grammar; decimals accept ASCII or the locale's digits only
            String text = isLocaleSensitive() || grouping || decimals != null ? normalize(csq, from, to) : token;
            if (decimals == null) {
                value = Double.parseDouble(text);
            } else {
                String unsigned = text.startsWith("+") || text.startsWith("-") ? text.substring(1) : text;
                if (unsigned.equals("NaN") || unsigned.equals("Infinity")) {
                    throw new MeasurementParseException(
                            "decimal quantities have no NaN or infinity: '" + token + "'",
                            csq,
                            from);
                }
                long scale = scaleOf(text);
                if (scale > MAX_SCALE || scale < -MAX_SCALE) {
                    throw new MeasurementParseException(
                            "invalid number '" + token + "': scale out of range"
                                    + (scale == Long.MAX_VALUE || scale == Long.MIN_VALUE ? "" : ": " + scale),
                            csq,
                            from);
                }
                decimal = new BigDecimal(text);
            }
        } catch (NumberFormatException e) {
            throw new MeasurementParseException(
                    token.isEmpty() ? "missing number" : "invalid number '" + token + "'",
                    csq,
                    from);
        }
        UomUnit<?> unit = s.substring(to).isBlank() ? BaseUnits.ONE : unitFormat.parseFrom(csq, to);
        if (decimals == null) {
            return create(value, unit);
        }
        try {
            return create(decimal, unit, decimals);
        } catch (ArithmeticException e) {
            throw new MeasurementParseException("invalid number '" + token + "': " + e.getMessage(), csq, from);
        }
    }

    /**
     * The scale {@code new BigDecimal(text)} would have, in linear time, so a huge text fails before the quadratic
     * parse; 0 if {@code text} is malformed (BigDecimal rejects it) and {@link Long#MAX_VALUE} or
     * {@link Long#MIN_VALUE} for an exponent of 10^15 or more.
     */
    private static long scaleOf(String text) {
        int i = text.startsWith("+") || text.startsWith("-") ? 1 : 0;
        int digits = 0;
        long fraction = 0;
        boolean point = false;
        for (; i < text.length() && text.charAt(i) != 'E'; i++) {
            char c = text.charAt(i);
            if (c == '.' && !point) {
                point = true;
            } else if (c >= '0' && c <= '9') {
                digits++;
                fraction += point ? 1 : 0;
            } else {
                return 0;
            }
        }
        if (digits == 0) {
            return 0;
        }
        if (i == text.length()) {
            return fraction;
        }
        boolean negative = i + 1 < text.length() && text.charAt(i + 1) == '-';
        int start = i + 1 < text.length() && (negative || text.charAt(i + 1) == '+') ? i + 2 : i + 1;
        if (start == text.length()) {
            return 0;
        }
        long e = 0;
        for (int j = start; j < text.length(); j++) {
            char c = text.charAt(j);
            if (c < '0' || c > '9') {
                return 0;
            }
            // saturates: an exponent of 10^15 or more is out of range whatever the fraction
            e = e >= 1_000_000_000_000_000L ? e : e * 10 + c - '0';
        }
        if (e >= 1_000_000_000_000_000L) {
            return negative ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        return fraction - (negative ? -e : e);
    }

    /**
     * The number in {@code csq[from, to)} as ASCII text for {@link Double#parseDouble} or {@code new BigDecimal};
     * throws {@link NumberFormatException} for a character outside the locale's number symbols.
     */
    private String normalize(CharSequence csq, int from, int to) {
        String s = csq.toString();
        // Format marks (Cf) before or after the number and after a leading sign, as DecimalFormat writes them.
        while (from < to && isFormatMark(s.charAt(from))) {
            from++;
        }
        while (to > from && isFormatMark(s.charAt(to - 1))) {
            to--;
        }
        int i = from;
        StringBuilder sb = new StringBuilder(to - from);
        if (i < to && isSign(s.charAt(i))) {
            sb.append(s.charAt(i) == '+' ? '+' : '-');
            i++;
            while (i < to && isFormatMark(s.charAt(i))) {
                i++;
            }
        }
        String rest = s.substring(i, to);
        if (rest.equals("NaN") || rest.equals("Infinity")) {
            return sb + rest;
        }
        boolean fraction = false;
        boolean exponentPart = false;
        while (i < to) {
            char c = s.charAt(i);
            if (isDigit(c)) {
                sb.append((char) ('0' + (c >= '0' && c <= '9' ? c - '0' : c - zero)));
            } else if (c == decimal) {
                sb.append('.');
                fraction = true;
            } else if (c == group) {
                if (!grouping) {
                    throw new MeasurementParseException("grouping separator not allowed: '" + c + "'", csq, i);
                }
                if (exponentPart || fraction) {
                    throw new MeasurementParseException(
                            "grouping separator not allowed "
                                    + (exponentPart ? "in the exponent" : "after the decimal separator"),
                            csq,
                            i);
                }
                if (i == from || !isDigit(s.charAt(i - 1)) || i + 1 >= to || !isDigit(s.charAt(i + 1))) {
                    throw new MeasurementParseException("grouping separator not between digits", csq, i);
                }
            } else if (isSign(c)) {
                sb.append(c == '+' ? '+' : '-');
            } else if (s.startsWith(exponent, i)) {
                sb.append('E');
                exponentPart = true;
                i += exponent.length();
                continue;
            } else if (c == 'E' || c == 'e') {
                sb.append('E');
                exponentPart = true;
            } else {
                throw new NumberFormatException();
            }
            i++;
        }
        return sb.toString();
    }

    private static boolean isFormatMark(char c) {
        return Character.getType(c) == Character.FORMAT;
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9' || c >= zero && c < zero + 10;
    }

    private boolean isSign(char c) {
        return c == minus || c == '-' || c == '+';
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Quantity<?> create(double value, UomUnit<?> unit) {
        QuantityFactory factory = Factories.BY_SYSTEM_UNIT.get(unit.getSystemUnit());
        return factory != null ? factory.create(value, unit) : UntypedDoubleQuantity.of(value, unit);
    }

    /** Keeps every digit of {@code value}: the factory's decimal quantity then takes {@code mc} without rounding. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Quantity<?> create(BigDecimal value, UomUnit<?> unit, MathContext mc) {
        QuantityFactory factory = Factories.BY_SYSTEM_UNIT.get(unit.getSystemUnit());
        return factory != null
                ? ((AbstractDecimalQuantity<?, ?>) factory.create(value, unit)).withMathContext(mc)
                : UntypedDecimalQuantity.of(value, unit, mc);
    }

    /** The factory of each standard type by its system unit, built on first use. */
    static final class Factories {
        static final Map<UomUnit<?>, QuantityFactory<?>> BY_SYSTEM_UNIT = build();

        private Factories() {
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
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
