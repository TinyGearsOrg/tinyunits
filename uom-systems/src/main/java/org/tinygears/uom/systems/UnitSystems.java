// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.systems;

import java.util.List;

/** The systems of units this library provides, the default one first. */
public final class UnitSystems {

    private UnitSystems() {
    }

    public static List<UomSystemOfUnits> all() {
        return List.of(SI.SYSTEM);
    }
}
