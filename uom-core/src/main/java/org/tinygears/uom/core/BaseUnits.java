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

import java.util.Map;
import javax.measure.quantity.AmountOfSubstance;
import javax.measure.quantity.Dimensionless;
import javax.measure.quantity.ElectricCurrent;
import javax.measure.quantity.Length;
import javax.measure.quantity.LuminousIntensity;
import javax.measure.quantity.Mass;
import javax.measure.quantity.Temperature;
import javax.measure.quantity.Time;

/** The seven SI base units and the dimensionless unit one. */
public final class BaseUnits {

    /** The metre, {@code m}. */
    public static final UomUnit<Length> METRE = base("m", "metre", UomDimension.LENGTH);
    /** The kilogram, {@code kg}. */
    public static final UomUnit<Mass> KILOGRAM = base("kg", "kilogram", UomDimension.MASS);
    /** The second, {@code s}. */
    public static final UomUnit<Time> SECOND = base("s", "second", UomDimension.TIME);
    /** The ampere, {@code A}. */
    public static final UomUnit<ElectricCurrent> AMPERE = base("A", "ampere", UomDimension.CURRENT);
    /** The kelvin, {@code K}. */
    public static final UomUnit<Temperature> KELVIN = base("K", "kelvin", UomDimension.TEMPERATURE);
    /** The mole, {@code mol}. */
    public static final UomUnit<AmountOfSubstance> MOLE = base("mol", "mole", UomDimension.AMOUNT);
    /** The candela, {@code cd}. */
    public static final UomUnit<LuminousIntensity> CANDELA = base("cd", "candela", UomDimension.LUMINOSITY);
    /** The dimensionless unit one, {@code 1}. */
    public static final UomUnit<Dimensionless> ONE = UomUnit.of(Map.of(), Converters.IDENTITY, "1", "one");

    private BaseUnits() {
    }

    private static <Q extends javax.measure.Quantity<Q>> UomUnit<Q> base(String symbol, String name, UomDimension d) {
        return UomUnit.of(Map.of(new UnitAtom(symbol, d), 1), Converters.IDENTITY, symbol, name);
    }
}
