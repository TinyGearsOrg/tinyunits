// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.codegen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The imports of one generated file: hands out short names and writes the sorted import block. */
public final class Imports {
    // Templates hold no "//" or "/*" in string literals.
    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/|//[^\n]*", Pattern.DOTALL);
    // Lower-case package segments, then the first capitalised segment: the top-level class.
    private static final Pattern QUALIFIED = Pattern.compile("\\b(?:java|javax|org)(?:\\.[a-z][a-z0-9]*)+\\.[A-Z]\\w*");
    private final String pkg;
    private final Set<String> taken;
    private final Map<String, String> bySimpleName = new HashMap<>();

    /**
     * {@code taken} are simple names the file declares or uses from its own package, including field names: a field
     * shadows a type of the same name in expressions such as {@code Map.entry(...)}, so such types stay qualified.
     */
    public Imports(String pkg, Collection<String> taken) {
        this.pkg = pkg;
        this.taken = Set.copyOf(taken);
    }

    /** The name to write for the top-level class {@code qualified}: short when imported or same-package. */
    public String name(String qualified) {
        String simple = simple(qualified);
        if (inPackage(qualified)) {
            return simple;
        }
        if (taken.contains(simple)) {
            return qualified;
        }
        String owner = bySimpleName.putIfAbsent(simple, qualified);
        return owner == null || owner.equals(qualified) ? simple : qualified;
    }

    /**
     * {@code text} with each qualified class name ({@code java.util.Objects.requireNonNull}) given its name; comments
     * only use names the code imports, so a class named only in Javadoc stays qualified and is not imported.
     */
    public String resolve(String text) {
        List<String> parts = new ArrayList<>();
        Matcher c = COMMENT.matcher(text);
        int end = 0;
        while (c.find()) {
            parts.add(text.substring(end, c.start()));
            parts.add(c.group());
            end = c.end();
        }
        parts.add(text.substring(end));
        for (int i = 0; i < parts.size(); i += 2) {
            parts.set(i, QUALIFIED.matcher(parts.get(i)).replaceAll(m -> Matcher.quoteReplacement(name(m.group()))));
        }
        for (int i = 1; i < parts.size(); i += 2) {
            parts.set(i, QUALIFIED.matcher(parts.get(i)).replaceAll(m -> Matcher.quoteReplacement(known(m.group()))));
        }
        return String.join("", parts);
    }

    /** The short name of a class that is imported or in the package, else the qualified one. */
    private String known(String qualified) {
        return inPackage(qualified) || qualified.equals(bySimpleName.get(simple(qualified))) ? simple(qualified)
                : qualified;
    }

    private static String simple(String qualified) {
        return qualified.substring(qualified.lastIndexOf('.') + 1);
    }

    private boolean inPackage(String qualified) {
        return qualified.substring(0, Math.max(qualified.lastIndexOf('.'), 0)).equals(pkg);
    }

    /** One sorted {@code import} line per imported class and a blank line, or nothing without imports. */
    public String block() {
        if (bySimpleName.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        new TreeSet<>(bySimpleName.values()).forEach(q -> out.append("import ").append(q).append(";\n"));
        return out.append('\n').toString();
    }
}
