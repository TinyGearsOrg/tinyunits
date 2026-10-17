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
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.tinygears.uom.engine.Factor;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FactorEmitterTest {
    @TempDir
    Path tmp;

    static Stream<Factor> factors() {
        BigInteger e30 = BigInteger.TEN.pow(30);
        return Stream.of(
                Factor.ONE,
                Factor.ZERO,
                Factor.PI,
                Factor.of(3, 2),
                Factor.of(-3, 2),
                Factor.PI.divide(Factor.ofLong(180)),
                Factor.ofLong(2).root(2),
                Factor.of(e30, BigInteger.ONE),
                Factor.of(BigInteger.ONE, e30),
                Factor.decimal("1.602176634e-19"),
                Factor.ofLong(149597870700L),
                Factor.decimal("1.66053906660e-27"),
                Factor.of(BigInteger.valueOf(5), BigInteger.valueOf(7), 2, 3),
                Factor.ofLong(2).root(3).negate());
    }

    @ParameterizedTest
    @MethodSource("factors")
    void rebuildsEqualFactor(Factor factor) throws Exception {
        Imports imports = UnitExpressionEmitterTest.imports();
        String expression = FactorEmitter.emit(factor, imports);
        String source = imports.block() + "public class E { public static Object value() { return " + expression
                + "; } }";
        try (URLClassLoader loader = InMemoryCompiler.compile(Map.of("E", source), tmp)) {
            Object result = loader.loadClass("E").getMethod("value").invoke(null);
            assertEquals(factor, result);
            assertEquals(factor.hashCode(), result.hashCode());
        }
    }

    @Test
    void exactText() {
        Imports imports = UnitExpressionEmitterTest.imports();
        assertEquals("Factor.of(60L, 1L)", FactorEmitter.emit(Factor.ofLong(60), imports));
        assertEquals("Factor.of(-3L, 2L)", FactorEmitter.emit(Factor.of(-3, 2), imports));
        assertEquals("Factor.ONE", FactorEmitter.emit(Factor.ONE, imports));
        assertEquals("Factor.ZERO", FactorEmitter.emit(Factor.ZERO, imports));
        assertEquals("Factor.PI", FactorEmitter.emit(Factor.PI, imports));
        assertEquals("import org.tinygears.uom.engine.Factor;\n\n", imports.block());
        assertEquals(
                "Factor.of(new BigInteger(\"1\"), new BigInteger(\"180\"), 1, 1)",
                FactorEmitter.emit(Factor.PI.divide(Factor.ofLong(180)), imports));
        assertEquals("import java.math.BigInteger;\n\nimport org.tinygears.uom.engine.Factor;\n\n", imports.block());
    }
}
