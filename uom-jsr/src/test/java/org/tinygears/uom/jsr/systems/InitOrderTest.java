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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.jsr.quantity.QuantityTypes;
import org.tinygears.uom.jsr.systems.initorder.FirstTouch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each catalog and factory, touched first in a fresh JVM, initialises without a cycle (SI -> factories only). */
class InitOrderTest {
    private static final String JAVA = System.getProperty("java.home") + File.separator + "bin" + File.separator
            + "java";

    @Test
    void everyClassCanBeTouchedFirst() throws Exception {
        List<String> first = new ArrayList<>();
        FirstTouch.CATALOGS.forEach(c -> first.add("org.tinygears.uom.jsr.systems." + c));
        QuantityTypes.types()
                .forEach(t -> first.add("org.tinygears.uom.jsr.quantity." + t.getSimpleName() + "Factory"));
        first.add("org.tinygears.uom.jsr.quantity.QuantityTypes");
        assertEquals(43, first.size());
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
        try {
            Map<String, Future<String>> runs = new LinkedHashMap<>();
            for (String name : first) {
                runs.put(name, pool.submit(() -> run(name)));
            }
            for (Map.Entry<String, Future<String>> r : runs.entrySet()) {
                assertEquals("ok", r.getValue().get(120, TimeUnit.SECONDS).strip(), r.getKey());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // Output goes to a file so waitFor can time out before anything blocks on the pipe. No perf data: JVMs in PID
    // namespaces that share /tmp can get the same PID, collide on its hsperfdata file and print a warning.
    private static String run(String name) throws Exception {
        Path log = Files.createTempFile("initorder", ".log");
        Process p = new ProcessBuilder(
                JAVA,
                "-XX:TieredStopAtLevel=1",
                "-XX:-UsePerfData",
                "-cp",
                System.getProperty("java.class.path"),
                FirstTouch.class.getName(),
                name).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(p.waitFor(60, TimeUnit.SECONDS), name + " did not finish");
            String out = Files.readString(log);
            assertEquals(0, p.exitValue(), name + ": " + out);
            return out;
        } finally {
            if (p.isAlive()) {
                p.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            }
            Files.deleteIfExists(log);
        }
    }
}
