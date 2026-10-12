// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.codegen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.SourceVersion;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.core.parse.Definition;
import org.tinygears.uom.core.parse.Definitions;
import org.tinygears.uom.core.parse.Preferred;

/** Resolves each definition to a catalog constant name and quantity type (decision D5). */
public final class CatalogBuilder {
    private CatalogBuilder() {
    }

    /** One entry per definition, in file order. */
    public static List<CatalogEntry> build(Definitions definitions, List<QuantityTypeSpec> types) {
        Map<String, QuantityTypeSpec> byName = byName(types);
        Map<String, Definition> byConstant = new HashMap<>();
        List<CatalogEntry> result = new ArrayList<>();
        for (Definition d : definitions.definitions()) {
            String constant = d.name().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
            if (!SourceVersion.isName(constant)) {
                throw error(d, "invalid constant name '" + constant + "' for '" + d.symbol() + "'");
            }
            Definition previous = byConstant.putIfAbsent(constant, d);
            if (previous != null) {
                throw error(d, "duplicate constant name '" + constant + "' ('" + previous.symbol() + "' and '"
                        + d.symbol() + "')");
            }
            result.add(new CatalogEntry(d, constant, type(d, types, byName)));
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
        List<String> matches = types.stream().filter(t -> t.systemUnit().equals(system))
                .map(QuantityTypeSpec::name).toList();
        if (matches.size() > 1) {
            throw error(d, "ambiguous quantity type for '" + d.symbol() + "': " + String.join(", ", matches)
                    + " (add a [Type])");
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    /** Type name to constant name, from the {@code @preferred} directives. */
    public static Map<String, String> preferred(Definitions definitions, List<CatalogEntry> entries,
            List<QuantityTypeSpec> types) {
        Map<String, QuantityTypeSpec> byName = byName(types);
        Map<String, CatalogEntry> bySymbol = entries.stream()
                .collect(Collectors.toMap(e -> e.definition().symbol(), e -> e));
        Map<String, String> result = new LinkedHashMap<>();
        for (Preferred p : definitions.preferred()) {
            CatalogEntry entry = bySymbol.get(p.symbol());
            if (entry == null) {
                throw new GenerationException(p.source(), p.line(), "unknown unit '" + p.symbol() + "'");
            }
            QuantityTypeSpec spec = byName.get(p.quantityType());
            if (spec == null) {
                throw new GenerationException(p.source(), p.line(), "unknown quantity type '" + p.quantityType()
                        + "'");
            }
            if (!spec.systemUnit().equals(entry.definition().unit().getSystemUnit())) {
                throw new GenerationException(p.source(), p.line(), "unit '" + p.symbol() + "' is not a unit of "
                        + spec.name());
            }
            if (result.putIfAbsent(spec.name(), entry.constantName()) != null) {
                throw new GenerationException(p.source(), p.line(), "duplicate preferred unit for '" + spec.name()
                        + "'");
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /** The symbols of the units named by {@code @prefixed}, in order of first appearance. */
    public static Set<String> prefixable(Definitions definitions) {
        Set<String> result = new LinkedHashSet<>();
        definitions.prefixed().forEach(p -> result.add(p.unitSymbol()));
        return Collections.unmodifiableSet(result);
    }

    private static Map<String, QuantityTypeSpec> byName(List<QuantityTypeSpec> types) {
        return types.stream().collect(Collectors.toMap(QuantityTypeSpec::name, t -> t));
    }

    private static GenerationException error(Definition d, String text) {
        return new GenerationException(d.source(), d.line(), text);
    }
}
