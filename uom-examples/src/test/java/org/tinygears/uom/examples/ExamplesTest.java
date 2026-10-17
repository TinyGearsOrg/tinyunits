/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.examples;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamplesTest {
    @Test
    void nativeExamplesPrintWhatTheDocumentationShows() {
        assertEquals(
                Map.ofEntries(
                        Map.entry("basics", "42.595 km = 42595.0 m"),
                        Map.entry("speed", "10.0 m/s"),
                        Map.entry("temperature", "373.15 K | 100.0 °C"),
                        Map.entry("equality", "equals=false equivalent=true"),
                        Map.entry(
                                "closeness",
                                "57.29577951308232087679815481410517 deg | equivalent: false, within 1e-30: true"
                                        + " | 1 ft within 1 mm of 0.305 m: true, within 0.1 mm: false"),
                        Map.entry("formats", "km mK km^2 | 20.0 km"),
                        Map.entry("work", "30.0 J"),
                        Map.entry(
                                "decimals",
                                "0.3 m equals 0.3 m: true | 0.30000000000000004 m equals 0.3 m: false"
                                        + " | 1/3 m = 0.3333333333333333333333333333333333 m"),
                        Map.entry(
                                "mixedPrecision",
                                "BigDecimal 0.3 m | same either way: true | doubles only: 0.30000000000000004 m, equivalent: true"),
                        Map.entry("contexts", "1.235 m | 0.4115226 m | Double"),
                        Map.entry(
                                "decimalEquality",
                                "decimal equals double: false | equivalent: true | 2 equals 2.00: true"),
                        Map.entry("decimalFormat", "1.50 km | Double 1.5"),
                        Map.entry(
                                "systems",
                                "1.0 ft = 30.48 cm | 100.0 °C = 212.0 °F | exact 212 °F"
                                        + " | 3.785411784 L vs 4.54609 L | 2.99792458E9 Fr"),
                        Map.entry(
                                "information",
                                "1.5 GiB / 100.0 Mbit/s = 128.84901888 s | 1610.612736 MB | KiB vs kB"),
                        Map.entry("serialization", "unit: true km/h | quantity: true 1.50 km"),
                        Map.entry("locales", "1,5 km | 1.5 km"),
                        Map.entry("offsetUnits", "K + 5 | parses back equal: true | 2·K + 136.575"),
                        Map.entry(
                                "decimalParse",
                                "1.5 km Double | 1.234567890 km scale 9 | /3 0.4115226 km | 2.0 kg·m/s BigDecimal"),
                        Map.entry("userUnits", "Medieval | 2.0 league = 9.656064 km = 48.0 furlong"),
                        Map.entry("bridge", "1.50 km Length | back equal: true")),
                Examples.all());
    }

    @Test
    void jsrExamplesPrintTheSame() {
        assertEquals(
                Map.ofEntries(
                        Map.entry("basics", "42.595 km = 42595.0 m"),
                        Map.entry("speed", "10.0 m/s"),
                        Map.entry("temperature", "373.15 K | 100.0 °C"),
                        Map.entry("equality", "equals=false equivalent=true"),
                        Map.entry(
                                "closeness",
                                "57.29577951308232087679815481410517 deg | equivalent: false, within 1e-30: true"
                                        + " | 1 ft within 1 mm of 0.305 m: true, within 0.1 mm: false"),
                        Map.entry("formats", "km mK km^2 | 20.0 km"),
                        Map.entry("provider", "UomServiceProvider true"),
                        Map.entry("work", "30.0 J"),
                        Map.entry(
                                "decimals",
                                "0.3 m equals 0.3 m: true | 0.30000000000000004 m equals 0.3 m: false"
                                        + " | 1/3 m = 0.3333333333333333333333333333333333 m"),
                        Map.entry(
                                "mixedPrecision",
                                "BigDecimal 0.3 m | same either way: true | doubles only: 0.30000000000000004 m, equivalent: true"),
                        Map.entry("contexts", "1.235 m | 0.4115226 m | Double"),
                        Map.entry(
                                "decimalEquality",
                                "decimal equals double: false | equivalent: true | 2 equals 2.00: true"),
                        Map.entry("decimalFormat", "1.50 km | Double 1.5"),
                        Map.entry(
                                "systems",
                                "1.0 ft = 30.48 cm | 100.0 °C = 212.0 °F | exact 212 °F"
                                        + " | 3.785411784 L vs 4.54609 L | 2.99792458E9 Fr"),
                        Map.entry(
                                "information",
                                "1.5 GiB / 100.0 Mbit/s = 128.84901888 s | 1610.612736 MB | KiB vs kB"),
                        Map.entry("serialization", "unit: true km/h | quantity: true 1.50 km"),
                        Map.entry("locales", "1,5 km | 1.5 km"),
                        Map.entry("offsetUnits", "K + 5 | parses back equal: true | 2·K + 136.575"),
                        Map.entry(
                                "decimalParse",
                                "1.5 km Double | 1.234567890 km scale 9 | /3 0.4115226 km | 2.0 kg·m/s BigDecimal"),
                        Map.entry("userUnits", "Medieval | 2.0 league = 9.656064 km = 48.0 furlong")),
                JsrExamples.all());
    }

    @Test
    void mainPrintsBothInOrder() throws IOException {
        StringBuilder out = new StringBuilder();
        Examples.all().forEach((name, result) -> out.append(name).append(": ").append(result).append('\n'));
        JsrExamples.all()
                .forEach((name, result) -> out.append("jsr.").append(name).append(": ").append(result).append('\n'));
        assertEquals(Files.readString(Path.of("../scripts/consumer/expected-output.txt")), out.toString());
    }

    @Test
    void securityDocumentShowsTheExamplesFilter() throws IOException {
        // the plain block and the java literal (joined over its " + " line break) of SECURITY.md
        String text = Files.readString(Path.of("../SECURITY.md"));
        assertTrue(text.lines().anyMatch(Examples.SERIAL_FILTER::equals), "plain filter block");
        assertTrue(
                text.replaceAll("\"\\s*\\+\\s*\"", "").contains("\"" + Examples.SERIAL_FILTER + "\""),
                "java filter literal");
    }
}
