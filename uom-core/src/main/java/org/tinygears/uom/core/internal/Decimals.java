// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.internal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/** Decimal helpers shared by the core packages; not exported, not API. */
public final class Decimals {
    private static final BigInteger FIVE = BigInteger.valueOf(5);

    private Decimals() {
    }

    /**
     * Exactly {@code x.divide(y, mc)}. A terminating quotient is built by multiplication instead, because JDK 21
     * strips the trailing zeros of an exact quotient padded to {@code mc} in quadratic time.
     */
    public static BigDecimal divide(BigDecimal x, BigDecimal y, MathContext mc) {
        BigInteger u = y.unscaledValue().abs();
        if (u.signum() == 0 || x.signum() == 0) {
            return x.divide(y, mc);
        }
        // y = 2^i 5^j r: the quotient terminates exactly when r divides x's unscaled value.
        int i = u.getLowestSetBit();
        BigInteger r = u.shiftRight(i);
        int j = 0;
        for (BigInteger[] qr = r.divideAndRemainder(FIVE); qr[1].signum() == 0; qr = r.divideAndRemainder(FIVE)) {
            r = qr[0];
            j++;
        }
        BigInteger a = x.unscaledValue();
        if (!r.equals(BigInteger.ONE)) {
            BigInteger[] qr = a.divideAndRemainder(r);
            if (qr[1].signum() != 0) {
                return x.divide(y, mc);
            }
            a = qr[0];
        }
        // a / (2^i 5^j) = a 2^(k-i) 5^(k-j) / 10^k
        int k = Math.max(i, j);
        BigInteger q = a.shiftLeft(k - i).multiply(FIVE.pow(k - j));
        if (y.signum() < 0) {
            q = q.negate();
        }
        long preferred = (long) x.scale() - y.scale();
        long scale = preferred + k;
        for (BigInteger[] qr = q.divideAndRemainder(BigInteger.TEN); scale > preferred && qr[1].signum() == 0;
                qr = q.divideAndRemainder(BigInteger.TEN)) {
            q = qr[0];
            scale--;
        }
        if (scale != (int) scale) {
            return x.divide(y, mc);
        }
        BigDecimal exact = new BigDecimal(q, (int) scale);
        return mc.getPrecision() == 0 || exact.precision() <= mc.getPrecision() ? exact : exact.round(mc);
    }
}
