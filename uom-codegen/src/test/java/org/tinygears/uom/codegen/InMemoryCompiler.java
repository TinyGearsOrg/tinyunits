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

import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compiles sources held in memory against the test class path and loads them before that class path. */
final class InMemoryCompiler {
    private InMemoryCompiler() {
    }

    /** {@code sources} maps binary class names to source text; fails on any diagnostic. */
    static URLClassLoader compile(Map<String, String> sources, Path outputDir) throws Exception {
        List<JavaFileObject> units = new ArrayList<>();
        sources.forEach(
                (name, text) -> units.add(
                        new SimpleJavaFileObject(
                                URI.create("string:///" + name.replace('.', '/') + ".java"),
                                JavaFileObject.Kind.SOURCE) {
                            @Override
                            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                                return text;
                            }
                        }));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<String> options = List.of(
                "-Xlint:all",
                "-proc:none",
                "-classpath",
                System.getProperty("java.class.path"),
                "-d",
                outputDir.toString());
        boolean ok = compiler.getTask(null, null, diagnostics, options, null, units).call();
        assertEquals(List.of(), diagnostics.getDiagnostics().stream().map(Object::toString).toList());
        assertTrue(ok);
        // Child first: the test class path holds uom-jsr, whose generated classes have the same names.
        return new URLClassLoader(
                new java.net.URL[] { outputDir.toUri().toURL() },
                InMemoryCompiler.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> c = findLoadedClass(name);
                    if (c == null && java.nio.file.Files
                            .isRegularFile(outputDir.resolve(name.replace('.', '/') + ".class"))) {
                        c = findClass(name);
                    }
                    if (c == null) {
                        return super.loadClass(name, resolve);
                    }
                    if (resolve) {
                        resolveClass(c);
                    }
                    return c;
                }
            }
        };
    }
}
