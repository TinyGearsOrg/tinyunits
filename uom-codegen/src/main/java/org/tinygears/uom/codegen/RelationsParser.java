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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.tinygears.uom.engine.Dimension;

/**
 * Parses {@code data/quantity-relations.txt}, one {@code Result = Left * Right} or {@code Result = Left / Right} per
 * line, and derives the typed product methods: the dimensions must agree, and no method may follow from two lines.
 */
public final class RelationsParser {
    private static final Pattern LINE = Pattern
            .compile("([A-Z][A-Za-z0-9]*)\\s*=\\s*([A-Z][A-Za-z0-9]*)\\s*([*/])\\s*([A-Z][A-Za-z0-9]*)");

    private RelationsParser() {
    }

    /** The relations of {@code reader}, checked against the dimensions of {@code types}. */
    public static List<QuantityRelation> parse(String source, Reader reader, List<QuantityTypeSpec> types) {
        Map<String, Dimension> dims = new LinkedHashMap<>();
        types.forEach(t -> dims.put(t.name(), t.systemUnit().dimension()));
        List<QuantityRelation> result = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(reader)) {
            String text;
            int ln = 0;
            while ((text = in.readLine()) != null) {
                ln++;
                int hash = text.indexOf('#');
                String content = (hash >= 0 ? text.substring(0, hash) : text).strip();
                if (content.isEmpty()) {
                    continue;
                }
                Matcher m = LINE.matcher(content);
                if (!m.matches()) {
                    throw new GenerationException(
                            source,
                            ln,
                            "expected 'Result = Left * Right' or 'Result = Left / Right'");
                }
                QuantityRelation r = new QuantityRelation(
                        m.group(1),
                        m.group(2),
                        m.group(3).equals("/"),
                        m.group(4),
                        source,
                        ln);
                for (String name : List.of(r.result(), r.left(), r.right())) {
                    if (!dims.containsKey(name)) {
                        throw new GenerationException(source, ln, "unknown quantity type '" + name + "'");
                    }
                }
                Dimension left = dims.get(r.left());
                Dimension right = dims.get(r.right());
                Dimension expected = r.divide() ? left.divide(right) : left.multiply(right);
                if (!expected.equals(dims.get(r.result()))) {
                    throw new GenerationException(
                            source,
                            ln,
                            "dimension of " + r.left() + (r.divide() ? " / " : " * ") + r.right() + " is " + expected
                                    + ", not " + dims.get(r.result()) + " of " + r.result());
                }
                result.add(r);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        methods(result);
        return List.copyOf(result);
    }

    /**
     * The methods the relations imply, in relation order; one that two relations imply throws, and a self-product
     * ({@code Area = Length * Length}) gives each method once.
     */
    public static List<QuantityRelation.Method> methods(List<QuantityRelation> relations) {
        Map<String, QuantityRelation.Method> methods = new LinkedHashMap<>();
        for (QuantityRelation r : relations) {
            List<QuantityRelation.Method> implied = r.divide()
                    ? List.of(
                            new QuantityRelation.Method(r.left(), true, r.right(), r.result(), r),
                            new QuantityRelation.Method(r.left(), true, r.result(), r.right(), r),
                            new QuantityRelation.Method(r.right(), false, r.result(), r.left(), r),
                            new QuantityRelation.Method(r.result(), false, r.right(), r.left(), r))
                    : List.of(
                            new QuantityRelation.Method(r.left(), false, r.right(), r.result(), r),
                            new QuantityRelation.Method(r.right(), false, r.left(), r.result(), r),
                            new QuantityRelation.Method(r.result(), true, r.left(), r.right(), r),
                            new QuantityRelation.Method(r.result(), true, r.right(), r.left(), r));
            Map<String, QuantityRelation.Method> own = new LinkedHashMap<>();
            implied.forEach(m -> own.putIfAbsent(key(m), m));
            for (QuantityRelation.Method m : own.values()) {
                QuantityRelation.Method earlier = methods.putIfAbsent(key(m), m);
                if (earlier != null) {
                    throw new GenerationException(
                            r.source(),
                            r.line(),
                            key(m) + " follows from line " + earlier.from().line() + " too");
                }
            }
        }
        return List.copyOf(methods.values());
    }

    private static String key(QuantityRelation.Method m) {
        return m.receiver() + (m.divide() ? ".divide(" : ".multiply(") + m.argument() + ")";
    }
}
