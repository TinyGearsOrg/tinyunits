/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.engine.internal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/** Decimal helpers shared by the core packages; exported to {@code org.tinygears.uom.jsr} only, not API. */
public final class Decimals {
    private static final BigInteger FIVE = BigInteger.valueOf(5);

    private Decimals() {
    }

    /**
     * Exactly {@code x.divide(y, mc)} (value, scale, exception class and message), except at scales close to the
     * {@code int} limits (up to about {@code mc.getPrecision()} away from them): there the JDK may throw an
     * overflow, underflow or non-terminating {@link ArithmeticException} although a correct result is
     * representable, and this method returns that result; under {@code RoundingMode.UNNECESSARY} it throws
     * "Rounding necessary" where the JDK says "Underflow". A terminating quotient is built by multiplication,
     * because JDK 21 strips the trailing zeros of an exact quotient padded to {@code mc} in quadratic time.
     */
    public static BigDecimal divide(BigDecimal x, BigDecimal y, MathContext mc) {
        BigInteger u = y.unscaledValue().abs();
        if (u.signum() == 0 || x.signum() == 0) {
            return x.divide(y, mc);
        }
        // |y| = 2^i 5^j r: the quotient terminates exactly when r divides x's unscaled value.
        int i = u.getLowestSetBit();
        Split fives = split(u.shiftRight(i), Integer.MAX_VALUE);
        int j = fives.count();
        BigInteger a = x.unscaledValue().abs();
        if (!fives.rest().equals(BigInteger.ONE)) {
            BigInteger[] qr = a.divideAndRemainder(fives.rest());
            if (qr[1].signum() != 0) {
                return x.divide(y, mc);
            }
            a = qr[0];
        }
        // a / (2^i 5^j) = a 2^(k-i) 5^(k-j) / 10^k; strip z <= k trailing zeros, so the scale stays >= the preferred.
        int k = Math.max(i, j);
        long twos = (long) a.getLowestSetBit() + k - i;
        int cap = (int) Math.min(k, twos);
        Split aFives = split(a, Math.max(0, cap - (k - j)));
        int z = (int) Math.min(cap, (long) aFives.count() + k - j);
        BigInteger q = aFives.rest().multiply(FIVE.pow(aFives.count() + k - j - z)).shiftLeft(k - i).shiftRight(z);
        if (x.signum() * y.signum() < 0) {
            q = q.negate();
        }
        long scale = (long) x.scale() - y.scale() + k - z;
        if (scale != (int) scale) {
            return x.divide(y, mc);
        }
        BigDecimal exact = new BigDecimal(q, (int) scale);
        return mc.getPrecision() == 0 || exact.precision() <= mc.getPrecision() ? exact : exact.round(mc);
    }

    /**
     * Whether {@code x / y} has a finite decimal expansion, so that {@link #divide} is exact at precision 0; {@code y}
     * must not be zero.
     */
    public static boolean terminates(BigDecimal x, BigDecimal y) {
        if (x.signum() == 0) {
            return true;
        }
        // |y| = 2^i 5^j r: the quotient terminates exactly when r divides x's unscaled value.
        BigInteger u = y.unscaledValue().abs();
        BigInteger rest = split(u.shiftRight(u.getLowestSetBit()), Integer.MAX_VALUE).rest();
        return rest.equals(BigInteger.ONE) || x.unscaledValue().mod(rest).signum() == 0;
    }

    /** {@code n = rest * 5^count} with {@code count = min(max, valuation)}, by powers 5^(2^m) from the largest down. */
    private static Split split(BigInteger n, int max) {
        List<BigInteger> powers = new ArrayList<>();
        for (BigInteger p = FIVE; (1L << powers.size()) <= max && p.bitLength() <= n.bitLength(); p = p.multiply(p)) {
            powers.add(p);
        }
        int count = 0;
        for (int m = powers.size() - 1; m >= 0; m--) {
            if ((long) count + (1L << m) <= max) {
                BigInteger[] qr = n.divideAndRemainder(powers.get(m));
                if (qr[1].signum() == 0) {
                    n = qr[0];
                    count += 1 << m;
                }
            }
        }
        return new Split(n, count);
    }

    private record Split(BigInteger rest, int count) {}
}
