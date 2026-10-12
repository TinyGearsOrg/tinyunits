// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.systems;

import java.util.Collection;
import java.util.List;
import javax.measure.spi.SystemOfUnits;
import javax.measure.spi.SystemOfUnitsService;

/** The systems of {@link UnitSystems#all()}; SI is the default. */
public final class UomSystemOfUnitsService implements SystemOfUnitsService {

    @Override
    public SystemOfUnits getSystemOfUnits() {
        return SI.SYSTEM;
    }

    /** The system with exactly this name, else null. */
    @Override
    public SystemOfUnits getSystemOfUnits(String name) {
        for (UomSystemOfUnits system : UnitSystems.all()) {
            if (system.getName().equals(name)) {
                return system;
            }
        }
        return null;
    }

    @Override
    public Collection<SystemOfUnits> getAvailableSystemsOfUnits() {
        return List.copyOf(UnitSystems.all());
    }
}
