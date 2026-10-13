// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DecimalsTest {

    @Test
    void divideEqualsBigDecimalDivide() {
        Random random = new Random(20261013);
        RoundingMode[] modes = RoundingMode.values();
        int[] precisions = {0, 1, 2, 3, 7, 16, 34, 100};
        int exact = 0;
        for (int i = 0; i < 5000; i++) {
            BigDecimal y = divisor(random);
            BigDecimal q = number(random, 25);
            // a third are exact quotients
            BigDecimal x = random.nextInt(3) == 0 ? y.multiply(q) : q;
            MathContext mc = new MathContext(precisions[random.nextInt(precisions.length)],
                    modes[random.nextInt(modes.length)]);
            String expected = outcome(() -> x.divide(y, mc));
            assertEquals(expected, outcome(() -> Decimals.divide(x, y, mc)), x + " / " + y + " " + mc);
            exact += expected.startsWith("java.lang.ArithmeticException") ? 0 : 1;
        }
        assertEquals(true, exact > 2500, "most cases have a result: " + exact);
    }

    @Test
    void edgeCases() {
        MathContext mc = new MathContext(5);
        BigDecimal[][] cases = {{BigDecimal.ZERO, BigDecimal.ONE}, {new BigDecimal("0E+7"), new BigDecimal("8")},
                {BigDecimal.ONE, BigDecimal.ZERO}, {new BigDecimal("1000"), new BigDecimal("1")},
                {new BigDecimal("1234000"), new BigDecimal("1E-3")}, {new BigDecimal("1.50"), new BigDecimal("-2")},
                {new BigDecimal("3"), new BigDecimal(BigInteger.TWO.pow(200))},
                {new BigDecimal("21"), new BigDecimal("3")},
                {new BigDecimal("1E+2147483647"), new BigDecimal("1E-10")}};
        for (BigDecimal[] c : cases) {
            for (MathContext m : new MathContext[] {mc, MathContext.UNLIMITED, new MathContext(2, RoundingMode.DOWN)}) {
                assertEquals(outcome(() -> c[0].divide(c[1], m)), outcome(() -> Decimals.divide(c[0], c[1], m)),
                        c[0] + " / " + c[1] + " " + m);
            }
        }
    }

    @Test
    void exactQuotientAtHighPrecisionIsFast() {
        BigDecimal q = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> Decimals.divide(new BigDecimal("1.5"), new BigDecimal("1000"), new MathContext(1_000_000)));
        assertEquals(new BigDecimal("0.0015"), q);
    }

    private static BigDecimal divisor(Random random) {
        BigInteger u = BigInteger.TWO.pow(random.nextInt(12)).multiply(BigInteger.valueOf(5).pow(random.nextInt(12)));
        if (random.nextInt(3) == 0) {
            u = u.multiply(BigInteger.valueOf(1 + random.nextInt(50)));
        }
        BigDecimal d = new BigDecimal(u, random.nextInt(20) - 10);
        return random.nextBoolean() ? d.negate() : d;
    }

    private static BigDecimal number(Random random, int digits) {
        BigInteger u = new BigInteger(1 + random.nextInt(digits * 4), random);
        if (random.nextInt(4) == 0) {
            u = u.multiply(BigInteger.TEN.pow(random.nextInt(15)));
        }
        BigDecimal x = new BigDecimal(u, random.nextInt(40) - 20);
        return random.nextBoolean() ? x.negate() : x;
    }

    /** The result with its scale, or the exception class and message. */
    private static String outcome(java.util.function.Supplier<BigDecimal> division) {
        try {
            BigDecimal r = division.get();
            return r.unscaledValue() + "E" + (-r.scale());
        } catch (ArithmeticException e) {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }
}
