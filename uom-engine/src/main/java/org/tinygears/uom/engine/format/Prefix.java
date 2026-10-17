/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.engine.format;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.tinygears.uom.engine.Factor;

/**
 * A metric or binary prefix: the SI prefixes from quecta to quetta and the IEC prefixes from kibi to yobi, with the
 * symbols, names and order of JSR-385's {@code MetricPrefix} and {@code BinaryPrefix}.
 *
 * @param symbol the symbol, such as {@code k}
 * @param name the name, such as {@code kilo}
 * @param base 10 or 1024
 * @param exponent the power of the base
 */
public record Prefix(String symbol, String name, int base, int exponent) {

    /** The metric prefixes from the largest down, then the binary ones from the smallest up. */
    public static final List<Prefix> ALL = List.of(
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
            new Prefix("µ", "micro", 10, -6),
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
            new Prefix("Yi", "yobi", 1024, 8));

    /** The prefixes by symbol. */
    public static final Map<String, Prefix> BY_SYMBOL = ALL.stream()
            .collect(Collectors.toUnmodifiableMap(Prefix::symbol, Function.identity()));

    /**
     * The factor, {@code base^exponent}.
     *
     * @return the factor
     */
    public Factor factor() {
        return Factor.ofLong(base).pow(exponent);
    }

    /**
     * Whether this is a binary prefix.
     *
     * @return true for base 1024
     */
    public boolean binary() {
        return base == 1024;
    }
}
