/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.core.internal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * A closed interval {@code [lo, hi]} of decimals enclosing a value. Its sums and products are exact, so the result of
 * arithmetic on enclosures encloses the result of the same arithmetic on the values. Exported to {@code org.tinygears.uom} only; not API.
 *
 * @param lo the lower end
 * @param hi the upper end, not below {@code lo}
 */
public record Interval(BigDecimal lo, BigDecimal hi) {

    /**
     * The interval holding only {@code x}.
     *
     * @param x the value
     * @return the point interval
     */
    public static Interval of(BigDecimal x) {
        return new Interval(x, x);
    }

    /**
     * Whether both ends are equal.
     *
     * @return true for a single value
     */
    public boolean isPoint() {
        return lo == hi || lo.compareTo(hi) == 0;
    }

    /**
     * The sum; a zero {@code other} gives this interval unchanged, so far-apart scales are not aligned.
     *
     * @param other the other interval
     * @return the sum
     */
    public Interval add(Interval other) {
        if (other.isPoint() && other.lo.signum() == 0) {
            return this;
        }
        if (isPoint() && other.isPoint()) {
            return of(lo.add(other.lo));
        }
        return new Interval(lo.add(other.lo), hi.add(other.hi));
    }

    /**
     * The sum rounded outwards to {@code digits} digits, so it still encloses; unlike the exact sum it does not align
     * far-apart scales.
     *
     * @param other the other interval
     * @param digits the digits, positive
     * @return the enclosure of the sum
     */
    public Interval add(Interval other, int digits) {
        BigDecimal a = lo.add(other.lo, new MathContext(digits, RoundingMode.FLOOR));
        BigDecimal b = hi.add(other.hi, new MathContext(digits, RoundingMode.CEILING));
        return a.compareTo(b) == 0 ? of(a) : new Interval(a, b);
    }

    /**
     * The negation.
     *
     * @return {@code -this}
     */
    public Interval negate() {
        return isPoint() ? of(lo.negate()) : new Interval(hi.negate(), lo.negate());
    }

    /**
     * The absolute values.
     *
     * @return {@code |this|}
     */
    public Interval abs() {
        if (lo.signum() >= 0) {
            return this;
        }
        if (hi.signum() <= 0) {
            return negate();
        }
        return new Interval(BigDecimal.ZERO, lo.negate().max(hi));
    }

    /**
     * The larger of two values, one from each interval.
     *
     * @param other the other interval
     * @return {@code max(this, other)}
     */
    public Interval max(Interval other) {
        return new Interval(lo.max(other.lo), hi.max(other.hi));
    }

    /**
     * The product with a value.
     *
     * @param x the value
     * @return the product
     */
    public Interval multiply(BigDecimal x) {
        if (isPoint()) {
            return of(lo.multiply(x));
        }
        BigDecimal a = lo.multiply(x);
        BigDecimal b = hi.multiply(x);
        return x.signum() >= 0 ? new Interval(a, b) : new Interval(b, a);
    }

    /**
     * The product.
     *
     * @param other the other interval
     * @return the product
     */
    public Interval multiply(Interval other) {
        if (other.isPoint()) {
            return multiply(other.lo);
        }
        if (isPoint()) {
            return other.multiply(lo);
        }
        BigDecimal[] corners = {
            lo.multiply(other.lo),
            lo.multiply(other.hi),
            hi.multiply(other.lo),
            hi.multiply(other.hi) };
        BigDecimal min = corners[0];
        BigDecimal max = corners[0];
        for (BigDecimal c : corners) {
            min = c.compareTo(min) < 0 ? c : min;
            max = c.compareTo(max) > 0 ? c : max;
        }
        return new Interval(min, max);
    }

    /**
     * The sign of every value in the interval.
     *
     * @return -1, 0 or 1, or 2 if the values differ in sign
     */
    public int signum() {
        return lo.signum() == hi.signum() ? lo.signum() : 2;
    }

    /**
     * The quotient by {@code den} rounded to {@code mc}, if every quotient of the two intervals rounds to it.
     * A point divided by a point is one {@link Decimals#divide}; a divisor that is the point zero throws as it does.
     *
     * @param den the divisor
     * @param mc the rounding, with a non-zero precision
     * @return the rounded quotient, or null if the quotients round differently or {@code den} contains zero
     */
    public BigDecimal divide(Interval den, MathContext mc) {
        if (den.isPoint() && (isPoint() || den.lo.signum() == 0)) {
            return Decimals.divide(lo, den.lo, mc);
        }
        if (den.signum() == 2) {
            return null;
        }
        MathContext down = new MathContext(mc.getPrecision() + 5, RoundingMode.FLOOR);
        MathContext up = new MathContext(mc.getPrecision() + 5, RoundingMode.CEILING);
        // The quotient is monotonic in each operand on this box, so its extremes lie at the corners.
        BigDecimal min = null;
        BigDecimal max = null;
        for (BigDecimal n : isPoint() ? new BigDecimal[] { lo } : new BigDecimal[] { lo, hi }) {
            for (BigDecimal d : den.isPoint() ? new BigDecimal[] { den.lo } : new BigDecimal[] { den.lo, den.hi }) {
                BigDecimal a = Decimals.divide(n, d, down);
                BigDecimal b = Decimals.divide(n, d, up);
                min = min == null || a.compareTo(min) < 0 ? a : min;
                max = max == null || b.compareTo(max) > 0 ? b : max;
            }
        }
        BigDecimal r = min.round(mc);
        return r.compareTo(max.round(mc)) == 0 ? r : null;
    }
}
