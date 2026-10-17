/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.jsr.systems;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import org.tinygears.uom.jsr.systems.modulepath.PrintCurrentProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModulePathTest {

    private static final Path HERE = Path.of("").toAbsolutePath();
    private static final String JAVA = System.getProperty("java.home") + File.separator + "bin" + File.separator
            + "java";

    private static String modulePath() {
        Path classes = HERE.resolve("target/classes");
        Path core = HERE.resolve("../uom/target/classes").normalize();
        Path engine = HERE.resolve("../uom-engine/target/classes").normalize();
        Assumptions.assumeTrue(
                Files.isDirectory(classes) && Files.isDirectory(core) && Files.isDirectory(engine),
                "sibling classes not built");
        String jar = null;
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (Path.of(entry).getFileName().toString().equals("unit-api-2.2.jar")) {
                jar = entry;
            }
        }
        Assumptions.assumeTrue(jar != null, "unit-api-2.2.jar not on the class path");
        return String.join(File.pathSeparator, classes.toString(), jar, core.toString(), engine.toString());
    }

    @Test
    void describesModuleOnModulePath() throws Exception {
        String out = run(List.of(JAVA, "--module-path", modulePath(), "--describe-module", "org.tinygears.uom.jsr"));
        assertTrue(
                out.contains(
                        "provides javax.measure.spi.ServiceProvider with org.tinygears.uom.jsr.systems.UomServiceProvider"),
                out);
        assertTrue(out.contains("requires org.tinygears.uom transitive"), out);
        assertTrue(out.contains("exports org.tinygears.uom.jsr.quantity"), out);
        assertTrue(out.contains("exports org.tinygears.uom.jsr.systems"), out);
    }

    @Test
    void serviceLoaderFindsProviderOnModulePath() throws Exception {
        String modulePath = modulePath();
        // Only the main class goes on the class path: test-classes would add the test provider's services file.
        String name = PrintCurrentProvider.class.getName().replace('.', '/') + ".class";
        Path cp = Files.createTempDirectory("modulepath");
        try {
            Path target = cp.resolve(name);
            Files.createDirectories(target.getParent());
            Files.copy(HERE.resolve("target/test-classes").resolve(name), target);
            String out = run(
                    List.of(
                            JAVA,
                            "--module-path",
                            modulePath,
                            "--add-modules",
                            "org.tinygears.uom.jsr",
                            "-cp",
                            cp.toString(),
                            PrintCurrentProvider.class.getName()));
            assertEquals("org.tinygears.uom.jsr.systems.UomServiceProvider in org.tinygears.uom.jsr", out.strip());
        } finally {
            Files.delete(cp.resolve(name));
            for (Path dir = cp.resolve(name).getParent(); !dir.equals(cp.getParent()); dir = dir.getParent()) {
                Files.delete(dir);
            }
        }
    }

    // Output goes to a file so waitFor can time out before anything blocks on the pipe.
    private static String run(List<String> command) throws Exception {
        Path log = Files.createTempFile("modulepath", ".log");
        Process p = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(p.waitFor(30, TimeUnit.SECONDS), "process did not finish within " + Duration.ofSeconds(30));
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
