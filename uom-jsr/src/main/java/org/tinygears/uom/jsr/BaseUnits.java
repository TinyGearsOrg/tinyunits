/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.jsr;

import javax.measure.quantity.AmountOfSubstance;
import javax.measure.quantity.Dimensionless;
import javax.measure.quantity.ElectricCurrent;
import javax.measure.quantity.Length;
import javax.measure.quantity.LuminousIntensity;
import javax.measure.quantity.Mass;
import javax.measure.quantity.Temperature;
import javax.measure.quantity.Time;

import org.tinygears.uom.engine.UnitAtom;

/** The seven SI base units and the dimensionless unit one. */
public final class BaseUnits {

    /** The metre, {@code m}. */
    public static final UomUnit<Length> METRE = base(UnitAtom.METRE, "metre");
    /** The kilogram, {@code kg}. */
    public static final UomUnit<Mass> KILOGRAM = base(UnitAtom.KILOGRAM, "kilogram");
    /** The second, {@code s}. */
    public static final UomUnit<Time> SECOND = base(UnitAtom.SECOND, "second");
    /** The ampere, {@code A}. */
    public static final UomUnit<ElectricCurrent> AMPERE = base(UnitAtom.AMPERE, "ampere");
    /** The kelvin, {@code K}. */
    public static final UomUnit<Temperature> KELVIN = base(UnitAtom.KELVIN, "kelvin");
    /** The mole, {@code mol}. */
    public static final UomUnit<AmountOfSubstance> MOLE = base(UnitAtom.MOLE, "mole");
    /** The candela, {@code cd}. */
    public static final UomUnit<LuminousIntensity> CANDELA = base(UnitAtom.CANDELA, "candela");
    /** The dimensionless unit one, {@code 1}. */
    public static final UomUnit<Dimensionless> ONE = UomUnit.of(org.tinygears.uom.engine.Unit.ONE);

    private BaseUnits() {
    }

    private static <Q extends javax.measure.Quantity<Q>> UomUnit<Q> base(UnitAtom atom, String name) {
        return UomUnit.of(org.tinygears.uom.engine.Unit.base(atom, name));
    }
}
