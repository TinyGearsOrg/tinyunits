// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.codegen;

import java.math.BigInteger;
import org.tinygears.uom.core.Factor;

/** Writes a Java expression that evaluates to a factor equal to a given one, without double literals. */
public final class FactorEmitter {
    private FactorEmitter() {
    }

    /** {@code imports} gives the names of the classes the expression uses. */
    public static String emit(Factor factor, Imports imports) {
        String factorClass = imports.name("org.tinygears.uom.core.Factor");
        if (factor.equals(Factor.ZERO)) {
            return factorClass + ".ZERO";
        }
        if (factor.equals(Factor.ONE)) {
            return factorClass + ".ONE";
        }
        if (factor.equals(Factor.PI)) {
            return factorClass + ".PI";
        }
        BigInteger n = factor.numerator();
        BigInteger d = factor.denominator();
        if (factor.isExact() && n.bitLength() < Long.SIZE && d.bitLength() < Long.SIZE) {
            return factorClass + ".of(" + n + "L, " + d + "L)";
        }
        String big = imports.name("java.math.BigInteger");
        return factorClass + ".of(new " + big + "(\"" + n + "\"), new " + big + "(\"" + d + "\"), "
                + factor.piExponent() + ", " + factor.rootIndex() + ")";
    }
}
