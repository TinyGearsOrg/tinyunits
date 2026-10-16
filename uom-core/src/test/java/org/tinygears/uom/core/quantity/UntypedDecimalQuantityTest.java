/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.core.quantity;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import javax.measure.Quantity;
import javax.measure.UnconvertibleException;
import javax.measure.Unit;
import javax.measure.quantity.Length;
import javax.measure.quantity.Speed;
import javax.measure.spi.QuantityFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.TestServiceProvider;
import org.tinygears.uom.core.UomUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UntypedDecimalQuantityTest {

    private static final UomUnit<Length> METRE = BaseUnits.METRE;
    private static final UomUnit<Length> KILOMETRE = METRE.multiply(1000);
    private static final UomUnit<Length> INCH = METRE.multiply(new BigDecimal("0.0254"));
    private static final UomUnit<Length> KELVIN = UomUnit.unchecked(BaseUnits.KELVIN);
    private static final UomUnit<Length> CELSIUS = UomUnit.unchecked(BaseUnits.KELVIN.shift(new BigDecimal("273.15")));
    private static final UomUnit<Length> SECOND = UomUnit.unchecked(BaseUnits.SECOND);
    private static final UomUnit<Length> HOUR = SECOND.multiply(3600);
    private static final MathContext DECIMAL32 = MathContext.DECIMAL32;
    private static final MathContext DECIMAL128 = MathContext.DECIMAL128;

    @BeforeAll
    static void registerFactories() {
        TestServiceProvider.FACTORIES.put(Speed.class, TestSpeedDecimalImpl.FACTORY);
    }

    @AfterAll
    static void removeFactories() {
        TestServiceProvider.FACTORIES.remove(Speed.class);
    }

    private static TestLengthDecimalImpl q(String value, UomUnit<Length> unit) {
        return new TestLengthDecimalImpl(new BigDecimal(value), unit);
    }

    private static TestLengthDecimalImpl q(String value, UomUnit<Length> unit, MathContext mc) {
        return new TestLengthDecimalImpl(new BigDecimal(value), unit, Quantity.Scale.ABSOLUTE, mc);
    }

    /** Asserts an untyped decimal result: numerically equal value, unit and context. */
    private static UntypedDecimalQuantity assertUntyped(
            String value,
            Unit<?> unit,
            MathContext mc,
            Quantity<?> actual
    ) {
        UntypedDecimalQuantity r = assertInstanceOf(UntypedDecimalQuantity.class, actual);
        assertEquals(0, new BigDecimal(value).compareTo(r.getValue()), () -> value + " vs " + r.getValue());
        assertEquals(unit, r.getUnit());
        assertEquals(mc, r.mathContext());
        assertSame(Quantity.Scale.ABSOLUTE, r.getScale());
        return r;
    }

    @Test
    void kilometrePerHourIsSpeed() {
        Quantity<?> r = q("36", KILOMETRE).divide(q("1", HOUR));
        assertUntyped("10", METRE.divide(SECOND), DECIMAL128, r);
        Quantity<Speed> speed = r.asType(Speed.class);
        TestSpeedDecimalImpl typed = assertInstanceOf(TestSpeedDecimalImpl.class, speed);
        assertEquals(0, BigDecimal.TEN.compareTo(typed.getValue()));
        assertEquals(METRE.divide(SECOND), typed.getUnit());
    }

    @Test
    void product() {
        Quantity<?> r = q("2", METRE).multiply(q("3", METRE));
        assertUntyped("6", METRE.multiply(METRE), DECIMAL128, r);
        assertEquals("6 m^2", r.toString());
        assertUntyped("21600", METRE.multiply(SECOND), DECIMAL128, q("2", METRE).multiply(q("3", HOUR)));
    }

    @Test
    void offsetOperandIsTakenToTheSystemUnit() {
        assertUntyped("550.3", KELVIN.multiply(METRE), DECIMAL128, q("2", CELSIUS).multiply(q("2", METRE)));
        assertUntyped("0.0254", METRE.multiply(METRE), DECIMAL128, q("1", INCH).multiply(q("1", METRE)));
    }

    @Test
    void lowerContextWinsInBothOrders() {
        assertUntyped(
                "0.3333333",
                METRE.divide(SECOND),
                DECIMAL32,
                q("1", METRE, DECIMAL32).divide(q("3", SECOND, DECIMAL128)));
        assertUntyped(
                "0.3333333",
                METRE.divide(SECOND),
                DECIMAL32,
                q("1", METRE, DECIMAL128).divide(q("3", SECOND, DECIMAL32)));
        assertUntyped(
                "1.234568",
                METRE.multiply(SECOND),
                DECIMAL32,
                q("1.2345678", METRE, DECIMAL128).multiply(q("1", SECOND, DECIMAL32)));
    }

    @Test
    void unlimitedContextIsExactWhereItCanBe() {
        MathContext unlimited = MathContext.UNLIMITED;
        assertUntyped(
                "1.23456789012345678901234567890123456789",
                METRE.multiply(METRE),
                unlimited,
                q("1.23456789012345678901234567890123456789", METRE, unlimited).multiply(q("1", METRE, unlimited)));
        assertUntyped(
                "0.3333333333333333333333333333333333",
                METRE.divide(SECOND),
                unlimited,
                q("1", METRE, unlimited).divide(q("3", SECOND, unlimited)));
    }

    @Test
    void foreignNumberKinds() {
        // An exact value and a foreign BigDecimal (unlimited) both leave the receiver's context.
        MathContext five = new MathContext(5);
        assertUntyped("0.33333", METRE.divide(SECOND), five, q("1", METRE, five).divide(foreign(3, SECOND)));
        assertUntyped(
                "0.33333",
                METRE.divide(SECOND),
                five,
                q("1", METRE, five).divide(foreign(new BigDecimal("3"), SECOND)));
        // Exact values never pass through double.
        BigInteger big = BigInteger.TEN.pow(400).add(BigInteger.ONE);
        MathContext unlimited = MathContext.UNLIMITED;
        assertUntyped(
                big.toString(),
                METRE.multiply(METRE),
                unlimited,
                q("1", METRE, unlimited).multiply(foreign(big, METRE)));
        assertUntyped(
                "9007199254740993",
                METRE.multiply(METRE),
                DECIMAL128,
                q("1", METRE).multiply(foreign((1L << 53) + 1, METRE)));
        String digits39 = "1.23456789012345678901234567890123456789";
        assertUntyped(
                digits39,
                METRE.multiply(METRE),
                unlimited,
                q("1", METRE, unlimited).multiply(foreign(new BigDecimal(digits39), METRE)));
    }

    @Test
    void zeroOffsetDoesNotAlignScales() {
        MathContext unlimited = MathContext.UNLIMITED;
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            Quantity<?> big = q("1e+99999", METRE, unlimited).multiply(q("1", METRE, unlimited));
            assertEquals("1E+99999 m^2", big.toString());
            Quantity<?> one = q("1e-99999", METRE, unlimited).multiply(q("1e+99999", METRE, unlimited));
            assertEquals(1, ((BigDecimal) one.getValue()).precision());
            assertEquals(1, ((BigDecimal) q("1e+99999", METRE, unlimited).inverse().getValue()).precision());
        });
    }

    @Test
    void doubleOperandIsReadAsDecimal() {
        assertUntyped("21600", METRE.multiply(SECOND), DECIMAL128, q("2", METRE).multiply(new TestLengthImpl(3, HOUR)));
        assertUntyped("0.25", METRE.divide(SECOND), DECIMAL128, q("1", METRE).divide(foreign(4.0, SECOND)));
        assertUntyped(
                "1e400",
                METRE.multiply(METRE),
                DECIMAL128,
                q("1e400", METRE).multiply(new TestLengthImpl(1, METRE)));
        assertThrows(ArithmeticException.class, () -> q("1", METRE).multiply(new TestLengthImpl(Double.NaN, METRE)));
    }

    @Test
    void inverse() {
        assertUntyped("0.25", METRE.inverse(), DECIMAL128, q("4", METRE).inverse());
        assertUntyped("0.001", METRE.inverse(), DECIMAL128, q("1", KILOMETRE).inverse());
        assertUntyped("0.3333333", METRE.inverse(), DECIMAL32, q("3", METRE, DECIMAL32).inverse());
        assertThrows(ArithmeticException.class, () -> q("0", METRE).inverse());
        assertThrows(ArithmeticException.class, () -> q("0.000", METRE, MathContext.UNLIMITED).inverse());
    }

    @Test
    void divisionByZero() {
        assertThrows(ArithmeticException.class, () -> q("1", METRE).divide(q("0", SECOND)));
        assertThrows(ArithmeticException.class, () -> q("0", METRE).divide(q("0", SECOND)));
        assertThrows(
                ArithmeticException.class,
                () -> q("1", METRE, MathContext.UNLIMITED).divide(q("0", SECOND, MathContext.UNLIMITED)));
    }

    @Test
    void scaleOfResultsIsAbsolute() {
        TestLengthDecimalImpl relative = new TestLengthDecimalImpl(
                BigDecimal.valueOf(2),
                METRE,
                Quantity.Scale.RELATIVE,
                DECIMAL128);
        assertSame(Quantity.Scale.ABSOLUTE, relative.multiply(q("3", SECOND)).getScale());
        assertSame(Quantity.Scale.ABSOLUTE, relative.divide(q("3", SECOND)).getScale());
        assertSame(Quantity.Scale.ABSOLUTE, relative.inverse().getScale());
    }

    @Test
    void asTypeKeepsTheContext() {
        MathContext five = new MathContext(5);
        Quantity<Speed> speed = q("10", METRE, five).divide(q("3", SECOND)).asType(Speed.class);
        TestSpeedDecimalImpl typed = assertInstanceOf(TestSpeedDecimalImpl.class, speed);
        assertEquals(five, typed.mathContext());
        assertEquals(new BigDecimal("3.3333"), typed.getValue());
    }

    @Test
    void asTypeKeepsARelativeScale() {
        // Untyped results are ABSOLUTE, so a fixture quantity in m/s stands in for a RELATIVE receiver.
        TestLengthDecimalImpl relative = new TestLengthDecimalImpl(
                new BigDecimal("2.5"),
                UomUnit.unchecked(METRE.divide(SECOND)),
                Quantity.Scale.RELATIVE,
                DECIMAL32);
        TestSpeedDecimalImpl typed = assertInstanceOf(TestSpeedDecimalImpl.class, relative.asType(Speed.class));
        assertSame(Quantity.Scale.RELATIVE, typed.getScale());
        assertEquals(DECIMAL32, typed.mathContext());
        assertEquals(new BigDecimal("2.5"), typed.getValue());
    }

    @Test
    void asTypeNeverChangesTheValue() {
        UntypedDecimalQuantity u = UntypedDecimalQuantity
                .of(new BigDecimal("1.23456789"), METRE.divide(SECOND), DECIMAL32);
        TestSpeedDecimalImpl kept = assertInstanceOf(TestSpeedDecimalImpl.class, u.asType(Speed.class));
        assertEquals(new BigDecimal("1.23456789"), kept.getValue());
        assertEquals(DECIMAL32, kept.mathContext());
        // A factory that rounds at creation does not round the receiver's value a second time.
        TestServiceProvider.FACTORIES.put(Speed.class, new RoundingFactory());
        try {
            TestSpeedDecimalImpl typed = assertInstanceOf(TestSpeedDecimalImpl.class, u.asType(Speed.class));
            assertEquals(new BigDecimal("1.23456789"), typed.getValue());
            assertEquals(DECIMAL32, typed.mathContext());
        } finally {
            TestServiceProvider.FACTORIES.put(Speed.class, TestSpeedDecimalImpl.FACTORY);
        }
    }

    @Test
    void asTypeUsesTheTypedUnitNotTheFactorys() {
        TestServiceProvider.FACTORIES.put(Speed.class, new KilometrePerHourFactory());
        try {
            Quantity<Speed> speed = q("36", KILOMETRE).divide(q("3600", SECOND)).asType(Speed.class);
            TestSpeedDecimalImpl typed = assertInstanceOf(TestSpeedDecimalImpl.class, speed);
            assertEquals(0, BigDecimal.TEN.compareTo(typed.getValue()), () -> typed.toString());
            assertEquals(METRE.divide(SECOND), typed.getUnit());
        } finally {
            TestServiceProvider.FACTORIES.put(Speed.class, TestSpeedDecimalImpl.FACTORY);
        }
    }

    /** Normalises every value to km/h. */
    private static final class KilometrePerHourFactory implements QuantityFactory<Speed> {
        @Override
        public Quantity<Speed> create(Number value, Unit<Speed> unit, Quantity.Scale scale) {
            UomUnit<Speed> kmh = UomUnit.unchecked(KILOMETRE.divide(HOUR));
            return TestSpeedDecimalImpl.FACTORY.create(value, unit, scale).to(kmh);
        }

        @Override
        public Quantity<Speed> create(Number value, Unit<Speed> unit) {
            return create(value, unit, Quantity.Scale.ABSOLUTE);
        }

        @Override
        public Unit<Speed> getSystemUnit() {
            return TestSpeedDecimalImpl.FACTORY.getSystemUnit();
        }
    }

    /** Rounds to two digits at creation. */
    private static final class RoundingFactory implements QuantityFactory<Speed> {
        @Override
        public Quantity<Speed> create(Number value, Unit<Speed> unit, Quantity.Scale scale) {
            return TestSpeedDecimalImpl.FACTORY.create(((BigDecimal) value).round(new MathContext(2)), unit, scale);
        }

        @Override
        public Quantity<Speed> create(Number value, Unit<Speed> unit) {
            return create(value, unit, Quantity.Scale.ABSOLUTE);
        }

        @Override
        public Unit<Speed> getSystemUnit() {
            return TestSpeedDecimalImpl.FACTORY.getSystemUnit();
        }
    }

    @Test
    void asTypeReturnsANonDecimalResultAsIs() {
        TestServiceProvider.FACTORIES.put(Speed.class, TestSpeedImpl.FACTORY);
        try {
            Quantity<Speed> speed = q("10", METRE).divide(q("4", SECOND)).asType(Speed.class);
            assertEquals(2.5, assertInstanceOf(TestSpeedImpl.class, speed).getValue());
        } finally {
            TestServiceProvider.FACTORIES.put(Speed.class, TestSpeedDecimalImpl.FACTORY);
        }
    }

    interface Unsupported extends Quantity<Unsupported> {
    }

    @Test
    void asTypeContract() {
        Quantity<?> speed = q("10", METRE).divide(q("2", SECOND));
        assertThrows(ClassCastException.class, () -> speed.asType(Length.class));
        UnsupportedOperationException e = assertThrows(
                UnsupportedOperationException.class,
                () -> speed.asType(Unsupported.class));
        assertEquals("no quantity factory for " + Unsupported.class.getName(), e.getMessage());
        assertSame(speed, speed.asType(Untyped.class));
        TestLengthDecimalImpl length = q("1", KILOMETRE);
        assertSame(length, length.asType(Length.class));
        assertThrows(NullPointerException.class, () -> length.asType(null));
    }

    private static Quantity<?> foreign(Number value, Unit<?> unit) {
        return (Quantity<?>) Proxy.newProxyInstance(
                UntypedDecimalQuantityTest.class.getClassLoader(),
                new Class<?>[] { Quantity.class },
                (p, m, a) -> switch (m.getName()) {
                    case "getValue" -> value;
                    case "getUnit" -> unit;
                    case "getScale" -> Quantity.Scale.ABSOLUTE;
                    default -> throw new UnsupportedOperationException(m.getName());
                });
    }

    @Test
    void foreignUnit() {
        Unit<?> foreignUnit = (Unit<?>) Proxy
                .newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Unit.class }, (p, m, a) -> null);
        Quantity<?> foreign = foreign(BigDecimal.ONE, foreignUnit);
        UnconvertibleException e = assertThrows(UnconvertibleException.class, () -> q("1", METRE).multiply(foreign));
        assertTrue(e.getMessage().contains("foreign unit implementation"), e.getMessage());
        assertThrows(UnconvertibleException.class, () -> q("1", METRE).divide(foreign));
        assertThrows(NullPointerException.class, () -> q("1", METRE).multiply((Quantity<?>) null));
    }

    @Test
    void publicFactory() {
        MathContext five = new MathContext(5);
        UntypedDecimalQuantity u = UntypedDecimalQuantity.of(new BigDecimal("2.5"), METRE.divide(SECOND), five);
        assertEquals(new BigDecimal("2.5"), u.getValue());
        assertEquals(METRE.divide(SECOND), u.getUnit());
        assertSame(Quantity.Scale.ABSOLUTE, u.getScale());
        assertEquals(five, u.mathContext());
        assertEquals(2.5, assertInstanceOf(UntypedDoubleQuantity.class, u.toDouble()).getValue());
        assertUntyped("2.5", METRE.divide(SECOND), DECIMAL32, u.toDecimal(DECIMAL32));
        assertThrows(NullPointerException.class, () -> UntypedDecimalQuantity.of(BigDecimal.ONE, null, five));
        assertThrows(NullPointerException.class, () -> UntypedDecimalQuantity.of(null, METRE, five));
        assertThrows(NullPointerException.class, () -> UntypedDecimalQuantity.of(BigDecimal.ONE, METRE, null));
    }

    /** A system value {@code n/d} with the unit factors written out independently of the converters. */
    private record Rational(BigInteger n, BigInteger d) {

        static Rational of(BigDecimal v) {
            return v.scale() >= 0
                    ? new Rational(v.unscaledValue(), BigInteger.TEN.pow(v.scale()))
                    : new Rational(v.unscaledValue().multiply(BigInteger.TEN.pow(-v.scale())), BigInteger.ONE);
        }

        Rational times(Rational o) {
            return new Rational(n.multiply(o.n), d.multiply(o.d));
        }

        Rational plus(Rational o) {
            return new Rational(n.multiply(o.d).add(o.n.multiply(d)), d.multiply(o.d));
        }

        Rational inverse() {
            return new Rational(d, n);
        }

        /** Correctly rounded to {@code mc}; unlimited is exact when terminating, else DECIMAL128. */
        BigDecimal round(MathContext mc) {
            if (d.signum() == 0) {
                throw new ArithmeticException("division by zero");
            }
            BigDecimal num = new BigDecimal(n);
            BigDecimal den = new BigDecimal(d);
            if (mc.getPrecision() == 0) {
                try {
                    return num.divide(den);
                } catch (ArithmeticException nonTerminating) {
                    return num.divide(den, DECIMAL128);
                }
            }
            return num.divide(den, mc);
        }
    }

    private record TestUnit(UomUnit<Length> unit, Rational factor, Rational offset) {

        Rational system(BigDecimal v) {
            return Rational.of(v).times(factor).plus(offset);
        }
    }

    @Test
    void randomizedAgainstExactRationals() {
        Rational zero = Rational.of(BigDecimal.ZERO);
        List<TestUnit> units = List.of(
                new TestUnit(KILOMETRE, Rational.of(new BigDecimal(1000)), zero),
                new TestUnit(METRE, Rational.of(BigDecimal.ONE), zero),
                new TestUnit(INCH, new Rational(BigInteger.valueOf(254), BigInteger.valueOf(10000)), zero),
                new TestUnit(KELVIN, Rational.of(BigDecimal.ONE), zero),
                new TestUnit(CELSIUS, Rational.of(BigDecimal.ONE), Rational.of(new BigDecimal("273.15"))));
        Random random = new Random(20261012);
        RoundingMode[] modes = RoundingMode.values();
        int exact = 0;
        for (int i = 0; i < 6000; i++) {
            TestUnit ua = units.get(random.nextInt(units.size()));
            TestUnit ub = units.get(random.nextInt(units.size()));
            BigDecimal va = randomValue(random);
            BigDecimal vb = randomValue(random);
            MathContext ma = randomContext(random, modes);
            MathContext mb = randomContext(random, modes);
            TestLengthDecimalImpl a = new TestLengthDecimalImpl(va, ua.unit, Quantity.Scale.ABSOLUTE, ma);
            TestLengthDecimalImpl b = new TestLengthDecimalImpl(vb, ub.unit, Quantity.Scale.ABSOLUTE, mb);
            Rational sa = ua.system(va);
            Rational sb = ub.system(vb);
            MathContext mc = Precision.lower(ma, mb);
            String what = va + " " + ua.unit + " (" + ma + ") op " + vb + " " + ub.unit + " (" + mb + ")";
            check(what + " multiply", () -> sa.times(sb).round(mc), mc, () -> a.multiply(b));
            check(what + " divide", () -> sa.times(sb.inverse()).round(mc), mc, () -> a.divide(b));
            check(what + " inverse", () -> sa.inverse().round(ma), ma, a::inverse);
            exact += mc.getPrecision() == 0 ? 1 : 0;
        }
        assertTrue(exact > 150, "unlimited contexts covered: " + exact);
    }

    private static void check(String what, Supplier<BigDecimal> expected, MathContext mc, Supplier<Quantity<?>> op) {
        BigDecimal e;
        try {
            e = expected.get();
        } catch (ArithmeticException zeroOrUnnecessary) {
            assertThrows(ArithmeticException.class, op::get, what);
            return;
        }
        UntypedDecimalQuantity r = assertInstanceOf(UntypedDecimalQuantity.class, op.get(), what);
        assertEquals(0, e.compareTo(r.getValue()), () -> what + ": " + e + " vs " + r.getValue());
        assertEquals(mc, r.mathContext(), what);
    }

    private static BigDecimal randomValue(Random random) {
        BigInteger unscaled = new BigInteger(random.nextInt(90), random);
        return new BigDecimal(random.nextBoolean() ? unscaled : unscaled.negate(), random.nextInt(-15, 26));
    }

    private static MathContext randomContext(Random random, RoundingMode[] modes) {
        return switch (random.nextInt(5)) {
            case 0 -> MathContext.DECIMAL32;
            case 1 -> MathContext.DECIMAL64;
            case 2 -> MathContext.DECIMAL128;
            case 3 -> MathContext.UNLIMITED;
            default -> new MathContext(random.nextInt(1, 21), modes[random.nextInt(modes.length)]);
        };
    }
}
