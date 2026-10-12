// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.measure.MetricPrefix;
import javax.measure.Unit;
import javax.measure.format.MeasurementParseException;
import org.junit.jupiter.api.Test;
import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.Factor;
import org.tinygears.uom.core.UomUnit;

class UnitFormatTest {
    private final UomUnitFormat format = UomUnitFormat.standard();

    private UomUnit<?> parse(String text) {
        return (UomUnit<?>) format.parse(text);
    }

    @Test
    void everyCatalogUnitRoundTrips() {
        assertEquals(111, SI.UNITS.size());
        for (UomUnit<?> unit : SI.UNITS) {
            assertEquals(unit, format.parse(unit.toString()), unit.toString());
            assertEquals(unit, format.parse(format.format(unit)), unit.toString());
        }
        for (UomUnit<?> unit : List.of(SI.SQUARE_METRE, SI.CUBIC_METRE, SI.OHM, SI.DEGREE_CELSIUS, SI.MICROMETRE,
                SI.KILOOHM, SI.MEGAELECTRONVOLT)) {
            assertEquals(unit, format.parse(unit.getSymbol()), unit.getSymbol());
            assertEquals(unit.getSymbol(), format.format(unit));
        }
    }

    private void assertPrefixed(String text, UomUnit<?> unit, MetricPrefix prefix, String factor, String name) {
        UomUnit<?> parsed = parse(text);
        assertEquals(unit.prefix(prefix), parsed, text);
        assertEquals(unit.atoms(), parsed.atoms(), text);
        assertEquals(unit.toSystemConverter().factor().multiply(Factor.decimal(factor)),
                parsed.toSystemConverter().factor(), text);
        assertEquals(text, parsed.getSymbol());
        assertEquals(name, parsed.getName());
    }

    @Test
    void prefixResolution() {
        assertPrefixed("Gm", SI.METRE, MetricPrefix.GIGA, "1e9", "gigametre");
        assertPrefixed("fs", SI.SECOND, MetricPrefix.FEMTO, "1e-15", "femtosecond");
        assertPrefixed("dam", SI.METRE, MetricPrefix.DECA, "10", "decametre");
        assertPrefixed("ZHz", SI.HERTZ, MetricPrefix.ZETTA, "1e21", "zettahertz");
        assertPrefixed("µmol", SI.MOLE, MetricPrefix.MICRO, "1e-6", "micromole");
        assertPrefixed("Mg", SI.GRAM, MetricPrefix.MEGA, "1e6", "megagram");
        assertPrefixed("qΩ", SI.OHM, MetricPrefix.QUECTO, "1e-30", "quectoohm");
        assertSame(SI.HECTOPASCAL, parse("hPa"));
        assertSame(SI.MILLIMOLE, parse("mmol"));
        assertSame(SI.KILOOHM, parse("kΩ"));
        assertSame(SI.PICOMETRE, parse("pm"));
        assertEquals(SI.PASCAL.prefix(MetricPrefix.HECTO), parse("hPa"));
        assertEquals(SI.MOLE.prefix(MetricPrefix.MILLI), parse("mmol"));
        assertEquals(SI.OHM.prefix(MetricPrefix.KILO), parse("kΩ"));
    }

    @Test
    void exactMatchesWin() {
        assertSame(SI.MINUTE, parse("min"));
        assertSame(SI.CANDELA, parse("cd"));
        assertSame(SI.TESLA, parse("T"));
        assertSame(SI.TONNE, parse("t"));
        assertSame(SI.HOUR, parse("h"));
        assertSame(SI.HECTARE, parse("ha"));
        assertSame(SI.DAY, parse("d"));
        assertSame(SI.PASCAL, parse("Pa"));
        assertSame(SI.MILLIMETRE, parse("mm"));
    }

    @Test
    void expressions() {
        assertEquals(SI.METRE.divide(SI.SECOND), parse("m/s"));
        assertEquals(SI.NEWTON, parse("kg·m/s^2"));
        assertEquals(SI.NEWTON, parse("kg m / s^2"));
        assertEquals(SI.METRE.divide(SI.SECOND).pow(2), parse("(m/s)^2"));
        assertEquals(SI.KILOMETRE.divide(SI.HOUR), parse("km/h"));
        assertEquals(BaseUnits.ONE.divide(SI.SECOND), parse("1/s"));
        assertEquals(SI.METRE.pow(2).multiply(SI.KILOGRAM), parse("m^2·kg"));
        assertEquals(SI.NEWTON, parse("  N \t"));
    }

    @Test
    void derivedLabelsParseBack() {
        for (UomUnit<?> unit : List.of(BaseUnits.METRE.divide(BaseUnits.SECOND).pow(2),
                BaseUnits.KILOGRAM.divide(BaseUnits.METRE.multiply(BaseUnits.SECOND)),
                BaseUnits.METRE.pow(2).multiply(BaseUnits.KILOGRAM))) {
            assertEquals(unit, parse(unit.getSymbol()), unit.getSymbol());
        }
    }

    private void assertFails(String text, int position) {
        MeasurementParseException e = assertThrows(MeasurementParseException.class, () -> format.parse(text), text);
        assertEquals(position, e.getPosition(), text);
        assertEquals(text, e.getParsedString());
        assertTrue(e.getMessage() != null && !e.getMessage().isEmpty(), text);
    }

    @Test
    void errors() {
        assertFails("foo", 0);
        assertFails("m/", 2);
        assertFails("", 0);
        assertFails("   ", 3);
        assertFails("kk m", 0);
        assertFails("kmin", 0);
        assertFails("ºC", 0);
        assertFails("m/foo", 2);
        assertFails("  m*x", 4);
        assertFails("m)", 1);
        assertThrows(NullPointerException.class, () -> format.parse(null));
    }

    @Test
    void parsePosition() {
        ParsePosition pos = new ParsePosition(4);
        assertEquals(SI.METRE.divide(SI.SECOND), format.parse("abc m/s", pos));
        assertEquals(7, pos.getIndex());
        assertEquals(-1, pos.getErrorIndex());

        ParsePosition bad = new ParsePosition(2);
        MeasurementParseException e = assertThrows(MeasurementParseException.class, () -> format.parse("x m/q", bad));
        assertEquals(4, e.getPosition());
        assertEquals(4, bad.getErrorIndex());
        assertEquals(2, bad.getIndex());

        ParsePosition past = new ParsePosition(9);
        assertThrows(MeasurementParseException.class, () -> format.parse("m", past));
        assertEquals(1, past.getErrorIndex());
    }

    @Test
    void label() {
        UomUnitFormat f = UomUnitFormat.standard();
        UomUnit<?> furlong = SI.METRE.multiply(201.168);
        f.label(furlong, "fur");
        assertEquals(furlong, f.parse("fur"));
        assertEquals("fur", f.format(furlong));
        assertEquals(furlong.divide(SI.SECOND), f.parse("fur/s"));
        f.label(furlong, "furlong");
        assertEquals("furlong", f.format(furlong));
        f.label(SI.METRE, "m");
        assertEquals("m", f.format(SI.METRE));
        f.label(SI.KILOMETRE, "klick");
        assertEquals("klick", f.format(SI.KILOMETRE));
        assertEquals("km", format.format(SI.KILOMETRE));
        for (String bad : List.of("", " ", "a b", "a*b", "a/b", "a(b", "a)", "a^2", "a|b", "a=b", "a#", "[a]", "a!",
                "a~", "a@", "a,b", "a·b", "\tx", "2x", "pi")) {
            assertThrows(IllegalArgumentException.class, () -> f.label(furlong, bad), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> f.label(furlong, "s"));
        assertThrows(NullPointerException.class, () -> f.label(furlong, null));
        assertThrows(NullPointerException.class, () -> f.label(null, "x"));
    }

    @Test
    void relabellingAnAliasMovesIt() {
        UomUnitFormat f = UomUnitFormat.standard();
        UomUnit<?> a = SI.METRE.multiply(3);
        UomUnit<?> b = SI.METRE.multiply(5);
        f.label(a, "x");
        f.label(b, "x");
        assertEquals(b, f.parse("x"));
        assertEquals("x", f.format(b));
        assertFalse(f.format(a).equals("x"));
    }

    @Test
    void formatting() throws IOException {
        assertEquals("km", format.format(SI.KILOMETRE));
        UomUnit<?> scaled = SI.METRE.multiply(3);
        assertNull(scaled.getSymbol());
        assertEquals(scaled.toString(), format.format(scaled));
        StringBuilder sb = new StringBuilder("x=");
        assertSame(sb, format.format(SI.NEWTON, sb));
        assertEquals("x=N", sb.toString());
        assertThrows(NullPointerException.class, () -> format.format((Unit<?>) null));
        assertFalse(format.isLocaleSensitive());
    }

    @Test
    void concurrentParsing() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < 20; i++) {
                        for (UomUnit<?> unit : SI.UNITS) {
                            assertEquals(unit, format.parse(unit.toString()));
                        }
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
