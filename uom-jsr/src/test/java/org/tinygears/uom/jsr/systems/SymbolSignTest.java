/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.jsr.systems;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.measure.MetricPrefix;
import javax.measure.Prefix;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.engine.parse.Definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code +} and {@code -} delimit formulas (offsets such as {@code K + 5}), so no symbol may contain them. */
class SymbolSignTest {

    private static Set<String> symbols() {
        Set<String> symbols = new LinkedHashSet<>();
        for (UomSystemOfUnits system : UnitSystems.all()) {
            system.getUnits().forEach(u -> symbols.add(u.getSymbol()));
        }
        for (SystemsCatalogTest.Catalog c : SystemsCatalogTest.CATALOGS) {
            c.units().forEach(u -> symbols.add(u.getSymbol()));
        }
        SystemsCatalogTest.PARSED.values()
                .forEach(d -> d.definitions().stream().map(Definition::symbol).forEach(symbols::add));
        return symbols;
    }

    private static Set<String> prefixedSymbols() {
        Set<String> prefixed = new LinkedHashSet<>();
        for (SystemsCatalogTest.Catalog c : SystemsCatalogTest.CATALOGS) {
            for (String symbol : SystemsCatalogTest.members(c.file())) {
                for (Prefix p : SystemsCatalogTest.prefixes(c.system(), symbol)) {
                    prefixed.add(p.getSymbol() + symbol);
                }
            }
        }
        return prefixed;
    }

    @Test
    void noSymbolContainsASign() {
        Set<String> all = symbols();
        all.addAll(prefixedSymbols());
        for (MetricPrefix p : MetricPrefix.values()) {
            all.add(p.getSymbol());
        }
        assertTrue(all.size() > 900, "symbols checked: " + all.size());
        List<String> signed = all.stream().filter(s -> s.indexOf('+') >= 0 || s.indexOf('-') >= 0).toList();
        assertEquals(List.of(), signed);
    }
}
