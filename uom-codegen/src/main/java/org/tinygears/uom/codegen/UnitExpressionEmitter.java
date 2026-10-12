// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.codegen;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.tinygears.uom.core.Converters;
import org.tinygears.uom.core.UnitAtom;
import org.tinygears.uom.core.UomConverter;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.core.parse.Definition;
import org.tinygears.uom.core.parse.Definitions;

/** Writes Java expressions that evaluate to units equal to given ones. */
public final class UnitExpressionEmitter {
    private static final String BASE_UNITS = "org.tinygears.uom.core.BaseUnits.";
    private static final Map<String, String> BASE_ATOMS = Map.of("m", "METRE", "kg", "KILOGRAM", "s", "SECOND",
            "A", "AMPERE", "K", "KELVIN", "mol", "MOLE", "cd", "CANDELA");
    private static final int MAX_DEPTH = 32;

    private UnitExpressionEmitter() {
    }

    public static String emit(UomUnit<?> unit, Definitions definitions) {
        return emit(unit, definitions, 0);
    }

    /** Like {@link #emit} but for any unit: its system unit transformed by its exact converter. */
    public static String emitUnit(UomUnit<?> unit, Definitions definitions) {
        String system = emit(unit.getSystemUnit(), definitions);
        UomConverter c = unit.toSystemConverter();
        return c.equals(Converters.IDENTITY) ? system : system + ".transform(org.tinygears.uom.core.Converters.affine("
                + FactorEmitter.emit(c.factor()) + ", " + FactorEmitter.emit(c.offset()) + "))";
    }

    private static String emit(UomUnit<?> unit, Definitions definitions, int depth) {
        if (!unit.toSystemConverter().equals(Converters.IDENTITY)) {
            throw new GenerationException("unit must be an unscaled system unit");
        }
        List<Map.Entry<UnitAtom, Integer>> atoms = unit.atoms().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(UnitAtom::symbol))).toList();
        StringBuilder out = new StringBuilder();
        for (Map.Entry<UnitAtom, Integer> e : atoms) {
            if (e.getValue() > 0) {
                String term = power(atom(e.getKey().symbol(), definitions, depth), e.getValue());
                out.append(out.isEmpty() ? term : ".multiply(" + term + ")");
            }
        }
        if (out.isEmpty()) {
            out.append(BASE_UNITS).append("ONE");
        }
        for (Map.Entry<UnitAtom, Integer> e : atoms) {
            if (e.getValue() < 0) {
                out.append(".divide(").append(power(atom(e.getKey().symbol(), definitions, depth), -e.getValue()))
                        .append(')');
            }
        }
        return out.toString();
    }

    private static String power(String term, int n) {
        return n == 1 ? term : term + ".pow(" + n + ")";
    }

    private static String atom(String symbol, Definitions definitions, int depth) {
        String base = BASE_ATOMS.get(symbol);
        if (base != null) {
            return BASE_UNITS + base;
        }
        Definition d = definitions.definitions().stream()
                .filter(x -> x.symbol().equals(symbol) && x.alternateOf() != null).findFirst()
                .orElseThrow(() -> new GenerationException("no definition for alternate unit '" + symbol + "'"));
        if (depth >= MAX_DEPTH) {
            throw new GenerationException("alternate unit definitions nest too deeply at '" + symbol + "'");
        }
        return emit(d.alternateOf(), definitions, depth + 1) + ".alternate(" + literal(symbol) + ")";
    }

    /** A Java string literal for {@code s}. */
    static String literal(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
