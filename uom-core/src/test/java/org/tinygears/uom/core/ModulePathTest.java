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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModulePathTest {

    @Test
    void describesModuleOnModulePath() throws Exception {
        Path classes = Path.of("").toAbsolutePath().resolve("target/classes");
        Assumptions.assumeTrue(Files.isDirectory(classes), "no target/classes");
        String jar = null;
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (Path.of(entry).getFileName().toString().equals("unit-api-2.2.jar")) {
                jar = entry;
            }
        }
        Assumptions.assumeTrue(jar != null, "unit-api-2.2.jar not on the class path");

        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String out = runBounded(
                List.of(
                        java,
                        "--module-path",
                        classes + File.pathSeparator + jar,
                        "--describe-module",
                        "org.tinygears.uom.core"),
                Duration.ofSeconds(30));
        assertEquals(
                List.of(
                        "exports org.tinygears.uom.core",
                        "exports org.tinygears.uom.core.parse",
                        "exports org.tinygears.uom.core.quantity"),
                out.lines().map(String::strip).filter(l -> l.startsWith("exports ")).sorted().toList(),
                out);
        // spi (the provider's hook) and internal (helpers of the formats) are exported to uom only, not to everyone.
        assertTrue(out.contains("qualified exports org.tinygears.uom.core.spi to org.tinygears.uom"), out);
        assertTrue(out.contains("qualified exports org.tinygears.uom.core.internal to org.tinygears.uom"), out);
        assertTrue(out.contains("requires java.measure transitive"), out);
    }

    @Test
    void runBoundedKillsHungProcess() throws Exception {
        Assumptions.assumeFalse(System.getProperty("os.name").startsWith("Windows"), "needs sleep");
        long start = System.nanoTime();
        AssertionError e = assertThrows(
                AssertionError.class,
                () -> runBounded(List.of("sleep", "60"), Duration.ofSeconds(1)));
        assertTrue(e.getMessage().contains("did not finish"), e.getMessage());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 10, "not bounded");
        assertTrue(
                ProcessHandle.allProcesses().noneMatch(
                        h -> h.parent().map(x -> x.pid() == ProcessHandle.current().pid()).orElse(false)
                                && h.info().command().map(c -> c.endsWith("/sleep")).orElse(false)),
                "sleep still alive");
    }

    // Output goes to a file so waitFor can time out before anything blocks on the pipe.
    static String runBounded(List<String> command, Duration timeout) throws Exception {
        Path log = Files.createTempFile("modulepath", ".log");
        Process p = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(
                    p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS),
                    "process did not finish within " + timeout);
            String out = Files.readString(log);
            assertEquals(0, p.exitValue(), out);
            return out;
        } finally {
            if (p.isAlive()) {
                p.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            }
            Files.deleteIfExists(log);
        }
    }
}
