/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.jsr.parse;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.engine.parse.*;
import org.tinygears.uom.jsr.BaseUnits;
import org.tinygears.uom.jsr.UomUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tinygears.uom.jsr.BaseUnits.KILOGRAM;
import static org.tinygears.uom.jsr.BaseUnits.METRE;
import static org.tinygears.uom.jsr.BaseUnits.SECOND;
import static org.tinygears.uom.jsr.parse.ParseAdapters.e;
import static org.tinygears.uom.jsr.parse.ParseAdapters.jsr;
import static org.tinygears.uom.jsr.parse.ParseAdapters.u;

class UnitLabelRoundTripTest {
    private static final List<UomUnit<?>> BASES = List.of(
            BaseUnits.METRE,
            BaseUnits.KILOGRAM,
            BaseUnits.SECOND,
            BaseUnits.AMPERE,
            BaseUnits.KELVIN,
            BaseUnits.MOLE,
            BaseUnits.CANDELA);
    private static final Map<String, UomUnit<?>> LOOKUP = Map.of(
            "m",
            BaseUnits.METRE,
            "kg",
            BaseUnits.KILOGRAM,
            "s",
            BaseUnits.SECOND,
            "A",
            BaseUnits.AMPERE,
            "K",
            BaseUnits.KELVIN,
            "mol",
            BaseUnits.MOLE,
            "cd",
            BaseUnits.CANDELA);

    private static void assertParsesBack(UomUnit<?> u) throws FormulaException {
        assertEquals(u, jsr(u.getSymbol(), LOOKUP::get), u.getSymbol());
    }

    @Test
    void algebraLabelsParseBack() throws FormulaException {
        Random r = new Random(42);
        int labelled = 0;
        for (int i = 0; i < 500; i++) {
            UomUnit<?> u = BASES.get(r.nextInt(BASES.size()));
            for (int ops = 1 + r.nextInt(3); ops > 0; ops--) {
                UomUnit<?> other = BASES.get(r.nextInt(BASES.size()));
                u = switch (r.nextInt(6)) {
                    case 0 -> u.multiply(other);
                    case 1 -> u.divide(other);
                    case 2 -> u.pow(2);
                    case 3 -> u.pow(3);
                    case 4 -> u.pow(-1);
                    default -> u.inverse();
                };
            }
            if (u.getSymbol() != null) {
                labelled++;
                assertParsesBack(u);
            }
        }
        assertTrue(labelled >= 400, "labelled: " + labelled);
    }

    @Test
    void explicitLabels() throws FormulaException {
        UomUnit<?> ms = METRE.divide(SECOND);
        assertEquals("(m/s)^2", ms.pow(2).getSymbol());
        assertEquals("m^2·kg", METRE.pow(2).multiply(KILOGRAM).getSymbol());
        assertEquals("kg/(m·s)", KILOGRAM.divide(METRE.multiply(SECOND)).getSymbol());
        assertEquals("1/s", BaseUnits.ONE.divide(SECOND).getSymbol());
        assertEquals("s^-1", SECOND.pow(-1).getSymbol());
        assertEquals("m/s^2", METRE.divide(SECOND.pow(2)).getSymbol());
        for (UomUnit<?> u : List.of(
                ms.pow(2),
                METRE.pow(2).multiply(KILOGRAM),
                KILOGRAM.divide(METRE.multiply(SECOND)),
                BaseUnits.ONE.divide(SECOND),
                SECOND.pow(-1),
                METRE.divide(SECOND.pow(2)))) {
            assertParsesBack(u);
        }
    }
}
