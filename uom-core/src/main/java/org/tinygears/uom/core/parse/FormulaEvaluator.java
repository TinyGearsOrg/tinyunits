// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.parse;

import java.math.BigInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.Converters;
import org.tinygears.uom.core.Factor;
import org.tinygears.uom.core.UomUnit;

/**
 * Evaluates unit formulas such as {@code kg m / s^2} with a recursive-descent parser. A formula may end in a shift
 * {@code +n} or {@code -n} ({@code n} a decimal literal or {@code a|b}, spaces allowed around the sign) that applies
 * to the whole expression: {@code K+273.15} is {@code K.shift(273.15)}, so a value {@code v} in it is
 * {@code v + 273.15} K.
 */
public final class FormulaEvaluator {
    private static final int MAX_LENGTH = 10_000;
    private static final int MAX_DEPTH = 100;
    private static final int MAX_DECIMAL_EXPONENT = 10_000;
    private static final String DELIMITERS = "*/·()^|=#[]!~@,+-";
    private static final String SHIFT_END = "shift is only allowed at the end of a formula";

    private final String s;
    private final Function<String, UomUnit<?>> lookup;
    private int pos;

    private FormulaEvaluator(String s, Function<String, UomUnit<?>> lookup) {
        this.s = s;
        this.lookup = lookup;
    }

    public static UomUnit<?> evaluate(String formula, Function<String, UomUnit<?>> lookup)
            throws FormulaException {
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
            Factor pi = Factor.PI;
            if (pos < s.length() && s.charAt(pos) == '|') {
                pos++;
                BigInteger d = integer("expected an integer after '|'");
                pi = guard(col, () -> Factor.PI.divide(Factor.of(d, BigInteger.ONE)));
            }
            Factor f = pi;
            return guard(col, () -> BaseUnits.ONE.scaledBy(f));
        }
        UomUnit<?> unit = guard(col, () -> lookup.apply(name));
        if (unit == null) {
            pos = start;
            throw error("unknown unit '" + name + "'");
        }
        return unit;
    }

    /** The trailing {@code +n} or {@code -n} at {@link #pos}; nothing may follow it. */
    private UomUnit<?> shift(UomUnit<?> unit) throws FormulaException {
        char sign = s.charAt(pos);
        int col = pos + 1;
        pos++;
        skipSpace();
        if (pos >= s.length()) {
            throw error("expected a number after '" + sign + "'");
        }
        if (!isDigit(s.charAt(pos))) {
            throw error("shift must be a number");
        }
        Factor n = literal(pos + 1);
        Factor offset = sign == '-' ? n.negate() : n;
        skipSpace();
        if (pos < s.length()) {
            throw new FormulaException(SHIFT_END, col);
        }
        return guard(col, () -> unit.transform(Converters.shift(offset)));
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
            throw new FormulaException(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), column, e);
        }
    }
}
