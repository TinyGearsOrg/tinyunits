/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.systems;

import java.util.Set;
import javax.measure.spi.FormatService;
import javax.measure.spi.FormatService.FormatType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FormatServiceTest {
    private final FormatService service = new UomFormatService();

    @Test
    void names() {
        assertEquals(Set.of("default"), service.getAvailableFormatNames(FormatType.UNIT_FORMAT));
        assertEquals(Set.of("default"), service.getAvailableFormatNames(FormatType.QUANTITY_FORMAT));
        assertThrows(NullPointerException.class, () -> service.getAvailableFormatNames(null));
    }

    @Test
    void defaults() {
        assertInstanceOf(UomUnitFormat.class, service.getUnitFormat());
        assertInstanceOf(UomQuantityFormat.class, service.getQuantityFormat());
        assertSame(service.getUnitFormat(), service.getUnitFormat("default"));
        assertSame(service.getQuantityFormat(), service.getQuantityFormat("default"));
        assertSame(service.getUnitFormat(), new UomFormatService().getUnitFormat());
        assertEquals(SI.NEWTON, service.getUnitFormat().parse("N"));
        assertEquals(SI.NEWTON, service.getQuantityFormat().parse("1 N").getUnit());
    }

    @Test
    void unknownNamesGiveNull() {
        assertNull(service.getUnitFormat("Simple"));
        assertNull(service.getUnitFormat(null));
        assertNull(service.getQuantityFormat("Simple"));
        assertNull(service.getQuantityFormat(null));
        assertNull(service.getUnitFormat("Simple", ""));
    }

    @Test
    void variants() {
        assertSame(service.getUnitFormat(), service.getUnitFormat("default", null));
        assertSame(service.getUnitFormat(), service.getUnitFormat("default", ""));
        assertSame(service.getUnitFormat(), service.getUnitFormat("default", "ASCII"));
        assertNull(service.getUnitFormat("Simple", ""));
        assertNull(service.getUnitFormat("Simple", null));
        assertNull(service.getUnitFormat("Simple", "ASCII"));
    }
}
