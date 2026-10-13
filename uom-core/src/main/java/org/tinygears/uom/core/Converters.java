// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core;

/** Factories of the affine converters. */
public final class Converters {

    /** The converter {@code x -> x}. */
    public static final UomConverter IDENTITY = new AffineConverter(Factor.ONE, Factor.ZERO);

    private Converters() {
    }

    /**
     * The converter {@code x -> factor * x + offset}.
     *
     * @param factor the factor
     * @param offset the offset
     * @return the converter
     */
    public static UomConverter affine(Factor factor, Factor offset) {
        return factor.isOne() && offset.isZero() ? IDENTITY : new AffineConverter(factor, offset);
    }

    /**
     * The converter {@code x -> factor * x}.
     *
     * @param factor the factor
     * @return the converter
     */
    public static UomConverter scale(Factor factor) {
        return affine(factor, Factor.ZERO);
    }

    /**
     * The converter {@code x -> x + offset}.
     *
     * @param offset the offset
     * @return the converter
     */
    public static UomConverter shift(Factor offset) {
        return affine(Factor.ONE, offset);
    }
}
