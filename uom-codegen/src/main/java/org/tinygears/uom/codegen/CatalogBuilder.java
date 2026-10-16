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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.lang.model.SourceVersion;

import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.core.parse.Definition;
import org.tinygears.uom.core.parse.Definitions;
import org.tinygears.uom.core.parse.Extends;
import org.tinygears.uom.core.parse.Member;
import org.tinygears.uom.core.parse.Preferred;
import org.tinygears.uom.core.parse.Prefixed;

/** Resolves each definition to a catalog constant name and quantity type (decision D5). */
public final class CatalogBuilder {
    private CatalogBuilder() {
    }

    /** One entry per definition, in file order. */
    public static List<CatalogEntry> build(Definitions definitions, List<QuantityTypeSpec> types) {
        return build(definitions.definitions(), types, new HashSet<>());
    }

    /** {@code claimed} holds the types whose system unit an earlier entry already matched. */
    private static List<CatalogEntry> build(
            List<Definition> definitions,
            List<QuantityTypeSpec> types,
            Set<String> claimed
    ) {
        Map<String, QuantityTypeSpec> byName = byName(types);
        Map<String, Definition> byConstant = new HashMap<>();
        List<CatalogEntry> result = new ArrayList<>();
        for (Definition d : definitions) {
            String constant = d.name().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
            if (!SourceVersion.isName(constant)) {
                throw error(d, "invalid constant name '" + constant + "' for '" + d.symbol() + "'");
            }
            Definition previous = byConstant.putIfAbsent(constant, d);
            if (previous != null) {
                throw error(
                        d,
                        "duplicate constant name '" + constant + "' ('" + previous.symbol() + "' and '" + d.symbol()
                                + "')");
            }
            String type = type(d, types, byName);
            result.add(new CatalogEntry(d, constant, type, shared(d, type, byName, claimed)));
        }
        return List.copyOf(result);
    }

    private static String type(Definition d, List<QuantityTypeSpec> types, Map<String, QuantityTypeSpec> byName) {
        UomUnit<?> system = d.unit().getSystemUnit();
        if (d.explicitType() != null) {
            QuantityTypeSpec spec = byName.get(d.explicitType());
            if (spec == null) {
                throw error(d, "unknown quantity type '" + d.explicitType() + "'");
            }
            if (!spec.systemUnit().equals(system)) {
                throw error(d, "unit '" + d.symbol() + "' is not a unit of " + spec.name());
            }
            return spec.name();
        }
        List<String> matches = types.stream().filter(t -> t.systemUnit().equals(system)).map(QuantityTypeSpec::name)
                .toList();
        if (matches.size() > 1) {
            throw error(
                    d,
                    "ambiguous quantity type for '" + d.symbol() + "': " + String.join(", ", matches)
                            + " (add a [Type])");
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    /**
     * The factory system unit a definition reuses: one equal to its type's system unit, with the same symbol and name.
     * The first such definition of a type, in file order across all catalogs ({@code claimed} spans them), must agree;
     * a later one with another symbol or name (an alias) keeps its own unit. A type named by {@code =>} has a symbol no
     * definition can carry ({@code m/s}), so every definition equal to its system unit is an alias.
     */
    private static String shared(Definition d, String type, Map<String, QuantityTypeSpec> byName, Set<String> claimed) {
        QuantityTypeSpec spec = type == null ? null : byName.get(type);
        if (spec == null || spec.explicit() || !d.unit().equals(spec.systemUnit())) {
            return null;
        }
        boolean agrees = d.symbol().equals(spec.systemSymbol()) && d.name().equals(spec.systemName());
        if (claimed.add(type) && !agrees) {
            String expected = spec.systemSymbol() == null
                    ? "an unnamed unit"
                    : "'" + spec.systemSymbol() + "' (" + spec.systemName() + ")";
            throw error(
                    d,
                    "unit '" + d.symbol() + "' (" + d.name() + ") is the system unit of " + type + ", which "
                            + spec.source() + ":" + spec.line() + " defines as " + expected);
        }
        return agrees ? "org.tinygears.uom.quantity." + type + "Factory.SYSTEM_UNIT" : null;
    }

    /**
     * A catalog class: unit references are qualified by their class ({@code SI.CENTIMETRE}) or are factory system
     * units; {@code units} maps symbols to references in list order, {@code preferred} maps every type name to one.
     * {@code prefixable} and {@code binaryPrefixable} are the symbols that take a metric and a binary prefix.
     */
    public record Catalog(
            String file,
            String className,
            List<CatalogEntry> entries,
            Map<String, String> units,
            Set<String> prefixable,
            Set<String> binaryPrefixable,
            Map<String, String> preferred
    ) {}

    /**
     * One catalog per parsed file, in map order; parents must precede dependents. The first catalog (SI) gives the
     * preferred units of types that a catalog without {@code @extends} does not prefer itself. Sharing a factory system
     * unit is decided in this order too: the first definition equal to a system unit, across all catalogs, must carry
     * its symbol and name (see {@code Generate.CATALOGS}, SI first).
     */
    public static Map<String, Catalog> catalogs(Map<String, Definitions> parsed, List<QuantityTypeSpec> types) {
        Map<String, Catalog> done = new LinkedHashMap<>();
        Map<String, String> fileByClass = new HashMap<>();
        Set<String> claimed = new HashSet<>();
        parsed.forEach((file, d) -> {
            if (d.system() == null) {
                throw new GenerationException(file, 0, "no @system directive");
            }
            String previous = fileByClass.putIfAbsent(d.system(), file);
            if (previous != null) {
                throw new GenerationException(
                        file,
                        0,
                        "duplicate system '" + d.system() + "' (also in " + previous + ")");
            }
            done.put(file, catalog(file, d, types, done, claimed));
        });
        return Collections.unmodifiableMap(done);
    }

    private static Catalog catalog(
            String file,
            Definitions d,
            List<QuantityTypeSpec> types,
            Map<String, Catalog> done,
            Set<String> claimed
    ) {
        String cls = d.system();
        List<CatalogEntry> entries = build(
                d.definitions().stream().filter(x -> x.source().equals(file)).toList(),
                types,
                claimed);
        Map<String, Definition> bySymbol = new HashMap<>();
        d.definitions().forEach(x -> bySymbol.putIfAbsent(x.symbol(), x));
        Map<String, String> units = new LinkedHashMap<>();
        entries.forEach(e -> units.put(e.definition().symbol(), cls + "." + e.constantName()));
        List<Catalog> parents = new ArrayList<>();
        for (Extends x : d.extended()) {
            Catalog parent = done.get(x.file());
            if (parent == null) {
                throw new GenerationException(
                        x.source(),
                        x.line(),
                        "'" + x.file() + "' is not a catalog generated before '" + file + "'");
            }
            parents.add(parent);
            parent.units().forEach(units::putIfAbsent);
        }
        Set<String> prefixable = new TreeSet<>();
        Set<String> binaryPrefixable = new TreeSet<>();
        d.prefixed().stream().filter(p -> p.source().equals(file))
                .forEach(p -> addPrefixable(p, prefixable, binaryPrefixable));
        parents.forEach(p -> prefixable.addAll(p.prefixable()));
        parents.forEach(p -> binaryPrefixable.addAll(p.binaryPrefixable()));
        for (Member m : d.members()) {
            Definition def = bySymbol.get(m.symbol());
            if (def == null) {
                throw new GenerationException(m.source(), m.line(), "unknown unit '" + m.symbol() + "'");
            }
            Catalog owner = done.get(def.source());
            if (owner == null) {
                throw new GenerationException(
                        m.source(),
                        m.line(),
                        "unit '" + m.symbol() + "' is defined in '" + def.source()
                                + "', which is not a catalog generated before '" + file + "'");
            }
            units.putIfAbsent(m.symbol(), owner.units().get(m.symbol()));
            d.prefixed().stream().filter(p -> p.source().equals(def.source()) && p.unitSymbol().equals(m.symbol()))
                    .forEach(p -> addPrefixable(p, prefixable, binaryPrefixable));
        }
        // UomSystemOfUnits rejects a prefixable symbol that is not one of its units.
        prefixable.retainAll(units.keySet());
        binaryPrefixable.retainAll(units.keySet());
        Map<String, String> fallback = !parents.isEmpty()
                ? parents.get(0).preferred()
                : done.isEmpty() ? null : done.values().iterator().next().preferred();
        Map<String, String> own = ownPreferred(file, d, types, bySymbol, units, cls);
        Map<String, String> preferred = new LinkedHashMap<>();
        for (QuantityTypeSpec t : types) {
            String ref = own.get(t.name());
            if (ref == null) {
                ref = fallback != null
                        ? fallback.get(t.name())
                        : "org.tinygears.uom.quantity." + t.name() + "Factory.SYSTEM_UNIT";
            }
            preferred.put(t.name(), ref);
        }
        return new Catalog(
                file,
                cls,
                entries,
                Collections.unmodifiableMap(units),
                Collections.unmodifiableSet(prefixable),
                Collections.unmodifiableSet(binaryPrefixable),
                Collections.unmodifiableMap(preferred));
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

    /** Type name to unit reference, from the {@code @preferred} lines of {@code file} itself. */
    private static Map<String, String> ownPreferred(
            String file,
            Definitions d,
            List<QuantityTypeSpec> types,
            Map<String, Definition> bySymbol,
            Map<String, String> units,
            String cls
    ) {
        Map<String, QuantityTypeSpec> byName = byName(types);
        Map<String, String> result = new HashMap<>();
        for (Preferred p : d.preferred()) {
            if (!p.source().equals(file)) {
                continue;
            }
            Definition def = bySymbol.get(p.symbol());
            if (def == null) {
                throw new GenerationException(p.source(), p.line(), "unknown unit '" + p.symbol() + "'");
            }
            if (!units.containsKey(p.symbol())) {
                throw new GenerationException(
                        p.source(),
                        p.line(),
                        "unit '" + p.symbol() + "' is not a unit of " + cls);
            }
            QuantityTypeSpec spec = byName.get(p.quantityType());
            if (spec == null) {
                throw new GenerationException(p.source(), p.line(), "unknown quantity type '" + p.quantityType() + "'");
            }
            if (!spec.systemUnit().equals(def.unit().getSystemUnit())) {
                throw new GenerationException(
                        p.source(),
                        p.line(),
                        "unit '" + p.symbol() + "' is not a unit of " + spec.name());
            }
            if (result.putIfAbsent(spec.name(), units.get(p.symbol())) != null) {
                throw new GenerationException(
                        p.source(),
                        p.line(),
                        "duplicate preferred unit for '" + spec.name() + "'");
            }
        }
        return result;
    }

    private static Map<String, QuantityTypeSpec> byName(List<QuantityTypeSpec> types) {
        return types.stream().collect(Collectors.toMap(QuantityTypeSpec::name, t -> t));
    }

    private static GenerationException error(Definition d, String text) {
        return new GenerationException(d.source(), d.line(), text);
    }
}
