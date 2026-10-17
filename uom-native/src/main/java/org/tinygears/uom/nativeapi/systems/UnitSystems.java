/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.nativeapi.systems;

import java.util.List;

import org.tinygears.uom.nativeapi.SystemOfUnits;

/** The catalogs of this library, in the lookup order of {@link org.tinygears.uom.nativeapi.UnitFormat#standard()}. */
public final class UnitSystems {

    private UnitSystems() {
    }

    /**
     * The systems: SI, US customary, imperial, CGS, ESU, EMU, Gaussian and information.
     *
     * @return the systems
     */
    public static List<SystemOfUnits> all() {
        return List.of(
                SI.SYSTEM,
                USCustomary.SYSTEM,
                Imperial.SYSTEM,
                CGS.SYSTEM,
                ESU.SYSTEM,
                EMU.SYSTEM,
                Gaussian.SYSTEM,
                Information.SYSTEM);
    }
}
