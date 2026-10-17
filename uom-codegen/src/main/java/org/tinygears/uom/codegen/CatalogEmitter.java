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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.measure.BinaryPrefix;
import javax.measure.MetricPrefix;
import javax.measure.Prefix;

import org.tinygears.uom.engine.parse.Definition;
import org.tinygears.uom.engine.parse.Definitions;
import org.tinygears.uom.engine.parse.Prefixed;

/** Writes the catalog class of typed unit constants and its system of units. */
public final class CatalogEmitter {
    private static final String PACKAGE = "org.tinygears.uom.jsr.systems";
    private static final List<Enum<?>> PREFIXES = Stream
            .<Enum<?>>concat(Arrays.stream(MetricPrefix.values()), Arrays.stream(BinaryPrefix.values())).toList();
    private static final String INDENT = "                    ";

    private CatalogEmitter() {
    }

    /** The catalog class source; {@code definitions} is the parse of the catalog's file. */
    public static String catalog(CatalogBuilder.Catalog catalog, Definitions definitions) {
        return catalog(catalog, definitions, List.of());
    }

    /**
     * The catalog class source with, after its units, a constant for the system unit of each of {@code extras}: quantity
     * types whose system unit has a symbol that no definition can carry ({@code m/s}). It is the factory's own unit.
     */
    public static String catalog(
            CatalogBuilder.Catalog catalog,
            Definitions definitions,
            List<QuantityTypeSpec> extras
    ) {
        String className = catalog.className();
        Imports imports = new Imports(PACKAGE, taken(catalog, extras));
        String unitClass = imports.name("org.tinygears.uom.jsr.UomUnit");
        List<String> files = dataFiles(catalog.file(), definitions);
        StringBuilder out = new StringBuilder(
                """
                        /** The $C units; generated from $J. */
                        public final class $C {

                            private $C() {
                            }
                        """.replace("$J", join(files.stream().map(f -> "{@code data/" + f + "}").toList()))
                        .replace("$C", className));
        Set<String> emitted = new TreeSet<>();
        for (CatalogEntry e : catalog.entries()) {
            Definition d = e.definition();
            String type = e.quantityType() == null ? "?" : imports.name("javax.measure.quantity." + e.quantityType());
            String unit;
            if (e.shared() != null) {
                unit = local(e.shared(), className, imports);
            } else {
                unit = prefixed(d, catalog, definitions, emitted, imports);
                if (unit == null) {
                    try {
                        unit = UnitExpressionEmitter.emitUnit(d.unit(), definitions, imports);
                    } catch (GenerationException x) {
                        throw new GenerationException(d.source(), d.line(), x.getMessage(), x);
                    }
                    unit = UnitExpressionEmitter.named(unit, d.symbol(), d.name(), imports);
                }
            }
            emitted.add(className + "." + e.constantName());
            out.append("\n    /** The ").append(d.name()).append(", {@code ").append(d.symbol())
                    .append("}. */\n    public static final ").append(unitClass).append('<').append(type).append("> ")
                    .append(e.constantName()).append(" =\n            ").append(unitClass).append(".unchecked(")
                    .append(wrap(unit)).append(");\n");
        }
        for (QuantityTypeSpec t : extras) {
            String constant = constantName(t);
            if (catalog.entries().stream().anyMatch(e -> e.constantName().equals(constant))) {
                throw new GenerationException(t.source(), t.line(), "duplicate constant name '" + constant + "'");
            }
            out.append("\n    /** The ").append(t.systemName()).append(", {@code ").append(t.systemSymbol())
                    .append("}, the system unit of ").append(t.name()).append(". */\n    public static final ")
                    .append(unitClass).append('<').append(imports.name("javax.measure.quantity." + t.name()))
                    .append("> ").append(constant).append(" =\n            ").append(unitClass).append(".unchecked(")
                    .append(imports.name("org.tinygears.uom.jsr.quantity." + t.name() + "Factory"))
                    .append(".SYSTEM_UNIT);\n");
        }
        String list = imports.name("java.util.List");
        out.append("\n    /** The units of this catalog. */\n    public static final ").append(list).append('<')
                .append(unitClass).append("<?>> UNITS =\n").append("            ").append(list).append(".<")
                .append(unitClass).append("<?>>of(")
                .append(args(catalog.units().values().stream().map(r -> local(r, className, imports)).toList()))
                .append(";\n");
        String set = imports.name("java.util.Set");
        out.append("\n    /** The symbols that take a prefix in this system. */\n    public static final ").append(set)
                .append("<String> PREFIXABLE =\n            ").append(set).append(".of(")
                .append(args(catalog.prefixable().stream().sorted().map(UnitExpressionEmitter::literal).toList()))
                .append(";\n");
        boolean binary = !catalog.binaryPrefixable().isEmpty();
        if (binary) {
            out.append("\n    /** The symbols that take a binary prefix in this system. */\n    public static final ")
                    .append(set).append("<String> BINARY_PREFIXABLE =\n            ").append(set).append(".of(")
                    .append(
                            args(
                                    catalog.binaryPrefixable().stream().sorted().map(UnitExpressionEmitter::literal)
                                            .toList()))
                    .append(";\n");
        }
        String map = imports.name("java.util.Map");
        List<String> pairs = new ArrayList<>();
        catalog.preferred().forEach(
                (type, ref) -> pairs.add(
                        map + ".entry(" + imports.name("javax.measure.quantity." + type) + ".class, "
                                + local(ref, className, imports) + ")"));
        out.append("\n    /** The preferred unit of each quantity type. */\n    public static final ").append(map)
                .append("<Class<?>, ").append(unitClass).append("<?>> PREFERRED =\n            ").append(map)
                .append(".<Class<?>, ").append(unitClass).append("<?>>ofEntries(").append(args(pairs)).append(";\n");
        String system = imports.name("org.tinygears.uom.jsr.systems.UomSystemOfUnits");
        out.append("\n    /** This system of units. */\n    public static final ").append(system)
                .append(" SYSTEM =\n            new ").append(system).append('(')
                .append(UnitExpressionEmitter.literal(className)).append(", UNITS, PREFERRED, PREFIXABLE")
                .append(binary ? ", BINARY_PREFIXABLE" : "").append(");\n");
        return Headers.java(join(files.stream().map(f -> "data/" + f).toList())) + """

                package org.tinygears.uom.jsr.systems;

                """ + imports.block() + out.append("}\n");
    }

    /**
     * A unit made by a {@code @prefixed} line, as its base constant with the prefix applied, or null if {@code d} is
     * not one or its base is not a constant of this catalog's units that an earlier field already initialised.
     */
    private static String prefixed(
            Definition d,
            CatalogBuilder.Catalog catalog,
            Definitions definitions,
            Set<String> emitted,
            Imports imports
    ) {
        for (Prefixed p : definitions.prefixed()) {
            if (!p.source().equals(d.source()) || p.line() != d.line()) {
                continue;
            }
            String base = catalog.units().get(p.unitSymbol());
            boolean own = base != null && base.startsWith(catalog.className() + ".");
            if (base == null || own && !emitted.contains(base)) {
                return null;
            }
            for (String symbol : p.prefixSymbols()) {
                if (d.symbol().equals(symbol + p.unitSymbol())) {
                    for (Enum<?> m : PREFIXES) {
                        if (((Prefix) m).getSymbol().equals(symbol)) {
                            return local(base, catalog.className(), imports) + ".prefix("
                                    + imports.name(m.getDeclaringClass().getName()) + "." + m.name() + ").named("
                                    + UnitExpressionEmitter.literal(d.symbol()) + ", "
                                    + UnitExpressionEmitter.literal(d.name()) + ")";
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * The simple names a catalog declares or uses from its own package: its class, its fields and the catalog
     * classes it references ({@code SI.METRE}). A field also shadows a type in {@code Map.entry(...)}.
     */
    private static Set<String> taken(CatalogBuilder.Catalog catalog, List<QuantityTypeSpec> extras) {
        Set<String> taken = new TreeSet<>(
                List.of(catalog.className(), "UNITS", "PREFIXABLE", "BINARY_PREFIXABLE", "PREFERRED", "SYSTEM"));
        catalog.entries().forEach(e -> taken.add(e.constantName()));
        extras.forEach(t -> taken.add(constantName(t)));
        Stream.concat(catalog.units().values().stream(), catalog.preferred().values().stream())
                .filter(r -> r.indexOf('.') == r.lastIndexOf('.'))
                .forEach(r -> taken.add(r.substring(0, r.indexOf('.'))));
        return taken;
    }

    /** The constant of a system unit named by its type's line: {@code metre per second} is {@code METRE_PER_SECOND}. */
    static String constantName(QuantityTypeSpec t) {
        return t.systemName().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
    }

    /** The catalog file, the other files its parse read (sorted), then the quantity types file. */
    static List<String> dataFiles(String file, Definitions definitions) {
        Set<String> read = new TreeSet<>();
        definitions.definitions().forEach(d -> read.add(d.source()));
        definitions.preferred().forEach(p -> read.add(p.source()));
        definitions.prefixed().forEach(p -> read.add(p.source()));
        read.remove(file);
        List<String> result = new ArrayList<>(List.of(file));
        result.addAll(read);
        result.add("quantity-types.txt");
        return result;
    }

    private static String join(List<String> items) {
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
    }

    /**
     * A reference to a constant of {@code className} is written unqualified, one of another catalog as is and one of
     * a class in another package ({@code org.tinygears.uom.jsr.quantity.LengthFactory.SYSTEM_UNIT}) imported.
     */
    private static String local(String ref, String className, Imports imports) {
        int dot = ref.lastIndexOf('.');
        if (ref.indexOf('.') != dot) {
            return imports.name(ref.substring(0, dot)) + ref.substring(dot);
        }
        return ref.startsWith(className + ".") ? ref.substring(className.length() + 1) : ref;
    }

    /** A parenthesised argument list, one argument per line. */
    private static String args(List<String> items) {
        return items.isEmpty() ? ")" : "\n" + INDENT + String.join(",\n" + INDENT, items) + ")";
    }

    /** Puts each top-level method call of a unit expression on its own line. */
    private static String wrap(String expression) {
        StringBuilder out = new StringBuilder();
        int depth = 0;
        boolean quoted = false;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (quoted) {
                quoted = c != '"';
                if (c == '\\') {
                    out.append(c);
                    c = expression.charAt(++i);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == '.' && depth == 0 && isCall(expression, i + 1)) {
                out.append('\n').append(INDENT);
            }
            out.append(c);
        }
        return out.toString();
    }

    private static boolean isCall(String s, int from) {
        int i = from;
        while (i < s.length() && Character.isJavaIdentifierPart(s.charAt(i))) {
            i++;
        }
        return i > from && Character.isLowerCase(s.charAt(from)) && i < s.length() && s.charAt(i) == '(';
    }
}
