/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.codegen;

import java.math.BigInteger;

import org.tinygears.uom.engine.Factor;

/** Writes a Java expression that evaluates to a factor equal to a given one, without double literals. */
public final class FactorEmitter {
    private FactorEmitter() {
    }

    /** {@code imports} gives the names of the classes the expression uses. */
    public static String emit(Factor factor, Imports imports) {
        String factorClass = imports.name("org.tinygears.uom.engine.Factor");
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
