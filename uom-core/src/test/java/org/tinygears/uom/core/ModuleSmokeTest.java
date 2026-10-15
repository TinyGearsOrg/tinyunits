/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleSmokeTest {

    @Test
    void unitApiIsVersion22() {
        String location = javax.measure.Unit.class.getProtectionDomain().getCodeSource().getLocation().toString();
        assertTrue(location.contains("unit-api-2.2"), location);
    }
}
