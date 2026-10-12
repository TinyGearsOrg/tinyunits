// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ExamplesTest {
    @Test
    void examplesPrintWhatTheDocumentationShows() {
        assertEquals(Map.ofEntries(
                Map.entry("basics", "42.595 km = 42595.0 m"),
                Map.entry("speed", "10.0 m/s"),
                Map.entry("temperature", "373.15 K | 100.0 °C"),
                Map.entry("equality", "equals=false equivalent=true"),
                Map.entry("formats", "km mK km^2 | 20.0 km"),
                Map.entry("provider", "UomServiceProvider true"),
                Map.entry("work", "30.0 J"),
                Map.entry("decimals", "0.3 m equals 0.3 m: true | 0.30000000000000004 m equals 0.3 m: false"
                        + " | 1/3 m = 0.3333333333333333333333333333333333 m"),
                Map.entry("mixedPrecision",
                        "Double 4.0 m | same type either way: true | 1e400 into a double: ArithmeticException"),
                Map.entry("contexts", "1.235 m | 0.4115226 m | Double"),
                Map.entry("decimalEquality",
                        "decimal equals double: false | equivalent: true | 2 equals 2.00: true"),
                Map.entry("decimalFormat", "1.50 km | Double 1.5"),
                Map.entry("systems", "1.0 ft = 30.48 cm | 100.0 °C = 212.0 °F | exact 212 °F"
                        + " | 3.785411784 L vs 4.54609 L | 2.99792458E9 Fr")), Examples.all());
    }
}
