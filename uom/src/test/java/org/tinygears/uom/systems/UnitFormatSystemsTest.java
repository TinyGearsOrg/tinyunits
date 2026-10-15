// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.measure.BinaryPrefix;
import javax.measure.MetricPrefix;
import javax.measure.Prefix;
import javax.measure.Unit;
import javax.measure.format.MeasurementParseException;
import org.junit.jupiter.api.Test;
import org.tinygears.uom.core.Factor;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.systems.SystemsCatalogTest.Catalog;

/** The standard format over all seven systems (D7). */
class UnitFormatSystemsTest {
    private static final List<Catalog> CATALOGS = SystemsCatalogTest.CATALOGS;
    private static final List<Prefix> ALL_PREFIXES = Stream.<Prefix>concat(Arrays.stream(MetricPrefix.values()),
            Arrays.stream(BinaryPrefix.values())).toList();
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
        for (Catalog c : CATALOGS) {
            for (Unit<?> unit : c.system().getUnits()) {
                assertEquals(unit, format.parse(format.format(unit)), c.name() + ": " + unit);
            }
        }
        Map<String, UomUnit<?>> symbols = symbols();
        symbols.forEach((symbol, unit) -> assertSame(unit, format.parse(symbol), symbol));
        Set<String> expected = new HashSet<>();
        CATALOGS.forEach(c -> expected.addAll(SystemsCatalogTest.members(c.file())));
        assertEquals(expected, symbols.keySet());
    }

    @Test
    void everyPrefixedFormRoundTrips() {
        Map<String, UomUnit<?>> symbols = symbols();
        int exact = 0;
        int prefixed = 0;
        for (Catalog c : CATALOGS) {
            for (String symbol : SystemsCatalogTest.members(c.file())) {
                UomUnit<?> unit = c.system().lookup(symbol).orElseThrow();
                for (Prefix p : SystemsCatalogTest.prefixes(c.system(), symbol)) {
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
        int expected = CATALOGS.stream().mapToInt(c -> SystemsCatalogTest.members(c.file()).stream()
                .mapToInt(s -> SystemsCatalogTest.prefixes(c.system(), s).size()).sum()).sum();
        assertEquals(expected, exact + prefixed);
        assertTrue(exact > 0 && prefixed > 0, exact + " " + prefixed);
    }

    @Test
    void informationUnitsTakeKiloAndUpAndBinaryPrefixes() {
        assertSame(Information.KILOBYTE, parse("kB"));
        assertSame(Information.MEBIBYTE, parse("MiB"));
        assertEquals(Information.BYTE.prefix(MetricPrefix.ZETTA), parse("ZB"));
        assertEquals(Information.BYTE.prefix(BinaryPrefix.YOBI), parse("YiB"));
        assertEquals("yobibyte", parse("YiB").getName());
        assertEquals(Information.BIT.prefix(BinaryPrefix.PEBI), parse("Pibit"));
        assertEquals(Information.BIT.multiply(8), Information.BYTE);
        for (String text : List.of("dB", "hB", "daB", "mB", "µbit", "cbit", "KiKiB", "Kim", "Mis", "GiHz", "kiB")) {
            assertThrows(MeasurementParseException.class, () -> format.parse(text), text);
        }
        assertSame(Information.GIBIBYTE, parse("GiB"));
        assertEquals("KiB", format.format(Information.BYTE.multiply(1024)));
        assertEquals("Zibit", format.format(Information.BIT.prefix(BinaryPrefix.ZEBI)));
        assertEquals("kB", format.format(Information.BIT.multiply(8000)));
        assertEquals("B", format.format(Information.BIT.multiply(8)));
        // A tenth of a byte has no prefixed text: it is not the decibel.
        assertEquals("4|5·bit", format.format(Information.BYTE.divide(10)));
        assertEquals("MiB/s", format.format(Information.MEBIBYTE.divide(SI.SECOND)));
        assertEquals(Information.MEBIBYTE.divide(SI.SECOND), parse("MiB/s"));
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
            Set<String> prefixable = SystemsCatalogTest.prefixable(c.file());
            Set<String> binary = SystemsCatalogTest.binaryPrefixable(c.file());
            for (String symbol : SystemsCatalogTest.members(c.file())) {
                if (prefixable.contains(symbol) || binary.contains(symbol)) {
                    continue;
                }
                // A prefix before a symbol with a superscript reads as a power of the prefixed rest (km² is (km)^2).
                if (symbol.matches(".*[\u2070\u00B9\u00B2\u00B3\u2074-\u2079]$")) {
                    continue;
                }
                for (Prefix p : ALL_PREFIXES) {
                    String text = p.getSymbol() + symbol;
                    if (!symbols.containsKey(text)) {
                        assertThrows(MeasurementParseException.class, () -> format.parse(text), text);
                        refused++;
                    }
                }
            }
        }
        assertTrue(refused > 0);
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
        texts.addAll(SystemsCatalogTest.members("si.units"));
        for (String symbol : SystemsCatalogTest.prefixable("si.units")) {
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

    /** Among equal units without a symbol, the first system in {@link UnitSystems#all()} order names it. */
    @Test
    void firstSystemWinsAmongEqualUnits() {
        UomUnit<?> nanohenry = SI.HENRY.divide(1_000_000_000);
        UomUnit<?> cubicCentimetre = SI.METRE.pow(3).divide(1_000_000);
        UomUnit<?> statcoulomb = SI.COULOMB.scaledBy(Factor.of(1L, 2997924580L));
        assertEquals(EMU.ABHENRY, nanohenry);
        assertEquals(CGS.CUBIC_CENTIMETRE, cubicCentimetre);
        assertEquals(Gaussian.FRANKLIN, statcoulomb);
        for (UomUnit<?> u : List.of(nanohenry, cubicCentimetre, statcoulomb)) {
            assertNull(u.getSymbol());
        }
        assertEquals("nH", format.format(nanohenry));
        assertEquals("mL", format.format(cubicCentimetre));
        assertEquals("statC", format.format(statcoulomb));
    }
}
