// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LicenseHeaderTest {

    private static final String HEADER = "// SPDX-License-Identifier: Apache-2.0";
    private static final Set<String> SKIPPED_DIRS = Set.of("target", ".superpowers", ".git", ".worktrees");

    @Test
    void everySourceFileHasTheHeader() throws IOException {
        Path root = Path.of("..").toAbsolutePath().normalize();
        List<String> violations;
        try (var files = Files.walk(root)) {
            violations = files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .map(root::relativize)
                    .filter(LicenseHeaderTest::isModuleSource)
                    .filter(p -> !hasHeader(root.resolve(p)))
                    .map(p -> p.toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
        assertTrue(violations.isEmpty(), () -> "Missing SPDX header in:\n" + String.join("\n", violations));
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

    private static boolean hasHeader(Path file) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }
            int nl = text.indexOf('\n');
            String first = nl < 0 ? text : text.substring(0, nl);
            return first.stripTrailing().equals(HEADER);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
