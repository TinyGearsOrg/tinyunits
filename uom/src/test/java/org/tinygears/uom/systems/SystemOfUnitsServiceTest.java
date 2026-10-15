// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import javax.measure.BinaryPrefix;
import javax.measure.MetricPrefix;
import javax.measure.spi.SystemOfUnitsService;
import org.junit.jupiter.api.Test;

class SystemOfUnitsServiceTest {
    private final SystemOfUnitsService service = new UomSystemOfUnitsService();

    @Test
    void defaultIsSi() {
        assertSame(SI.SYSTEM, service.getSystemOfUnits());
        assertEquals("SI", service.getSystemOfUnits().getName());
    }

    @Test
    void byName() {
        assertSame(SI.SYSTEM, service.getSystemOfUnits("SI"));
        assertSame(USCustomary.SYSTEM, service.getSystemOfUnits("USCustomary"));
        assertSame(Imperial.SYSTEM, service.getSystemOfUnits("Imperial"));
        assertSame(CGS.SYSTEM, service.getSystemOfUnits("CGS"));
        assertSame(ESU.SYSTEM, service.getSystemOfUnits("ESU"));
        assertSame(EMU.SYSTEM, service.getSystemOfUnits("EMU"));
        assertSame(Gaussian.SYSTEM, service.getSystemOfUnits("Gaussian"));
        assertNull(service.getSystemOfUnits("si"));
        assertNull(service.getSystemOfUnits("imperial"));
        assertNull(service.getSystemOfUnits("gaussian"));
        assertNull(service.getSystemOfUnits("US"));
        assertNull(service.getSystemOfUnits(" CGS"));
        assertNull(service.getSystemOfUnits(""));
        assertNull(service.getSystemOfUnits(null));
    }

    @Test
    void available() {
        List<UomSystemOfUnits> all = List.of(SI.SYSTEM, USCustomary.SYSTEM, Imperial.SYSTEM, CGS.SYSTEM, ESU.SYSTEM,
                EMU.SYSTEM, Gaussian.SYSTEM, Information.SYSTEM);
        assertEquals(all, List.copyOf(service.getAvailableSystemsOfUnits()));
        assertEquals(all, UnitSystems.all());
        assertEquals(List.of("SI", "USCustomary", "Imperial", "CGS", "ESU", "EMU", "Gaussian", "Information"),
                service.getAvailableSystemsOfUnits().stream().map(s -> s.getName()).toList());
    }

    @Test
    void prefixes() {
        assertEquals(24, service.getPrefixes(MetricPrefix.class).size());
        assertEquals(8, service.getPrefixes(BinaryPrefix.class).size());
    }
}
