// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.measure.MetricPrefix;
import javax.measure.Quantity;
import javax.measure.UnconvertibleException;
import javax.measure.Unit;
import javax.measure.format.MeasurementParseException;
import org.junit.jupiter.api.Test;
import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.Converters;
import org.tinygears.uom.core.Factor;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.core.quantity.UntypedDoubleQuantity;

class OffsetUnitFormatTest {
    private static final UomUnitFormat FORMAT = UomUnitFormat.standard();
    private static final UomUnit<?> K = BaseUnits.KELVIN;

    private static UomUnit<?> shifted(UomUnit<?> unit, Factor offset) {
        return unit.transform(Converters.shift(offset));
    }

    private static void assertFormats(UomUnitFormat format, String text, UomUnit<?> unit) {
        assertNull(unit.getSymbol());
        assertEquals(text, format.format(unit));
        assertEquals(unit, format.parse(text), text);
    }

    private static void assertFormats(String text, UomUnit<?> unit) {
        assertFormats(FORMAT, text, unit);
    }

    private static void assertRefused(UomUnit<?> unit, String message) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> FORMAT.format(unit));
        assertEquals(message, e.getMessage());
    }

    @Test
    void symbolFirstRulesKeepPriority() {
        assertEquals("°C", FORMAT.format(SI.DEGREE_CELSIUS));
        assertEquals("°F", FORMAT.format(USCustomary.DEGREE_FAHRENHEIT));
        assertEquals("°R", FORMAT.format(USCustomary.DEGREE_RANKINE));
        // Equal to the catalog units, but unnamed: the catalog symbol wins over the formula.
        assertEquals("°C", FORMAT.format(K.shift(273.15)));
        UomUnit<?> fahrenheit = K.multiply(5).divide(9).shift(459.67);
        assertNull(fahrenheit.getSymbol());
        assertEquals(USCustomary.DEGREE_FAHRENHEIT, fahrenheit);
        assertEquals("°F", FORMAT.format(fahrenheit));
        UomUnitFormat labelled = UomUnitFormat.standard();
        labelled.label(K.shift(5), "K5");
        assertEquals("K5", labelled.format(K.shift(5)));
        labelled.label(SI.DEGREE_CELSIUS, "degC");
        assertEquals("degC", labelled.format(SI.DEGREE_CELSIUS));
    }

    @Test
    void unnamedOffsetUnitsFormatAsShiftedFormulas() {
        assertFormats("K + 5", K.shift(5));
        assertFormats("K - 5", K.shift(-5));
        assertFormats("m + 1", SI.METRE.shift(1));
        assertFormats("K + 1|3", shifted(K, Factor.of(1, 3)));
        assertFormats("K - 2|7", shifted(K, Factor.of(-2, 7)));
        assertFormats("K + 0.5", K.shift(new BigDecimal("0.50")));
        assertFormats("K + 1000000", K.shift(1e6));
        assertFormats("K - 0.001", K.shift(new BigDecimal("-0.001")));
        // s = o/f: (7|9·K).shift(1.5) has offset 7|6 in K.
        UomUnit<?> u = K.scaledBy(Factor.of(7, 9)).shift(1.5);
        assertEquals(Factor.of(7, 6), u.toSystemConverter().offset());
        assertFormats("7|9·K + 1.5", u);
        assertFormats("7|9·K + 1|3", shifted(K.scaledBy(Factor.of(7, 9)), Factor.of(1, 3)));
        assertFormats("2·K + 136.575", SI.DEGREE_CELSIUS.multiply(2));
        assertFormats("1|1000·K - 273150", SI.DEGREE_CELSIUS.prefix(MetricPrefix.MILLI).shift(-546_300));
        assertFormats("3|2·m/s + 5", SI.METRE.divide(SI.SECOND).scaledBy(Factor.of(3, 2)).shift(5));
        assertFormats("2·kg·m^2/s^2 - 1|3", shifted(SI.JOULE.multiply(2), Factor.of(-1, 3)));
        // o = pi/3 and f = pi, so the shift is rational.
        assertFormats("pi·K + 1|3", shifted(K.scaledBy(Factor.PI), Factor.of(1, 3)));
        assertFormats("1|90·pi·rad + 2", SI.RADIAN.scaledBy(Factor.PI.divide(Factor.ofLong(90))).shift(2));
    }

    @Test
    void unnamedFahrenheitWithoutTheUsCatalog() {
        UomUnitFormat siOnly = new UomUnitFormat(List.of(SI.SYSTEM));
        UomUnit<?> fahrenheit = K.scaledBy(Factor.of(5, 9)).shift(459.67);
        assertFormats(siOnly, "5|9·K + 459.67", fahrenheit);
        assertEquals(USCustomary.DEGREE_FAHRENHEIT, siOnly.parse("5|9·K + 459.67"));
        assertEquals("5|9·K + 459.67", siOnly.format(USCustomary.DEGREE_FAHRENHEIT));
        assertEquals("°C", siOnly.format(K.shift(273.15)));
    }

    @Test
    void unitsWithoutFaithfulFormAreRefused() {
        assertRefused(shifted(K, Factor.PI), "unit can not be formatted: K (factor 1, offset 1*pi^1)");
        // f = pi and o = 1: the shift 1/pi is irrational.
        assertRefused(K.shift(1).scaledBy(Factor.PI), "unit can not be formatted: K (factor 1*pi^1, offset 1)");
        assertRefused(K.multiply(-2).shift(1), "unit can not be formatted: K (factor -2, offset -2)");
        Factor root2 = Factor.ofLong(2).root(2);
        assertRefused(K.scaledBy(root2).shift(1), "unit can not be formatted: K (factor (2)^(1/2), offset (2)^(1/2))");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> FORMAT.format(K.multiply(BigInteger.TWO.pow(60_000)).shift(1)));
        assertTrue(e.getMessage().startsWith("unit can not be formatted: K (factor "), e.getMessage().substring(0, 40));
        // o/f exceeds the factor size limit: refused, not an ArithmeticException.
        UomUnit<?> huge = K.transform(Converters.affine(Factor.of(BigInteger.ONE, BigInteger.valueOf(3).pow(41_000)),
                Factor.of(BigInteger.TWO.pow(65_000), BigInteger.ONE)));
        e = assertThrows(IllegalArgumentException.class, () -> FORMAT.format(huge));
        assertTrue(e.getMessage().startsWith("unit can not be formatted: K (factor 1/"), e.getMessage().substring(0, 40));
        // Offset units have no products, so no compound shifted form can arise.
        assertThrows(UnconvertibleException.class, () -> SI.DEGREE_CELSIUS.multiply(SI.METRE));
    }

    @Test
    void offsetQuantityRoundTrips() {
        UomQuantityFormat qf = new UomQuantityFormat(FORMAT);
        for (UomUnit<?> unit : List.of(K.shift(5), K.scaledBy(Factor.of(7, 9)).shift(-1.5), SI.METRE.shift(1))) {
            Quantity<?> q = UntypedDoubleQuantity.of(-2.5, unit);
            String text = qf.format(q);
            assertEquals("-2.5 " + FORMAT.format(unit), text);
            Quantity<?> parsed = qf.parse(text);
            assertEquals(-2.5, parsed.getValue());
            assertEquals(unit, parsed.getUnit());
        }
        assertEquals("1.0 7|9·K - 1.5", qf.format(UntypedDoubleQuantity.of(1, K.scaledBy(Factor.of(7, 9)).shift(-1.5))));
    }

    private enum Refusal { NEGATIVE_FACTOR, ROOT_FACTOR, IRRATIONAL_SHIFT, OVER_LONG }

    /** The reason a unit has no faithful form, recomputed from its converter. */
    private static Refusal reason(UomUnit<?> unit) {
        Factor f = unit.toSystemConverter().factor();
        Factor o = unit.toSystemConverter().offset();
        if (f.signum() < 0) {
            return Refusal.NEGATIVE_FACTOR;
        }
        if (f.rootIndex() != 1) {
            return Refusal.ROOT_FACTOR;
        }
        Factor s;
        try {
            s = o.divide(f);
        } catch (ArithmeticException e) {
            return Refusal.OVER_LONG;
        }
        if (!o.isZero() && (s.rootIndex() != 1 || s.piExponent() != 0)) {
            return Refusal.IRRATIONAL_SHIFT;
        }
        // A text over 10,000 characters: a terminating decimal is at most ~3.4 times its fraction's digits.
        long digits = f.numerator().abs().toString().length() + f.denominator().toString().length()
                + s.numerator().abs().toString().length() + s.denominator().toString().length();
        return digits > 2_900 ? Refusal.OVER_LONG : null;
    }

    private static final List<MetricPrefix> PREFIXES = List.of(MetricPrefix.values());

    private static UomUnit<?> randomOp(UomUnit<?> u, List<UomUnit<?>> catalog, Random r) {
        return switch (r.nextInt(12)) {
            case 0 -> u.multiply(r.nextInt(20) == 0 ? -r.nextInt(1, 50)
                    : BigDecimal.valueOf(r.nextInt(1, 100_000), r.nextInt(6)));
            case 1 -> u.divide(r.nextInt(1, 2000));
            case 2 -> u.pow(r.nextInt(-3, 4));
            case 3 -> u.root(r.nextInt(1, 3));
            case 4 -> r.nextBoolean() ? u.multiply(catalog.get(r.nextInt(catalog.size())))
                    : u.divide(catalog.get(r.nextInt(catalog.size())));
            case 5 -> u.prefix(PREFIXES.get(r.nextInt(PREFIXES.size())));
            case 6, 7 -> u.shift(r.nextBoolean() ? BigDecimal.valueOf(r.nextInt(-100_000, 100_000), r.nextInt(4))
                    : r.nextInt(-500, 500));
            case 8 -> u.transform(Converters.shift(Factor.of(r.nextInt(-50, 50), r.nextInt(1, 50))));
            case 9 -> u.transform(Converters.scale(switch (r.nextInt(4)) {
                case 0 -> Factor.PI.pow(r.nextInt(-2, 3));
                case 1 -> Factor.of(BigInteger.valueOf(r.nextInt(1, 20)), BigInteger.ONE, 0, r.nextInt(2, 4));
                case 2 -> Factor.of(BigInteger.valueOf(3).pow(r.nextInt(2_000, 9_000)), BigInteger.valueOf(7));
                default -> Factor.of(r.nextInt(1, 100), r.nextInt(1, 100));
            }));
            case 10 -> u.transform(Converters.shift(Factor.PI.multiply(Factor.of(r.nextInt(1, 9), 3))));
            default -> u.transform(Converters.affine(Factor.of(r.nextInt(1, 30), r.nextInt(1, 30)),
                    Factor.of(r.nextInt(-300, 300), r.nextInt(1, 30))));
        };
    }

    @Test
    void everyUnitFormatsFaithfullyOrIsRefusedForADocumentedReason() {
        List<UomUnit<?>> catalog = new ArrayList<>();
        for (UomSystemOfUnits system : UnitSystems.all()) {
            for (Unit<?> unit : system.getUnits()) {
                catalog.add((UomUnit<?>) unit);
            }
        }
        List<UomUnit<?>> units = new ArrayList<>(catalog);
        Random random = new Random(42);
        while (units.size() < catalog.size() + 20_000) {
            UomUnit<?> u = catalog.get(random.nextInt(catalog.size()));
            for (int ops = 1 + random.nextInt(4); ops > 0; ops--) {
                try {
                    u = randomOp(u, catalog, random);
                } catch (RuntimeException e) {
                    // offset units have no products or powers; some roots and factors do not exist
                }
            }
            units.add(u);
        }
        Map<Refusal, Integer> refused = new TreeMap<>();
        List<String> bad = new ArrayList<>();
        int offsetFormatted = 0;
        for (UomUnit<?> unit : units) {
            String text;
            try {
                text = FORMAT.format(unit);
            } catch (IllegalArgumentException e) {
                Refusal why = reason(unit);
                if (why == null || !e.getMessage().startsWith("unit can not be formatted: " + unit + " (factor ")) {
                    bad.add("refused " + unit.toSystemConverter() + ": " + e.getMessage());
                } else {
                    refused.merge(why, 1, Integer::sum);
                }
                continue;
            }
            if (text.matches("(?s).*[^\\s^]-[0-9].*")) {
                bad.add("glued minus " + text);
            }
            try {
                if (!unit.equals(FORMAT.parse(text))) {
                    bad.add("mismatch " + text);
                } else if (!unit.toSystemConverter().offset().isZero()) {
                    offsetFormatted++;
                }
            } catch (MeasurementParseException e) {
                bad.add("unparseable " + text + ": " + e.getMessage());
            }
        }
        assertEquals(List.of(), bad.subList(0, Math.min(bad.size(), 10)));
        assertEquals(Set.of(Refusal.values()), refused.keySet(), refused::toString);
        assertTrue(offsetFormatted > 1000, "offset units formatted: " + offsetFormatted);
        System.out.println("OffsetUnitFormatTest refused " + refused + ", offset units formatted " + offsetFormatted);
    }

    @Test
    void concurrentOffsetFormatting() throws Exception {
        List<UomUnit<?>> units = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            units.add(K.scaledBy(Factor.of(i, 7)).transform(Converters.shift(Factor.of(i, 3))));
        }
        List<String> expected = units.stream().map(FORMAT::format).toList();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                futures.add(pool.submit(() -> {
                    for (int n = 0; n < 50; n++) {
                        for (int i = 0; i < units.size(); i++) {
                            assertEquals(expected.get(i), FORMAT.format(units.get(i)));
                        }
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals("1|7·K + 1|3", expected.get(0));
    }

    @Test
    void formattingAnOffsetUnitIsFast() {
        List<UomUnit<?>> units = List.of(K.shift(5), K.scaledBy(Factor.of(7, 9)).shift(1.5),
                SI.METRE.divide(SI.SECOND).scaledBy(Factor.of(3, 2)).shift(5));
        for (int i = 0; i < 20_000; i++) {
            FORMAT.format(units.get(i % 3));
        }
        int n = 30_000;
        long start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            FORMAT.format(units.get(i % 3));
        }
        double micros = (System.nanoTime() - start) / 1000.0 / n;
        assertFalse(micros >= 50, "format took " + micros + " µs");
    }
}
