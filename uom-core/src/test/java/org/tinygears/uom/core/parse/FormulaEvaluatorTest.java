// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tinygears.uom.core.BaseUnits.KILOGRAM;
import static org.tinygears.uom.core.BaseUnits.METRE;
import static org.tinygears.uom.core.BaseUnits.SECOND;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.Factor;
import org.tinygears.uom.core.UomUnit;

class FormulaEvaluatorTest {
    private static final UomUnit<?> N = KILOGRAM.multiply(METRE).divide(SECOND.pow(2));
    private static final UomUnit<?> DEG_C = METRE.shift(273.15);
    private static final Map<String, UomUnit<?>> UNITS = Map.of(
            "m", METRE, "kg", KILOGRAM, "s", SECOND, "cm", METRE.multiply(0.01), "N", N,
            "°C", DEG_C, "Ω", KILOGRAM, "µm", METRE.multiply(0.000001));

    private static UomUnit<?> eval(String f) throws FormulaException {
        return FormulaEvaluator.evaluate(f, UNITS::get);
    }

    private static void fails(String f, String message, int column) {
        FormulaException e = assertThrows(FormulaException.class, () -> eval(f));
        assertEquals(message, e.getMessage());
        assertEquals(column, e.column());
    }

    @Test
    void compound() throws Exception {
        assertEquals(KILOGRAM.multiply(METRE).divide(SECOND.pow(2)), eval("kg m / s^2"));
    }

    @Test
    void decimalLiteralIsExact() throws Exception {
        BigDecimal v = (BigDecimal) eval("0.0254 m").getConverterToAny(METRE)
                .convert(BigDecimal.ONE, MathContext.DECIMAL128);
        assertEquals(0, v.compareTo(new BigDecimal("0.0254")));
    }

    @Test
    void fractionsAndPi() throws Exception {
        assertTrue(eval("1|1000 m").isEquivalentTo((UomUnit) METRE.divide(1000)));
        assertEquals(BaseUnits.ONE.scaledBy(Factor.PI.divide(Factor.ofLong(180))), eval("pi|180"));
        assertEquals(BaseUnits.ONE.scaledBy(Factor.PI), eval("pi"));
    }

    @Test
    void groupingExponentsAndJuxtaposition() throws Exception {
        assertEquals(METRE.divide(SECOND).pow(2), eval("(m / s)^2"));
        assertEquals(SECOND.inverse(), eval("s^-1"));
        assertEquals(N.multiply(METRE).multiply(2), eval("2 N m"));
        assertEquals(METRE.multiply(SECOND), eval("m*s"));
        assertEquals(METRE.pow(2).multiply(KILOGRAM), eval("m^2 kg"));
        assertEquals(METRE.pow(2).multiply(KILOGRAM), eval("m ^ 2 kg"));
        assertEquals(METRE.multiply(SECOND), eval("m(s)"));
    }

    @Test
    void juxtapositionBindsTighterThanDivision() throws Exception {
        assertEquals(METRE.divide(SECOND.multiply(SECOND)), eval("m / s s"));
        assertEquals(METRE.divide(KILOGRAM.multiply(SECOND)), eval("m / kg s"));
        assertEquals(METRE.divide(KILOGRAM.multiply(SECOND)), eval("m / kg(s)"));
        assertEquals(METRE.divide(SECOND).multiply(KILOGRAM), eval("m / s * kg"));
        assertEquals(METRE.divide(SECOND).divide(KILOGRAM), eval("m / s / kg"));
        assertEquals(METRE.multiply(SECOND.multiply(KILOGRAM)), eval("m * s kg"));
        assertEquals(METRE.divide(SECOND.multiply(SECOND)).multiply(KILOGRAM), eval("m / s^2 * kg"));
        assertEquals(METRE.multiply(2).divide(SECOND.multiply(4)), eval("2 m / 4 s"));
    }

    @Test
    void whitespaceVariations() throws Exception {
        assertEquals(eval("kg m / s^2"), eval("\t kg\tm  /\ts^2 \n"));
    }

    @Test
    void symbolsAreExactLookupResultsAndMayBeNonAscii() throws Exception {
        assertEquals(DEG_C, eval("°C"));
        assertEquals(KILOGRAM, eval("Ω"));
        assertEquals(METRE.multiply(0.000001), eval("µm"));
    }

    @Test
    void errors() {
        fails("foo", "unknown unit 'foo'", 1);
        fails("m /", "unexpected end of formula", 4);
        fails("m ^ x", "exponent must be an integer", 5);
        fails("(m", "missing ')'", 3);
        fails("1|0", "division by zero", 1);
        fails("", "empty formula", 1);
        fails("   ", "empty formula", 1);
        fails("m )", "unexpected ')'", 3);
        fails("m s foo kg", "unknown unit 'foo'", 5);
        fails("m ^ 99999999999", "exponent must be an integer", 5);
        fails("1|x", "expected an integer after '|'", 3);
    }

    @Test
    void evaluationFailuresAreWrapped() {
        FormulaException e = assertThrows(FormulaException.class, () -> eval("m^100000"));
        assertEquals(2, e.column());
        assertInstanceOf(ArithmeticException.class, e.getCause());
        e = assertThrows(FormulaException.class, () -> eval("1|0"));
        assertInstanceOf(ArithmeticException.class, e.getCause());
    }

    @Test
    void limits() throws Exception {
        assertEquals(METRE, eval("(".repeat(100) + "m" + ")".repeat(100)));
        fails("(".repeat(101) + "m" + ")".repeat(101), "formula nested too deeply", 101);
        fails("m ".repeat(5001), "formula too long", 1);
    }

    @Test
    void whitespaceInsideParentheses() throws Exception {
        assertEquals(METRE, eval("( m )"));
        assertEquals(METRE.divide(SECOND).pow(2), eval("( m / s )^2"));
        assertEquals(METRE.multiply(KILOGRAM), eval("(  m  )  kg"));
    }

    @Test
    void lookupNeverSeesEmptySymbol() {
        java.util.List<String> seen = new java.util.ArrayList<>();
        for (String f : new String[] {"m / )", "( )", "(", "( ", "m / ( )"}) {
            assertThrows(FormulaException.class, () -> FormulaEvaluator.evaluate(f, n -> {
                seen.add(n);
                return UNITS.get(n);
            }));
        }
        assertTrue(!seen.contains(""));
        fails("m / )", "unexpected ')'", 5);
        fails("( )", "unexpected ')'", 3);
        fails("(", "unexpected end of formula", 2);
    }

    @Test
    void hugeFactorsAreRejected() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            fails("m ((10^1000)^1000)^100", "factor too large", 13);
            assertEquals(METRE.multiply(new BigDecimal("1e10000")), eval("1e10000 m"));
        });
    }

    @Test
    void lookupFailureIsWrappedWithColumn() {
        FormulaException e = assertThrows(FormulaException.class,
                () -> FormulaEvaluator.evaluate("m  zz", n -> {
                    if (n.equals("zz")) {
                        throw new IllegalStateException("boom");
                    }
                    return METRE;
                }));
        assertTrue(e.getMessage().contains("boom"));
        assertEquals(4, e.column());
        assertInstanceOf(IllegalStateException.class, e.getCause());
    }

    @Test
    void messageLessLookupFailureUsesClassName() {
        class Silent extends RuntimeException {
            private static final long serialVersionUID = 1L;
        }
        FormulaException e = assertThrows(FormulaException.class,
                () -> FormulaEvaluator.evaluate("m", n -> {
                    throw new Silent();
                }));
        assertTrue(e.getMessage().contains("Silent"));
    }
}
