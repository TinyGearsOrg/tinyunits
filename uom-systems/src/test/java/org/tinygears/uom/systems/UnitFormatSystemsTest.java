// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.measure.MetricPrefix;
import javax.measure.Unit;
import javax.measure.format.MeasurementParseException;
import org.junit.jupiter.api.Test;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.systems.SystemsCatalogTest.Catalog;

/** The standard format over all seven systems (D7). */
class UnitFormatSystemsTest {
    private static final List<Catalog> CATALOGS = SystemsCatalogTest.CATALOGS;
    private final UomUnitFormat format = UomUnitFormat.standard();

    private UomUnit<?> parse(String text) {
        return (UomUnit<?>) format.parse(text);
    }

    /** Every symbol of every system with its unit; a symbol never names two different units. */
    private static Map<String, UomUnit<?>> symbols() {
        Map<String, UomUnit<?>> all = new HashMap<>();
        for (Catalog c : CATALOGS) {
            for (UomUnit<?> u : c.units()) {
                UomUnit<?> previous = all.put(u.getSymbol(), u);
                assertTrue(previous == null || previous == u, u.getSymbol());
            }
        }
        return all;
    }

    @Test
    void standardFormatSeesAllSystems() {
        assertEquals(CATALOGS.stream().map(Catalog::system).toList(), UnitSystems.all());
    }

    @Test
    void everyUnitOfEverySystemRoundTrips() {
        int units = 0;
        for (Catalog c : CATALOGS) {
            for (Unit<?> unit : c.system().getUnits()) {
                assertEquals(unit, format.parse(format.format(unit)), c.name() + ": " + unit);
                units++;
            }
        }
        assertEquals(128 + 35 + 42 + 16 + 22 + 29 + 26, units);
        Map<String, UomUnit<?>> symbols = symbols();
        symbols.forEach((symbol, unit) -> assertSame(unit, format.parse(symbol), symbol));
        assertEquals(128 + 35 + 7 + 13 + 6 + 13 + 1, symbols.size());
    }

    @Test
    void everyPrefixedFormRoundTrips() {
        Map<String, UomUnit<?>> symbols = symbols();
        int exact = 0;
        int prefixed = 0;
        for (Catalog c : CATALOGS) {
            for (String symbol : c.prefixable()) {
                UomUnit<?> unit = c.system().lookup(symbol).orElseThrow();
                for (MetricPrefix p : MetricPrefix.values()) {
                    String text = p.getSymbol() + symbol;
                    UomUnit<?> parsed = parse(text);
                    assertEquals(parsed, format.parse(format.format(parsed)), text);
                    if (symbols.containsKey(text)) {
                        assertSame(symbols.get(text), parsed, text);
                        exact++;
                    } else {
                        assertEquals(unit.prefix(p), parsed, text);
                        assertEquals(text, parsed.getSymbol());
                        prefixed++;
                    }
                }
            }
        }
        assertEquals((32 + 5 + 5 + 6 + 6) * 24, exact + prefixed);
        assertEquals(List.of(140, 1156), List.of(exact, prefixed));
    }

    @Test
    void parseExamples() {
        assertSame(USCustomary.FOOT, parse("ft"));
        assertSame(USCustomary.GALLON_US, parse("gal_US"));
        assertSame(Imperial.GALLON_UK, parse("gal_UK"));
        assertSame(USCustomary.DEGREE_FAHRENHEIT, parse("°F"));
        assertSame(USCustomary.DEGREE_RANKINE, parse("°R"));
        assertSame(USCustomary.KNOT, parse("kn"));
        assertSame(USCustomary.MILE_PER_HOUR, parse("mph"));
        assertSame(CGS.CENTIPOISE, parse("cP"));
        assertSame(CGS.CENTISTOKES, parse("cSt"));
        assertSame(CGS.MILLIGALILEO, parse("mGal"));
        assertSame(ESU.STATCOULOMB, parse("statC"));
        assertSame(ESU.STATOHM, parse("statΩ"));
        assertSame(EMU.ABAMPERE, parse("abA"));
        assertSame(EMU.GAUSS, parse("G"));
        assertSame(EMU.MILLIGAUSS, parse("mG"));
        assertSame(EMU.MICROGAUSS, parse("µG"));
        assertSame(Gaussian.FRANKLIN, parse("Fr"));
        assertSame(CGS.DYNE, parse("dyn"));
        assertSame(CGS.ERG, parse("erg"));
        assertSame(CGS.POISE, parse("P"));
        assertSame(CGS.STOKES, parse("St"));
        assertSame(CGS.GALILEO, parse("Gal"));
        assertEquals(EMU.GAUSS.prefix(MetricPrefix.NANO), parse("nG"));
        assertEquals(CGS.GALILEO.prefix(MetricPrefix.KILO), parse("kGal"));
        assertEquals(USCustomary.FOOT.divide(SI.SECOND), parse("ft/s"));
        assertEquals(CGS.DYNE, parse("g·cm/s^2"));
    }

    @Test
    void exactSymbolsBeatPrefixReadings() {
        assertSame(USCustomary.FOOT, parse("ft"));
        assertEquals(SI.TONNE.prefix(MetricPrefix.FEMTO), new UomUnitFormat(List.of(SI.SYSTEM)).parse("ft"));
        assertSame(CGS.STOKES, parse("St"));
        assertSame(CGS.GALILEO, parse("Gal"));
        assertSame(CGS.POISE, parse("P"));
        assertSame(SI.MILLISECOND, parse("ms"));
        assertSame(EMU.MILLIGAUSS, parse("mG"));
        assertSame(SI.MINUTE, parse("min"));
        assertSame(SI.MEGATONNE, parse("Mt"));
    }

    @Test
    void nonPrefixableUnitsTakeNoPrefix() {
        Map<String, UomUnit<?>> symbols = symbols();
        int refused = 0;
        for (Catalog c : CATALOGS.subList(1, CATALOGS.size())) {
            for (UomUnit<?> unit : c.units()) {
                String symbol = unit.getSymbol();
                if (c.prefixable().contains(symbol)) {
                    continue;
                }
                for (MetricPrefix p : MetricPrefix.values()) {
                    String text = p.getSymbol() + symbol;
                    if (!symbols.containsKey(text)) {
                        assertThrows(MeasurementParseException.class, () -> format.parse(text), text);
                        refused++;
                    }
                }
            }
        }
        assertEquals(3572, refused);
        MeasurementParseException e = assertThrows(MeasurementParseException.class, () -> format.parse("mgal_US"));
        assertEquals(0, e.getPosition());
        assertThrows(MeasurementParseException.class, () -> format.parse("kft"));
        assertThrows(MeasurementParseException.class, () -> format.parse("cdyn"));
    }

    /**
     * Every SI symbol and every prefixed SI reading parses as in SI-only mode, except the strings that a new
     * system defines as an exact symbol (D7).
     */
    @Test
    void shadowSet() {
        UomUnitFormat siOnly = new UomUnitFormat(List.of(SI.SYSTEM));
        Set<String> texts = new TreeSet<>();
        SI.UNITS.forEach(u -> texts.add(u.getSymbol()));
        for (String symbol : SI.PREFIXABLE) {
            for (MetricPrefix p : MetricPrefix.values()) {
                texts.add(p.getSymbol() + symbol);
            }
        }
        Map<String, String> changed = new TreeMap<>();
        for (String text : texts) {
            UomUnit<?> before = (UomUnit<?>) siOnly.parse(text);
            UomUnit<?> after = parse(text);
            if (!before.equals(after) || !before.getSymbol().equals(after.getSymbol())
                    || !before.getName().equals(after.getName())) {
                changed.put(text, before.getName() + " -> " + after.getName());
            }
        }
        assertEquals(811, texts.size());
        // ft: the femtotonne (f + t) becomes the international foot of USCustomary.
        assertEquals(Map.of("ft", "femtotonne -> foot"), changed);
        assertThrows(MeasurementParseException.class, () -> siOnly.parse("mG"));
        assertSame(EMU.MILLIGAUSS, parse("mG"));
    }

    @Test
    void labelsThatAreSymbolsOfAnotherSystemAreRejected() {
        UomUnitFormat f = UomUnitFormat.standard();
        List<String> messages = new ArrayList<>();
        for (String label : List.of("ft", "dyn", "statC", "G", "gal_UK")) {
            messages.add(assertThrows(IllegalArgumentException.class, () -> f.label(SI.METRE, label)).getMessage());
        }
        assertEquals(List.of("label 'ft' is the symbol of foot", "label 'dyn' is the symbol of dyne",
                "label 'statC' is the symbol of statcoulomb", "label 'G' is the symbol of gauss",
                "label 'gal_UK' is the symbol of gallon UK"), messages);
        f.label(USCustomary.FOOT, "ft");
        f.label(ESU.STATCOULOMB, "Fr");
        assertEquals("Fr", f.format(ESU.STATCOULOMB));
        assertSame(ESU.STATCOULOMB, f.parse("Fr"));
    }

    @Test
    void sharedUnitsFormatByTheirSymbol() {
        assertEquals("cm", format.format(SI.CENTIMETRE));
        assertEquals("cm", format.format(CGS.SYSTEM.getUnit("cm")));
        assertEquals("cm²", format.format(CGS.SQUARE_CENTIMETRE));
        assertEquals("g", format.format(Gaussian.SYSTEM.getUnit("g")));
        assertEquals("Fr", format.format(Gaussian.FRANKLIN));
        assertEquals("statC", format.format(ESU.STATCOULOMB));
        assertEquals("ft", format.format(USCustomary.FOOT));
        assertEquals("gal_UK", format.format(Imperial.GALLON_UK));
        assertEquals("ft", format.format(SI.METRE.multiply(0.3048)));
    }
}
