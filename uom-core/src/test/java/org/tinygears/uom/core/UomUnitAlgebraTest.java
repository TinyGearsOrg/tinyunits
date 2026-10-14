// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tinygears.uom.core.BaseUnits.KELVIN;
import static org.tinygears.uom.core.BaseUnits.KILOGRAM;
import static org.tinygears.uom.core.BaseUnits.METRE;
import static org.tinygears.uom.core.BaseUnits.SECOND;

import java.math.BigDecimal;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import javax.measure.BinaryPrefix;
import javax.measure.MetricPrefix;
import javax.measure.Unit;
import javax.measure.UnconvertibleException;
import javax.measure.UnitConverter;
import javax.measure.quantity.Length;
import javax.measure.quantity.Temperature;
import org.junit.jupiter.api.Test;

class UomUnitAlgebraTest {

    @Test
    void footIsExact() {
        UomUnit<Length> foot = METRE.multiply(3048).divide(10000);
        assertEquals(0.3048, foot.getConverterTo(METRE).convert(1.0), 1e-15);
        assertTrue(foot.isEquivalentTo(METRE.multiply(0.3048)));
    }

    @Test
    void composition() {
        assertEquals(KILOGRAM.multiply(METRE).divide(SECOND).divide(SECOND),
                KILOGRAM.multiply(METRE).divide(SECOND.pow(2)));
        assertEquals(METRE, METRE.pow(2).root(2));
        assertEquals(BaseUnits.ONE.divide(METRE), METRE.inverse());
        assertEquals(METRE.divide(SECOND), METRE.divide(SECOND).getSystemUnit());
    }

    @Test
    void exponentEdges() {
        assertEquals(BaseUnits.ONE, METRE.pow(0));
        assertEquals(BaseUnits.ONE.divide(METRE.pow(2)), METRE.pow(-2));
        assertThrows(ArithmeticException.class, () -> METRE.root(0));
        assertThrows(ArithmeticException.class, () -> METRE.root(2));
        assertThrows(ArithmeticException.class, () -> METRE.pow(100_000));
        assertThrows(ArithmeticException.class, () -> METRE.pow(Integer.MAX_VALUE));
        assertEquals(METRE, METRE.pow(1));
        assertEquals(METRE, METRE.root(1));
    }

    @Test
    void exponentOverflow() {
        UomUnit<?> giga = METRE.pow(1000).pow(1000).pow(1000);
        assertEquals(METRE.pow(1000).pow(2), METRE.pow(1000).multiply(METRE.pow(1000)));
        assertThrows(ArithmeticException.class, () -> giga.pow(3));
        assertThrows(ArithmeticException.class, () -> giga.multiply(giga).multiply(giga));
        assertThrows(ArithmeticException.class, () -> giga.inverse().divide(giga).divide(giga));
    }

    @Test
    void irrationalRoot() throws Exception {
        UomUnit<?> twoSquareMetres = METRE.pow(2).multiply(2);
        UomUnit<?> r = twoSquareMetres.root(2);
        assertEquals(1.4142135623730951, r.getConverterToAny(METRE).convert(1.0));
        assertEquals(twoSquareMetres, r.pow(2));
    }

    @Test
    void offsetUnits() {
        UomUnit<Temperature> celsius = KELVIN.shift(273.15);
        assertEquals(373.15, celsius.getConverterTo(KELVIN).convert(100.0), 1e-12);
        assertEquals(KELVIN.shift(3), KELVIN.shift(1).shift(2));
        // A product takes an offset unit by its factor only: it measures differences, so °C·m is K·m.
        assertEquals(KELVIN.multiply(METRE), celsius.multiply(METRE));
        assertEquals(KELVIN.multiply(METRE), METRE.multiply(celsius));
        assertEquals(KELVIN.divide(SECOND), celsius.divide(SECOND));
        assertEquals(SECOND.divide(KELVIN), SECOND.divide(celsius));
        assertEquals(KELVIN.pow(2), celsius.pow(2));
        assertEquals(KELVIN.inverse(), celsius.inverse());
        assertEquals(KELVIN, celsius.pow(2).root(2));
        UomUnit<Temperature> fahrenheit = KELVIN.multiply(5).divide(9).shift(459.67);
        assertEquals(KELVIN.multiply(5).divide(9).divide(METRE), fahrenheit.divide(METRE));
        assertEquals(celsius, celsius.pow(1));
        assertEquals(celsius, celsius.root(1));
        // The exponent of K must still divide: °C has K^1.
        assertThrows(ArithmeticException.class, () -> celsius.root(2));
    }

    @Test
    void numbers() {
        assertThrows(IllegalArgumentException.class, () -> METRE.multiply(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> METRE.multiply(Double.valueOf(Double.POSITIVE_INFINITY)));
        assertThrows(ArithmeticException.class, () -> METRE.divide(0));
        assertThrows(ArithmeticException.class, () -> METRE.multiply(0));
        assertTrue(METRE.multiply(new BigDecimal("0.0254")).isEquivalentTo(METRE.multiply(254).divide(10000)));
        UomUnit<Length> km = METRE.multiply(1000);
        assertEquals(km, METRE.multiply(BigInteger.valueOf(1000)));
        assertEquals(km, METRE.multiply(new AtomicLong(1000)));
        assertEquals(km, METRE.multiply(1000.0f));
        assertEquals(km, METRE.divide(0.001));
        assertEquals(Factor.decimal("0.1"), METRE.multiply(0.1).toSystemConverter().factor());
        assertEquals(Factor.decimal("0.1"), METRE.multiply(Float.valueOf(0.1f)).toSystemConverter().factor());
        assertThrows(IllegalArgumentException.class, () -> METRE.multiply(Float.valueOf(Float.NaN)));
    }

    @Test
    void transform() {
        assertEquals(METRE.multiply(5), METRE.transform(Converters.scale(Factor.ofLong(5))));
        UnitConverter foreign = foreign(UnitConverter.class);
        assertThrows(IllegalArgumentException.class, () -> METRE.transform(foreign));
    }

    @Test
    void foreignOperand() {
        Unit<?> foreign = foreign(Unit.class);
        assertThrows(UnconvertibleException.class, () -> METRE.multiply(foreign));
        assertThrows(UnconvertibleException.class, () -> METRE.divide(foreign));
    }

    @Test
    void prefix() {
        UomUnit<Length> km = METRE.prefix(MetricPrefix.KILO);
        assertTrue(km.isEquivalentTo(METRE.multiply(1000)));
        assertEquals(METRE.multiply(1000), km);
        assertTrue(METRE.prefix(MetricPrefix.MICRO).isEquivalentTo(METRE.divide(1_000_000)));
        UomUnit<Length> kibimetre = METRE.prefix(BinaryPrefix.KIBI);
        assertTrue(kibimetre.isEquivalentTo(METRE.multiply(1024)));
        // A prefix does not name a unit: only a system knows which units take it and what else the symbol means.
        for (UomUnit<?> u : List.of(km, kibimetre, METRE.multiply(2).prefix(MetricPrefix.KILO),
                METRE.pow(2).prefix(MetricPrefix.KILO), KILOGRAM.prefix(MetricPrefix.MILLI),
                METRE.prefix(MetricPrefix.ATTO).prefix(MetricPrefix.DECI), KELVIN.shift(273.15).prefix(MetricPrefix.MILLI))) {
            assertNull(u.getSymbol(), u.toString());
            assertNull(u.getName(), u.toString());
        }
        UomUnit<Length> named = km.named("km", "kilometre");
        assertEquals("km", named.getSymbol());
        assertEquals(km, named);
    }

    @Test
    void anUnnamedUnitPrintsItsDefinition() {
        assertEquals("m", METRE.multiply(1).toString());
        assertEquals("1000·m", METRE.prefix(MetricPrefix.KILO).toString());
        assertEquals("1|1000·m", METRE.divide(1000).toString());
        assertEquals("18|5·m/s", METRE.divide(SECOND).multiply(3.6).toString());
        assertEquals("1000·1/s", SECOND.inverse().multiply(1000).toString());
        assertEquals("K + 5", KELVIN.shift(5).toString());
        assertEquals("K - 5", KELVIN.shift(-5).toString());
        assertEquals("K + 5463|20", KELVIN.shift(273.15).toString());
        assertEquals("5|9·K + 45967|100", KELVIN.multiply(5).divide(9).shift(459.67).toString());
        assertEquals("1*pi^1·m", METRE.scaledBy(Factor.PI).toString());
        assertEquals("km", METRE.prefix(MetricPrefix.KILO).named("km", "kilometre").toString());
    }

    @Test
    void labels() {
        assertEquals("kg·m", KILOGRAM.multiply(METRE).getSymbol());
        assertEquals("m/s", METRE.divide(SECOND).getSymbol());
        assertEquals("m^2", METRE.pow(2).getSymbol());
        assertNull(METRE.multiply(METRE).getName());
        assertNull(METRE.multiply(2).getSymbol());
        assertNull(METRE.multiply(2).multiply(METRE).getSymbol());
        assertNull(KELVIN.shift(1).getSymbol());
        assertEquals(METRE.multiply(Factor.of(1, 2).doubleValue()), METRE.scaledBy(Factor.of(1, 2)));
    }

    @Test
    void groupedLabels() {
        assertEquals("(m/s)^2", METRE.divide(SECOND).pow(2).getSymbol());
        assertEquals("m/(m/s)", METRE.divide(METRE.divide(SECOND)).getSymbol());
        assertNull(METRE.pow(2).prefix(MetricPrefix.KILO).getSymbol());
        assertNull(METRE.prefix(MetricPrefix.KILO).getSymbol());
        assertEquals("kg·m/s^2", KILOGRAM.multiply(METRE).divide(SECOND.pow(2)).getSymbol());
        assertEquals("(m/s)·kg", METRE.divide(SECOND).multiply(KILOGRAM).getSymbol());
        assertEquals("(m^2)^2", METRE.pow(2).pow(2).getSymbol());
        assertEquals("(m/s)/(kg·s)", METRE.divide(SECOND).divide(KILOGRAM.multiply(SECOND)).getSymbol());
        assertEquals("m^2·kg", METRE.pow(2).multiply(KILOGRAM).getSymbol());
    }

    private static <T> T foreign(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (p, m, a) -> null));
    }
}
