package org.tinygears.uom.core;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.tinygears.uom.core.BaseUnits.METRE;
import static org.tinygears.uom.core.BaseUnits.ONE;
import static org.tinygears.uom.core.BaseUnits.SECOND;

import javax.measure.Unit;
import javax.measure.quantity.Frequency;
import javax.measure.quantity.Length;
import javax.measure.quantity.Speed;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UomUnitAsTypeTest {

    private static final UomUnit<?> HZ = ONE.divide(SECOND).alternate("Hz");

    @BeforeAll
    static void systemUnits() {
        TestServiceProvider.SYSTEM_UNITS.put(Length.class, METRE);
        TestServiceProvider.SYSTEM_UNITS.put(Speed.class, METRE.divide(SECOND));
        TestServiceProvider.SYSTEM_UNITS.put(Frequency.class, HZ);
    }

    @Test
    void matchingSystemUnit() {
        UomUnit<Length> km = METRE.multiply(1000);
        Unit<Length> typed = km.asType(Length.class);
        assertSame(km, typed);
    }

    @Test
    void mismatchedSystemUnit() {
        assertThrows(ClassCastException.class, () -> METRE.asType(Speed.class));
    }

    @Test
    void hertzIsFrequencyButPerSecondIsNot() {
        assertSame(HZ, HZ.asType(Frequency.class));
        UomUnit<?> khz = HZ.multiply(1000);
        assertSame(khz, khz.asType(Frequency.class));
        assertThrows(ClassCastException.class, () -> ONE.divide(SECOND).asType(Frequency.class));
    }
}
