/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.engine.arithmetic;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.function.IntFunction;

import org.tinygears.uom.engine.Converter;
import org.tinygears.uom.engine.Factor;
import org.tinygears.uom.engine.Unit;
import org.tinygears.uom.engine.internal.Decimals;
import org.tinygears.uom.engine.internal.Interval;
import org.tinygears.uom.engine.internal.Irrationals;

/**
 * The decimal quantity arithmetic on engine units: every result is evaluated exactly and rounded once to the given
 * context; irrational factors are enclosed with doubling digits until the rounding is decided. An unlimited context
 * keeps results exact where they terminate and rounds the others to {@code DECIMAL128}. A {@code delta} or
 * {@code relative} flag marks a value as a difference, which converts by the factor only. The callers check that the
 * units are compatible.
 */
public final class DecimalArithmetic {

    private DecimalArithmetic() {
    }

    /**
     * {@code value} converted by {@code c}, rounded to {@code mc}.
     *
     * @param value the value
     * @param c the converter
     * @param delta whether the value is a difference
     * @param mc the context
     * @return the converted value
     */
    public static BigDecimal convert(BigDecimal value, Converter c, boolean delta, MathContext mc) {
        return Deltas.of(c, delta).convert(value, mc);
    }

    /**
     * {@code a ± (fb·b + ob)/fa} in the unit of {@code a}, as one exact quotient rounded once; if {@code delta},
     * {@code b} is a difference and converts by the factor only.
     *
     * @param a the first value
     * @param ua its unit
     * @param b the second value
     * @param ub its unit
     * @param delta whether either operand is a difference
     * @param subtract whether to subtract
     * @param mc the context of the result
     * @return the sum or difference in {@code ua}
     */
    public static BigDecimal combine(
            BigDecimal a,
            Unit ua,
            BigDecimal b,
            Unit ub,
            boolean delta,
            boolean subtract,
            MathContext mc
    ) {
        Converter ta = ua.toSystem();
        Converter tb = Deltas.of(ub.toSystem(), delta);
        Factor ff = tb.factor().divide(ta.factor());
        Factor off = tb.offset().divide(ta.factor());
        return evaluate(mc, maxDigits(mc, a, b), digits -> {
            Ratio f = Ratio.of(ff, digits);
            Ratio o = Ratio.of(off, digits);
            Interval term = f.num.multiply(b).multiply(o.den).add(o.num.multiply(f.den));
            Interval num = Interval.of(a.multiply(f.den).multiply(o.den)).add(subtract ? term.negate() : term);
            return new Quotient(num, Interval.of(f.den.multiply(o.den)), f.exact && o.exact);
        });
    }

    /**
     * {@code a·s + q·(s - 1)} with {@code q = oa/fa} (or the same with {@code 1/s} to divide), in the unit of
     * {@code a}, rounded once; a difference is scaled as a plain value. Division by zero throws
     * {@link ArithmeticException}.
     *
     * @param a the value
     * @param u its unit
     * @param relative whether {@code a} is a difference
     * @param s the scalar
     * @param divide whether to divide
     * @param mc the context of the result
     * @return the result in {@code u}
     */
    public static BigDecimal scalar(
            BigDecimal a,
            Unit u,
            boolean relative,
            BigDecimal s,
            boolean divide,
            MathContext mc
    ) {
        Converter ta = Deltas.of(u.toSystem(), relative);
        Factor qf = ta.offset().divide(ta.factor());
        return evaluate(mc, maxDigits(mc, a, s), digits -> {
            Ratio q = Ratio.of(qf, digits);
            Interval aq = Interval.of(a.multiply(q.den)).add(q.num);
            Interval num = divide ? aq.add(q.num.multiply(s).negate()) : aq.multiply(s).add(q.num.negate());
            return new Quotient(num, Interval.of(divide ? q.den.multiply(s) : q.den), q.exact);
        });
    }

    /**
     * {@code sys(a)·sys(b)} or {@code sys(a)/sys(b)} as one exact quotient, rounded once. Division by zero throws
     * {@link ArithmeticException}.
     *
     * @param a the first value
     * @param ua its unit
     * @param aRelative whether {@code a} is a difference
     * @param b the second value
     * @param ub its unit
     * @param bRelative whether {@code b} is a difference
     * @param divide whether to divide
     * @param mc the context of the result
     * @return the product or quotient of the system values
     */
    public static BigDecimal product(
            BigDecimal a,
            Unit ua,
            boolean aRelative,
            BigDecimal b,
            Unit ub,
            boolean bRelative,
            boolean divide,
            MathContext mc
    ) {
        return evaluate(mc, maxDigits(mc, a, b), digits -> {
            Ratio sa = system(a, ua, digits, aRelative);
            Ratio sb = system(b, ub, digits, bRelative);
            Interval num = divide ? sa.num.multiply(sb.den) : sa.num.multiply(sb.num);
            Interval den = divide ? sb.num.multiply(sa.den) : Interval.of(sa.den.multiply(sb.den));
            return new Quotient(num, den, sa.exact && sb.exact);
        });
    }

    /**
     * {@code 1/sys(a)}, rounded once. Zero throws {@link ArithmeticException}.
     *
     * @param a the value
     * @param u its unit
     * @param relative whether {@code a} is a difference
     * @param mc the context of the result
     * @return the inverse of the system value
     */
    public static BigDecimal inverse(BigDecimal a, Unit u, boolean relative, MathContext mc) {
        return evaluate(mc, maxDigits(mc, a, BigDecimal.ONE), digits -> {
            Ratio s = system(a, u, digits, relative);
            return new Quotient(Interval.of(s.den), s.num, s.exact);
        });
    }

    /**
     * Compares the exact system values. A difference not separated from zero at 1000 digits plus twice those of the
     * context and both operands (irrational terms that cancel) compares as zero.
     *
     * @param a the first value
     * @param ua its unit
     * @param b the second value
     * @param ub its unit
     * @param deltas whether both are differences
     * @param mc the context of the comparison, which sets the starting digits of the enclosures
     * @return the sign of {@code sys(a) - sys(b)}
     */
    public static int compare(BigDecimal a, Unit ua, BigDecimal b, Unit ub, boolean deltas, MathContext mc) {
        Converter ta = Deltas.of(ua.toSystem(), deltas);
        Converter tb = Deltas.of(ub.toSystem(), deltas);
        // sign(sys(a) - sys(b)) = sign(fa) * sign(a - (fb/fa) b - (ob - oa)/fa)
        Factor gf = tb.factor().divide(ta.factor());
        Factor hf = tb.offset().add(ta.offset().negate()).divide(ta.factor());
        MathContext calc = Precision.calculation(mc);
        long cap = maxDigits(calc, a, b);
        for (int digits = calc.getPrecision() + Irrationals.GUARD;; digits *= 2) {
            Ratio g = Ratio.of(gf, digits);
            Ratio h = Ratio.of(hf, digits);
            Interval d = Interval.of(a.multiply(g.den).multiply(h.den)).add(g.num.multiply(b.multiply(h.den)).negate())
                    .add(h.num.multiply(g.den).negate());
            int sign = d.signum();
            if (sign != 2) {
                return ta.factor().signum() * sign;
            }
            if (digits > cap) {
                return 0;
            }
        }
    }

    /**
     * Whether {@code |sys(a) - sys(b)| <= relative · max(|sys(a)|, |sys(b)|)}, or {@code <= |sys(t)|} for the
     * absolute tolerance {@code t}, which converts as a difference; exact. Values still undecided at the digit cap
     * (irrational terms that cancel exactly) count as on the boundary, so close.
     *
     * @param a the first value
     * @param ua its unit
     * @param b the second value
     * @param ub its unit
     * @param deltas whether both are differences
     * @param relative the relative tolerance, or null for an absolute one
     * @param t the absolute tolerance, or null for a relative one
     * @param ut the unit of {@code t}, or null
     * @param mc the context of the comparison, which sets the starting digits of the enclosures
     * @return true if they are within the tolerance
     */
    public static boolean isCloseTo(
            BigDecimal a,
            Unit ua,
            BigDecimal b,
            Unit ub,
            boolean deltas,
            BigDecimal relative,
            BigDecimal t,
            Unit ut,
            MathContext mc
    ) {
        MathContext calc = Precision.calculation(mc);
        long cap = maxDigits(calc, a, b) + (t == null ? 0 : 2L * t.precision());
        for (int digits = calc.getPrecision() + Irrationals.GUARD;; digits *= 2) {
            Ratio sa = system(a, ua, digits, deltas);
            Ratio sb = system(b, ub, digits, deltas);
            // a = x / D and b = y / D, with D = sa.den * sb.den > 0
            Interval x = sa.num.multiply(sb.den);
            Interval y = sb.num.multiply(sa.den);
            Interval diff = x.add(y.negate()).abs();
            Interval bound;
            if (relative != null) {
                bound = x.abs().max(y.abs()).multiply(relative);
            } else {
                Ratio st = system(t, ut, digits, true);
                diff = diff.multiply(st.den);
                bound = st.num.abs().multiply(sa.den.multiply(sb.den));
            }
            if (diff.hi().compareTo(bound.lo()) <= 0) {
                return true;
            }
            if (diff.lo().compareTo(bound.hi()) > 0) {
                return false;
            }
            if (digits > cap) {
                return true;
            }
        }
    }

    /** The system value {@code f·v + o} as numerator over denominator; {@code o} is 0 for a difference. */
    private static Ratio system(BigDecimal v, Unit u, int digits, boolean delta) {
        Converter c = Deltas.of(u.toSystem(), delta);
        Ratio f = Ratio.of(c.factor(), digits);
        Ratio o = Ratio.of(c.offset(), digits);
        return new Ratio(
                f.num.multiply(v).multiply(o.den).add(o.num.multiply(f.den)),
                f.den.multiply(o.den),
                f.exact && o.exact);
    }

    /**
     * The quotient {@code at} gives, rounded once to {@code mc}. Exact factors give one exact quotient; irrational ones
     * are enclosed with doubling digits until both ends round alike, or, past {@code cap} digits (irrational terms
     * that cancel exactly), the lower end rounds.
     */
    private static BigDecimal evaluate(MathContext mc, long cap, IntFunction<Quotient> at) {
        MathContext calc = Precision.calculation(mc);
        for (int digits = calc.getPrecision() + Irrationals.GUARD;; digits *= 2) {
            Quotient q = at.apply(digits);
            if (q.exact) {
                return roundOnce(q.num.lo(), q.den.lo(), mc);
            }
            BigDecimal r = q.num.divide(q.den, calc);
            if (r != null) {
                return r;
            }
            if (digits > cap) {
                return Decimals.divide(q.num.lo(), q.den.lo(), calc);
            }
        }
    }

    /**
     * {@code num / den} rounded once to {@code mc}; an unlimited context rounds a non-terminating quotient to
     * {@code DECIMAL128}. A zero {@code den} throws {@link ArithmeticException}.
     */
    private static BigDecimal roundOnce(BigDecimal num, BigDecimal den, MathContext mc) {
        if (mc.getPrecision() == 0) {
            try {
                return Decimals.divide(num, den, MathContext.UNLIMITED);
            } catch (ArithmeticException nonTerminating) {
                // a zero den throws again below
            }
        }
        return Decimals.divide(num, den, Precision.calculation(mc));
    }

    /** Digits after which irrational terms count as cancelling: twice the context's and both operands', plus 1000. */
    private static long maxDigits(MathContext mc, BigDecimal a, BigDecimal b) {
        return 2L * (Precision.calculation(mc).getPrecision() + a.precision() + b.precision()) + 1000;
    }

    /** A factor as an enclosure of its numerator over an exact positive denominator; a point if the factor is exact. */
    private record Ratio(Interval num, BigDecimal den, boolean exact) {

        static Ratio of(Factor f, int digits) {
            return f.isExact()
                    ? new Ratio(Interval.of(new BigDecimal(f.numerator())), new BigDecimal(f.denominator()), true)
                    : new Ratio(Irrationals.enclose(f, digits), BigDecimal.ONE, false);
        }
    }

    /** A quotient of enclosures; exact if no irrational factor took part. */
    private record Quotient(Interval num, Interval den, boolean exact) {}
}
