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
        assertNull(service.getSystemOfUnits("Imperial"));
        assertNull(service.getSystemOfUnits("si"));
        assertNull(service.getSystemOfUnits(null));
    }

    @Test
    void available() {
        assertEquals(List.of(SI.SYSTEM), List.copyOf(service.getAvailableSystemsOfUnits()));
        assertEquals(List.of(SI.SYSTEM), UnitSystems.all());
    }

    @Test
    void prefixes() {
        assertEquals(24, service.getPrefixes(MetricPrefix.class).size());
        assertEquals(8, service.getPrefixes(BinaryPrefix.class).size());
    }
}
