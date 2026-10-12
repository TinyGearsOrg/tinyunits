// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.codegen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.measure.Quantity;
import org.tinygears.uom.core.Converters;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.core.parse.Definition;
import org.tinygears.uom.core.parse.Definitions;
import org.tinygears.uom.core.parse.FormulaEvaluator;
import org.tinygears.uom.core.parse.FormulaException;

/** Parses {@code data/quantity-types.txt}: one {@code Name formula [# comment]} per line. */
public final class QuantityTypesParser {
    private static final Pattern NAME = Pattern.compile("[A-Z][A-Za-z0-9]*");

    private QuantityTypesParser() {
    }

    public static List<QuantityTypeSpec> parse(String source, Reader reader, Definitions definitions) {
        Map<String, Definition> bySymbol = new HashMap<>();
        for (Definition d : definitions.definitions()) {
            bySymbol.put(d.symbol(), d);
        }
        List<QuantityTypeSpec> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try (BufferedReader in = new BufferedReader(reader)) {
            String text;
            int ln = 0;
            while ((text = in.readLine()) != null) {
                ln++;
                if (ln == 1 && text.startsWith("﻿")) {
                    text = text.substring(1);
                }
                int hash = text.indexOf('#');
                String content = (hash >= 0 ? text.substring(0, hash) : text).strip();
                if (content.isEmpty()) {
                    continue;
                }
                int ws = 0;
                while (ws < content.length() && !Character.isWhitespace(content.charAt(ws))) {
                    ws++;
                }
                String name = content.substring(0, ws);
                String formula = content.substring(ws).strip();
                if (!NAME.matcher(name).matches()) {
                    throw new GenerationException(source, ln, "invalid quantity type name '" + name + "'");
                }
                if (!seen.add(name)) {
                    throw new GenerationException(source, ln, "duplicate quantity type '" + name + "'");
                }
                if (!isQuantity(name)) {
                    throw new GenerationException(source, ln, "unknown quantity type '" + name + "'");
                }
                if (formula.isEmpty()) {
                    throw new GenerationException(source, ln, "missing system unit");
                }
                UomUnit<?> unit;
                try {
                    unit = FormulaEvaluator.evaluate(formula, s -> {
                        Definition d = bySymbol.get(s);
                        return d == null ? null : d.unit();
                    });
                } catch (FormulaException e) {
                    throw new GenerationException(source, ln, e.getMessage(), e);
                } catch (RuntimeException e) {
                    throw new GenerationException(source, ln, e.getMessage(), e);
                }
                if (!unit.toSystemConverter().equals(Converters.IDENTITY)) {
                    throw new GenerationException(source, ln, "system unit must be an unscaled unit");
                }
                Definition def = bySymbol.get(formula);
                result.add(new QuantityTypeSpec(name, formula, unit, def == null ? null : def.symbol(),
                        def == null ? null : def.name(), source, ln));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return List.copyOf(result);
    }

    private static boolean isQuantity(String name) {
        try {
            Class<?> c = Class.forName("javax.measure.quantity." + name);
            return c.isInterface() && Quantity.class.isAssignableFrom(c);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
