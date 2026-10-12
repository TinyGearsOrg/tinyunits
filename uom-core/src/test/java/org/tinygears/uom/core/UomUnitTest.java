// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tinygears.uom.core.BaseUnits.KILOGRAM;
import static org.tinygears.uom.core.BaseUnits.METRE;
import static org.tinygears.uom.core.BaseUnits.ONE;
import static org.tinygears.uom.core.BaseUnits.SECOND;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.measure.Unit;
import org.junit.jupiter.api.Test;

class UomUnitTest {

    private static final UnitAtom M = atom(METRE);
    private static final UnitAtom KG = atom(KILOGRAM);
    private static final UnitAtom S = atom(SECOND);

    private static UnitAtom atom(UomUnit<?> base) {
        return base.atoms().keySet().iterator().next();
    }

    private static Map<UnitAtom, Integer> atoms(Object... atomExp) {
        Map<UnitAtom, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < atomExp.length; i += 2) {
            m.put((UnitAtom) atomExp[i], (Integer) atomExp[i + 1]);
        }
        return m;
    }

    private static UomUnit<?> km() {
        return UomUnit.of(atoms(M, 1), Converters.scale(Factor.ofLong(1000)), "km", "kilometre");
    }

    private static String systemSymbol(Map<UnitAtom, Integer> atoms) {
        return UomUnit.of(atoms, Converters.IDENTITY, null, null).getSystemUnit().getSymbol();
    }

    @Test
    void metreHasLengthDimension() {
        assertEquals(UomDimension.LENGTH, METRE.getDimension());
    }

    @Test
    void baseUnitsOfBaseUnitAndOneAreNull() {
        assertNull(METRE.getBaseUnits());
        assertNull(ONE.getBaseUnits());
    }

    @Test
    void baseUnitsOfScaledUnitMapMetreToOne() {
        Map<? extends Unit<?>, Integer> base = km().getBaseUnits();
        assertNotNull(base);
        assertEquals(Map.of(METRE, 1), base);
    }

    @Test
    void systemUnitOfKilometreIsMetre() {
        assertEquals(METRE, km().getSystemUnit());
    }

    @Test
    void equalsIgnoresLabels() {
        UomUnit<?> a = UomUnit.of(atoms(M, 1, S, -1), Converters.IDENTITY, "a", "alpha");
        UomUnit<?> b = UomUnit.of(atoms(M, 1, S, -1), Converters.IDENTITY, "b", "beta");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void scaledUnitIsNotEqualToMetre() {
        assertNotEquals(METRE, km());
    }

    @Test
    void isCompatibleComparesDimensions() {
        assertTrue(METRE.isCompatible(km()));
        assertFalse(METRE.isCompatible(SECOND));
    }

    @Test
    void isEquivalentToNullThrows() {
        assertThrows(NullPointerException.class, () -> METRE.isEquivalentTo(null));
    }

    @Test
    void zeroExponentsAreDropped() {
        assertEquals(METRE, UomUnit.of(atoms(M, 1, S, 0), Converters.IDENTITY, null, null));
    }

    @Test
    void systemUnitSymbols() {
        assertEquals("m/s", systemSymbol(atoms(M, 1, S, -1)));
        assertEquals("kg·m/s^2", systemSymbol(atoms(S, -2, M, 1, KG, 1)));
        assertEquals("1/s", systemSymbol(atoms(S, -1)));
        assertEquals("m^2", systemSymbol(atoms(M, 2)));
        assertEquals("kg/(m·s)", systemSymbol(atoms(KG, 1, M, -1, S, -1)));
        assertEquals("1", systemSymbol(atoms()));
        assertNull(km().getSystemUnit().getName());
    }

    @Test
    void toStringOfUnlabelledUnitIsNotNull() {
        UomUnit<?> u = UomUnit.of(atoms(M, 1, S, -1), Converters.IDENTITY, null, null);
        assertNotNull(u.toString());
        assertEquals("km", km().toString());
    }

    @Test
    void namedKeepsAtomsAndConverter() {
        UomUnit<?> k = km().named("x", "y");
        assertEquals(km(), k);
        assertEquals("x", k.getSymbol());
        assertEquals("y", k.getName());
    }

    @Test
    void uncheckedReturnsTheSameUnit() {
        UomUnit<?> original = METRE.divide(SECOND);
        UomUnit<javax.measure.quantity.Speed> speed = UomUnit.unchecked(original);
        assertEquals(original, speed);
        assertSame(original, speed);
        assertThrows(NullPointerException.class, () -> UomUnit.unchecked(null));
    }

    @Test
    void dimensionIsComputedOnce() {
        UomUnit<?> u = METRE.divide(SECOND);
        assertSame(u.getDimension(), u.getDimension());
    }

    @Test
    void dimensionOverflowSurfacesAtConstruction() {
        UnitAtom other = new UnitAtom("m2", UomDimension.LENGTH);
        assertThrows(ArithmeticException.class,
                () -> UomUnit.of(atoms(M, Integer.MAX_VALUE, other, 2), Converters.IDENTITY, null, null));
    }
}
