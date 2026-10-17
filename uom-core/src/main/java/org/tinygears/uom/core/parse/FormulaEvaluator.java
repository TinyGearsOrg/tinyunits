/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.core.parse;

import java.math.BigInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.Converters;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.engine.Factor;

/**
 * Evaluates unit formulas such as {@code kg m / s^2} with a recursive-descent parser.
 *
 * <p>A formula may end in a shift {@code +n} or {@code -n} ({@code n} a decimal literal or {@code a|b}, spaces
 * allowed around the sign) that applies to the whole expression: {@code K + 273.15} is {@code K.shift(273.15)}, so
 * a value {@code v} in it is {@code v + 273.15} K. The shift is top level only, never inside parentheses. A
 * {@code -} glued on both sides to a plain integer ({@code s-1}, {@code m^2-1}) is an error, since it reads as a
 * negative exponent in other notations: write {@code s^-1} or {@code s - 1}.
 *
 * <p>An exponent is written {@code ^n} or as superscript digits glued to what it applies to ({@code m/s²},
 * {@code s⁻¹}, {@code (m/s)²}, {@code km²} is {@code (km)^2}). A symbol that is a unit as a whole wins over that
 * reading, so a unit named {@code m²} stays {@code m²}.
 */
public final class FormulaEvaluator {
    private static final int MAX_LENGTH = 10_000;
    private static final int MAX_DEPTH = 100;
    private static final int MAX_DECIMAL_EXPONENT = 10_000;
    private static final String DELIMITERS = "*/·()^|=#[]!~@,+-";
    private static final String SHIFT_END = "shift is only allowed at the end of a formula";
    private static final String SUPERSCRIPT_DIGITS = "\u2070\u00B9\u00B2\u00B3\u2074\u2075\u2076\u2077\u2078\u2079";
    private static final char SUPERSCRIPT_MINUS = '\u207B';

    private final String s;
    private final Function<String, UomUnit<?>> lookup;
    private int pos;

    private FormulaEvaluator(String s, Function<String, UomUnit<?>> lookup) {
        this.s = s;
        this.lookup = lookup;
    }

    /**
     * Evaluates a formula.
     *
     * @param formula the formula
     * @param lookup resolves a unit symbol, null when unknown
     * @return the unit
     * @throws FormulaException if the formula is malformed or names an unknown unit
     */
    public static UomUnit<?> evaluate(String formula, Function<String, UomUnit<?>> lookup) throws FormulaException {
        if (formula.length() > MAX_LENGTH) {
            throw new FormulaException("formula too long", 1);
        }
        if (formula.isBlank()) {
            throw new FormulaException("empty formula", 1);
        }
        FormulaEvaluator p = new FormulaEvaluator(formula, lookup);
        p.skipSpace();
        UomUnit<?> result = p.expr(0);
        p.skipSpace();
        if (p.pos < formula.length() && isSign(formula.charAt(p.pos))) {
            result = p.shift(result);
        }
        if (p.pos < formula.length()) {
            throw p.error("unexpected '" + formula.charAt(p.pos) + "'");
        }
        return result;
    }

    /** {@code *}, {@code ·} (U+00B7) and {@code /}: equal precedence, left-associative, looser than juxtaposition. */
    private UomUnit<?> expr(int depth) throws FormulaException {
        UomUnit<?> left = product(depth);
        while (true) {
            skipSpace();
            if (pos >= s.length() || s.charAt(pos) == ')' || isSign(s.charAt(pos))) {
                return left;
            }
            char c = s.charAt(pos);
            int col = pos + 1;
            pos++;
            skipSpace();
            UomUnit<?> right = product(depth);
            UomUnit<?> l = left;
            left = c == '*' || c == '·' ? guard(col, () -> l.multiply(right)) : guard(col, () -> l.divide(right));
        }
    }

    /** Juxtaposed terms, so {@code J / kg K} is {@code J / (kg K)}. */
    private UomUnit<?> product(int depth) throws FormulaException {
        UomUnit<?> left = term(depth);
        while (true) {
            skipSpace();
            if (pos >= s.length() || ")*/·+-".indexOf(s.charAt(pos)) >= 0) {
                return left;
            }
            int col = pos + 1;
            UomUnit<?> right = term(depth);
            UomUnit<?> l = left;
            left = guard(col, () -> l.multiply(right));
        }
    }

    private UomUnit<?> term(int depth) throws FormulaException {
        UomUnit<?> base = atom(depth);
        if (pos < s.length() && isSuperscript(s.charAt(pos))) {
            int start = pos;
            int n = superscriptExponent(start, s.length());
            pos = end(start);
            return guard(start + 1, () -> base.pow(n));
        }
        int save = pos;
        skipSpace();
        if (pos >= s.length() || s.charAt(pos) != '^') {
            pos = save;
            return base;
        }
        int col = pos + 1;
        pos++;
        skipSpace();
        int start = pos;
        if (pos < s.length() && s.charAt(pos) == '-') {
            pos++;
        }
        int digits = pos;
        while (pos < s.length() && isDigit(s.charAt(pos))) {
            pos++;
        }
        int n;
        try {
            if (digits == pos) {
                throw new NumberFormatException();
            }
            n = Integer.parseInt(s.substring(start, pos));
        } catch (NumberFormatException e) {
            pos = start;
            throw error("exponent must be an integer");
        }
        return guard(col, () -> base.pow(n));
    }

    private UomUnit<?> atom(int depth) throws FormulaException {
        if (pos >= s.length()) {
            throw error("unexpected end of formula");
        }
        char c = s.charAt(pos);
        int col = pos + 1;
        if (c == '(') {
            if (depth >= MAX_DEPTH) {
                throw error("formula nested too deeply");
            }
            pos++;
            skipSpace();
            UomUnit<?> inner = expr(depth + 1);
            if (pos >= s.length()) {
                throw error("missing ')'");
            }
            if (isSign(s.charAt(pos))) {
                throw error(SHIFT_END);
            }
            pos++;
            return inner;
        }
        if (isDigit(c)) {
            return number(col);
        }
        if (c == '.' || DELIMITERS.indexOf(c) >= 0) {
            throw error("unexpected '" + c + "'");
        }
        int start = pos;
        while (pos < s.length() && !Character.isWhitespace(s.charAt(pos)) && DELIMITERS.indexOf(s.charAt(pos)) < 0) {
            pos++;
        }
        if (pos == start) {
            throw error("unexpected '" + c + "'");
        }
        String name = s.substring(start, pos);
        if (name.equals("pi")) {
            return pi(col);
        }
        UomUnit<?> unit = guard(col, () -> lookup.apply(name));
        if (unit == null) {
            // Not a symbol as a whole: superscript digits at its end are an exponent of the rest.
            int run = superscriptStart(name);
            if (run > 0) {
                String base = name.substring(0, run);
                pos = start + run;
                unit = base.equals("pi") ? pi(col) : guard(col, () -> lookup.apply(base));
            }
        }
        if (unit == null) {
            pos = start;
            throw error("unknown unit '" + name + "'");
        }
        return unit;
    }

    /** {@code pi}, or {@code pi|d} with {@link #pos} after the {@code pi}. */
    private UomUnit<?> pi(int col) throws FormulaException {
        Factor pi = Factor.PI;
        if (pos < s.length() && s.charAt(pos) == '|') {
            pos++;
            BigInteger d = integer("expected an integer after '|'");
            pi = guard(col, () -> Factor.PI.divide(Factor.of(d, BigInteger.ONE)));
        }
        Factor f = pi;
        return guard(col, () -> BaseUnits.ONE.scaledBy(f));
    }

    private static boolean isSuperscript(char c) {
        return c == SUPERSCRIPT_MINUS || SUPERSCRIPT_DIGITS.indexOf(c) >= 0;
    }

    /** The index where the superscript exponent at the end of {@code name} starts, or -1 if it has none or is only one. */
    private static int superscriptStart(String name) {
        int i = name.length();
        while (i > 0 && SUPERSCRIPT_DIGITS.indexOf(name.charAt(i - 1)) >= 0) {
            i--;
        }
        if (i == name.length()) {
            return -1;
        }
        if (i > 0 && name.charAt(i - 1) == SUPERSCRIPT_MINUS) {
            i--;
        }
        return i > 0 ? i : -1;
    }

    /** The end of the run of superscript characters that starts at {@code from}. */
    private int end(int from) {
        int i = from;
        while (i < s.length() && isSuperscript(s.charAt(i))) {
            i++;
        }
        return i;
    }

    /** The value of the superscript exponent at {@code from} (an optional minus, then digits), which ends at the run's end. */
    private int superscriptExponent(int from, int limit) throws FormulaException {
        int i = from;
        boolean negative = i < limit && s.charAt(i) == SUPERSCRIPT_MINUS;
        if (negative) {
            i++;
        }
        int digits = i;
        int stop = end(from);
        long value = 0;
        for (; i < stop; i++) {
            int digit = SUPERSCRIPT_DIGITS.indexOf(s.charAt(i));
            if (digit < 0) {
                pos = from;
                throw error("exponent must be an integer");
            }
            value = value * 10 + digit;
            if (value > Integer.MAX_VALUE) {
                pos = from;
                throw error("exponent must be an integer");
            }
        }
        if (digits == i) {
            pos = from;
            throw error("exponent must be an integer");
        }
        return negative ? (int) -value : (int) value;
    }

    /** The trailing {@code +n} or {@code -n} at {@link #pos}; nothing may follow it. */
    private UomUnit<?> shift(UomUnit<?> unit) throws FormulaException {
        char sign = s.charAt(pos);
        int col = pos + 1;
        boolean glued = sign == '-' && !Character.isWhitespace(s.charAt(pos - 1)) && pos + 1 < s.length()
                && !Character.isWhitespace(s.charAt(pos + 1));
        pos++;
        skipSpace();
        if (pos >= s.length()) {
            throw error("expected a number after '" + sign + "'");
        }
        if (!isDigit(s.charAt(pos))) {
            if (glued && Character.isLetter(s.charAt(pos))) {
                throw new FormulaException("shift must be a number (use '·' or a space to multiply)", col);
            }
            throw error("shift must be a number");
        }
        int start = pos;
        Factor n = literal(pos + 1);
        String digits = s.substring(start, pos);
        if (glued && digits.chars().allMatch(c -> isDigit((char) c))) {
            throw new FormulaException(ambiguous(col - 1, digits), col);
        }
        Factor offset = sign == '-' ? n.negate() : n;
        skipSpace();
        if (pos < s.length()) {
            char c = s.charAt(pos);
            if ("*/·^()+-".indexOf(c) >= 0) {
                throw new FormulaException(SHIFT_END, col);
            }
            throw error("unexpected '" + c + "' after the shift");
        }
        return guard(col, () -> unit.transform(Converters.shift(offset)));
    }

    /** Hints at {@code x^-n} when a symbol precedes the sign, else at a spaced shift. */
    private String ambiguous(int sign, String n) {
        int from = sign;
        while (from > 0 && !Character.isWhitespace(s.charAt(from - 1))) {
            from--;
        }
        int symbol = sign;
        while (symbol > from && DELIMITERS.indexOf(s.charAt(symbol - 1)) < 0) {
            symbol--;
        }
        String tail = s.substring(symbol, sign);
        if (!tail.isEmpty() && !isDigit(tail.charAt(0))) {
            return "ambiguous '" + tail + "-" + n + "': write '" + tail + "^-" + n + "' for a negative exponent or '"
                    + tail + " - " + n + "' for a shift";
        }
        String run = s.substring(from, sign);
        return "ambiguous '" + run + "-" + n + "': write '" + run + " - " + n + "' for a shift";
    }

    private UomUnit<?> number(int col) throws FormulaException {
        Factor f = literal(col);
        return guard(col, () -> BaseUnits.ONE.scaledBy(f));
    }

    /** A decimal literal or an exact fraction {@code a|b}. */
    private Factor literal(int col) throws FormulaException {
        int start = pos;
        digitRun();
        if (pos < s.length() && s.charAt(pos) == '|') {
            BigInteger n = new BigInteger(s.substring(start, pos));
            pos++;
            BigInteger d = integer("expected an integer after '|'");
            return guard(col, () -> Factor.of(n, d));
        }
        if (pos + 1 < s.length() && s.charAt(pos) == '.' && isDigit(s.charAt(pos + 1))) {
            pos++;
            digitRun();
        }
        if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
            int p = pos + 1;
            if (p < s.length() && (s.charAt(p) == '+' || s.charAt(p) == '-')) {
                p++;
            }
            if (p < s.length() && isDigit(s.charAt(p))) {
                pos = p;
                digitRun();
                String exp = s.substring(p, pos);
                if (exp.length() > 5 || Integer.parseInt(exp) > MAX_DECIMAL_EXPONENT) {
                    throw new FormulaException("number out of range", col);
                }
            }
        }
        String literal = s.substring(start, pos);
        return guard(col, () -> Factor.decimal(literal));
    }

    private BigInteger integer(String message) throws FormulaException {
        int start = pos;
        digitRun();
        if (start == pos) {
            throw error(message);
        }
        return new BigInteger(s.substring(start, pos));
    }

    private void digitRun() {
        while (pos < s.length() && isDigit(s.charAt(pos))) {
            pos++;
        }
    }

    private static boolean isSign(char c) {
        return c == '+' || c == '-';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private void skipSpace() {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
            pos++;
        }
    }

    private FormulaException error(String message) {
        return new FormulaException(message, pos + 1);
    }

    /** Wraps any runtime failure of an exact-arithmetic or unit operation with its column. */
    private static <T> T guard(int column, Supplier<T> op) throws FormulaException {
        try {
            return op.get();
        } catch (RuntimeException e) {
            throw new FormulaException(
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(),
                    column,
                    e);
        }
    }
}
