// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.quantity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.measure.Quantity;
import javax.measure.UnconvertibleException;
import javax.measure.Unit;
import javax.measure.quantity.Length;
import org.junit.jupiter.api.Test;
import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.UomUnit;

class AbstractDoubleQuantityTest {

    private static final UomUnit<Length> METRE = BaseUnits.METRE;
    private static final UomUnit<Length> KILOMETRE = BaseUnits.METRE.multiply(1000);
    private static final UomUnit<Length> CELSIUS = UomUnit.unchecked(BaseUnits.KELVIN.shift(273.15));
    private static final UomUnit<Length> KELVIN = UomUnit.unchecked(BaseUnits.KELVIN);

    private static TestLengthImpl q(double value, UomUnit<Length> unit) {
        return new TestLengthImpl(value, unit);
    }

    @SuppressWarnings("unchecked")
    private static Unit<Length> foreignUnit() {
        return (Unit<Length>) Proxy.newProxyInstance(
                AbstractDoubleQuantityTest.class.getClassLoader(), new Class<?>[] {Unit.class}, (p, m, a) -> null);
    }

    @SuppressWarnings("unchecked")
    private static Quantity<Length> foreignQuantity(Number value, Unit<Length> unit) {
        return (Quantity<Length>) Proxy.newProxyInstance(
                AbstractDoubleQuantityTest.class.getClassLoader(), new Class<?>[] {Quantity.class},
                (p, m, a) -> switch (m.getName()) {
                    case "getValue" -> value;
                    case "getUnit" -> unit;
                    case "getScale" -> Quantity.Scale.ABSOLUTE;
                    default -> throw new UnsupportedOperationException(m.getName());
                });
    }

    @Test
    void convertsToAnotherUnit() {
        TestLength r = q(1.5, KILOMETRE).to(METRE);
        assertEquals(1500.0, r.getValue());
        assertEquals(METRE, r.getUnit());
        assertEquals(q(1.5, KILOMETRE), q(1.5, KILOMETRE).to(KILOMETRE));
        assertEquals(100.0, q(373.15, KELVIN).to(CELSIUS).getValue().doubleValue(), 1e-12);
    }

    @Test
    void toForeignUnitFails() {
        TestLengthImpl one = q(1, METRE);
        assertThrows(UnconvertibleException.class, () -> one.to(foreignUnit()));
        assertThrows(NullPointerException.class, () -> one.to(null));
    }

    @Test
    void toIncompatibleUnitFails() {
        UomUnit<Length> second = UomUnit.unchecked(BaseUnits.SECOND);
        assertThrows(UnconvertibleException.class, () -> q(1, METRE).to(second));
    }

    @Test
    void equalsAndHashCode() {
        assertEquals(q(1.5, METRE), q(1.5, METRE));
        assertEquals(q(1.5, METRE).hashCode(), q(1.5, METRE).hashCode());
        assertNotEquals(q(1, KILOMETRE), q(1000, METRE));
        assertNotEquals(q(0.0, METRE), q(-0.0, METRE));
        assertEquals(q(Double.NaN, METRE), q(Double.NaN, METRE));
        assertEquals(q(Double.NaN, METRE).hashCode(), q(Double.NaN, METRE).hashCode());
        assertNotEquals(q(1, METRE), new TestLengthImpl(1, METRE, Quantity.Scale.RELATIVE));
        assertNotEquals(q(1, METRE), null);
        assertNotEquals(q(1, METRE), "1.0 m");
    }

    @Test
    void equalsAcrossSubclassesIsFalse() {
        TestLengthImpl other = new TestLengthImpl(1, METRE, Quantity.Scale.ABSOLUTE) { };
        assertNotEquals(q(1, METRE), other);
        assertNotEquals(other, q(1, METRE));
    }

    @Test
    void equivalence() {
        assertTrue(q(1, KILOMETRE).isEquivalentTo(q(1000, METRE)));
        assertTrue(q(100, CELSIUS).isEquivalentTo(q(373.15, KELVIN)));
        assertTrue(q(373.15, KELVIN).isEquivalentTo(q(100, CELSIUS)));
        assertTrue(q(1.0, METRE).isEquivalentTo(q(1.0 + 1e-13, METRE)));
        assertFalse(q(1.0, METRE).isEquivalentTo(q(1.0 + 1e-9, METRE)));
        TestLengthImpl nan = q(Double.NaN, METRE);
        assertFalse(nan.isEquivalentTo(nan));
        assertFalse(nan.isEquivalentTo(q(1, METRE)));
        assertFalse(q(1, METRE).isEquivalentTo(nan));
        double inf = Double.POSITIVE_INFINITY;
        assertTrue(q(inf, METRE).isEquivalentTo(q(inf, KILOMETRE)));
        assertTrue(q(-inf, METRE).isEquivalentTo(q(-inf, METRE)));
        assertFalse(q(inf, METRE).isEquivalentTo(q(-inf, METRE)));
        assertFalse(q(inf, METRE).isEquivalentTo(q(Double.MAX_VALUE, METRE)));
        assertFalse(q(Double.MAX_VALUE, METRE).isEquivalentTo(q(inf, METRE)));
        assertTrue(q(0.0, METRE).isEquivalentTo(q(-0.0, METRE)));
        assertThrows(UnconvertibleException.class, () -> q(1, METRE).isEquivalentTo(foreignQuantity(1, foreignUnit())));
        assertTrue(q(1, KILOMETRE).isEquivalentTo(foreignQuantity(1000, METRE)));
        assertThrows(NullPointerException.class, () -> q(1, METRE).isEquivalentTo(null));
    }

    @Test
    void incompatibleDimensions() {
        assertFalse(q(1, METRE).isEquivalentTo(q(1, KELVIN)));
        assertThrows(UnconvertibleException.class, () -> q(1, METRE).compareTo(q(1, KELVIN)));
    }

    @Test
    void ordering() {
        assertTrue(q(1, KILOMETRE).compareTo(q(999, METRE)) > 0);
        assertEquals(0, q(1, KILOMETRE).compareTo(q(1000, METRE)));
        assertNotEquals(q(1, KILOMETRE), q(1000, METRE));
        assertTrue(q(Double.NaN, METRE).compareTo(q(Double.POSITIVE_INFINITY, METRE)) > 0);
        assertTrue(q(-0.0, METRE).compareTo(q(0.0, METRE)) < 0);
        assertTrue(q(1, KILOMETRE).compareTo(foreignQuantity(999, METRE)) > 0);
        assertThrows(UnconvertibleException.class, () -> q(1, METRE).compareTo(foreignQuantity(1, foreignUnit())));
        assertThrows(NullPointerException.class, () -> q(1, METRE).compareTo(null));

        List<TestLengthImpl> list = new ArrayList<>(List.of(
                q(Double.NaN, METRE), q(2, KILOMETRE), q(5, METRE), q(1, KILOMETRE), q(-3, METRE)));
        list.sort(null);
        assertEquals(List.of(q(-3, METRE), q(5, METRE), q(1, KILOMETRE), q(2, KILOMETRE), q(Double.NaN, METRE)), list);
    }

    @Test
    void comparisonAndEquivalenceAreConsistentOnASample() {
        List<UomUnit<Length>> units = List.of(METRE, KILOMETRE, METRE.multiply(0.3048), METRE.divide(100));
        Random random = new Random(42);
        double[] systemValues = random.doubles(20, -1e6, 1e6).toArray();
        List<TestLengthImpl> sample = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            UomUnit<Length> unit = units.get(random.nextInt(units.size()));
            double system = systemValues[random.nextInt(systemValues.length)];
            sample.add(q(unit.toSystemConverter().inverse().convert(system), unit));
        }
        int equivalentPairs = 0;
        for (TestLengthImpl a : sample) {
            for (TestLengthImpl b : sample) {
                assertEquals(Integer.signum(a.compareTo(b)), -Integer.signum(b.compareTo(a)));
                assertEquals(a.isEquivalentTo(b), b.isEquivalentTo(a));
                equivalentPairs += a.isEquivalentTo(b) && !a.equals(b) ? 1 : 0;
                if (a.compareTo(b) <= 0) {
                    for (TestLengthImpl c : sample) {
                        if (b.compareTo(c) <= 0) {
                            assertTrue(a.compareTo(c) <= 0);
                        }
                    }
                }
            }
        }
        assertTrue(equivalentPairs > 0);
    }

    @Test
    void toStringFormats() {
        assertEquals("1.5 km", q(1.5, KILOMETRE.named("km", "kilometre")).toString());
        String unlabelled = q(1.5, KILOMETRE.named(null, null)).toString();
        assertNotNull(unlabelled);
        assertTrue(unlabelled.startsWith("1.5 "), unlabelled);
        assertEquals("NaN m", q(Double.NaN, METRE).toString());
    }

    @Test
    void scaleIsKept() {
        TestLengthImpl relative = new TestLengthImpl(1, KILOMETRE, Quantity.Scale.RELATIVE);
        assertSame(Quantity.Scale.RELATIVE, relative.to(METRE).getScale());
        assertSame(Quantity.Scale.RELATIVE, relative.getScale());
        assertThrows(NullPointerException.class, () -> new TestLengthImpl(1, METRE, null));
    }

    @Test
    void constructorRejectsNullUnit() {
        assertThrows(NullPointerException.class, () -> new TestLengthImpl(1, null, Quantity.Scale.ABSOLUTE));
    }

    @Test
    void accessors() {
        TestLengthImpl x = q(2.5, METRE);
        assertEquals(Double.valueOf(2.5), x.getValue());
        assertEquals(2.5, x.doubleValue());
        assertSame(METRE, x.getUnit());
        assertSame(Quantity.Scale.ABSOLUTE, x.getScale());
    }
}
