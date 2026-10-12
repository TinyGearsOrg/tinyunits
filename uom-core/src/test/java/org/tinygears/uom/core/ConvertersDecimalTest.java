// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.tinygears.uom.core.BaseUnits.METRE;
import static org.tinygears.uom.core.Converters.affine;
import static org.tinygears.uom.core.Factor.of;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Random;
import javax.measure.quantity.Length;
import org.junit.jupiter.api.Test;

class ConvertersDecimalTest {

    private static final UomUnit<Length> KILOMETRE =
            UomUnit.of(METRE.atoms(), Converters.scale(Factor.ofLong(1000)), "km", "kilometre");
    private static final UomConverter F_TO_C = affine(of(5, 9), of(-160, 9));

    private static BigDecimal fast(UomConverter c, String value, MathContext mc) {
        return assertTimeoutPreemptively(Duration.ofSeconds(2), () -> c.convert(new BigDecimal(value), mc));
    }

    @Test
    void hugeScalePureConversionIsFast() {
        UomConverter c = METRE.getConverterTo(KILOMETRE);
        assertEquals(new BigDecimal("1e-20000003"), fast(c, "1e-20000000", MathContext.DECIMAL64));
        assertEquals(0, new BigDecimal("1e+19999997").compareTo(fast(c, "1e+20000000", MathContext.DECIMAL64)));
    }

    @Test
    void hugeScaleFahrenheitToCelsius() {
        assertEquals(new BigDecimal("-17.77777777777778"), fast(F_TO_C, "1e-20000000", MathContext.DECIMAL64));
        assertEquals(new BigDecimal("-17.77777777777777"), fast(F_TO_C, "1e-20000000",
                new MathContext(16, RoundingMode.CEILING)));
        assertEquals(new BigDecimal("5.555555555555556E+19999999"),
                fast(F_TO_C, "1e+20000000", MathContext.DECIMAL64));
        assertEquals(new BigDecimal("5.555555555555555E+19999999"),
                fast(F_TO_C, "1e+20000000", new MathContext(16, RoundingMode.DOWN)));
    }

    @Test
    void hugeScaleIrrationalWithOffset() {
        UomConverter c = affine(Factor.PI.divide(Factor.ofLong(180)), Factor.decimal("-17.5"));
        assertEquals(new BigDecimal("-17.50000000000000"), fast(c, "1e-20000000", MathContext.DECIMAL64));
        assertEquals(new BigDecimal("1.745329251994330E+19999998"), fast(c, "1e+20000000", MathContext.DECIMAL64));
    }

    @Test
    void droppedTermBreaksTiesInItsDirection() {
        // x + 1 with x = +-2.5e100, and 5/2 + x with x = +-1e-200: exact ties at one digit plus a far term.
        UomConverter plusOne = affine(Factor.ONE, Factor.ONE);
        UomConverter plusHalf5 = affine(Factor.ONE, Factor.decimal("2.5"));
        String[][] cases = {
            {"2.5e100", "HALF_EVEN", "3E+100"}, {"2.5e100", "HALF_DOWN", "3E+100"},
            {"-2.5e100", "HALF_EVEN", "-2E+100"}, {"-2.5e100", "HALF_UP", "-2E+100"},
            {"-2.5e100", "FLOOR", "-3E+100"}, {"2.5e100", "DOWN", "2E+100"},
        };
        for (String[] t : cases) {
            MathContext mc = new MathContext(1, RoundingMode.valueOf(t[1]));
            assertEquals(new BigDecimal(t[2]), fast(plusOne, t[0], mc), () -> String.join(" ", t));
        }
        String[][] small = {
            {"1e-200", "HALF_EVEN", "3"}, {"1e-200", "HALF_DOWN", "3"}, {"-1e-200", "HALF_UP", "2"},
            {"-1e-200", "HALF_EVEN", "2"}, {"1e-200", "FLOOR", "2"}, {"-1e-200", "CEILING", "3"},
        };
        for (String[] t : small) {
            MathContext mc = new MathContext(1, RoundingMode.valueOf(t[1]));
            assertEquals(new BigDecimal(t[2]), fast(plusHalf5, t[0], mc), () -> String.join(" ", t));
        }
        assertThrows(ArithmeticException.class,
                () -> plusOne.convert(new BigDecimal("2.5e100"), new MathContext(2, RoundingMode.UNNECESSARY)));
    }

    @Test
    void stickyDigitStaysBelowBothGuardBounds() {
        // The offset's last digit is one unit below a rounding boundary: a sticky digit at that unit lands on it.
        BigInteger n = new BigInteger("111706672390044947752674" + "9".repeat(78));
        UomConverter lastDigit =
                affine(Factor.ofLong(-2503), of(n, BigInteger.valueOf(5).multiply(BigInteger.TEN.pow(56))));
        MathContext down = new MathContext(34, RoundingMode.DOWN);
        BigDecimal v = new BigDecimal("-7.3820565E-114");
        assertEquals(reference(lastDigit.factor(), lastDigit.offset(), v, down), lastDigit.convert(v, down));
        assertEquals(new BigDecimal("2.234133447800898955053499999999999E+44"), lastDigit.convert(v, down));
        // The rounding grid of the quotient sits digits(490309) below the numerator's leading digit.
        UomConverter divisor = affine(of(1, 490309), Factor.ofLong(-1));
        assertEquals(new BigDecimal("1E+97"),
                divisor.convert(new BigDecimal("4.658E+102"), new MathContext(1, RoundingMode.HALF_UP)));
    }

    @Test
    void optimisationMatchesTheExactPath() {
        Random r = new Random(20261012L);
        for (int i = 0; i < 2000; i++) {
            int p = 1 + r.nextInt(40);
            // Half the cases put value / fd within a few last digits of a rounding midpoint, the offset far below.
            boolean tie = r.nextBoolean();
            int fd = 1 + r.nextInt(r.nextBoolean() ? 20 : 100_000);
            Factor f = tie ? of(1, fd) : of(nonZero(r, 1_000_000), 1 + r.nextInt(1_000_000));
            Factor o = r.nextInt(8) == 0 ? Factor.ZERO
                    : tie ? Factor.ofLong(nonZero(r, 1000)) : of(nonZero(r, 1_000_000_000), 1 + r.nextInt(10_000));
            BigInteger unscaled;
            if (tie) {
                BigInteger digits = BigInteger.TEN.pow(p - 1).add(new BigInteger(p * 4, r).mod(BigInteger.TEN.pow(p - 1)));
                unscaled = digits.multiply(BigInteger.TEN).add(BigInteger.valueOf(5)).multiply(BigInteger.valueOf(fd))
                        .multiply(BigInteger.TEN.pow(r.nextInt(20))).divide(BigInteger.TEN.pow(r.nextInt(4)))
                        .add(BigInteger.valueOf(r.nextInt(3) - 1));
            } else {
                unscaled = new BigInteger(1 + r.nextInt(150), r).add(BigInteger.ONE);
            }
            if (r.nextBoolean()) {
                unscaled = unscaled.negate();
            }
            BigDecimal a = new BigDecimal(f.numerator().multiply(o.denominator()));
            BigDecimal b = new BigDecimal(o.numerator().multiply(f.denominator()));
            int gap = tie ? r.nextInt(p + 30) : r.nextInt(301) * (r.nextBoolean() ? 1 : -1);
            int target = adjusted(b) - adjusted(a) + gap;
            BigDecimal v = new BigDecimal(unscaled, new BigDecimal(unscaled).precision() - 1 - target);
            UomConverter c = affine(f, o);
            for (RoundingMode mode : RoundingMode.values()) {
                MathContext mc = new MathContext(p, mode);
                String where = v + " * " + f + " + " + o + " @ " + mc;
                BigDecimal expected;
                try {
                    expected = reference(f, o, v, mc);
                } catch (ArithmeticException inexact) {
                    assertThrows(ArithmeticException.class, () -> c.convert(v, mc), where);
                    continue;
                }
                assertEquals(expected, c.convert(v, mc), where);
            }
        }
    }

    @Test
    void zeroOffsetNeverAddsScalesAligned() {
        UomConverter c = METRE.getConverterTo(KILOMETRE);
        assertEquals(new BigDecimal("0E-5"), c.convert(new BigDecimal("-0E-5"), MathContext.DECIMAL64));
        assertEquals(BigDecimal.ZERO, fast(c, "0E+20000000", MathContext.DECIMAL64));
        // Exact quotients take the scale of the sum aligned to scale >= 0.
        assertEquals(new BigDecimal("1000"), c.convert(new BigDecimal("1E+6"), MathContext.DECIMAL64));
    }

    private static long nonZero(Random r, int bound) {
        return (1 + r.nextInt(bound)) * (r.nextBoolean() ? 1L : -1L);
    }

    private static int adjusted(BigDecimal x) {
        return x.precision() - x.scale() - 1;
    }

    /** The straightforward form: one exact numerator, one division, one rounding. */
    private static BigDecimal reference(Factor f, Factor o, BigDecimal v, MathContext mc) {
        BigDecimal n = v.multiply(new BigDecimal(f.numerator().multiply(o.denominator())))
                .add(new BigDecimal(o.numerator().multiply(f.denominator())));
        return n.divide(new BigDecimal(f.denominator().multiply(o.denominator())), mc);
    }
}
