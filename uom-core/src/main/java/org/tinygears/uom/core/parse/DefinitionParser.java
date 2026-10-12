// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.parse;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.measure.MeasurementException;
import javax.measure.MetricPrefix;
import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.Converters;
import org.tinygears.uom.core.UomUnit;

/** Parses {@code .units} definition files: one {@code symbol[:name] body [Type] [# comment]} per line. */
public final class DefinitionParser {
    private static final Pattern TYPE = Pattern.compile("[A-Z][A-Za-z0-9]*");
    private static final String DELIMITERS = "*/·()^|=#[]!~@,+-";
    private static final String BASE_LETTERS = "LMTIΘNJK";
    // JLS 3.9 reserved keywords, '_', the literals and the restricted identifiers that are not type names;
    // avoids depending on the java.compiler module.
    private static final Set<String> RESERVED = Set.of("abstract", "assert", "boolean", "break", "byte", "case",
            "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends",
            "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short",
            "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try",
            "void", "volatile", "while", "_", "true", "false", "null", "var", "yield", "record", "sealed", "permits");
    private static final Map<String, MetricPrefix> PREFIXES = Arrays.stream(MetricPrefix.values())
            .collect(Collectors.toUnmodifiableMap(MetricPrefix::getSymbol, p -> p));
    private static final UomUnit<?>[] BASES = {BaseUnits.METRE, BaseUnits.KILOGRAM, BaseUnits.SECOND,
        BaseUnits.AMPERE, BaseUnits.KELVIN, BaseUnits.MOLE, BaseUnits.CANDELA, BaseUnits.KELVIN};

    /** Opens an included file by name. */
    public interface Resolver {
        Reader open(String name) throws IOException;
    }

    private final Resolver resolver;
    private final Map<String, UomUnit<?>> units = new HashMap<>();
    private final List<Definition> definitions = new ArrayList<>();
    private final List<Preferred> preferred = new ArrayList<>();
    private final List<Prefixed> prefixed = new ArrayList<>();
    private String system;
    private final List<Extends> extended = new ArrayList<>();
    private final List<Member> members = new ArrayList<>();
    private final List<String> stack = new ArrayList<>();
    private final Set<String> done = new HashSet<>();

    private DefinitionParser(Resolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Parses {@code reader} without closing it; included files are opened and closed here. Alternate-unit
     * registrations ({@code ~} lines) are JVM-global and permanent, so a failed or corrected {@code ~}
     * definition cannot be re-registered differently until the JVM restarts.
     */
    public static Definitions parse(String source, Reader reader, Resolver resolver) {
        DefinitionParser p = new DefinitionParser(resolver);
        try {
            p.file(source, reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Definitions(p.definitions, p.preferred, p.prefixed, p.system, p.extended, p.members);
    }

    private void file(String source, Reader reader) throws IOException {
        stack.add(source);
        done.add(source);
        BufferedReader in = new BufferedReader(reader);
        int n = 0;
        for (String text = in.readLine(); text != null; text = in.readLine()) {
            n++;
            line(source, n, n == 1 && text.startsWith("\uFEFF") ? text.substring(1) : text);
        }
        stack.remove(stack.size() - 1);
    }

    private void line(String src, int ln, String text) throws IOException {
        String t = text.strip();
        if (t.isEmpty() || t.startsWith("#")) {
            return;
        }
        try {
            if (t.startsWith("@")) {
                directive(src, ln, t);
            } else {
                definition(src, ln, t);
            }
        } catch (FormulaException | ArithmeticException | IllegalArgumentException | MeasurementException e) {
            throw new DefinitionException(src, ln, e.getMessage(), e);
        }
    }

    private void directive(String src, int ln, String t) throws IOException {
        int hash = t.indexOf('#');
        String[] w = (hash < 0 ? t : t.substring(0, hash)).strip().split("\\s+");
        switch (w[0]) {
            case "@include" -> {
                if (w.length != 2) {
                    throw new DefinitionException(src, ln, "expected '@include name'", null);
                }
                include(src, ln, w[1]);
            }
            case "@preferred" -> {
                if (w.length != 3) {
                    throw new DefinitionException(src, ln, "expected '@preferred Type symbol'", null);
                }
                checkType(src, ln, w[1]);
                if (!units.containsKey(w[2])) {
                    throw new DefinitionException(src, ln, "unknown unit '" + w[2] + "'", null);
                }
                preferred.add(new Preferred(w[1], w[2], src, ln));
            }
            case "@prefixed" -> prefixed(src, ln, w);
            case "@system" -> system(src, ln, w);
            case "@extends" -> {
                if (w.length != 2) {
                    throw new DefinitionException(src, ln, "expected '@extends name'", null);
                }
                if (root() && extended.stream().anyMatch(e -> e.file().equals(w[1]))) {
                    throw new DefinitionException(src, ln, "duplicate '@extends' of '" + w[1] + "'", null);
                }
                include(src, ln, w[1]);
                if (root()) {
                    extended.add(new Extends(w[1], src, ln));
                }
            }
            case "@member" -> member(src, ln, w);
            default -> throw new DefinitionException(src, ln, "unknown directive '" + w[0] + "'", null);
        }
    }

    // Nested files are parsed on their own by the generator, so their system directives are not the root's.
    private boolean root() {
        return stack.size() == 1;
    }

    private void system(String src, int ln, String[] w) {
        if (w.length != 2) {
            throw new DefinitionException(src, ln, "expected '@system Name'", null);
        }
        if (!isJavaName(w[1])) {
            throw new DefinitionException(src, ln, "invalid system name '" + w[1] + "'", null);
        }
        if (root()) {
            if (system != null) {
                throw new DefinitionException(src, ln, "duplicate '@system'", null);
            }
            system = w[1];
        }
    }

    private static boolean isJavaName(String s) {
        return !RESERVED.contains(s) && Character.isJavaIdentifierStart(s.codePointAt(0))
                && s.codePoints().allMatch(Character::isJavaIdentifierPart);
    }

    private void member(String src, int ln, String[] w) {
        if (w.length < 2) {
            throw new DefinitionException(src, ln, "expected '@member symbol...'", null);
        }
        for (String symbol : List.of(w).subList(1, w.length)) {
            if (!units.containsKey(symbol)) {
                throw new DefinitionException(src, ln, "unknown unit '" + symbol + "'", null);
            }
            if (definitions.stream().anyMatch(d -> d.symbol().equals(symbol) && d.source().equals(src))) {
                throw new DefinitionException(src, ln, "unit '" + symbol + "' is defined in this file", null);
            }
            if (root()) {
                if (members.stream().anyMatch(m -> m.symbol().equals(symbol))) {
                    throw new DefinitionException(src, ln, "duplicate member '" + symbol + "'", null);
                }
                members.add(new Member(symbol, src, ln));
            }
        }
    }

    private void prefixed(String src, int ln, String[] w) {
        if (w.length < 3) {
            throw new DefinitionException(src, ln, "expected '@prefixed unit prefix...'", null);
        }
        Definition base = definitions.stream().filter(d -> d.symbol().equals(w[1])).findFirst().orElseThrow(
                () -> new DefinitionException(src, ln, "unknown unit '" + w[1] + "'", null));
        if (!base.unit().toSystemConverter().isLinear()) {
            throw new DefinitionException(src, ln, "unit '" + w[1] + "' can not be prefixed", null);
        }
        List<String> symbols = List.of(w).subList(2, w.length);
        for (String p : symbols) {
            MetricPrefix prefix = PREFIXES.get(p);
            if (prefix == null) {
                throw new DefinitionException(src, ln, "unknown metric prefix '" + p + "'", null);
            }
            String symbol = p + base.symbol();
            checkSymbol(src, ln, symbol);
            String name = prefix.getName().toLowerCase(Locale.ROOT) + base.name();
            UomUnit<?> unit = base.unit().prefix(prefix).named(symbol, name);
            units.put(symbol, unit);
            definitions.add(new Definition(symbol, name, unit, null, base.explicitType(), src, ln));
        }
        prefixed.add(new Prefixed(base.symbol(), symbols, src, ln));
    }

    private void include(String src, int ln, String name) {
        int i = stack.indexOf(name);
        if (i >= 0) {
            String chain = String.join(" -> ", stack.subList(i, stack.size())) + " -> " + name;
            throw new DefinitionException(src, ln, "include cycle: " + chain, null);
        }
        if (done.contains(name)) {
            return;
        }
        try (Reader r = resolver == null ? null : resolver.open(name)) {
            if (r == null) {
                throw new FileNotFoundException(name);
            }
            file(name, r);
        } catch (IOException e) {
            throw new DefinitionException(src, ln, "cannot include '" + name + "'", e);
        }
    }

    private void definition(String src, int ln, String t) throws FormulaException {
        // '#' and '[' can not occur in symbols or formulas, so the first of each delimits.
        int hash = t.indexOf('#');
        int open = t.indexOf('[');
        String type = null;
        if (open >= 0 && (hash < 0 || open < hash)) {
            int close = t.indexOf(']', open);
            String rest = close < 0 ? "" : t.substring(close + 1).strip();
            boolean trailing = close < 0 || !rest.isEmpty() && !rest.startsWith("#");
            type = trailing ? t.substring(open + 1).strip() : t.substring(open + 1, close);
            if (trailing) {
                throw malformedType(src, ln, type);
            }
            checkType(src, ln, type);
            t = t.substring(0, open).strip();
        } else if (hash >= 0) {
            t = t.substring(0, hash).strip();
        }
        int sp = 0;
        while (sp < t.length() && !Character.isWhitespace(t.charAt(sp))) {
            sp++;
        }
        String head = t.substring(0, sp);
        String body = t.substring(sp).strip();
        int colon = head.indexOf(':');
        String symbol = colon < 0 ? head : head.substring(0, colon);
        String name = colon < 0 ? symbol : head.substring(colon + 1).replace('_', ' ');
        checkSymbol(src, ln, symbol);

        UomUnit<?> unit;
        UomUnit<?> alternateOf = null;
        int eq = body.indexOf('=');
        if (body.startsWith("!")) {
            unit = base(src, ln, symbol, body.substring(1).strip());
        } else if (body.startsWith("~")) {
            String formula = body.substring(1);
            alternateOf = formula.isBlank() ? BaseUnits.ONE : evaluate(formula);
            try {
                unit = alternateOf.alternate(symbol);
            } catch (IllegalArgumentException e) {
                throw new DefinitionException(src, ln, "alternate unit must be an unscaled unit", e);
            }
        } else if (eq >= 0) {
            UomUnit<?> ref = evaluate(body.substring(0, eq));
            AffineFunction f = AffineFunction.parse(body.substring(eq + 1));
            unit = ref.transform(Converters.affine(f.a(), f.b()));
        } else {
            unit = evaluate(body);
        }
        UomUnit<?> named = unit.named(symbol, name);
        units.put(symbol, named);
        definitions.add(new Definition(symbol, name, named, alternateOf, type, src, ln));
    }

    private UomUnit<?> evaluate(String formula) throws FormulaException {
        return FormulaEvaluator.evaluate(formula, units::get);
    }

    private static UomUnit<?> base(String src, int ln, String symbol, String letter) {
        int i = letter.length() == 1 ? BASE_LETTERS.indexOf(letter.charAt(0)) : -1;
        if (i < 0) {
            throw new DefinitionException(src, ln, "unknown base dimension '" + letter + "'", null);
        }
        if (!BASES[i].getSymbol().equals(symbol)) {
            throw new DefinitionException(src, ln, "base unit symbol must be '" + BASES[i].getSymbol() + "'", null);
        }
        return BASES[i];
    }

    private void checkSymbol(String src, int ln, String symbol) {
        boolean valid = !symbol.isEmpty() && symbol.charAt(0) != '.'
                && !(symbol.charAt(0) >= '0' && symbol.charAt(0) <= '9')
                && symbol.chars().noneMatch(c -> DELIMITERS.indexOf(c) >= 0);
        if (!valid) {
            throw new DefinitionException(src, ln, "invalid symbol '" + symbol + "'", null);
        }
        if (symbol.equals("pi")) {
            throw new DefinitionException(src, ln, "symbol 'pi' is reserved", null);
        }
        if (units.containsKey(symbol)) {
            throw new DefinitionException(src, ln, "duplicate symbol '" + symbol + "'", null);
        }
    }

    private static void checkType(String src, int ln, String type) {
        if (!TYPE.matcher(type).matches()) {
            throw malformedType(src, ln, type);
        }
    }

    private static DefinitionException malformedType(String src, int ln, String type) {
        return new DefinitionException(src, ln, "malformed quantity type '" + type + "'", null);
    }
}
