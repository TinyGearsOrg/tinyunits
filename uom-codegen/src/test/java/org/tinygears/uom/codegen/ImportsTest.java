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

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImportsTest {
    @Test
    void shortNamesAndSortedBlock() {
        Imports imports = new Imports("p", List.of("Own"));
        assertEquals("", imports.block());
        assertEquals("Set", imports.name("java.util.Set"));
        assertEquals("BigDecimal", imports.name("java.math.BigDecimal"));
        assertEquals("Set", imports.name("java.util.Set"));
        assertEquals("Quantity", imports.name("javax.measure.Quantity"));
        assertEquals(
                "import java.math.BigDecimal;\nimport java.util.Set;\nimport javax.measure.Quantity;\n\n",
                imports.block());
    }

    @Test
    void samePackageIsNeverImported() {
        Imports imports = new Imports("org.tinygears.uom.jsr.systems", List.of());
        assertEquals("UomSystemOfUnits", imports.name("org.tinygears.uom.jsr.systems.UomSystemOfUnits"));
        assertEquals("", imports.block());
        assertEquals("Foo", new Imports("", List.of()).name("Foo"));
    }

    @Test
    void takenNameStaysQualified() {
        Imports imports = new Imports("org.tinygears.uom.jsr.quantity", List.of("Length", "Unit"));
        assertEquals("javax.measure.quantity.Length", imports.name("javax.measure.quantity.Length"));
        assertEquals("javax.measure.Unit", imports.name("javax.measure.Unit"));
        assertEquals("Quantity", imports.name("javax.measure.Quantity"));
        assertEquals("import javax.measure.Quantity;\n\n", imports.block());
    }

    @Test
    void secondClassOfASimpleNameStaysQualified() {
        Imports imports = new Imports("p", List.of());
        assertEquals("List", imports.name("java.util.List"));
        assertEquals("java.awt.List", imports.name("java.awt.List"));
        assertEquals("List", imports.name("java.util.List"));
        assertEquals("import java.util.List;\n\n", imports.block());
    }

    @Test
    void javadocOnlyClassStaysQualifiedAndIsNotImported() {
        Imports imports = new Imports("p", List.of());
        assertEquals(
                "/** {@link java.util.Map} and {@link Set}, // java.util.List */\nSet s;",
                imports.resolve(
                        "/** {@link java.util.Map} and {@link java.util.Set}, // java.util.List */\n"
                                + "java.util.Set s;"));
        assertEquals(
                "// Map\nMap m; /* Map */",
                imports.resolve("// java.util.Map\njava.util.Map m; " + "/* java.util.Map */"));
        assertEquals("import java.util.Map;\nimport java.util.Set;\n\n", imports.block());
    }

    @Test
    void resolveShortensQualifiedClassNames() {
        Imports imports = new Imports("org.tinygears.uom.jsr.quantity", List.of("Length"));
        assertEquals(
                "Unit<javax.measure.quantity.Length> u = Objects.requireNonNull(x);\n"
                        + "Quantity.Scale s = Quantity.Scale.ABSOLUTE; MathContext m = MathContext.DECIMAL128; Length l;",
                imports.resolve(
                        "javax.measure.Unit<javax.measure.quantity.Length> u = "
                                + "java.util.Objects.requireNonNull(x);\njavax.measure.Quantity.Scale s = "
                                + "javax.measure.Quantity.Scale.ABSOLUTE; java.math.MathContext m = "
                                + "java.math.MathContext.DECIMAL128; Length l;"));
        assertEquals(
                "import java.math.MathContext;\nimport java.util.Objects;\nimport javax.measure.Quantity;\n"
                        + "import javax.measure.Unit;\n\n",
                imports.block());
    }
}
