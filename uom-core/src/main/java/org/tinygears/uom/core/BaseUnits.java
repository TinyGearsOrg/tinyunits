// SPDX-License-Identifier: Apache-2.0

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

    public static final UomUnit<Length> METRE = base("m", "metre", UomDimension.LENGTH);
    public static final UomUnit<Mass> KILOGRAM = base("kg", "kilogram", UomDimension.MASS);
    public static final UomUnit<Time> SECOND = base("s", "second", UomDimension.TIME);
    public static final UomUnit<ElectricCurrent> AMPERE = base("A", "ampere", UomDimension.CURRENT);
    public static final UomUnit<Temperature> KELVIN = base("K", "kelvin", UomDimension.TEMPERATURE);
    public static final UomUnit<AmountOfSubstance> MOLE = base("mol", "mole", UomDimension.AMOUNT);
    public static final UomUnit<LuminousIntensity> CANDELA = base("cd", "candela", UomDimension.LUMINOSITY);
    public static final UomUnit<Dimensionless> ONE = UomUnit.of(Map.of(), Converters.IDENTITY, "1", "one");

    private BaseUnits() {
    }

    private static <Q extends javax.measure.Quantity<Q>> UomUnit<Q> base(String symbol, String name, UomDimension d) {
        return UomUnit.of(Map.of(new UnitAtom(symbol, d), 1), Converters.IDENTITY, symbol, name);
    }
}
