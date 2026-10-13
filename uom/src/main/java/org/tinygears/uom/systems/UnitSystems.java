// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.systems;

import java.util.List;

/**
 * The systems of units this library provides, SI (the default) first. Symbols are unique across systems, so the
 * order does not affect exact symbol lookup; it decides which symbol a format picks among equal units.
 */
public final class UnitSystems {

    private UnitSystems() {
    }

    /**
     * The systems: SI, US customary, Imperial, CGS, ESU, EMU and Gaussian.
     *
     * @return the systems
     */
    public static List<UomSystemOfUnits> all() {
        return List.of(SI.SYSTEM, USCustomary.SYSTEM, Imperial.SYSTEM, CGS.SYSTEM, ESU.SYSTEM, EMU.SYSTEM,
                Gaussian.SYSTEM);
    }
}
