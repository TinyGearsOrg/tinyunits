// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.codegen;

import java.math.BigInteger;
import org.tinygears.uom.core.Factor;

/** Writes a Java expression that evaluates to a factor equal to a given one, without double literals. */
public final class FactorEmitter {
    private static final String FACTOR = "org.tinygears.uom.core.Factor.";

    private FactorEmitter() {
    }

    public static String emit(Factor factor) {
        if (factor.equals(Factor.ZERO)) {
            return FACTOR + "ZERO";
        }
        if (factor.equals(Factor.ONE)) {
            return FACTOR + "ONE";
        }
        if (factor.equals(Factor.PI)) {
            return FACTOR + "PI";
        }
        BigInteger n = factor.numerator();
        BigInteger d = factor.denominator();
        if (factor.isExact() && n.bitLength() < Long.SIZE && d.bitLength() < Long.SIZE) {
            return FACTOR + "of(" + n + "L, " + d + "L)";
        }
        return FACTOR + "of(new java.math.BigInteger(\"" + n + "\"), new java.math.BigInteger(\"" + d + "\"), "
                + factor.piExponent() + ", " + factor.rootIndex() + ")";
    }
}
