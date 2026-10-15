// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.parse;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.measure.BinaryPrefix;
import javax.measure.MetricPrefix;
import javax.measure.Prefix;

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
    /** The metric and binary prefixes by symbol; the two families share no symbol. */
    static final Map<String, Prefix> PREFIXES = Stream.concat(Arrays.stream(MetricPrefix.values()),
            Arrays.stream(BinaryPrefix.values())).collect(Collectors.toUnmodifiableMap(Prefix::getSymbol,
            Function.identity()));

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
        return prefixSymbols.stream().anyMatch(s -> PREFIXES.get(s) instanceof MetricPrefix);
    }

    /**
     * Whether a prefix of the line is binary; such a unit takes binary prefixes at run time.
     *
     * @return whether a prefix is binary
     */
    public boolean binary() {
        return prefixSymbols.stream().anyMatch(s -> PREFIXES.get(s) instanceof BinaryPrefix);
    }
}
