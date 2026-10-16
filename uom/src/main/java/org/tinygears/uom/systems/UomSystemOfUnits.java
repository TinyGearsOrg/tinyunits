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

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.measure.Dimension;
import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.spi.SystemOfUnits;

import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.core.parse.Definition;
import org.tinygears.uom.core.parse.DefinitionException;
import org.tinygears.uom.core.parse.DefinitionParser;
import org.tinygears.uom.core.parse.Definitions;
import org.tinygears.uom.core.parse.Extends;
import org.tinygears.uom.core.parse.Member;
import org.tinygears.uom.core.parse.Preferred;
import org.tinygears.uom.core.parse.Prefixed;
import org.tinygears.uom.quantity.QuantityTypes;

/** An immutable system of units indexed by symbol. */
public final class UomSystemOfUnits implements SystemOfUnits {

    private final String name;
    private final Map<String, UomUnit<?>> bySymbol;
    private final Set<UomUnit<?>> units;
    private final Map<Class<?>, UomUnit<?>> preferred;
    private final Set<String> prefixable;
    private final Set<String> binaryPrefixable;

    /**
     * A system whose units take no binary prefix; see {@link #UomSystemOfUnits(String, List, Map, Set, Set)}.
     *
     * @param name the name
     * @param units the units, in lookup and listing order
     * @param preferred the unit of each quantity type
     * @param prefixable the symbols that take a metric prefix
     */
    public UomSystemOfUnits(
            String name,
            List<UomUnit<?>> units,
            Map<Class<?>, UomUnit<?>> preferred,
            Set<String> prefixable
    ) {
        this(name, units, preferred, prefixable, Set.of());
    }

    /**
     * A system; a unit without a symbol, a duplicate symbol or a prefixable symbol that is not a unit symbol throws IllegalArgumentException.
     *
     * @param name the name
     * @param units the units, in lookup and listing order
     * @param preferred the unit of each quantity type
     * @param prefixable the symbols that take a metric prefix
     * @param binaryPrefixable the symbols that take a binary prefix ({@code Ki}, {@code Mi}, ...)
     */
    public UomSystemOfUnits(
            String name,
            List<UomUnit<?>> units,
            Map<Class<?>, UomUnit<?>> preferred,
            Set<String> prefixable,
            Set<String> binaryPrefixable
    ) {
        this.name = Objects.requireNonNull(name, "name");
        Map<String, UomUnit<?>> index = new LinkedHashMap<>();
        for (UomUnit<?> unit : Objects.requireNonNull(units, "units")) {
            String symbol = Objects.requireNonNull(unit, "unit").getSymbol();
            if (symbol == null || symbol.isEmpty()) {
                throw new IllegalArgumentException("unit without a symbol: " + unit);
            }
            if (index.put(symbol, unit) != null) {
                throw new IllegalArgumentException("duplicate unit symbol: " + symbol);
            }
        }
        this.bySymbol = Collections.unmodifiableMap(index);
        this.units = Collections.unmodifiableSet(new LinkedHashSet<>(index.values()));
        this.preferred = Map.copyOf(Objects.requireNonNull(preferred, "preferred"));
        this.prefixable = Set.copyOf(Objects.requireNonNull(prefixable, "prefixable"));
        this.binaryPrefixable = Set.copyOf(Objects.requireNonNull(binaryPrefixable, "binaryPrefixable"));
        for (Set<String> symbols : List.of(this.prefixable, this.binaryPrefixable)) {
            for (String symbol : symbols) {
                if (!index.containsKey(symbol)) {
                    throw new IllegalArgumentException("prefixable symbol is not a unit symbol: " + symbol);
                }
            }
        }
    }

    /**
     * Loads a system from a {@code .units} file in UTF-8. Every {@code @include} and {@code @extends} name, in
     * nested files too, is resolved against the directory of this root file (one flat namespace, like the
     * generator's data directory). A name must be relative and stay inside that directory both lexically and after
     * resolving symbolic links (the real path of the file must lie in the real path of the directory), else it can
     * not be included. The name is the {@code @system} name, else the file name without its extension.
     *
     * @param file the root file
     * @return the system
     * @throws IOException when the file itself can not be read ({@link java.nio.file.NoSuchFileException} if it
     *     does not exist, {@link java.nio.charset.MalformedInputException} if it is not UTF-8); errors in the
     *     content, includes too, are {@link DefinitionException}s
     * @throws IllegalArgumentException when {@code file} has no file name, such as {@code /}
     * @see #load(String, Reader, DefinitionParser.Resolver)
     */
    public static UomSystemOfUnits load(Path file) throws IOException {
        Path fileName = file.getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("not a file: " + file);
        }
        Path dir = file.toAbsolutePath().normalize().getParent();
        String source = fileName.toString();
        DefinitionParser.Resolver resolver = name -> {
            try {
                Path p = dir.resolve(name).normalize();
                if (Path.of(name).isAbsolute() || !p.startsWith(dir)) {
                    throw new IOException("'" + name + "' is outside of " + dir);
                }
                Path real = p.toRealPath();
                if (!real.startsWith(dir.toRealPath())) {
                    throw new IOException("'" + name + "' is outside of " + dir + " (symbolic link)");
                }
                return new StringReader(Files.readString(real));
            } catch (InvalidPathException e) {
                throw new IOException(e.getMessage(), e);
            }
        };
        try (Reader reader = Files.newBufferedReader(file)) {
            return load(source, withoutExtension(source), reader, once(resolver), new HashMap<>());
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * Loads a system from {@code reader} (not closed), using {@code name} as file name in messages and as the
     * system name when there is no {@code @system} line. {@code resolver} opens included and extended user files
     * (null: none); it is called at most once per name and must return a fresh reader each time, which is read
     * fully and closed here. A file named by {@code @extends} is also loaded as a system of its own. No built-in
     * files exist: {@code @include si.units} fails unless the resolver provides it.
     *
     * <p>Formulas may use the symbols {@link UomUnitFormat#standard()} resolves (not labels), and the file may not
     * define any of them. The units are those the file defines, then those of {@code @extends}-ed files, then the
     * {@code @member}s; units of other included files are usable in formulas only. {@code @preferred Type symbol}
     * names one of the 33 standard quantity types by simple name; a type without one takes the first
     * {@code @extends}-ed system's preferred unit, else {@link SI}'s. Units are {@code Unit<?>}: narrow them with
     * {@code asType}. Nothing is registered globally, except that {@code ~} lines register their alternate-unit
     * symbols in the JVM-wide registry permanently: loading the same definition again works, a different one for
     * the same symbol is an error until the JVM restarts. {@code @prefixed u k} checks only the generated
     * {@code ku} against the built-in symbols, though every prefix {@code u} takes (see {@link UomUnitFormat}) is
     * readable at run time; an exact built-in symbol wins over such a reading.
     *
     * @param name the file name in messages and the default system name
     * @param reader the content of the root file
     * @param resolver opens included and extended files, or null
     * @return the system
     * @throws DefinitionException for an error in a file, with its name and line
     * @throws UncheckedIOException when {@code reader} fails, or a reader from {@code resolver} can not be read
     */
    public static UomSystemOfUnits load(String name, Reader reader, DefinitionParser.Resolver resolver) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(reader, "reader");
        return load(name, name, reader, resolver == null ? null : once(resolver), new HashMap<>());
    }

    /** Opens each name once and keeps its text, so parents shared by several files are not read again. */
    private static DefinitionParser.Resolver once(DefinitionParser.Resolver resolver) {
        Map<String, String> texts = new HashMap<>();
        return name -> {
            String text = texts.get(name);
            if (text == null) {
                try (Reader r = resolver.open(name)) {
                    if (r == null) {
                        return null;
                    }
                    StringWriter w = new StringWriter();
                    try {
                        r.transferTo(w);
                    } catch (IOException e) {
                        throw new UncheckedIOException(
                                "cannot read '" + name + "': the resolver returned a failing, closed or reused reader",
                                e);
                    }
                    text = w.toString();
                }
                texts.put(name, text);
            }
            return new StringReader(text);
        };
    }

    private static String withoutExtension(String file) {
        int dot = file.lastIndexOf('.');
        return dot > 0 ? file.substring(0, dot) : file;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static UomSystemOfUnits load(
            String source,
            String defaultName,
            Reader reader,
            DefinitionParser.Resolver resolver,
            Map<String, UomSystemOfUnits> loaded
    ) {
        UomUnitFormat builtIn = UomUnitFormat.standard();
        Definitions d = DefinitionParser.parse(source, reader, resolver, s -> builtIn.resolve(s).orElse(null));
        String name = d.system() != null ? d.system() : defaultName;
        Map<String, UomUnit<?>> units = new LinkedHashMap<>();
        Map<String, Definition> bySymbol = new HashMap<>();
        for (Definition def : d.definitions()) {
            bySymbol.putIfAbsent(def.symbol(), def);
            if (def.source().equals(source)) {
                units.put(def.symbol(), def.unit());
            }
        }
        Set<String> prefixable = new LinkedHashSet<>();
        Set<String> binaryPrefixable = new LinkedHashSet<>();
        for (Prefixed p : d.prefixed()) {
            if (p.source().equals(source)) {
                addPrefixable(p, prefixable, binaryPrefixable);
            }
        }
        List<UomSystemOfUnits> parents = new ArrayList<>();
        for (Extends x : d.extended()) {
            UomSystemOfUnits system = loaded.get(x.file());
            if (system == null) {
                try (Reader r = resolver.open(x.file())) {
                    system = load(x.file(), withoutExtension(x.file()), r, resolver, loaded);
                } catch (IOException e) {
                    throw new DefinitionException(x.source(), x.line(), "cannot include '" + x.file() + "'", e);
                }
                loaded.put(x.file(), system);
            }
            parents.add(system);
            for (String symbol : system.symbols()) {
                units.putIfAbsent(symbol, system.bySymbol.get(symbol));
                if (system.isPrefixable(symbol)) {
                    prefixable.add(symbol);
                }
                if (system.isBinaryPrefixable(symbol)) {
                    binaryPrefixable.add(symbol);
                }
            }
        }
        for (Member m : d.members()) {
            Definition def = bySymbol.get(m.symbol());
            units.putIfAbsent(m.symbol(), def.unit());
            for (Prefixed p : d.prefixed()) {
                if (p.source().equals(def.source()) && p.unitSymbol().equals(m.symbol())) {
                    addPrefixable(p, prefixable, binaryPrefixable);
                }
            }
        }
        prefixable.retainAll(units.keySet());
        binaryPrefixable.retainAll(units.keySet());

        Map<String, Class<?>> types = new HashMap<>();
        QuantityTypes.types().forEach(t -> types.put(t.getSimpleName(), t));
        Map<Class<?>, UomUnit<?>> preferred = new HashMap<>();
        for (Preferred p : d.preferred()) {
            if (!p.source().equals(source)) {
                continue;
            }
            Class<?> type = types.get(p.quantityType());
            if (type == null) {
                throw new DefinitionException(
                        p.source(),
                        p.line(),
                        "unknown quantity type '" + p.quantityType() + "'",
                        null);
            }
            UomUnit<?> unit = units.get(p.symbol());
            if (unit == null) {
                throw new DefinitionException(
                        p.source(),
                        p.line(),
                        "unit '" + p.symbol() + "' is not a unit of " + name,
                        null);
            }
            if (!QuantityTypes.factory((Class) type).getSystemUnit().equals(unit.getSystemUnit())) {
                throw new DefinitionException(
                        p.source(),
                        p.line(),
                        "unit '" + p.symbol() + "' is not a unit of " + p.quantityType(),
                        null);
            }
            if (preferred.putIfAbsent(type, unit) != null) {
                throw new DefinitionException(
                        p.source(),
                        p.line(),
                        "duplicate preferred unit for '" + p.quantityType() + "'",
                        null);
            }
        }
        Map<Class<?>, UomUnit<?>> fallback = parents.isEmpty() ? SI.PREFERRED : parents.get(0).preferred;
        for (Class<?> type : QuantityTypes.types()) {
            preferred.computeIfAbsent(type, fallback::get);
        }
        return new UomSystemOfUnits(name, List.copyOf(units.values()), preferred, prefixable, binaryPrefixable);
    }

    /** A line with a metric prefix makes its unit metric-prefixable, one with a binary prefix binary-prefixable. */
    private static void addPrefixable(Prefixed p, Set<String> prefixable, Set<String> binaryPrefixable) {
        if (p.metric()) {
            prefixable.add(p.unitSymbol());
        }
        if (p.binary()) {
            binaryPrefixable.add(p.unitSymbol());
        }
    }

    /** Every symbol, in list order, aliases included. */
    Collection<String> symbols() {
        return bySymbol.keySet();
    }

    @Override
    public String getName() {
        return name;
    }

    /** Returns the preferred unit for the type, or null when none is defined. */
    @Override
    @SuppressWarnings("unchecked")
    public <Q extends Quantity<Q>> Unit<Q> getUnit(Class<Q> type) {
        // preferred maps each quantity interface to a unit of that type (the catalogs and load build it so); the
        // public constructor does not check it.
        return (Unit<Q>) preferred.get(type);
    }

    /** Returns the unit with exactly this symbol, or null when unknown. */
    @Override
    public Unit<?> getUnit(String symbol) {
        return bySymbol.get(symbol);
    }

    /** Units in list order; units equal to each other (aliases) collapse to the first one listed. */
    @Override
    public Set<? extends Unit<?>> getUnits() {
        return units;
    }

    @Override
    public Set<? extends Unit<?>> getUnits(Dimension dimension) {
        Set<UomUnit<?>> result = new LinkedHashSet<>();
        for (UomUnit<?> unit : units) {
            if (unit.getDimension().equals(dimension)) {
                result.add(unit);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * The unit with exactly this symbol; no prefixed forms.
     *
     * @param symbol the symbol
     * @return the unit, or empty
     */
    public Optional<UomUnit<?>> lookup(String symbol) {
        return Optional.ofNullable(bySymbol.get(symbol));
    }

    /**
     * Whether metric prefixes apply to the unit with this symbol.
     *
     * @param unitSymbol the unit symbol
     * @return true if prefixable
     */
    public boolean isPrefixable(String unitSymbol) {
        return prefixable.contains(unitSymbol);
    }

    /**
     * Whether binary prefixes ({@code Ki}, {@code Mi}, ...) apply to the unit with this symbol. Such a unit takes
     * only the metric prefixes from kilo up in a format, as IEC 80000-13 uses them ({@code kB}, not {@code dB}).
     *
     * @param unitSymbol the unit symbol
     * @return true if binary-prefixable
     */
    public boolean isBinaryPrefixable(String unitSymbol) {
        return binaryPrefixable.contains(unitSymbol);
    }

    @Override
    public String toString() {
        return name;
    }
}
