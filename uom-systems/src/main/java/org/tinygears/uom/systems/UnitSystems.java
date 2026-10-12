// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.systems;

import java.util.List;

/**
 * The systems of units this library provides, SI (the default) first. Formats resolve exact symbols in this order,
 * before any prefix reading.
 */
public final class UnitSystems {

    private UnitSystems() {
    }

    public static List<UomSystemOfUnits> all() {
        return List.of(SI.SYSTEM, USCustomary.SYSTEM, Imperial.SYSTEM, CGS.SYSTEM, ESU.SYSTEM, EMU.SYSTEM,
                Gaussian.SYSTEM);
    }
}
