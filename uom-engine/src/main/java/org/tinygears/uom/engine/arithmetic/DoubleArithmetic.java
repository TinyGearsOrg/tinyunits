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

import org.tinygears.uom.engine.Converter;
import org.tinygears.uom.engine.Unit;

/**
 * The {@code double} quantity arithmetic on engine units. A {@code delta} or {@code relative} flag marks a value as a
 * difference (scale {@code RELATIVE}), which converts by the factor only. The callers check that the units are
 * compatible.
 */
public final class DoubleArithmetic {

    /** Relative tolerance of {@code isEquivalentTo} for {@code double} quantities. */
    public static final double EQUIVALENCE_TOLERANCE = 1e-12;

    private DoubleArithmetic() {
    }

    /**
     * {@code value} converted by {@code c}.
     *
     * @param value the value
     * @param c the converter
     * @param delta whether the value is a difference
     * @return the converted value
     */
    public static double convert(double value, Converter c, boolean delta) {
        return Deltas.of(c, delta).convert(value);
    }

    /**
     * {@code value} in the system unit of {@code unit}.
     *
     * @param value the value
     * @param unit its unit
     * @param delta whether the value is a difference
     * @return the system value
     */
    public static double systemValue(double value, Unit unit, boolean delta) {
        return convert(value, unit.toSystem(), delta);
    }

    /**
     * {@code a ± b} in the unit of {@code a}, as if both were in the system unit; linear units skip the system round
     * trip. If {@code delta}, {@code b} is a difference: it is converted by the factor only and added to {@code a}.
     *
     * @param a the first value
     * @param ua its unit
     * @param b the second value
     * @param ub its unit
     * @param toA the converter from {@code ub} to {@code ua}
     * @param delta whether either operand is a difference
     * @param subtract whether to subtract
     * @return the sum or difference in {@code ua}
     */
    public static double combine(double a, Unit ua, double b, Unit ub, Converter toA, boolean delta, boolean subtract) {
        if (delta) {
            return op(a, Deltas.converter(toA).convert(b), subtract);
        }
        Converter ta = ua.toSystem();
        Converter tb = ub.toSystem();
        if (ta.isLinear() && tb.isLinear()) {
            return op(a, toA.convert(b), subtract);
        }
        return ta.inverse().convert(op(ta.convert(a), tb.convert(b), subtract));
    }

    /**
     * {@code a·n} or {@code a/n} in the unit of {@code a}; an offset unit goes through the system unit, unless
     * {@code a} is a difference, which is scaled as a plain value.
     *
     * @param a the value
     * @param u its unit
     * @param relative whether {@code a} is a difference
     * @param n the scalar
     * @param divide whether to divide
     * @return the result in {@code u}
     */
    public static double scalar(double a, Unit u, boolean relative, double n, boolean divide) {
        Converter t = u.toSystem();
        if (relative || t.isLinear()) {
            return divide ? a / n : a * n;
        }
        double s = t.convert(a);
        return t.inverse().convert(divide ? s / n : s * n);
    }

    /**
     * {@code sys(a)·sys(b)} or {@code sys(a)/sys(b)}: the value of the product or quotient in the product or quotient
     * of the system units.
     *
     * @param a the first value
     * @param ua its unit
     * @param aRelative whether {@code a} is a difference
     * @param b the second value
     * @param ub its unit
     * @param bRelative whether {@code b} is a difference
     * @param divide whether to divide
     * @return the product or quotient of the system values
     */
    public static double product(
            double a,
            Unit ua,
            boolean aRelative,
            double b,
            Unit ub,
            boolean bRelative,
            boolean divide
    ) {
        double sa = systemValue(a, ua, aRelative);
        double sb = systemValue(b, ub, bRelative);
        return divide ? sa / sb : sa * sb;
    }

    /**
     * Compares the system values with {@link Double#compare}.
     *
     * @param a the first value
     * @param ua its unit
     * @param b the second value
     * @param ub its unit
     * @param deltas whether both are differences
     * @return the comparison
     */
    public static int compare(double a, Unit ua, double b, Unit ub, boolean deltas) {
        return Double.compare(systemValue(a, ua, deltas), systemValue(b, ub, deltas));
    }

    /**
     * Whether {@code a} and {@code b}, in the same unit, agree within {@link #EQUIVALENCE_TOLERANCE}; NaN agrees with
     * nothing, an infinity only with itself.
     *
     * @param a one value
     * @param b the other value
     * @return true if they agree
     */
    public static boolean equivalent(double a, double b) {
        if (a == b) {
            return true;
        }
        if (Double.isInfinite(a) || Double.isInfinite(b)) {
            return false;
        }
        return Math.abs(a - b) <= EQUIVALENCE_TOLERANCE * Math.max(Math.abs(a), Math.abs(b));
    }

    /**
     * Whether {@code |sys(a) - sys(b)| <= relativeTolerance · max(|sys(a)|, |sys(b)|)}.
     *
     * @param a the first value
     * @param ua its unit
     * @param b the second value
     * @param ub its unit
     * @param deltas whether both are differences
     * @param relativeTolerance the tolerance, checked by the caller
     * @return true if they are within the tolerance
     */
    public static boolean isCloseTo(double a, Unit ua, double b, Unit ub, boolean deltas, double relativeTolerance) {
        double sa = systemValue(a, ua, deltas);
        double sb = systemValue(b, ub, deltas);
        if (sa == sb) {
            return true;
        }
        return Double.isFinite(sa) && Double.isFinite(sb)
                && Math.abs(sa - sb) <= relativeTolerance * Math.max(Math.abs(sa), Math.abs(sb));
    }

    /**
     * Whether {@code |sys(a) - sys(b)| <= |sys(t)|}, the tolerance converted as a difference.
     *
     * @param a the first value
     * @param ua its unit
     * @param b the second value
     * @param ub its unit
     * @param deltas whether both are differences
     * @param t the tolerance, checked by the caller
     * @param ut its unit
     * @return true if they are within the tolerance
     */
    public static boolean isCloseTo(double a, Unit ua, double b, Unit ub, boolean deltas, double t, Unit ut) {
        double sa = systemValue(a, ua, deltas);
        double sb = systemValue(b, ub, deltas);
        if (sa == sb) {
            return true;
        }
        double st = Math.abs(systemValue(t, ut, true));
        return Double.isFinite(sa) && Double.isFinite(sb) && Math.abs(sa - sb) <= st;
    }

    private static double op(double a, double b, boolean subtract) {
        return subtract ? a - b : a + b;
    }
}
