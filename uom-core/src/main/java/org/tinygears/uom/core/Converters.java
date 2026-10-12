// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core;

public final class Converters {

    public static final UomConverter IDENTITY = new AffineConverter(Factor.ONE, Factor.ZERO);

    private Converters() {
    }

    /** The converter {@code x -> factor * x + offset}. */
    public static UomConverter affine(Factor factor, Factor offset) {
        return factor.isOne() && offset.isZero() ? IDENTITY : new AffineConverter(factor, offset);
    }

    public static UomConverter scale(Factor factor) {
        return affine(factor, Factor.ZERO);
    }

    public static UomConverter shift(Factor offset) {
        return affine(Factor.ONE, offset);
    }
}
