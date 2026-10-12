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
        assertThrows(UnconvertibleException.class, () -> celsius.multiply(METRE));
        assertThrows(UnconvertibleException.class, () -> METRE.multiply(celsius));
        assertThrows(UnconvertibleException.class, () -> celsius.divide(SECOND));
        assertThrows(UnconvertibleException.class, () -> celsius.pow(2));
        assertThrows(UnconvertibleException.class, () -> celsius.root(2));
        assertThrows(UnconvertibleException.class, celsius::inverse);
        assertEquals(celsius, celsius.pow(1));
        assertEquals(celsius, celsius.root(1));
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
        assertEquals("km", km.getSymbol());
        assertTrue(METRE.prefix(MetricPrefix.MICRO).isEquivalentTo(METRE.divide(1_000_000)));
        UomUnit<Length> kibimetre = METRE.prefix(BinaryPrefix.KIBI);
        assertTrue(kibimetre.isEquivalentTo(METRE.multiply(1024)));
        assertEquals("Kim", kibimetre.getSymbol());
        assertNull(METRE.multiply(2).prefix(MetricPrefix.KILO).getSymbol());
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

    private static <T> T foreign(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (p, m, a) -> null));
    }
}
