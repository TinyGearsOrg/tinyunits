// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tinygears.uom.core.Factor;

class FactorEmitterTest {
    @TempDir
    Path tmp;

    static Stream<Factor> factors() {
        BigInteger e30 = BigInteger.TEN.pow(30);
        return Stream.of(Factor.ONE, Factor.ZERO, Factor.PI, Factor.of(3, 2), Factor.of(-3, 2),
                Factor.PI.divide(Factor.ofLong(180)), Factor.ofLong(2).root(2), Factor.of(e30, BigInteger.ONE),
                Factor.of(BigInteger.ONE, e30), Factor.decimal("1.602176634e-19"), Factor.ofLong(149597870700L),
                Factor.decimal("1.66053906660e-27"),
                Factor.of(BigInteger.valueOf(5), BigInteger.valueOf(7), 2, 3), Factor.ofLong(2).root(3).negate());
    }

    @ParameterizedTest
    @MethodSource("factors")
    void rebuildsEqualFactor(Factor factor) throws Exception {
        String source = "public class E { public static Object value() { return " + FactorEmitter.emit(factor)
                + "; } }";
        try (URLClassLoader loader = InMemoryCompiler.compile(Map.of("E", source), tmp)) {
            Object result = loader.loadClass("E").getMethod("value").invoke(null);
            assertEquals(factor, result);
            assertEquals(factor.hashCode(), result.hashCode());
        }
    }

    @Test
    void exactText() {
        assertEquals("org.tinygears.uom.core.Factor.of(60L, 1L)", FactorEmitter.emit(Factor.ofLong(60)));
        assertEquals("org.tinygears.uom.core.Factor.of(-3L, 2L)", FactorEmitter.emit(Factor.of(-3, 2)));
        assertEquals("org.tinygears.uom.core.Factor.ONE", FactorEmitter.emit(Factor.ONE));
        assertEquals("org.tinygears.uom.core.Factor.ZERO", FactorEmitter.emit(Factor.ZERO));
        assertEquals("org.tinygears.uom.core.Factor.PI", FactorEmitter.emit(Factor.PI));
        assertEquals("org.tinygears.uom.core.Factor.of(new java.math.BigInteger(\"1\"), "
                + "new java.math.BigInteger(\"180\"), 1, 1)", FactorEmitter.emit(Factor.PI.divide(Factor.ofLong(180))));
    }
}
