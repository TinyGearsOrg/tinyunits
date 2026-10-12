// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.quantity;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Operand-kind, conversion and precision rules shared by the decimal quantities. */
final class Precision {

    /** Largest absolute scale accepted for a {@link BigDecimal} quantity value. */
    static final int MAX_SCALE = 100_000;

    enum Kind { EXACT, DOUBLE, DECIMAL }

    private Precision() {
    }

    static Kind kind(Number n) {
        if (n instanceof Byte || n instanceof Short || n instanceof Integer || n instanceof Long
                || n instanceof BigInteger || n instanceof AtomicInteger || n instanceof AtomicLong) {
            return Kind.EXACT;
        }
        return n instanceof BigDecimal ? Kind.DECIMAL : Kind.DOUBLE;
    }

    static double toDouble(Number n) {
        double d = n.doubleValue();
        if (n instanceof BigDecimal && Double.isInfinite(d)) {
            throw new ArithmeticException("value out of double range: " + n);
        }
        return d;
    }

    static BigDecimal toDecimal(Number n) {
        if (n instanceof BigDecimal b) {
            return b;
        }
        if (n instanceof BigInteger i) {
            return new BigDecimal(i);
        }
        if (kind(n) == Kind.EXACT) {
            return BigDecimal.valueOf(n.longValue());
        }
        if (n instanceof Float f) {
            requireFinite(f, n);
            return new BigDecimal(Float.toString(f));
        }
        double d = n.doubleValue();
        requireFinite(d, n);
        return BigDecimal.valueOf(d);
    }

    private static void requireFinite(double d, Number n) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new ArithmeticException("not a finite number: " + n);
        }
    }

    static BigDecimal checked(BigDecimal v) {
        if (Math.abs((long) v.scale()) > MAX_SCALE) {
            throw new ArithmeticException("scale out of range: " + v.scale());
        }
        return v;
    }

    /** The lower precision; 0 is unlimited and highest; equal precision keeps {@code a}. */
    static MathContext lower(MathContext a, MathContext b) {
        int pa = a.getPrecision();
        int pb = b.getPrecision();
        if (pa == 0) {
            return pb == 0 ? a : b;
        }
        return pb != 0 && pb < pa ? b : a;
    }

    /** The context used for division; an unlimited context falls back to DECIMAL128. */
    static MathContext calculation(MathContext mc) {
        return mc.getPrecision() == 0 ? MathContext.DECIMAL128 : mc;
    }
}
