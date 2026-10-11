package org.tinygears.tinyunits.quantities;

import org.junit.jupiter.api.Test;
import org.tinygears.tinyunits.IncommensurableException;

import static org.junit.jupiter.api.Assertions.*;
import static org.tinygears.tinyunits.quantities.Units.*;

class QuantitiesTest {

    @Test
    void convertsBetweenUnits() {
        assertEquals(1000, LengthFactory.of(1, KILOMETRE).to(METRE).value(), 1e-9);
        assertEquals(1.609344, LengthFactory.of(1, MILE).value(KILOMETRE), 1e-9);
    }

    @Test
    void addKeepsUnitOfLeftOperand() {
        Length sum = LengthFactory.of(1, KILOMETRE).add(LengthFactory.of(500, METRE));
        assertEquals(1.5, sum.value(), 1e-9);
        assertEquals(KILOMETRE, sum.unit());
    }

    @Test
    void subtractAndNegate() {
        Mass diff = MassFactory.of(1, KILOGRAM).subtract(MassFactory.of(250, GRAM));
        assertEquals(0.75, diff.value(), 1e-9);
        assertEquals(-0.75, diff.negate().value(), 1e-9);
    }

    @Test
    void comparesAcrossUnits() {
        assertTrue(LengthFactory.of(1, KILOMETRE).compareTo(LengthFactory.of(999, METRE)) > 0);
        assertEquals(0, TimeFactory.of(1, HOUR).compareTo(TimeFactory.of(60, MINUTE)));
    }

    @Test
    void derivedUnitsAreTyped() {
        assertEquals(10, SpeedFactory.of(36, KILOMETRE_PER_HOUR).value(METRE_PER_SECOND), 1e-9);
    }

    @Test
    void rejectsUnitOfWrongDimension() {
        assertThrows(IncommensurableException.class, () -> SpeedFactory.from(METRE));
    }

    @Test
    void handlesOffsetUnits() {
        assertEquals(373.15, TemperatureFactory.of(100, CELSIUS).value(KELVIN), 1e-9);
        assertEquals(0, TemperatureFactory.of(273.15, KELVIN).value(CELSIUS), 1e-9);
    }

    @Test
    void offsetUnitsCanNotBeDivided() {
        assertThrows(IllegalArgumentException.class, () -> CELSIUS.divide(SECOND));
    }

    @Test
    void equalsAndToString() {
        assertEquals(LengthFactory.of(2, METRE), LengthFactory.of(2, METRE));
        assertEquals("2.0 m", LengthFactory.of(2, METRE).toString());
    }
}
