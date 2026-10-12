// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ExamplesTest {
    @Test
    void examplesPrintWhatTheDocumentationShows() {
        assertEquals(Map.of(
                "basics", "42.595 km = 42595.0 m",
                "speed", "10.0 m/s",
                "temperature", "373.15 K | 100.0 °C",
                "equality", "equals=false equivalent=true",
                "formats", "km mK km^2 | 20.0 km",
                "provider", "UomServiceProvider true",
                "work", "30.0 J",
                "decimals", "0.3 m equals 0.3 m: true | 0.30000000000000004 m equals 0.3 m: false"
                        + " | 1/3 m = 0.3333333333333333333333333333333333 m"), Examples.all());
    }
}
