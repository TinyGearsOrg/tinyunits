/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.engine.parse;

import java.math.BigInteger;
import java.util.function.Supplier;

import org.tinygears.uom.engine.Factor;

/** Parses a function of {@code x} such as {@code (x - 32) * 5|9} and reduces it to {@code a*x + b}. */
final class AffineFunction {
    private static final int MAX_DEPTH = 100;

    private final Factor a;
    private final Factor b;

    private AffineFunction(Factor a, Factor b) {
        this.a = a;
        this.b = b;
    }

    Factor a() {
        return a;
    }

    Factor b() {
        return b;
    }

    static AffineFunction parse(String function) throws FormulaException {
        Parser p = new Parser(function);
        AffineFunction f = p.expr(0);
        p.skipSpace();
        if (p.pos < function.length()) {
            throw p.error("unexpected '" + function.charAt(p.pos) + "'");
        }
        return f;
    }

    /** Recursive descent over the values {@code a*x + b}. */
    private static final class Parser {
        private final String s;
        private int pos;

        private Parser(String s) {
            this.s = s;
        }

        private AffineFunction expr(int depth) throws FormulaException {
            AffineFunction left = term(depth);
            while (true) {
                skipSpace();
                if (pos >= s.length() || (s.charAt(pos) != '+' && s.charAt(pos) != '-')) {
                    return left;
                }
                char op = s.charAt(pos++);
                int col = pos;
                AffineFunction r = term(depth);
                Factor sign = op == '+' ? Factor.ONE : Factor.ONE.negate();
                AffineFunction l = left;
                left = guard(col, () -> new AffineFunction(l.a.add(r.a.multiply(sign)), l.b.add(r.b.multiply(sign))));
            }
        }

        private AffineFunction term(int depth) throws FormulaException {
            AffineFunction left = factor(depth);
            while (true) {
                skipSpace();
                if (pos >= s.length() || (s.charAt(pos) != '*' && s.charAt(pos) != '/')) {
                    return left;
                }
                char op = s.charAt(pos++);
                int col = pos;
                AffineFunction r = factor(depth);
                AffineFunction l = left;
                if (op == '*') {
                    if (!l.a.isZero() && !r.a.isZero()) {
                        throw new FormulaException("function is not affine", col);
                    }
                    left = guard(
                            col,
                            () -> new AffineFunction(l.a.multiply(r.b).add(r.a.multiply(l.b)), l.b.multiply(r.b)));
                } else {
                    if (!r.a.isZero()) {
                        throw new FormulaException("function is not affine", col);
                    }
                    if (r.b.isZero()) {
                        throw new FormulaException("division by zero", col);
                    }
                    left = guard(col, () -> new AffineFunction(l.a.divide(r.b), l.b.divide(r.b)));
                }
            }
        }

        private AffineFunction factor(int depth) throws FormulaException {
            skipSpace();
            if (pos < s.length() && s.charAt(pos) == '-') {
                pos++;
                int col = pos;
                AffineFunction p = primary(depth);
                return guard(col, () -> new AffineFunction(p.a.negate(), p.b.negate()));
            }
            return primary(depth);
        }

        private AffineFunction primary(int depth) throws FormulaException {
            skipSpace();
            if (pos >= s.length()) {
                throw error("unexpected end of function");
            }
            char c = s.charAt(pos);
            int col = pos + 1;
            if (c == 'x') {
                pos++;
                return new AffineFunction(Factor.ONE, Factor.ZERO);
            }
            if (c == '(') {
                if (depth >= MAX_DEPTH) {
                    throw error("function nested too deeply");
                }
                pos++;
                AffineFunction inner = expr(depth + 1);
                skipSpace();
                if (pos >= s.length() || s.charAt(pos) != ')') {
                    throw error("missing ')'");
                }
                pos++;
                return inner;
            }
            if (!isDigit(c)) {
                throw error("unexpected '" + c + "'");
            }
            int start = pos;
            digitRun();
            if (pos < s.length() && s.charAt(pos) == '|') {
                BigInteger n = new BigInteger(s.substring(start, pos));
                pos++;
                int d0 = pos;
                digitRun();
                if (d0 == pos) {
                    throw error("expected an integer after '|'");
                }
                BigInteger d = new BigInteger(s.substring(d0, pos));
                if (d.signum() == 0) {
                    throw new FormulaException("division by zero", col);
                }
                return new AffineFunction(Factor.ZERO, Factor.of(n, d));
            }
            if (pos + 1 < s.length() && s.charAt(pos) == '.' && isDigit(s.charAt(pos + 1))) {
                pos++;
                digitRun();
            }
            String literal = s.substring(start, pos);
            return guard(col, () -> new AffineFunction(Factor.ZERO, Factor.decimal(literal)));
        }

        private void digitRun() {
            while (pos < s.length() && isDigit(s.charAt(pos))) {
                pos++;
            }
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

        private static AffineFunction guard(int column, Supplier<AffineFunction> op) throws FormulaException {
            try {
                return op.get();
            } catch (RuntimeException e) {
                throw new FormulaException(String.valueOf(e.getMessage()), column, e);
            }
        }
    }
}
