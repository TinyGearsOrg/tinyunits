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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks the import block of a generated source against its body. */
final class ImportCheck {
    private static final Pattern LAYOUT = Pattern.compile(
            Pattern.quote(Headers.LICENSE)
                    // import groups (config/import-order.importorder), each followed by a blank line
                    + "// [^\n]*\n\npackage [\\w.]+;\n\n((?:import [\\w.]+;\n)+\n)*(?!import |\n)",
            Pattern.DOTALL);
    private static final Pattern IMPORT = Pattern.compile("^import ([\\w.]+);$", Pattern.MULTILINE);
    private static final Pattern NON_CODE = Pattern
            .compile("//[^\n]*|/\\*.*?\\*/|\"(?:\\\\.|[^\"\\\\])*\"", Pattern.DOTALL);
    private static final Pattern STANDARD_TYPE = Pattern.compile("javax\\.measure\\.quantity\\.(\\w+)");

    private ImportCheck() {
    }

    /**
     * Problems of {@code text}: layout, unsorted or duplicate imports, imports unused in code, imported classes still
     * written qualified, and standard quantity types written qualified although their name is not in {@code clashing}.
     */
    static List<String> problems(String text, Set<String> clashing) {
        List<String> problems = new ArrayList<>();
        if (!LAYOUT.matcher(text).lookingAt()) {
            problems.add("header, package and import block are not laid out as expected");
        }
        List<String> imports = new ArrayList<>();
        Matcher m = IMPORT.matcher(text);
        while (m.find()) {
            imports.add(m.group(1));
        }
        if (!imports.equals(imports.stream().sorted().toList())) {
            problems.add("imports not sorted: " + imports);
        }
        String code = NON_CODE.matcher(IMPORT.matcher(text).replaceAll("")).replaceAll("")
                .replaceFirst("package [\\w.]+;", "");
        Set<String> simpleNames = new HashSet<>();
        for (String q : imports) {
            String simple = q.substring(q.lastIndexOf('.') + 1);
            if (!simpleNames.add(simple)) {
                problems.add("two imports named " + simple);
            }
            if (!Pattern.compile("(?<![\\w.])" + simple + "\\b").matcher(code).find()) {
                problems.add("unused import " + q);
            }
            if (Pattern.compile(Pattern.quote(q) + "\\b").matcher(code).find()) {
                problems.add("imported but written qualified: " + q);
            }
        }
        Matcher t = STANDARD_TYPE.matcher(code);
        while (t.find()) {
            if (!clashing.contains(t.group(1))) {
                problems.add("qualified without a clash: " + t.group());
            }
        }
        return problems;
    }
}
