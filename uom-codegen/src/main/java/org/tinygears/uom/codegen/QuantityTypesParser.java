/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

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

import org.tinygears.uom.engine.Converter;
import org.tinygears.uom.engine.Unit;
import org.tinygears.uom.engine.parse.Definition;
import org.tinygears.uom.engine.parse.Definitions;
import org.tinygears.uom.engine.parse.FormulaEvaluator;
import org.tinygears.uom.engine.parse.FormulaException;

/** Parses {@code data/quantity-types.txt}: one {@code Name formula [=> symbol:name] [# comment]} per line. */
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
        Set<String> explicitSymbols = new HashSet<>();
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
                String explicit = null;
                int arrow = formula.indexOf("=>");
                if (arrow >= 0) {
                    explicit = formula.substring(arrow + 2).strip();
                    formula = formula.substring(0, arrow).strip();
                }
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
                Unit unit;
                try {
                    unit = evaluate(formula, bySymbol);
                } catch (FormulaException e) {
                    throw new GenerationException(source, ln, e.getMessage(), e);
                } catch (RuntimeException e) {
                    throw new GenerationException(source, ln, e.getMessage(), e);
                }
                if (!unit.toSystem().equals(Converter.IDENTITY)) {
                    throw new GenerationException(source, ln, "system unit must be an unscaled unit");
                }
                Definition def = bySymbol.get(formula);
                String symbol = def == null ? null : def.symbol();
                String unitName = def == null ? null : def.name();
                if (explicit != null) {
                    String[] parts = explicit(explicit, source, ln);
                    if (def != null && !(parts[0].equals(symbol) && parts[1].equals(unitName))) {
                        throw new GenerationException(
                                source,
                                ln,
                                "explicit name for an already named unit '" + symbol + "'");
                    }
                    if (def == null && !evaluatesTo(parts[0], unit, bySymbol)) {
                        throw new GenerationException(
                                source,
                                ln,
                                "explicit symbol '" + parts[0] + "' does not evaluate to the unit");
                    }
                    if (def == null && (bySymbol.containsKey(parts[0]) || !explicitSymbols.add(parts[0]))) {
                        throw new GenerationException(source, ln, "duplicate symbol '" + parts[0] + "'");
                    }
                    symbol = parts[0];
                    unitName = parts[1];
                }
                result.add(
                        new QuantityTypeSpec(
                                name,
                                formula,
                                unit,
                                symbol,
                                unitName,
                                source,
                                ln,
                                explicit != null && def == null));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return List.copyOf(result);
    }

    private static Unit evaluate(String formula, Map<String, Definition> bySymbol) throws FormulaException {
        return FormulaEvaluator.evaluate(formula, s -> {
            Definition d = bySymbol.get(s);
            return d == null ? null : d.unit();
        });
    }

    // The symbol must parse back to the unit: formats print it whenever it does.
    private static boolean evaluatesTo(String symbol, Unit unit, Map<String, Definition> bySymbol) {
        try {
            return unit.equals(evaluate(symbol, bySymbol));
        } catch (FormulaException | RuntimeException e) {
            return false;
        }
    }

    /** Symbol and name of {@code symbol:name}; an underscore in the name stands for a space. */
    private static String[] explicit(String text, String source, int ln) {
        if (text.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
            throw new GenerationException(source, ln, "whitespace in '" + text + "' after '=>'");
        }
        String[] parts = text.split(":", -1);
        if (parts.length > 2) {
            throw new GenerationException(source, ln, "extra ':' in '" + text + "' after '=>'");
        }
        if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            throw new GenerationException(source, ln, "expected 'symbol:name' after '=>', got '" + text + "'");
        }
        String name = parts[1].replace('_', ' ');
        if (name.isBlank()) {
            throw new GenerationException(source, ln, "blank name in '" + text + "' after '=>'");
        }
        return new String[] { parts[0], name };
    }

    private static boolean isQuantity(String name) {
        try {
            // only the type is checked, so the class is not initialized
            Class<?> c = Class
                    .forName("javax.measure.quantity." + name, false, QuantityTypesParser.class.getClassLoader());
            return c.isInterface() && Quantity.class.isAssignableFrom(c);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
