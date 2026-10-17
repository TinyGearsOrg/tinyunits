/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.core.parse;

import java.io.StringReader;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.Converters;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.engine.Factor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tinygears.uom.core.BaseUnits.KELVIN;
import static org.tinygears.uom.core.BaseUnits.KILOGRAM;
import static org.tinygears.uom.core.BaseUnits.METRE;
import static org.tinygears.uom.core.BaseUnits.SECOND;

class FormulaEvaluatorTest {
    private static final UomUnit<?> N = KILOGRAM.multiply(METRE).divide(SECOND.pow(2));
    private static final UomUnit<?> DEG_C = METRE.shift(273.15);
    private static final Map<String, UomUnit<?>> UNITS = Map.of(
            "m",
            METRE,
            "kg",
            KILOGRAM,
            "s",
            SECOND,
            "cm",
            METRE.multiply(0.01),
            "N",
            N,
            "°C",
            DEG_C,
            "Ω",
            KILOGRAM,
            "µm",
            METRE.multiply(0.000001),
            "K",
            KELVIN,
            "km",
            METRE.multiply(1000));

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
        BigDecimal v = eval("0.0254 m").getConverterToAny(METRE).convert(BigDecimal.ONE, MathContext.DECIMAL128);
        assertEquals(0, v.compareTo(new BigDecimal("0.0254")));
    }

    @Test
    void fractionsAndPi() throws Exception {
        assertTrue(eval("1|1000 m").isEquivalentTo(UomUnit.unchecked(METRE.divide(1000))));
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
    void offsetUnitsTakePartInProductsByTheirFactor() throws Exception {
        assertEquals(METRE.divide(SECOND), eval("°C/s"));
        assertEquals(METRE.multiply(SECOND), eval("°C s"));
        assertEquals(METRE.pow(2), eval("°C^2"));
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
        for (String f : new String[] { "m / )", "( )", "(", "( ", "m / ( )" }) {
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
        FormulaException e = assertThrows(FormulaException.class, () -> FormulaEvaluator.evaluate("m  zz", n -> {
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
        FormulaException e = assertThrows(FormulaException.class, () -> FormulaEvaluator.evaluate("m", n -> {
            throw new Silent();
        }));
        assertTrue(e.getMessage().contains("Silent"));
    }

    @Test
    void dotIsMultiplication() throws FormulaException {
        assertEquals(METRE.multiply(SECOND), eval("m·s"));
        assertEquals(eval("kg m / s^2"), eval("kg·m/s^2"));
        assertEquals(METRE.multiply(SECOND), eval("m ·s"));
        assertEquals(METRE.multiply(SECOND), eval("m· s"));
        FormulaEvaluator.evaluate("m·s", name -> {
            assertTrue(name.indexOf('·') < 0, name);
            return UNITS.get(name);
        });
        fails("m·", "unexpected end of formula", 3);
        fails("·m", "unexpected '·'", 1);
    }

    private static UomUnit<?> defined(Definitions d, String symbol) {
        return d.definitions().stream().filter(x -> x.symbol().equals(symbol)).findFirst().orElseThrow().unit();
    }

    @Test
    void shiftEqualsTheDataDefinedOffsetUnitsExactly() throws Exception {
        Definitions d = DefinitionParser.parse("test.units", new StringReader("""
                K:kelvin !Θ
                °C:degree_Celsius K = x + 273.15
                °F:degree_Fahrenheit K = (x + 459.67) * 5|9
                """), null);
        UomUnit<?> celsius = eval("K+273.15");
        assertEquals(KELVIN.shift(273.15), celsius);
        assertEquals(defined(d, "°C"), celsius);
        assertEquals(Factor.ONE, celsius.toSystemConverter().factor());
        assertEquals(Factor.decimal("273.15"), celsius.toSystemConverter().offset());
        assertEquals(defined(d, "°F"), eval("(5|9 K)+45967|100"));
        assertEquals(defined(d, "°F"), eval("5|9 K+459.67"));
    }

    @Test
    void shiftForms() throws Exception {
        assertEquals(KELVIN.shift(-273.15), eval("K-273.15"));
        assertEquals(Factor.decimal("-273.15"), eval("K-273.15").toSystemConverter().offset());
        assertEquals(METRE.shift(1), eval("m+1"));
        assertEquals(METRE.multiply(1000).shift(0.5), eval("km+0.5"));
        assertEquals(Factor.of(1, 3), eval("K+1|3").toSystemConverter().offset());
        assertEquals(Factor.of(-1, 3), eval("K-1|3").toSystemConverter().offset());
        assertEquals(Factor.decimal("0.001"), eval("K+1e-3").toSystemConverter().offset());
        assertEquals(Factor.ofLong(100), eval("K+1E+2").toSystemConverter().offset());
        assertEquals(Factor.decimal("1000"), eval("km+1e-3").toSystemConverter().factor());
        assertEquals(Factor.decimal("1"), eval("km+1e-3").toSystemConverter().offset());
        assertEquals(METRE, eval("m+0"));
        assertEquals(METRE.shift(274.15), eval("°C+1"));
    }

    @Test
    void shiftAppliesToTheWholeExpression() throws Exception {
        assertEquals(METRE.divide(SECOND).shift(2), eval("m/s+2"));
        assertEquals(METRE.multiply(KILOGRAM).shift(2), eval("m kg+2"));
        assertEquals(METRE.pow(2).shift(-1), eval("m^2 - 1"));
        assertEquals(METRE.pow(-2).shift(-1), eval("m^-2 -1"));
        assertEquals(METRE.shift(1), eval("(m)+1"));
        assertEquals(METRE.multiply(2).shift(1), eval("2 m+1"));
    }

    @Test
    void spacesAroundTheShiftAreAllowed() throws Exception {
        for (String f : new String[] { "K + 273.15", "K+ 273.15", "K +273.15", " K\t+\t273.15 " }) {
            assertEquals(KELVIN.shift(273.15), eval(f), f);
        }
    }

    @Test
    void superscriptDigitsAreExponents() throws Exception {
        assertEquals(METRE.pow(2), eval("m²"));
        assertEquals(METRE.divide(SECOND.pow(2)), eval("m/s²"));
        assertEquals(SECOND.pow(-1), eval("s⁻¹"));
        assertEquals(KILOGRAM.multiply(METRE.pow(2)).divide(SECOND.pow(3)), eval("kg·m²/s³"));
        assertEquals(METRE.divide(SECOND).pow(2), eval("(m/s)²"));
        assertEquals(METRE.pow(10), eval("m¹⁰"));
        assertEquals(METRE.pow(-2), eval("m⁻²"));
        assertEquals(METRE.pow(0).multiply(METRE), eval("m⁰ m"));
        // A prefixed unit is raised as a whole, as with ^: km² is (km)^2.
        assertEquals(METRE.multiply(1000).pow(2), eval("km²"));
        assertEquals(eval("pi^2 m"), eval("pi²·m"));
    }

    @Test
    void aSymbolThatIsAUnitAsAWholeWinsOverTheExponentReading() throws Exception {
        UomUnit<?> own = METRE.pow(2).multiply(3);
        Map<String, UomUnit<?>> units = new HashMap<>(UNITS);
        units.put("m²", own);
        assertEquals(own, FormulaEvaluator.evaluate("m²", units::get));
        assertEquals(METRE.pow(2), FormulaEvaluator.evaluate("m^2", units::get));
    }

    @Test
    void superscriptErrors() {
        fails("x²", "unknown unit 'x²'", 1);
        fails("s⁻", "unknown unit 's⁻'", 1);
        fails("²", "unknown unit '²'", 1);
        fails("⁻²", "unknown unit '⁻²'", 1);
        fails("m⁹⁹⁹⁹", "exponent 9999 exceeds 1000", 2);
        fails("(m)⁻", "exponent must be an integer", 4);
    }

    @Test
    void exponentSignsBelongToTheNumber() throws Exception {
        assertEquals(METRE.multiply(100000), eval("1e+5 m"));
        assertEquals(METRE.multiply(100000), eval("m*1e+5"));
        assertEquals(METRE.multiply(new BigDecimal("0.0015")), eval("1.5E-3 m"));
        assertEquals(METRE.multiply(new BigDecimal("0.0015")), eval("m 1.5E-3"));
    }

    @Test
    void shiftErrors() {
        fails("m+", "expected a number after '+'", 3);
        fails("m-", "expected a number after '-'", 3);
        fails("m+ ", "expected a number after '+'", 4);
        fails("m+x", "shift must be a number", 3);
        fails("m + x", "shift must be a number", 5);
        fails("m++1", "shift must be a number", 3);
        fails("m+-1", "shift must be a number", 3);
        fails("m-+1", "shift must be a number", 3);
        fails("m+pi", "shift must be a number", 3);
        fails("m+.5", "shift must be a number", 3);
        fails("m+(1)", "shift must be a number", 3);
        fails("m+1+2", "shift is only allowed at the end of a formula", 2);
        fails("m+1 / s", "shift is only allowed at the end of a formula", 2);
        fails("m+1 s", "unexpected 's' after the shift", 5);
        fails("m+1.", "unexpected '.' after the shift", 4);
        fails("m+1e", "unexpected 'e' after the shift", 4);
        fails("m+3.e2", "unexpected '.' after the shift", 4);
        fails("m+1m", "unexpected 'm' after the shift", 4);
        fails("K - 1.5x", "unexpected 'x' after the shift", 8);
        fails("m+1^2", "shift is only allowed at the end of a formula", 2);
        fails("m+1(s)", "shift is only allowed at the end of a formula", 2);
        fails("m+1)", "shift is only allowed at the end of a formula", 2);
        fails("(m+1)", "shift is only allowed at the end of a formula", 3);
        fails("(m+1)/s", "shift is only allowed at the end of a formula", 3);
        fails("((m) - 1)", "shift is only allowed at the end of a formula", 6);
        fails("m+1|0", "division by zero", 3);
        fails("m+1|x", "expected an integer after '|'", 5);
        fails("m+1e10001", "number out of range", 3);
        fails("+1", "unexpected '+'", 1);
        fails("-1 m", "unexpected '-'", 1);
        fails("m/+1", "unexpected '+'", 3);
        fails("m^+1", "exponent must be an integer", 3);
    }

    @Test
    void lookupNeverSeesSigns() throws FormulaException {
        for (String f : new String[] { "m+1", "m - 1", "m kg -1", "(m / s)^2+1" }) {
            FormulaEvaluator.evaluate(f, name -> {
                assertTrue(name.indexOf('+') < 0 && name.indexOf('-') < 0, name);
                return UNITS.get(name);
            });
        }
        fails("m-s", "shift must be a number (use '·' or a space to multiply)", 2);
        fails("km-h", "shift must be a number (use '·' or a space to multiply)", 3);
        fails("kg-m", "shift must be a number (use '·' or a space to multiply)", 3);
        fails("m - s", "shift must be a number", 5);
        fails("m+s", "shift must be a number", 3);
        fails("e+5", "unknown unit 'e'", 1);
    }

    @Test
    void shiftLimits() throws Exception {
        assertEquals(METRE.shift(1), eval("(".repeat(100) + "m" + ")".repeat(100) + "+1"));
        fails("m".repeat(9999) + "+1", "formula too long", 1);
    }

    @Test
    void evaluationIsStateless() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<UomUnit<?>>> results = new ArrayList<>();
            for (int i = 0; i < 2000; i++) {
                String f = i % 2 == 0 ? "K+" + i + "|3" : "km - " + i;
                results.add(pool.submit(() -> eval(f)));
            }
            for (int i = 0; i < 2000; i++) {
                UomUnit<?> expected = i % 2 == 0
                        ? KELVIN.transform(Converters.shift(Factor.of(i, 3)))
                        : METRE.multiply(1000).shift(-i);
                assertEquals(expected, results.get(i).get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void gluedMinusIntegerIsAmbiguous() {
        String hint = "' for a negative exponent or '";
        fails("s-1", "ambiguous 's-1': write 's^-1" + hint + "s - 1' for a shift", 2);
        fails("m s-1", "ambiguous 's-1': write 's^-1" + hint + "s - 1' for a shift", 4);
        fails("kg m-3", "ambiguous 'm-3': write 'm^-3" + hint + "m - 3' for a shift", 5);
        fails("1/s-1", "ambiguous 's-1': write 's^-1" + hint + "s - 1' for a shift", 4);
        fails("°C-10", "ambiguous '°C-10': write '°C^-10" + hint + "°C - 10' for a shift", 3);
        fails("m s-1 kg", "ambiguous 's-1': write 's^-1" + hint + "s - 1' for a shift", 4);
        fails("m^2-1", "ambiguous 'm^2-1': write 'm^2 - 1' for a shift", 4);
        fails("(m/s)-1", "ambiguous '(m/s)-1': write '(m/s) - 1' for a shift", 6);
        fails("2 m-1.", "ambiguous 'm-1': write 'm^-1" + hint + "m - 1' for a shift", 4);
    }

    @Test
    void spacedOrNonIntegerMinusShiftsAreValid() throws Exception {
        for (String f : new String[] { "K - 5", "K -5", "K- 5", "K\t-\t5" }) {
            assertEquals(KELVIN.shift(-5), eval(f), f);
        }
        assertEquals(KELVIN.shift(5), eval("K+5"));
        assertEquals(KELVIN.shift(-273.15), eval("K-273.15"));
        assertEquals(KELVIN.transform(Converters.shift(Factor.of(-1, 3))), eval("K-1|3"));
        assertEquals(KELVIN.shift(-100), eval("K-1e2"));
        assertEquals(SECOND.inverse().shift(-1), eval("1/s - 1"));
        assertEquals(SECOND.inverse(), eval("s^-1"));
    }
}
