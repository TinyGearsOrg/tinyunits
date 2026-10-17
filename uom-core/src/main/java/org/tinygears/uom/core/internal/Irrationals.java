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
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

import org.tinygears.uom.core.Factor;

/**
 * Enclosures of factors with pi or a root, in directed rounding, and correct rounding by widening them until both
 * ends round alike (Ziv's strategy). Exported to {@code org.tinygears.uom} only; not API.
 */
public final class Irrationals {
    static final int PI_CACHE_SIZE = 16;
    private static final BoundedCache<Integer, BigDecimal> PI_CACHE = new BoundedCache<>(PI_CACHE_SIZE);

    /** Digits of the first enclosure beyond those of the result; retries double the digits. */
    public static final int GUARD = 10;

    private Irrationals() {
    }

    /**
     * An interval enclosing {@code f}, its ends within a relative distance of about {@code 10^-digits} of it
     * (more for large pi exponents or root indices); a terminating rational factor within {@code digits} digits is a
     * point.
     *
     * @param f the factor
     * @param digits the digits, positive
     * @return the enclosure
     */
    public static Interval enclose(Factor f, int digits) {
        MathContext down = new MathContext(digits + 3, RoundingMode.FLOOR);
        MathContext up = new MathContext(digits + 3, RoundingMode.CEILING);
        if (f.isExact()) {
            BigDecimal n = new BigDecimal(f.numerator());
            BigDecimal d = new BigDecimal(f.denominator());
            BigDecimal lo = Decimals.divide(n, d, down);
            BigDecimal hi = Decimals.divide(n, d, up);
            return lo.compareTo(hi) == 0 ? Interval.of(lo) : new Interval(lo, hi);
        }
        // The magnitude (|n|/d * pi^k)^(1/r), every step rounded outwards.
        BigDecimal n = new BigDecimal(f.numerator().abs());
        BigDecimal d = new BigDecimal(f.denominator());
        BigDecimal lo = Decimals.divide(n, d, down);
        BigDecimal hi = Decimals.divide(n, d, up);
        int k = f.piExponent();
        if (k != 0) {
            int m = Math.abs(k);
            // pi() is within one unit in the last place, so these bound it.
            BigDecimal pi = pi(digits + 5 + String.valueOf(m).length());
            BigDecimal piLo = pow(pi.subtract(pi.ulp()), m, down);
            BigDecimal piHi = pow(pi.add(pi.ulp()), m, up);
            if (k > 0) {
                lo = lo.multiply(piLo, down);
                hi = hi.multiply(piHi, up);
            } else {
                lo = Decimals.divide(lo, piHi, down);
                hi = Decimals.divide(hi, piLo, up);
            }
        }
        int r = f.rootIndex();
        if (r > 1) {
            lo = rootBelow(lo, r, down, up);
            hi = rootAbove(hi, r, down, up);
        }
        return f.signum() < 0 ? new Interval(hi.negate(), lo.negate()) : new Interval(lo, hi);
    }

    /**
     * {@code f} correctly rounded to {@code mc}. An irrational factor is never on a rounding boundary, so the
     * enclosures narrow until both ends round alike; under {@code UNNECESSARY} it throws.
     *
     * @param f the factor, not exact
     * @param mc the rounding, with a non-zero precision
     * @return the rounded value
     * @throws ArithmeticException under {@code RoundingMode.UNNECESSARY}
     */
    public static BigDecimal round(Factor f, MathContext mc) {
        if (mc.getRoundingMode() == RoundingMode.UNNECESSARY) {
            throw new ArithmeticException("Rounding necessary");
        }
        for (int guard = GUARD;; guard *= 2) {
            Interval x = enclose(f, mc.getPrecision() + guard);
            BigDecimal r = x.lo().round(mc);
            if (r.compareTo(x.hi().round(mc)) == 0) {
                return r;
            }
        }
    }

    /**
     * {@code f} correctly rounded to a {@code double}, the nearest one.
     *
     * @param f the factor, not exact
     * @return the value
     */
    public static double doubleValue(Factor f) {
        for (int digits = 25;; digits *= 2) {
            Interval x = enclose(f, digits);
            double lo = x.lo().doubleValue();
            if (lo == x.hi().doubleValue()) {
                return lo;
            }
        }
    }

    /** {@code x^n} for {@code x > 0} and {@code n >= 1}, each product rounded by {@code mc}, so in one direction. */
    private static BigDecimal pow(BigDecimal x, int n, MathContext mc) {
        BigDecimal result = null;
        BigDecimal base = x;
        for (int e = n;; e >>= 1) {
            if ((e & 1) != 0) {
                result = result == null ? base : result.multiply(base, mc);
            }
            if (e == 1) {
                return result;
            }
            base = base.multiply(base, mc);
        }
    }

    /** A {@code y >= 0} with {@code y^r <= x}, checked with the power rounded up. */
    private static BigDecimal rootBelow(BigDecimal x, int r, MathContext down, MathContext up) {
        BigDecimal y = nthRoot(x, r, new MathContext(down.getPrecision()));
        for (BigDecimal step = unit(y, down);; step = step.add(step)) {
            BigDecimal c = y.subtract(step);
            if (c.signum() <= 0) {
                return BigDecimal.ZERO;
            }
            if (pow(c, r, up).compareTo(x) <= 0) {
                return c;
            }
        }
    }

    /** A {@code y} with {@code y^r >= x}, checked with the power rounded down. */
    private static BigDecimal rootAbove(BigDecimal x, int r, MathContext down, MathContext up) {
        BigDecimal y = nthRoot(x, r, new MathContext(up.getPrecision()));
        for (BigDecimal step = unit(y, up);; step = step.add(step)) {
            BigDecimal c = y.add(step);
            if (pow(c, r, down).compareTo(x) >= 0) {
                return c;
            }
        }
    }

    /** The unit in the last place of {@code y} at the precision of {@code mc}, whatever digits {@code y} has. */
    private static BigDecimal unit(BigDecimal y, MathContext mc) {
        return BigDecimal.ONE.scaleByPowerOfTen(y.precision() - y.scale() - mc.getPrecision());
    }

    /** Pi to at least {@code digits} digits; precisions round up to multiples of 64 and the cache holds 16 of them. */
    static BigDecimal pi(int digits) {
        return PI_CACHE.get((digits + 63) / 64 * 64, p -> {
            // Chudnovsky: pi = 426880 sqrt(10005) Q / T, about 14.18 digits per term; binary splitting keeps it fast.
            MathContext mc = new MathContext(p + 10);
            BigInteger[] pqt = chudnovsky(0, p / 14 + 2);
            // The truncated root rounded HALF_UP is the correctly rounded root (irrational: never a tie), and
            // BigInteger.sqrt is twice as fast as BigDecimal.sqrt on JDK 21.
            BigDecimal root = new BigDecimal(
                    BigInteger.valueOf(10_005).multiply(BigInteger.TEN.pow(2 * (p + 10))).sqrt(),
                    p + 10).round(mc);
            BigDecimal pi = new BigDecimal(pqt[1].multiply(BigInteger.valueOf(426_880))).multiply(root, mc)
                    .divide(new BigDecimal(pqt[2]), mc);
            return pi.round(new MathContext(p));
        });
    }

    static int piCacheSize() {
        return PI_CACHE.size();
    }

    static boolean piCacheHolds(int bucket) {
        return PI_CACHE.contains(bucket);
    }

    static void clearPiCache() {
        PI_CACHE.clear();
    }

    private static final BigInteger CHUDNOVSKY_C3_24 = BigInteger.valueOf(640_320).pow(3)
            .divide(BigInteger.valueOf(24));

    /** P, Q and T of the Chudnovsky series over terms {@code [a, b)}. */
    private static BigInteger[] chudnovsky(long a, long b) {
        if (b - a == 1) {
            if (a == 0) {
                return new BigInteger[] { BigInteger.ONE, BigInteger.ONE, BigInteger.valueOf(13_591_409) };
            }
            BigInteger p = BigInteger.valueOf(6 * a - 5).multiply(BigInteger.valueOf(2 * a - 1))
                    .multiply(BigInteger.valueOf(6 * a - 1));
            BigInteger q = BigInteger.valueOf(a).pow(3).multiply(CHUDNOVSKY_C3_24);
            BigInteger t = p.multiply(BigInteger.valueOf(13_591_409 + 545_140_134 * a));
            return new BigInteger[] { p, q, a % 2 == 0 ? t : t.negate() };
        }
        long m = (a + b) / 2;
        BigInteger[] l = chudnovsky(a, m);
        BigInteger[] r = chudnovsky(m, b);
        return new BigInteger[] {
            l[0].multiply(r[0]),
            l[1].multiply(r[1]),
            l[2].multiply(r[1]).add(l[0].multiply(r[2])) };
    }

    /** r-th root of a > 0 by Newton iteration from a double-precision guess. */
    private static BigDecimal nthRoot(BigDecimal a, int r, MathContext mc) {
        int e = a.precision() - a.scale();
        double lg = (e + Math.log10(a.scaleByPowerOfTen(-e).doubleValue())) / r;
        int ie = (int) Math.floor(lg);
        BigDecimal y = BigDecimal.valueOf(Math.pow(10, lg - ie)).scaleByPowerOfTen(ie);
        BigDecimal rm1 = BigDecimal.valueOf(r - 1);
        BigDecimal br = BigDecimal.valueOf(r);
        for (int i = 0; i < 100; i++) {
            BigDecimal next = y.multiply(rm1).add(a.divide(y.pow(r - 1, mc), mc)).divide(br, mc);
            boolean done = next.subtract(y).abs().compareTo(next.ulp()) <= 0;
            y = next;
            if (done) {
                break;
            }
        }
        return y;
    }
}
