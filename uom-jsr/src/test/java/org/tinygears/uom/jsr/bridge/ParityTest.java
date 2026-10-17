/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.jsr;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.Quantity;
import org.tinygears.uom.Scale;
import org.tinygears.uom.Unit;
import org.tinygears.uom.jsr.UomUnit;
import org.tinygears.uom.jsr.quantity.UomQuantity;
import org.tinygears.uom.quantity.Length;
import org.tinygears.uom.quantity.QuantityTypes;
import org.tinygears.uom.quantity.Temperature;
import org.tinygears.uom.systems.SI;
import org.tinygears.uom.systems.USCustomary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The native API and the JSR-385 implementation are generated from the same data and compute with the same engine:
 * their catalogs hold the same units, and every operation gives the same value, scale and unit on both sides.
 */
class ParityTest {
    private static final List<String> CATALOGS = List
            .of("SI", "USCustomary", "Imperial", "CGS", "ESU", "EMU", "Gaussian", "Information");

    @Test
    void catalogsHoldTheSameUnits() throws ReflectiveOperationException {
        int units = 0;
        for (String name : CATALOGS) {
            Class<?> nativeCatalog = Class.forName("org.tinygears.uom.systems." + name);
            Class<?> jsrCatalog = Class.forName("org.tinygears.uom.jsr.systems." + name);
            List<?> n = (List<?>) nativeCatalog.getField("UNITS").get(null);
            List<?> j = (List<?>) jsrCatalog.getField("UNITS").get(null);
            assertEquals(j.size(), n.size(), name);
            for (int i = 0; i < n.size(); i++) {
                Unit<?> nu = (Unit<?>) n.get(i);
                UomUnit<?> ju = (UomUnit<?>) j.get(i);
                assertEquals(ju.engine(), nu.engine(), name + " " + ju);
                assertEquals(ju.getSymbol(), nu.symbol(), name + " " + ju);
                assertEquals(ju.getName(), nu.name(), name + " " + ju);
            }
            Map<?, ?> np = (Map<?, ?>) nativeCatalog.getField("PREFERRED").get(null);
            Map<?, ?> jp = (Map<?, ?>) jsrCatalog.getField("PREFERRED").get(null);
            assertEquals(33, np.size(), name);
            np.forEach((type, unit) -> {
                UomUnit<?> ju = (UomUnit<?>) jp.get(standard((Class<?>) type));
                assertEquals(ju.engine(), ((Unit<?>) unit).engine(), name + " " + type);
                assertEquals(ju.getSymbol(), ((Unit<?>) unit).symbol(), name + " " + type);
            });
            units += n.size();
        }
        assertEquals(321, units);
    }

    @Test
    void typesHaveTheSameSystemUnits() throws ReflectiveOperationException {
        for (Class<?> type : QuantityTypes.types()) {
            Unit<?> n = (Unit<?>) type.getField("SYSTEM_UNIT").get(null);
            UomUnit<?> j = (UomUnit<?>) Class
                    .forName("org.tinygears.uom.jsr.quantity." + type.getSimpleName() + "Factory")
                    .getField("SYSTEM_UNIT").get(null);
            assertEquals(j.engine(), n.engine(), type.getSimpleName());
            assertEquals(j.getSymbol(), n.symbol(), type.getSimpleName());
            assertEquals(QuantityTypes.dimension(type), j.engine().dimension(), type.getSimpleName());
        }
    }

    private static Class<?> standard(Class<?> type) {
        try {
            return Class.forName("javax.measure.quantity." + type.getSimpleName());
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private static final MathContext D32 = MathContext.DECIMAL32;

    private static List<Length> lengths() {
        return List.of(
                Length.of(1.5, SI.KILOMETRE),
                Length.of(new BigDecimal("0.1"), SI.METRE),
                Length.of(3, USCustomary.FOOT),
                Length.of(new BigDecimal("2.5"), USCustomary.INCH, Scale.ABSOLUTE, D32),
                Length.of(-0.0, SI.METRE),
                Length.of(Double.NaN, SI.METRE));
    }

    private static List<Temperature> temperatures() {
        return List.of(
                Temperature.of(20, SI.DEGREE_CELSIUS),
                Temperature.of(5, SI.KELVIN, Scale.RELATIVE),
                Temperature.of(new BigDecimal("68"), USCustomary.DEGREE_FAHRENHEIT),
                Temperature.of(new BigDecimal("1.5"), SI.DEGREE_CELSIUS, Scale.RELATIVE, D32),
                Temperature.of(300, SI.KELVIN));
    }

    @Test
    void sumsComparisonsAndScalarsMatch() {
        int checked = check(lengths()) + check(temperatures());
        assertEquals(6 * 6 * 7 + 6 * 6 + 5 * 5 * 7 + 5 * 6, checked);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static <Q extends Quantity<Q>> int check(List<Q> samples) {
        int n = 0;
        for (Q a : samples) {
            UomQuantity ja = Jsr.toJsr(a);
            for (Q b : samples) {
                UomQuantity jb = Jsr.toJsr(b);
                String what = a + " and " + b;
                same(what + ": add", () -> a.add(b), () -> ja.add(jb));
                same(what + ": subtract", () -> a.subtract(b), () -> ja.subtract(jb));
                same(what + ": multiply", () -> a.multiply(b), () -> ja.multiply(jb));
                same(what + ": divide", () -> a.divide(b), () -> ja.divide(jb));
                same(what + ": compareTo", () -> a.compareTo(b), () -> ja.compareTo(jb));
                same(what + ": isEquivalentTo", () -> a.isEquivalentTo(b), () -> ja.isEquivalentTo(jb));
                same(what + ": isCloseTo", () -> a.isCloseTo(b, 1e-3), () -> ja.isCloseTo(jb, 1e-3));
                n += 7;
            }
            same(a + ": to", () -> a.to(a.unit().systemUnit()), () -> ja.to(ja.getUnit().getSystemUnit()));
            same(a + ": multiply(3)", () -> a.multiply(3), () -> ja.multiply(3));
            same(a + ": divide(7)", () -> a.divide(new BigDecimal(7)), () -> ja.divide(new BigDecimal(7)));
            same(a + ": negate", a::negate, ja::negate);
            same(a + ": inverse", a::inverse, ja::inverse);
            same(a + ": round trip", () -> a, () -> Jsr.toJsr(Jsr.fromJsr(ja, a.unit().type())));
            n += 6;
        }
        return n;
    }

    /** The same result, or an ArithmeticException on both sides. */
    private static void same(String what, Supplier<Object> nativeSide, Supplier<Object> jsrSide) {
        Object n;
        Object j;
        try {
            n = nativeSide.get();
        } catch (ArithmeticException e) {
            n = e.getClass();
        }
        try {
            j = jsrSide.get();
        } catch (ArithmeticException e) {
            j = e.getClass();
        }
        if (n instanceof Quantity<?> q) {
            assertTrue(j instanceof UomQuantity<?, ?>, what + ": " + j);
            UomQuantity<?, ?> jq = (UomQuantity<?, ?>) j;
            assertEquals(jq.getValue(), q.value(), what);
            assertEquals(jq.getScale().name(), q.scale().name(), what);
            assertEquals(((UomUnit<?>) jq.getUnit()).engine(), q.unit().engine(), what);
            assertEquals(jq.getUnit().getSymbol(), q.unit().symbol(), what);
            assertEquals(
                    jq instanceof org.tinygears.uom.jsr.quantity.AbstractDecimalQuantity<?, ?> d
                            ? d.mathContext()
                            : null,
                    q.mathContext(),
                    what);
        } else {
            assertEquals(j, n, what);
        }
        Objects.requireNonNull(what);
    }

    @Test
    void parityCoversTheErrorCases() {
        List<String> thrown = new ArrayList<>();
        for (Length a : lengths()) {
            try {
                a.add(Length.of(new BigDecimal("1"), SI.METRE));
            } catch (ArithmeticException e) {
                thrown.add(a.toString());
            }
        }
        assertEquals(List.of("NaN m"), thrown);
    }
}
