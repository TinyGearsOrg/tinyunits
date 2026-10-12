// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.measure.MeasurementException;
import javax.measure.MetricPrefix;
import javax.measure.Unit;
import org.junit.jupiter.api.Test;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.core.parse.Definition;
import org.tinygears.uom.core.parse.FormulaEvaluator;
import org.tinygears.uom.core.parse.FormulaException;

/** {@code +} and {@code -} became formula delimiters (D2): every text that parsed before parses identically. */
class ShiftGrammarRegressionTest {
    private static final UomUnitFormat FORMAT = UomUnitFormat.standard();
    private static final Function<String, UomUnit<?>> LOOKUP = FORMAT::lookup;
    /** {@code s-1}: a minus glued on both sides to an integer, not an exponent sign. */
    private static final Pattern GLUED_MINUS_INTEGER = Pattern.compile("(?<![\\s^]|\\d[eE])-\\d+(?![\\d.|eE])");

    /** A parse result: the unit with its symbol and name, or the error message, column and cause type. */
    record Outcome(UomUnit<?> unit, String symbol, String name, String message, int column, Class<?> cause) {
    }

    interface Evaluator {
        UomUnit<?> evaluate(String formula, Function<String, UomUnit<?>> lookup) throws FormulaException;
    }

    private static Outcome outcome(Evaluator evaluator, String formula) {
        try {
            UomUnit<?> u = evaluator.evaluate(formula, LOOKUP);
            return new Outcome(u, u.getSymbol(), u.getName(), null, 0, null);
        } catch (FormulaException e) {
            Class<?> cause = e.getCause() == null ? null : e.getCause().getClass();
            return new Outcome(null, null, null, e.getMessage(), e.column(), cause);
        }
    }

    private static Outcome before(String formula) {
        return outcome(LegacyFormulaEvaluator::evaluate, formula);
    }

    private static Outcome after(String formula) {
        return outcome(FormulaEvaluator::evaluate, formula);
    }

    private static Set<String> symbols() {
        Set<String> symbols = new LinkedHashSet<>();
        for (UomSystemOfUnits system : UnitSystems.all()) {
            system.getUnits().forEach(u -> symbols.add(u.getSymbol()));
        }
        for (SystemsCatalogTest.Catalog c : SystemsCatalogTest.CATALOGS) {
            c.units().forEach(u -> symbols.add(u.getSymbol()));
        }
        SystemsCatalogTest.PARSED.values()
                .forEach(d -> d.definitions().stream().map(Definition::symbol).forEach(symbols::add));
        return symbols;
    }

    private static Set<String> prefixedSymbols() {
        Set<String> prefixed = new LinkedHashSet<>();
        for (SystemsCatalogTest.Catalog c : SystemsCatalogTest.CATALOGS) {
            for (String symbol : c.prefixable()) {
                for (MetricPrefix p : MetricPrefix.values()) {
                    prefixed.add(p.getSymbol() + symbol);
                }
            }
        }
        return prefixed;
    }

    @Test
    void noSymbolContainsASign() {
        Set<String> all = symbols();
        all.addAll(prefixedSymbols());
        for (MetricPrefix p : MetricPrefix.values()) {
            all.add(p.getSymbol());
        }
        assertTrue(all.size() > 900, "symbols checked: " + all.size());
        List<String> signed = all.stream().filter(s -> s.indexOf('+') >= 0 || s.indexOf('-') >= 0).toList();
        assertEquals(List.of(), signed);
    }

    @Test
    void symbolsAndPrefixedSymbolsParseIdentically() {
        Set<String> all = symbols();
        all.addAll(prefixedSymbols());
        for (String s : all) {
            Outcome old = before(s);
            assertEquals(old, after(s), s);
            assertNotNull(old.unit(), s);
        }
    }

    @Test
    void formattedUnitsAndFormatTestFormulasParseIdentically() {
        List<String> texts = new ArrayList<>(List.of("(m/s)^2", "1/s", "1e3 Hz", "2.5 km/h", "g·cm/s^2", "kg m / s^2",
                "kg·m/s^2", "km^2", "km²", "m^2·kg", "x m/q", "cm^3", "1 rad/s", "abc m/s", "1 2 m", "°C", "°F",
                "°R", "1|1000·m/s", "pi·rad", "pi^2·sr", "1e+5 m", "1.5E-3 m", "m^-1", "m ^ -2 kg", "s^-1·m"));
        for (SystemsCatalogTest.Catalog c : SystemsCatalogTest.CATALOGS) {
            for (UomUnit<?> u : c.units()) {
                for (Function<UomUnit<?>, Unit<?>> op : List.<Function<UomUnit<?>, Unit<?>>>of(x -> x,
                        x -> x.multiply(1000), x -> x.divide(7), x -> x.pow(2), UomUnit::inverse)) {
                    try {
                        // The legacy parser has no shift syntax: offset units are covered by OffsetUnitFormatTest.
                        if (((UomUnit<?>) op.apply(u)).toSystemConverter().offset().isZero()) {
                            texts.add(FORMAT.format(op.apply(u)));
                        }
                    } catch (IllegalArgumentException | MeasurementException e) {
                        // no faithful form, or an offset unit raised to a power
                    }
                }
            }
        }
        assertTrue(texts.size() > 1000, "texts: " + texts.size());
        for (String t : texts) {
            assertEquals(before(t), after(t), t);
        }
    }

    /** 50,000 random formulas; only an unknown-symbol failure of a formula with a sign outside a number may change. */
    @Test
    void randomFormulasParseIdentically() {
        List<String> symbols = new ArrayList<>(symbols());
        List<String> prefixed = new ArrayList<>(prefixedSymbols());
        String[] numbers = {"1", "2", "0", "10", "0.5", "1.25", "273.15", "1|3", "5|9", "1|0", "1e3", "1E-3",
            "1e+5", "1.5E-3", "2e10001", "pi", "pi|180", "3.", "1e", ".5", "007"};
        String[] operators = {" ", " ", " ", "*", "/", "·", " / ", " * ", "", "\t"};
        String[] junk = {")", "(", "^", "|", "#", ".", "foo", "x", "e", "^x", "^ -", "=", "²"};
        String[] signed = {"+", "-", "+1", "-1", " + 273.15", " - 459.67", "+1|3", "-x", "a-b", "e+5", "2e+", "+-1",
            "++", "-(", "+ 1e-3", "-3", "s-1", "m-h"};
        Random random = new Random(20261012);
        int signedCount = 0;
        int nowValid = 0;
        int changed = 0;
        int ambiguous = 0;
        for (int i = 0; i < 50_000; i++) {
            StringBuilder f = new StringBuilder();
            boolean free = false;
            int depth = 0;
            int atoms = 1 + random.nextInt(6);
            for (int a = 0; a < atoms; a++) {
                String operator = a > 0 ? operators[random.nextInt(operators.length)] : "";
                f.append(operator);
                if (random.nextInt(6) == 0) {
                    f.append('(');
                    depth++;
                }
                int kind = random.nextInt(20);
                if (kind < 9) {
                    f.append(symbols.get(random.nextInt(symbols.size())));
                } else if (kind < 12) {
                    f.append(prefixed.get(random.nextInt(prefixed.size())));
                } else if (kind < 16) {
                    String n = numbers[random.nextInt(numbers.length)];
                    // glued to a symbol, or with its digits taken by a '^', the exponent sign was part of a symbol
                    free |= (n.contains("+") || n.contains("-"))
                            && (a > 0 && operator.isEmpty() || f.toString().matches("(?s).*\\^\\s*-?\\s*"));
                    f.append(n);
                } else if (kind < 18) {
                    f.append(junk[random.nextInt(junk.length)]);
                } else {
                    f.append(signed[random.nextInt(signed.length)]);
                    free = true;
                }
                if (random.nextInt(5) == 0) {
                    f.append(new String[] {"^2", "^-1", "^ -3", "^0", "^ 2"}[random.nextInt(5)]);
                }
                if (depth > 0 && random.nextInt(3) == 0) {
                    f.append(')');
                    depth--;
                }
            }
            if (random.nextInt(4) > 0) {
                f.append(")".repeat(depth));
            }
            String formula = f.toString();
            Outcome old = before(formula);
            Outcome now = after(formula);
            if (!free) {
                assertEquals(old, now, formula);
                continue;
            }
            signedCount++;
            if (old.unit() != null) {
                assertEquals(old, now, formula);
            }
            if (now.unit() != null) {
                nowValid++;
                assertFalse(GLUED_MINUS_INTEGER.matcher(formula).find(), formula);
            }
            ambiguous += now.message() != null && now.message().startsWith("ambiguous '") ? 1 : 0;
            if (!old.equals(now)) {
                changed++;
                // before, the sign was swallowed into an unknown symbol
                assertTrue(old.message().matches("unknown unit '.*[+-].*'"), formula + ": " + old.message());
            }
        }
        assertTrue(signedCount > 5000 && nowValid > 0 && changed > 0 && ambiguous > 0,
                signedCount + " " + nowValid + " " + changed + " " + ambiguous);
    }
}
