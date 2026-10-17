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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.spi.QuantityFactory;
import javax.measure.spi.SystemOfUnits;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.core.UomConverter;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.core.quantity.AbstractDecimalQuantity;
import org.tinygears.uom.core.quantity.AbstractDoubleQuantity;
import org.tinygears.uom.engine.Factor;
import org.tinygears.uom.quantity.Area;
import org.tinygears.uom.quantity.ElectricCharge;
import org.tinygears.uom.quantity.ElectricCurrent;
import org.tinygears.uom.quantity.ElectricPotential;
import org.tinygears.uom.quantity.Energy;
import org.tinygears.uom.quantity.Length;
import org.tinygears.uom.quantity.MagneticFlux;
import org.tinygears.uom.quantity.MagneticFluxDensity;
import org.tinygears.uom.quantity.Power;
import org.tinygears.uom.quantity.Pressure;
import org.tinygears.uom.quantity.QuantityTypes;
import org.tinygears.uom.quantity.Speed;
import org.tinygears.uom.quantity.Temperature;
import org.tinygears.uom.quantity.Volume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Physics checks whose expected values are derived here from first principles, not from catalog units. */
class SystemConversionTest {

    private static final Quantity.Scale ABS = Quantity.Scale.ABSOLUTE;
    private static final MathContext UNL = MathContext.UNLIMITED;

    /** A reduced fraction with a positive denominator. */
    record Rat(BigInteger n, BigInteger d) {
        Rat {
            BigInteger g = n.gcd(d);
            if (d.signum() < 0) {
                g = g.negate();
            }
            n = n.divide(g);
            d = d.divide(g);
        }

        static Rat of(long n, long d) {
            return new Rat(BigInteger.valueOf(n), BigInteger.valueOf(d));
        }

        static Rat of(String decimal) {
            BigDecimal v = new BigDecimal(decimal);
            return v.scale() >= 0
                    ? new Rat(v.unscaledValue(), BigInteger.TEN.pow(v.scale()))
                    : new Rat(v.unscaledValue().multiply(BigInteger.TEN.pow(-v.scale())), BigInteger.ONE);
        }

        Rat mul(Rat o) {
            return new Rat(n.multiply(o.n), d.multiply(o.d));
        }

        Rat div(Rat o) {
            return new Rat(n.multiply(o.d), d.multiply(o.n));
        }

        Rat add(Rat o) {
            return new Rat(n.multiply(o.d).add(o.n.multiply(d)), d.multiply(o.d));
        }

        Rat sub(Rat o) {
            return add(new Rat(o.n.negate(), o.d));
        }

        Rat pow(int k) {
            return new Rat(n.pow(k), d.pow(k));
        }

        BigDecimal decimal() {
            return new BigDecimal(n).divide(new BigDecimal(d), MathContext.DECIMAL128);
        }

        boolean terminates() {
            BigInteger r = d;
            for (int p : new int[] { 2, 5 }) {
                while (r.mod(BigInteger.valueOf(p)).signum() == 0) {
                    r = r.divide(BigInteger.valueOf(p));
                }
            }
            return r.equals(BigInteger.ONE);
        }
    }

    private static final Rat INCH = Rat.of("0.0254");
    private static final Rat FOOT = INCH.mul(Rat.of(12, 1));
    private static final Rat FOOT_US = Rat.of(1200, 3937);
    private static final Rat LB = Rat.of("0.45359237");
    private static final Rat G0 = Rat.of("9.80665");
    private static final Rat LBF = LB.mul(G0);
    /** Speed of light in cm/s. */
    private static final Rat C_CM = Rat.of(29979245800L, 1);

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Rat ratio(Unit<?> from, Unit<?> to) {
        Factor f = ((UomUnit) from).getConverterTo((Unit) to).factor();
        assertTrue(f.isExact() && f.piExponent() == 0, from + " to " + to);
        return new Rat(f.numerator(), f.denominator());
    }

    private static Quantity<?> to(Quantity<?> q, SystemOfUnits system) {
        return q instanceof AbstractDoubleQuantity<?, ?> d
                ? (Quantity<?>) d.toSystem(system)
                : (Quantity<?>) ((AbstractDecimalQuantity<?, ?>) q).toSystem(system);
    }

    private static void assertValue(String expected, Quantity<?> q) {
        assertEquals(0, new BigDecimal(expected).compareTo((BigDecimal) q.getValue()), q.toString());
    }

    private static void assertRatValue(Rat expected, Quantity<?> q) {
        assertEquals(expected.decimal(), q.getValue(), q.toString());
    }

    // (a) energy and electromagnetic identities

    @Test
    void ergFromChargeTimesPotential() {
        assertEquals(
                Rat.of(1, 1),
                ratio(ESU.STATCOULOMB.multiply(ESU.STATVOLT).asType(javax.measure.quantity.Energy.class), CGS.ERG));
        assertEquals(
                Rat.of(1, 1),
                ratio(EMU.ABCOULOMB.multiply(EMU.ABVOLT).asType(javax.measure.quantity.Energy.class), CGS.ERG));
        Energy e = (Energy) ElectricCharge.of(BigDecimal.ONE, ESU.STATCOULOMB)
                .multiply(ElectricPotential.of(BigDecimal.ONE, ESU.STATVOLT))
                .asType(javax.measure.quantity.Energy.class);
        assertValue("1", e.to(CGS.ERG));
        Energy f = (Energy) ElectricCharge.of(BigDecimal.ONE, EMU.ABCOULOMB)
                .multiply(ElectricPotential.of(BigDecimal.ONE, EMU.ABVOLT)).asType(javax.measure.quantity.Energy.class);
        assertValue("1", f.to(CGS.ERG));
        // double path
        Energy d = (Energy) ElectricCharge.of(1.0, EMU.ABCOULOMB).multiply(ElectricPotential.of(1.0, EMU.ABVOLT))
                .asType(javax.measure.quantity.Energy.class);
        assertEquals(1.0, d.to(CGS.ERG).getValue().doubleValue(), 1e-12);
    }

    @Test
    void gaussTimesSquareCentimetreIsMaxwell() {
        assertEquals(
                Rat.of(1, 1),
                ratio(
                        EMU.GAUSS.multiply(CGS.SQUARE_CENTIMETRE).asType(javax.measure.quantity.MagneticFlux.class),
                        EMU.MAXWELL));
        MagneticFlux m = (MagneticFlux) MagneticFluxDensity.of(BigDecimal.ONE, EMU.GAUSS)
                .multiply(Area.of(BigDecimal.ONE, CGS.SQUARE_CENTIMETRE))
                .asType(javax.measure.quantity.MagneticFlux.class);
        assertValue("1", m.to(EMU.MAXWELL));
        // 1e-4 T x 1e-4 m2 = 1e-8 Wb
        assertEquals(Rat.of("1e-8"), ratio(EMU.MAXWELL, SI.WEBER));
    }

    @Test
    void abampereOverStatampereIsSpeedOfLight() {
        assertEquals(C_CM, ratio(EMU.ABAMPERE, ESU.STATAMPERE));
        assertEquals(C_CM, ratio(EMU.ABCOULOMB, ESU.STATCOULOMB));
    }

    @Test
    void statampereTimesStatvoltIsErgPerSecond() {
        UomUnit<?> p = ESU.STATAMPERE.multiply(ESU.STATVOLT);
        assertEquals(Rat.of("1e-7"), ratio(p.asType(javax.measure.quantity.Power.class), SI.WATT));
        Power w = (Power) ElectricCurrent.of(BigDecimal.ONE, ESU.STATAMPERE)
                .multiply(ElectricPotential.of(BigDecimal.ONE, ESU.STATVOLT))
                .asType(javax.measure.quantity.Power.class);
        assertValue("1e-7", w.to(SI.WATT));
    }

    // (a) length, area, volume

    @Test
    void usSurveyMile() {
        Rat mileUs = Rat.of(5280, 1).mul(FOOT_US);
        assertEquals(Rat.of(6336000, 3937), mileUs);
        assertEquals(FOOT_US, ratio(USCustomary.FOOT_US, SI.METRE));
        assertEquals(mileUs, ratio(USCustomary.MILE_US, SI.METRE));
        Length q = Length.of(BigDecimal.ONE, USCustomary.MILE_US, ABS, UNL).to(SI.METRE);
        assertRatValue(mileUs, q);
        Rat mile = Rat.of(5280, 1).mul(FOOT);
        assertEquals(mile, ratio(USCustomary.MILE, SI.METRE));
        // 6336000/3937 - 1609344/1000 = 12672/3937000 m, about 3.2 mm
        Rat diff = mileUs.sub(mile);
        assertEquals(Rat.of(12672, 3937000), diff);
        assertEquals(0.003218, diff.decimal().doubleValue(), 1e-6);
    }

    @Test
    void usSurveyAcreDiffersFromInternational() {
        Rat acre = Rat.of(43560, 1).mul(FOOT.pow(2));
        Rat acreUs = Rat.of(43560, 1).mul(FOOT_US.pow(2));
        assertEquals(Rat.of("4046.8564224"), acre);
        assertEquals(acre, ratio(USCustomary.ACRE, SI.SQUARE_METRE));
        assertEquals(acreUs, ratio(USCustomary.ACRE_US, SI.SQUARE_METRE));
        assertTrue(!acre.equals(acreUs));
        // 1 ac_US is about 4046.8726 m2, about 0.0162 m2 more
        assertEquals(4046.8726, acreUs.decimal().doubleValue(), 1e-4);
    }

    @Test
    void gallons() {
        assertRatValue(Rat.of("3.785411784"), Volume.of(BigDecimal.ONE, USCustomary.GALLON_US, ABS, UNL).to(SI.LITRE));
        assertRatValue(Rat.of("4.54609"), Volume.of(BigDecimal.ONE, Imperial.GALLON_UK, ABS, UNL).to(SI.LITRE));
        // 231 in3 in litres: 231 x 0.0254^3 x 1000
        assertEquals(Rat.of("3.785411784"), Rat.of(231, 1).mul(INCH.pow(3)).mul(Rat.of(1000, 1)));
    }

    // (a) energy, power, pressure, speed

    @Test
    void horsepowerAndBtu() {
        // 550 ft lbf/s
        Rat hp = Rat.of(550, 1).mul(FOOT).mul(LBF);
        assertEquals(Rat.of("745.69987158227022"), hp);
        assertValue("745.69987158227022", Power.of(BigDecimal.ONE, USCustomary.HORSEPOWER, ABS, UNL).to(SI.WATT));
        // 75 kgf m/s
        assertEquals(Rat.of("735.49875"), Rat.of(75, 1).mul(G0));
        assertValue("735.49875", Power.of(BigDecimal.ONE, USCustomary.METRIC_HORSEPOWER, ABS, UNL).to(SI.WATT));
        assertValue(
                "1055.05585262",
                Energy.of(BigDecimal.ONE, USCustomary.BRITISH_THERMAL_UNIT_IT, ABS, UNL).to(SI.JOULE));
    }

    @Test
    void psiAndKnot() {
        Rat psi = LBF.div(INCH.pow(2));
        assertEquals(psi, ratio(USCustomary.POUND_FORCE_PER_SQUARE_INCH, SI.PASCAL));
        Pressure pa = Pressure.of(BigDecimal.ONE, USCustomary.POUND_FORCE_PER_SQUARE_INCH, ABS, UNL).to(SI.PASCAL);
        assertRatValue(psi, pa);
        assertEquals(new BigDecimal("6894.757293168361336722673445346891"), pa.getValue());
        assertEquals(
                6894.757293168,
                Pressure.of(1.0, USCustomary.POUND_FORCE_PER_SQUARE_INCH).to(SI.PASCAL).getValue().doubleValue(),
                1e-9);
        // 1852 m per 3600 s
        Speed v = Speed.of(BigDecimal.ONE, USCustomary.KNOT, ABS, UNL)
                .to(SI.SYSTEM.getUnit(javax.measure.quantity.Speed.class));
        assertRatValue(Rat.of(463, 900), v);
    }

    // (a) temperature

    @Test
    void fahrenheitAndRankine() {
        Rat zeroF = Rat.of("459.67").mul(Rat.of(5, 9));
        UomConverter c = USCustomary.DEGREE_FAHRENHEIT.getConverterTo(SI.KELVIN);
        assertEquals(new Rat(c.offset().numerator(), c.offset().denominator()), zeroF);
        assertEquals(Rat.of(5, 9), ratio(USCustomary.DEGREE_FAHRENHEIT, SI.KELVIN));
        Temperature k = Temperature.of(BigDecimal.ZERO, USCustomary.DEGREE_FAHRENHEIT, ABS, UNL).to(SI.KELVIN);
        assertEquals(new BigDecimal("2298.35").divide(new BigDecimal(9), MathContext.DECIMAL128), k.getValue());
        assertEquals(
                255.37222222222222,
                Temperature.of(0.0, USCustomary.DEGREE_FAHRENHEIT).to(SI.KELVIN).getValue().doubleValue(),
                1e-12);
        assertValue(
                "0",
                Temperature.of(new BigDecimal("-459.67"), USCustomary.DEGREE_FAHRENHEIT, ABS, UNL).to(SI.KELVIN));
        assertValue("0", Temperature.of(BigDecimal.ZERO, USCustomary.DEGREE_RANKINE, ABS, UNL).to(SI.KELVIN));
        assertValue(
                "273.15",
                Temperature.of(new BigDecimal("491.67"), USCustomary.DEGREE_RANKINE, ABS, UNL).to(SI.KELVIN));
        assertValue(
                "100",
                Temperature.of(new BigDecimal("212"), USCustomary.DEGREE_FAHRENHEIT, ABS, UNL).to(SI.DEGREE_CELSIUS));
    }

    // (b) round trips

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void everySystemRoundTripsThroughSi() {
        for (UomSystemOfUnits system : UnitSystems.all()) {
            for (Class<?> type : QuantityTypes.types()) {
                QuantityFactory f = QuantityTypes.factory((Class) type);
                UomUnit u = (UomUnit) system.getUnit((Class) type);
                UomUnit s = (UomUnit) SI.SYSTEM.getUnit((Class) type);
                String where = system.getName() + " " + type.getSimpleName() + " " + u;

                Quantity back = to(to(f.create(7.25, u), SI.SYSTEM), system);
                assertSame(u, back.getUnit(), where);
                assertEquals(7.25, back.getValue().doubleValue(), 1e-12 * 7.25, where);

                // intermediate value x f + o in SI; a pi factor (Gb, Oe) has no rational form, so only the
                // tolerance check applies to it
                UomConverter c = u.getConverterTo(s);
                boolean rational = c.factor().isExact() && c.offset().isExact() && c.factor().piExponent() == 0
                        && c.offset().piExponent() == 0;
                Rat mid = !rational
                        ? null
                        : Rat.of("7.25").mul(new Rat(c.factor().numerator(), c.factor().denominator()))
                                .add(new Rat(c.offset().numerator(), c.offset().denominator()));
                Quantity d = to(to(f.create(new BigDecimal("7.25"), u), SI.SYSTEM), system);
                assertSame(u, d.getUnit(), where);
                BigDecimal got = (BigDecimal) d.getValue();
                // exact only when the intermediate fits DECIMAL128's 34 digits
                if (rational && mid.terminates()
                        && new BigDecimal(mid.n()).divide(new BigDecimal(mid.d())).precision() <= 34) {
                    assertEquals(0, new BigDecimal("7.25").compareTo(got), where + ": " + got);
                } else {
                    // two DECIMAL128 roundings give at most about 1e-33 relative (observed max 1e-33)
                    BigDecimal err = got.subtract(new BigDecimal("7.25")).abs();
                    assertTrue(err.compareTo(new BigDecimal("7.25e-32")) < 0, where + ": " + got);
                }
            }
        }
    }

    // (c) double and decimal agree

    private int cases;

    private <Q extends Quantity<Q>> void parity(Class<Q> type, double v, Unit<Q> src, SystemOfUnits system) {
        QuantityFactory<Q> f = QuantityTypes.factory(type);
        double d = to(f.create(v, src), system).getValue().doubleValue();
        BigDecimal b = (BigDecimal) to(f.create(BigDecimal.valueOf(v), src), system).getValue();
        String where = v + " " + src + " to " + system.getName();
        assertEquals(b.doubleValue(), d, 1e-12 * Math.abs(b.doubleValue()), where);
        cases++;
    }

    @Test
    void doubleAndDecimalAgree() {
        parity(javax.measure.quantity.Length.class, 3.5, USCustomary.FOOT, SI.SYSTEM);
        parity(javax.measure.quantity.Length.class, 2.25, USCustomary.MILE_US, SI.SYSTEM);
        parity(javax.measure.quantity.Length.class, 2.25, USCustomary.MILE, SI.SYSTEM);
        parity(javax.measure.quantity.Length.class, 7.0, USCustomary.FOOT_US, CGS.SYSTEM);
        parity(javax.measure.quantity.Length.class, 1234.5, SI.METRE, USCustomary.SYSTEM);
        parity(javax.measure.quantity.Length.class, 80.0, SI.CENTIMETRE, Imperial.SYSTEM);
        parity(javax.measure.quantity.Mass.class, 12.5, USCustomary.POUND, SI.SYSTEM);
        parity(javax.measure.quantity.Mass.class, 0.75, SI.KILOGRAM, Imperial.SYSTEM);
        parity(javax.measure.quantity.Volume.class, 2.5, USCustomary.GALLON_US, SI.SYSTEM);
        parity(javax.measure.quantity.Volume.class, 1.5, Imperial.GALLON_UK, USCustomary.SYSTEM);
        parity(javax.measure.quantity.Volume.class, 10.0, SI.LITRE, Imperial.SYSTEM);
        parity(javax.measure.quantity.Volume.class, 3.0, SI.LITRE, CGS.SYSTEM);
        parity(javax.measure.quantity.Area.class, 2.0, USCustomary.ACRE, SI.SYSTEM);
        parity(javax.measure.quantity.Area.class, 2.0, USCustomary.ACRE_US, SI.SYSTEM);
        parity(javax.measure.quantity.Area.class, 100.0, SI.SQUARE_METRE, USCustomary.SYSTEM);
        parity(javax.measure.quantity.Area.class, 12.0, USCustomary.SQUARE_FOOT, CGS.SYSTEM);
        parity(javax.measure.quantity.Energy.class, 5.0, USCustomary.BRITISH_THERMAL_UNIT_IT, SI.SYSTEM);
        parity(javax.measure.quantity.Energy.class, 1000.0, SI.JOULE, USCustomary.SYSTEM);
        parity(javax.measure.quantity.Energy.class, 1e7, CGS.ERG, SI.SYSTEM);
        parity(javax.measure.quantity.Energy.class, 1.0, SI.JOULE, CGS.SYSTEM);
        parity(javax.measure.quantity.Power.class, 2.0, USCustomary.HORSEPOWER, SI.SYSTEM);
        parity(javax.measure.quantity.Power.class, 3.0, USCustomary.METRIC_HORSEPOWER, USCustomary.SYSTEM);
        parity(javax.measure.quantity.Power.class, 1000.0, SI.WATT, USCustomary.SYSTEM);
        parity(javax.measure.quantity.Pressure.class, 14.7, USCustomary.POUND_FORCE_PER_SQUARE_INCH, SI.SYSTEM);
        parity(javax.measure.quantity.Pressure.class, 101325.0, SI.PASCAL, USCustomary.SYSTEM);
        parity(javax.measure.quantity.Pressure.class, 1000.0, CGS.BARYE, SI.SYSTEM);
        parity(javax.measure.quantity.Force.class, 10.0, USCustomary.POUND_FORCE, SI.SYSTEM);
        parity(javax.measure.quantity.Force.class, 10.0, SI.NEWTON, CGS.SYSTEM);
        parity(javax.measure.quantity.Force.class, 1e5, CGS.DYNE, SI.SYSTEM);
        parity(javax.measure.quantity.Speed.class, 10.0, USCustomary.KNOT, SI.SYSTEM);
        parity(javax.measure.quantity.Speed.class, 60.0, USCustomary.MILE_PER_HOUR, SI.SYSTEM);
        parity(javax.measure.quantity.Temperature.class, 98.6, USCustomary.DEGREE_FAHRENHEIT, SI.SYSTEM);
        parity(javax.measure.quantity.Temperature.class, 37.0, SI.DEGREE_CELSIUS, USCustomary.SYSTEM);
        parity(javax.measure.quantity.Temperature.class, 500.0, USCustomary.DEGREE_RANKINE, SI.SYSTEM);
        parity(javax.measure.quantity.Temperature.class, 300.0, SI.KELVIN, USCustomary.SYSTEM);
        parity(javax.measure.quantity.ElectricCharge.class, 2.0, ESU.STATCOULOMB, SI.SYSTEM);
        parity(javax.measure.quantity.ElectricCharge.class, 1.5, EMU.ABCOULOMB, ESU.SYSTEM);
        parity(javax.measure.quantity.ElectricCharge.class, 1.0, SI.COULOMB, Gaussian.SYSTEM);
        parity(javax.measure.quantity.ElectricCurrent.class, 3.0, EMU.ABAMPERE, SI.SYSTEM);
        parity(javax.measure.quantity.ElectricPotential.class, 5.0, ESU.STATVOLT, SI.SYSTEM);
        parity(javax.measure.quantity.ElectricPotential.class, 1e8, EMU.ABVOLT, SI.SYSTEM);
        parity(javax.measure.quantity.MagneticFluxDensity.class, 1e4, EMU.GAUSS, SI.SYSTEM);
        parity(javax.measure.quantity.MagneticFluxDensity.class, 1.0, SI.TESLA, Gaussian.SYSTEM);
        parity(javax.measure.quantity.MagneticFlux.class, 1e8, EMU.MAXWELL, SI.SYSTEM);
        parity(javax.measure.quantity.MagneticFlux.class, 1.0, SI.WEBER, Gaussian.SYSTEM);
        parity(javax.measure.quantity.ElectricCurrent.class, 1.0, SI.AMPERE, EMU.SYSTEM);
        assertTrue(cases >= 30, "cases: " + cases);
    }

    // (d) to(system) chain

    @Test
    void cgsEsuGaussianChain() {
        BigDecimal one = BigDecimal.ONE;
        ElectricCharge abc = ElectricCharge.of(one, EMU.ABCOULOMB, ABS, UNL);
        ElectricCharge viaCgs = abc.toSystem(CGS.SYSTEM);
        assertValue("10", viaCgs);
        ElectricCharge viaEsu = viaCgs.toSystem(ESU.SYSTEM);
        assertSame(ESU.STATCOULOMB, viaEsu.getUnit());
        assertValue("29979245800", viaEsu);
        ElectricCharge viaGauss = viaEsu.toSystem(Gaussian.SYSTEM);
        assertSame(Gaussian.FRANKLIN, viaGauss.getUnit());
        assertValue("29979245800", viaGauss);
        assertValue("29979245800", abc.toSystem(ESU.SYSTEM));
        assertValue("2997924580", ElectricCharge.of(one, SI.COULOMB, ABS, UNL).toSystem(Gaussian.SYSTEM));
        MagneticFluxDensity g = MagneticFluxDensity.of(one, SI.TESLA, ABS, UNL).toSystem(Gaussian.SYSTEM);
        assertSame(EMU.GAUSS, g.getUnit());
        assertValue("10000", g);
        MagneticFlux mx = MagneticFlux.of(one, SI.WEBER, ABS, UNL).toSystem(Gaussian.SYSTEM);
        assertSame(EMU.MAXWELL, mx.getUnit());
        assertValue("100000000", mx);
        ElectricCurrent ab = ElectricCurrent.of(one, SI.AMPERE, ABS, UNL).toSystem(EMU.SYSTEM);
        assertSame(EMU.ABAMPERE, ab.getUnit());
        assertValue("0.1", ab);
    }
}
