/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.systems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.measure.MetricPrefix;
import javax.measure.quantity.Length;
import javax.measure.quantity.Time;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.UomDimension;
import org.tinygears.uom.core.UomUnit;

import static org.junit.jupiter.api.Assertions.*;

class UomSystemOfUnitsTest {

    static final UomUnit<?> M = BaseUnits.METRE.named("m", "metre");
    static final UomUnit<?> KM = BaseUnits.METRE.prefix(MetricPrefix.KILO).named("km", "kilometre");
    static final UomUnit<?> S = BaseUnits.SECOND.named("s", "second");
    static final UomUnit<?> HZ = BaseUnits.ONE.divide(BaseUnits.SECOND).alternate("Hz").named("Hz", "hertz");

    static UomSystemOfUnits system() {
        return new UomSystemOfUnits("Test", List.of(M, KM, S, HZ), Map.of(Length.class, M), Set.of("m", "s"));
    }

    @Test
    void name() {
        assertEquals("Test", system().getName());
        assertEquals("Test", system().toString());
        assertEquals("SI", SI.SYSTEM.toString());
    }

    @Test
    void preferredUnitByType() {
        assertSame(M, system().getUnit(Length.class));
    }

    @Test
    void typeWithoutPreferredUnitIsNull() {
        assertNull(system().getUnit(Time.class));
    }

    @Test
    void unitBySymbol() {
        assertSame(KM, system().getUnit("km"));
        assertNull(system().getUnit("nope"));
        assertNull(system().getUnit("KM"));
    }

    @Test
    void unitsKeepListOrderAndAreUnmodifiable() {
        var units = system().getUnits();
        assertEquals(List.of(M, KM, S, HZ), new ArrayList<>(units));
        assertThrows(UnsupportedOperationException.class, () -> ((Set<UomUnit<?>>) units).add(M));
        assertThrows(UnsupportedOperationException.class, () -> units.remove(M));
    }

    @Test
    void unitsByDimension() {
        var sys = system();
        var length = sys.getUnits(UomDimension.LENGTH);
        assertEquals(List.of(M, KM), new ArrayList<>(length));
        assertThrows(UnsupportedOperationException.class, () -> length.remove(M));
        assertEquals(List.of(HZ), new ArrayList<>(sys.getUnits(HZ.getDimension())));
    }

    @Test
    void aliasesEqualUnitsCollapseInTheSet() {
        var alias = BaseUnits.METRE.named("meter", "meter");
        var sys = new UomSystemOfUnits("A", List.of(M, alias), Map.of(), Set.of());
        assertEquals(1, sys.getUnits().size());
        assertSame(alias, sys.getUnit("meter"));
    }

    @Test
    void inputCollectionsAreCopied() {
        var units = new ArrayList<UomUnit<?>>(List.of(M, S));
        var preferred = new HashMap<Class<?>, UomUnit<?>>(Map.of(Length.class, M));
        var prefixable = new HashSet<>(Set.of("m"));
        var sys = new UomSystemOfUnits("C", units, preferred, prefixable);
        units.add(KM);
        preferred.clear();
        prefixable.add("s");
        assertEquals(2, sys.getUnits().size());
        assertNull(sys.getUnit("km"));
        assertSame(M, sys.getUnit(Length.class));
        assertFalse(sys.isPrefixable("s"));
    }

    @Test
    void duplicateSymbolIsRejected() {
        var e = assertThrows(
                IllegalArgumentException.class,
                () -> new UomSystemOfUnits("D", List.of(M, BaseUnits.SECOND.named("m", "other")), Map.of(), Set.of()));
        assertEquals("duplicate unit symbol: m", e.getMessage());
    }

    @Test
    void unitWithoutSymbolIsRejected() {
        var unit = BaseUnits.METRE.multiply(3);
        var e = assertThrows(
                IllegalArgumentException.class,
                () -> new UomSystemOfUnits("D", List.of(unit), Map.of(), Set.of()));
        assertEquals("unit without a symbol: " + unit, e.getMessage());
    }

    @Test
    void emptySymbolIsRejected() {
        var unit = BaseUnits.METRE.named("", "empty");
        var e = assertThrows(
                IllegalArgumentException.class,
                () -> new UomSystemOfUnits("D", List.of(unit), Map.of(), Set.of()));
        assertEquals("unit without a symbol: " + unit, e.getMessage());
    }

    @Test
    void unknownPrefixableSymbolIsRejected() {
        var e = assertThrows(
                IllegalArgumentException.class,
                () -> new UomSystemOfUnits("D", List.of(M), Map.of(), Set.of("zz")));
        assertEquals("prefixable symbol is not a unit symbol: zz", e.getMessage());
        e = assertThrows(
                IllegalArgumentException.class,
                () -> new UomSystemOfUnits("D", List.of(M), Map.of(), Set.of(), Set.of("zz")));
        assertEquals("prefixable symbol is not a unit symbol: zz", e.getMessage());
    }

    @Test
    void nullsAreRejected() {
        assertEquals(
                "name",
                assertThrows(
                        NullPointerException.class,
                        () -> new UomSystemOfUnits(null, List.of(), Map.of(), Set.of())).getMessage());
        assertEquals(
                "units",
                assertThrows(NullPointerException.class, () -> new UomSystemOfUnits("n", null, Map.of(), Set.of()))
                        .getMessage());
        assertEquals(
                "preferred",
                assertThrows(NullPointerException.class, () -> new UomSystemOfUnits("n", List.of(), null, Set.of()))
                        .getMessage());
        assertEquals(
                "prefixable",
                assertThrows(NullPointerException.class, () -> new UomSystemOfUnits("n", List.of(), Map.of(), null))
                        .getMessage());
        assertEquals(
                "binaryPrefixable",
                assertThrows(
                        NullPointerException.class,
                        () -> new UomSystemOfUnits("n", List.of(), Map.of(), Set.of(), null)).getMessage());
        var withNull = new ArrayList<UomUnit<?>>();
        withNull.add(null);
        assertEquals(
                "unit",
                assertThrows(NullPointerException.class, () -> new UomSystemOfUnits("n", withNull, Map.of(), Set.of()))
                        .getMessage());
    }

    @Test
    void lookupAndPrefixable() {
        var sys = system();
        assertSame(KM, sys.lookup("km").orElseThrow());
        assertTrue(sys.lookup("x").isEmpty());
        assertTrue(sys.isPrefixable("m"));
        assertFalse(sys.isPrefixable("km"));
        assertFalse(sys.isPrefixable("x"));
        assertFalse(sys.isBinaryPrefixable("m"));
        var binary = new UomSystemOfUnits("B", List.of(M, KM), Map.of(), Set.of(), Set.of("m"));
        assertFalse(binary.isPrefixable("m"));
        assertTrue(binary.isBinaryPrefixable("m"));
    }

    @Test
    void readsAreThreadSafe() throws Exception {
        var sys = system();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(pool.submit(() -> {
                    go.await();
                    for (int n = 0; n < 1000; n++) {
                        if (sys.getUnit("km") != KM || sys.getUnits().size() != 4 || sys.getUnit(Length.class) != M
                                || !sys.isPrefixable("m")) {
                            return false;
                        }
                    }
                    return true;
                }));
            }
            go.countDown();
            for (var f : results) {
                assertTrue(f.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
