/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicenseHeaderTest {

    // config/license-header.txt with any year; Spotless writes it, the generator writes it with its own year.
    private static final Pattern HEADER = Pattern.compile(
            Pattern.quote(header().get(0)) + "\\R" + Pattern.quote(header().get(1)).replace("$YEAR", "\\E\\d{4}\\Q")
                    + header().stream().skip(2).map(l -> "\\R" + Pattern.quote(l)).collect(Collectors.joining())
                    + "\\R");
    private static final Set<String> SKIPPED_DIRS = Set.of("target", ".superpowers", ".git", ".worktrees");

    @Test
    void everySourceFileHasTheHeader() throws IOException {
        Path root = Path.of("..").toAbsolutePath().normalize();
        List<Path> sources;
        try (var files = Files.walk(root)) {
            sources = files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".java"))
                    .map(root::relativize).filter(LicenseHeaderTest::isModuleSource).toList();
        }
        // a wrong root or filter would check nothing: hand-written, module and generated files are all there
        List<String> names = sources.stream().map(p -> p.toString().replace('\\', '/')).toList();
        assertTrue(names.size() > 300, "checked " + names.size());
        assertTrue(names.contains("uom-core/src/main/java/module-info.java"), "module-info.java");
        assertTrue(names.contains("uom/src/main/java/org/tinygears/uom/quantity/Length.java"), "generated Length.java");
        List<String> violations = sources.stream().filter(p -> !hasHeader(root.resolve(p)))
                .map(p -> p.toString().replace('\\', '/')).sorted().toList();
        assertTrue(
                violations.isEmpty(),
                () -> "Missing license header (config/license-header.txt) in:\n" + String.join("\n", violations));
    }

    @Test
    void theHeaderTakesAnyYearAndNothingElse() {
        String header = String.join("\n", header()) + "\n";
        assertTrue(HEADER.matcher(header.replace("$YEAR", "2031") + "package x;\n").lookingAt());
        assertFalse(HEADER.matcher(header.replace("$YEAR", "20x1")).lookingAt());
        assertFalse(HEADER.matcher("// SPDX-License-Identifier: Apache-2.0\n\npackage x;\n").lookingAt());
    }

    /** Matches {@code <module>/src/**}, outside skipped directories. */
    private static boolean isModuleSource(Path rel) {
        if (rel.getNameCount() < 3 || !rel.getName(1).toString().equals("src")) {
            return false;
        }
        for (Path part : rel) {
            if (SKIPPED_DIRS.contains(part.toString())) {
                return false;
            }
        }
        return true;
    }

    private static List<String> header() {
        try {
            return Files.readAllLines(Path.of("../config/license-header.txt"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static boolean hasHeader(Path file) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }
            return HEADER.matcher(text).lookingAt();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
