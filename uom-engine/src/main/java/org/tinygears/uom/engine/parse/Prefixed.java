/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.engine.parse;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.tinygears.uom.engine.Factor;

/**
 * A {@code @prefixed unit prefix...} directive, with the symbols as written. A line may mix metric ({@code k}) and
 * binary ({@code Ki}) prefixes.
 *
 * @param unitSymbol the unit symbol
 * @param prefixSymbols the prefix symbols
 * @param source the file the line is in
 * @param line the 1-based line number
 */
public record Prefixed(String unitSymbol, List<String> prefixSymbols, String source, int line) {
    /** A prefix: {@code base^exponent}, named as the JSR-385 prefixes are (lower case). */
    record Prefix(String symbol, String name, int base, int exponent) {
        Factor factor() {
            return Factor.ofLong(base).pow(exponent);
        }

        boolean binary() {
            return base == 1024;
        }
    }

    /** The metric (SI) and binary (IEC) prefixes by symbol; the two families share no symbol. */
    static final Map<String, Prefix> PREFIXES = Stream
            .of(
                    new Prefix("Q", "quetta", 10, 30),
                    new Prefix("R", "ronna", 10, 27),
                    new Prefix("Y", "yotta", 10, 24),
                    new Prefix("Z", "zetta", 10, 21),
                    new Prefix("E", "exa", 10, 18),
                    new Prefix("P", "peta", 10, 15),
                    new Prefix("T", "tera", 10, 12),
                    new Prefix("G", "giga", 10, 9),
                    new Prefix("M", "mega", 10, 6),
                    new Prefix("k", "kilo", 10, 3),
                    new Prefix("h", "hecto", 10, 2),
                    new Prefix("da", "deca", 10, 1),
                    new Prefix("d", "deci", 10, -1),
                    new Prefix("c", "centi", 10, -2),
                    new Prefix("m", "milli", 10, -3),
                    new Prefix("\u00B5", "micro", 10, -6),
                    new Prefix("n", "nano", 10, -9),
                    new Prefix("p", "pico", 10, -12),
                    new Prefix("f", "femto", 10, -15),
                    new Prefix("a", "atto", 10, -18),
                    new Prefix("z", "zepto", 10, -21),
                    new Prefix("y", "yocto", 10, -24),
                    new Prefix("r", "ronto", 10, -27),
                    new Prefix("q", "quecto", 10, -30),
                    new Prefix("Ki", "kibi", 1024, 1),
                    new Prefix("Mi", "mebi", 1024, 2),
                    new Prefix("Gi", "gibi", 1024, 3),
                    new Prefix("Ti", "tebi", 1024, 4),
                    new Prefix("Pi", "pebi", 1024, 5),
                    new Prefix("Ei", "exbi", 1024, 6),
                    new Prefix("Zi", "zebi", 1024, 7),
                    new Prefix("Yi", "yobi", 1024, 8))
            .collect(Collectors.toUnmodifiableMap(Prefix::symbol, Function.identity()));

    /**
     * Copies the prefix list.
     *
     * @param unitSymbol the unit symbol
     * @param prefixSymbols the prefix symbols
     * @param source the file the line is in
     * @param line the 1-based line number
     */
    public Prefixed {
        prefixSymbols = List.copyOf(prefixSymbols);
    }

    /**
     * Whether a prefix of the line is metric; such a unit takes metric prefixes at run time.
     *
     * @return whether a prefix is metric
     */
    public boolean metric() {
        return prefixSymbols.stream().anyMatch(s -> PREFIXES.containsKey(s) && !PREFIXES.get(s).binary());
    }

    /**
     * Whether a prefix of the line is binary; such a unit takes binary prefixes at run time.
     *
     * @return whether a prefix is binary
     */
    public boolean binary() {
        return prefixSymbols.stream().anyMatch(s -> PREFIXES.containsKey(s) && PREFIXES.get(s).binary());
    }
}
