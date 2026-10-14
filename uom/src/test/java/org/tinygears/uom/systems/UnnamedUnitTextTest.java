// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import javax.measure.MetricPrefix;
import javax.measure.Unit;
import org.junit.jupiter.api.Test;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.quantity.Frequency;
import org.tinygears.uom.quantity.Length;

/** An unnamed unit prints, and the format writes it, with a metric prefix on a prefixable unit of the systems. */
class UnnamedUnitTextTest {
    private static final UomUnitFormat FORMAT = UomUnitFormat.standard();
    private static final String MICRO = MetricPrefix.MICRO.getSymbol();

    @Test
    void aPrefixOnAPrefixableUnitIsWritten() {
        assertEquals("mHz", FORMAT.format(SI.HERTZ.prefix(MetricPrefix.MILLI)));
        assertEquals("ym", FORMAT.format(SI.METRE.prefix(MetricPrefix.YOCTO)));
        assertEquals("pW", FORMAT.format(SI.WATT.prefix(MetricPrefix.PICO)));
        assertEquals(MICRO + "g", FORMAT.format(SI.GRAM.prefix(MetricPrefix.MICRO)));
        // Two prefixes make one, and a prefix on the kilogram is a prefix on the gram.
        assertEquals(MICRO + "m", FORMAT.format(SI.METRE.prefix(MetricPrefix.MILLI).prefix(MetricPrefix.MILLI)));
        assertEquals("g", FORMAT.format(SI.KILOGRAM.prefix(MetricPrefix.MILLI)));
        assertEquals("km", FORMAT.format(SI.METRE.multiply(1000)));
    }

    @Test
    void aUnitThatTakesNoPrefixKeepsItsDefinition() {
        // min and day are not prefixable, km² is not a prefix on a unit, and an offset unit is not prefixed.
        assertEquals("60000·s", FORMAT.format(SI.MINUTE.prefix(MetricPrefix.KILO)));
        assertEquals("864·s", FORMAT.format(SI.DAY.prefix(MetricPrefix.CENTI)));
        assertEquals("1000·m^2", FORMAT.format(SI.SQUARE_METRE.prefix(MetricPrefix.KILO)));
        String celsius = FORMAT.format(SI.DEGREE_CELSIUS.prefix(MetricPrefix.MILLI));
        assertFalse(celsius.contains("m°C"), celsius);
        assertEquals(SI.DEGREE_CELSIUS.prefix(MetricPrefix.MILLI), FORMAT.parse(celsius));
    }

    @Test
    void everyPrefixOnEveryPrefixableUnitRoundTrips() {
        List<String> bad = new ArrayList<>();
        int checked = 0;
        for (UomSystemOfUnits system : UnitSystems.all()) {
            for (Unit<?> known : system.getUnits()) {
                if (known.getSymbol() == null || !system.isPrefixable(known.getSymbol())) {
                    continue;
                }
                for (MetricPrefix prefix : MetricPrefix.values()) {
                    UomUnit<?> unit = ((UomUnit<?>) known).prefix(prefix);
                    String text = FORMAT.format(unit);
                    checked++;
                    if (!unit.equals(FORMAT.parse(text))) {
                        bad.add(prefix + " " + known.getSymbol() + ": " + text);
                    }
                    // A prefix on a prefixable unit is never left as a long formula.
                    if (text.contains("|") || text.contains("·") && !text.contains("/")
                            && Character.isDigit(text.charAt(0))) {
                        bad.add("formula " + prefix + " " + known.getSymbol() + ": " + text);
                    }
                }
            }
        }
        assertEquals(List.of(), bad.subList(0, Math.min(bad.size(), 10)));
        assertTrue(checked > 1000, "checked " + checked);
    }

    @Test
    void anUnnamedUnitPrintsAsTheFormatWritesIt() {
        assertEquals("mHz", SI.HERTZ.prefix(MetricPrefix.MILLI).toString());
        assertEquals("km", SI.METRE.multiply(1000).toString());
        assertEquals("K + 5", SI.KELVIN.shift(5).toString());
        assertEquals("1000·m^2", SI.SQUARE_METRE.prefix(MetricPrefix.KILO).toString());
        assertEquals("km", SI.KILOMETRE.toString());
        // The format has no text for a negative factor, so the definition is printed.
        assertEquals("-2·m", SI.METRE.multiply(-2).toString());
        assertEquals("5.0 mHz", Frequency.of(5, SI.HERTZ.prefix(MetricPrefix.MILLI)).toString());
        assertEquals("2.0 km", Length.of(2, SI.METRE.multiply(1000)).toString());
        assertEquals("5.0 mHz", new UomQuantityFormat(FORMAT).format(Frequency.of(5, SI.HERTZ.prefix(MetricPrefix.MILLI))));
        assertNotEquals("1|1000·Hz", SI.HERTZ.prefix(MetricPrefix.MILLI).toString());
    }
}
