/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.examples;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.tinygears.uom.Quantity;
import org.tinygears.uom.QuantityFormat;
import org.tinygears.uom.Scale;
import org.tinygears.uom.SystemOfUnits;
import org.tinygears.uom.Unit;
import org.tinygears.uom.UnitFormat;
import org.tinygears.uom.Untyped;
import org.tinygears.uom.jsr.Jsr;
import org.tinygears.uom.quantity.Angle;
import org.tinygears.uom.quantity.Dimensionless;
import org.tinygears.uom.quantity.ElectricCharge;
import org.tinygears.uom.quantity.Energy;
import org.tinygears.uom.quantity.Force;
import org.tinygears.uom.quantity.Length;
import org.tinygears.uom.quantity.Speed;
import org.tinygears.uom.quantity.Temperature;
import org.tinygears.uom.quantity.Time;
import org.tinygears.uom.quantity.Volume;
import org.tinygears.uom.systems.CGS;
import org.tinygears.uom.systems.Gaussian;
import org.tinygears.uom.systems.Imperial;
import org.tinygears.uom.systems.Information;
import org.tinygears.uom.systems.SI;
import org.tinygears.uom.systems.USCustomary;

/**
 * Usage examples of the native API ({@code uom}), then of the JSR-385 implementation ({@link JsrExamples}); each
 * method returns what it prints so that a test can check it.
 */
public final class Examples {
    private Examples() {
    }

    /** Typed quantities: create, convert, add. */
    static String basics() {
        Length marathon = Length.of(42.195, SI.KILOMETRE);
        Length lap = Length.of(400, SI.METRE);
        Length total = marathon.add(lap);
        Length inMetres = total.to(SI.METRE);
        return total + " = " + inMetres;
    }

    /** A length divided by a time is a Speed: the relation is declared, so the product is typed. */
    static String speed() {
        Speed speed = Length.of(36, SI.KILOMETRE).divide(Time.of(1, SI.HOUR));
        return speed.value() + " " + speed.unit();
    }

    /** Offset units: Celsius to kelvin and back. */
    static String temperature() {
        Temperature boiling = Temperature.of(100, SI.DEGREE_CELSIUS);
        return boiling.to(SI.KELVIN) + " | " + boiling.to(SI.KELVIN).to(SI.DEGREE_CELSIUS);
    }

    /** Equals is strict; isEquivalentTo compares what the quantities measure. */
    static String equality() {
        Length km = Length.of(2, SI.KILOMETRE);
        Length m = Length.of(2000, SI.METRE);
        return "equals=" + km.equals(m) + " equivalent=" + km.isEquivalentTo(m);
    }

    /**
     * isEquivalentTo compares decimals exactly, so a value rounded on its way differs from its source; isCloseTo
     * compares within a tolerance the caller states, relative or as a quantity.
     */
    static String closeness() {
        Angle radian = Angle.of(BigDecimal.ONE, SI.RADIAN);
        Angle degrees = radian.to(SI.DEGREE);
        Length foot = Length.of(1, USCustomary.FOOT);
        Length measured = Length.of(0.305, SI.METRE);
        return degrees + " | equivalent: " + degrees.isEquivalentTo(radian) + ", within 1e-30: "
                + degrees.isCloseTo(radian, 1e-30) + " | 1 ft within 1 mm of " + measured + ": "
                + foot.isCloseTo(measured, Length.of(1, SI.MILLIMETRE)) + ", within 0.1 mm: "
                + foot.isCloseTo(measured, Length.of(0.1, SI.MILLIMETRE));
    }

    /** Units and quantities from text, and back to text; a parsed quantity has the type of its unit. */
    static String formats() {
        UnitFormat units = UnitFormat.standard();
        QuantityFormat quantities = new QuantityFormat(units);
        Unit<?> millikelvin = units.parse("mK");
        Unit<?> squareKm = units.parse("km^2");
        Length parsed = quantities.parse("20 km").as(Length.class);
        return units.format(SI.KILOMETRE) + " " + units.format(millikelvin) + " " + units.format(squareKm) + " | "
                + parsed.value() + " " + parsed.unit();
    }

    /** Force times length is an Energy, typed by the declared relation. */
    static String work() {
        Energy work = Force.of(10, SI.NEWTON).multiply(Length.of(3, SI.METRE));
        return work.to(SI.JOULE).value() + " J";
    }

    /** BigDecimal quantities add exactly where doubles do not; division rounds to DECIMAL128 by default. */
    static String decimals() {
        Length decimal = Length.of(new BigDecimal("0.1"), SI.METRE).add(Length.of(new BigDecimal("0.2"), SI.METRE));
        Length binary = Length.of(0.1, SI.METRE).add(Length.of(0.2, SI.METRE));
        Length third = Length.of(BigDecimal.ONE, SI.METRE).divide(3);
        return decimal + " equals 0.3 m: " + decimal.equals(Length.of(new BigDecimal("0.3"), SI.METRE)) + " | " + binary
                + " equals 0.3 m: " + binary.equals(Length.of(0.3, SI.METRE)) + " | 1/3 m = " + third;
    }

    /**
     * Mixing precisions: the decimal wins, and a double operand is read as its shortest decimal (0.2 is 0.2), so the
     * sum is exact. isEquivalentTo compares within the tolerance of the less precise operand.
     */
    static String mixedPrecision() {
        Length decimal = Length.of(new BigDecimal("0.1"), SI.METRE);
        Length binary = Length.of(0.2, SI.METRE);
        Length sum = decimal.add(binary);
        boolean sameEitherWay = sum.equals(binary.add(decimal));
        Length doublesOnly = Length.of(0.1, SI.METRE).add(binary);
        return sum.value().getClass().getSimpleName() + " " + sum + " | same either way: " + sameEitherWay
                + " | doubles only: " + doublesOnly + ", equivalent: " + sum.isEquivalentTo(doublesOnly);
    }

    /** A decimal carries a MathContext (DECIMAL128 by default); toDecimal and toDouble change the precision. */
    static String contexts() {
        Length precise = Length.of(new BigDecimal("1.23456789"), SI.METRE);
        Length rounded = precise.toDecimal(new MathContext(4));
        Length viaFactory = Length.of(new BigDecimal("1.23456789"), SI.METRE, Scale.ABSOLUTE, MathContext.DECIMAL32);
        return rounded + " | " + viaFactory.divide(3) + " | " + precise.toDouble().value().getClass().getSimpleName();
    }

    /** Strict equals separates the precisions; isEquivalentTo compares what they measure. */
    static String decimalEquality() {
        Length decimal = Length.of(new BigDecimal("2"), SI.KILOMETRE);
        Length binary = Length.of(2000, SI.METRE);
        Length scaled = Length.of(new BigDecimal("2.00"), SI.KILOMETRE);
        return "decimal equals double: " + decimal.equals(binary) + " | equivalent: " + decimal.isEquivalentTo(binary)
                + " | 2 equals 2.00: " + decimal.equals(scaled);
    }

    /** The quantity format prints a decimal with its own digits; the default parse gives a double quantity. */
    static String decimalFormat() {
        QuantityFormat format = QuantityFormat.standard();
        Quantity<?> parsed = format.parse("1.50 km");
        String printed = format.format(Length.of(new BigDecimal("1.50"), SI.KILOMETRE));
        return printed + " | " + parsed.value().getClass().getSimpleName() + " " + parsed.value();
    }

    /** quantity.toSystem(system) converts to the system's preferred unit; US and imperial gallons differ. */
    static String systems() {
        Length foot = Length.of(1, USCustomary.FOOT);
        Temperature boiling = Temperature.of(100, SI.DEGREE_CELSIUS);
        Temperature exact = Temperature.of(new BigDecimal("100"), SI.DEGREE_CELSIUS);
        return foot + " = " + foot.toSystem(CGS.SYSTEM) + " | " + boiling + " = " + boiling.toSystem(USCustomary.SYSTEM)
                + " | exact " + exact.toSystem(USCustomary.SYSTEM) + " | "
                + Volume.of(1, USCustomary.GALLON_US).to(SI.LITRE) + " vs "
                + Volume.of(1, Imperial.GALLON_UK).to(SI.LITRE) + " | "
                + ElectricCharge.of(1, SI.COULOMB).toSystem(Gaussian.SYSTEM);
    }

    /** Information units: decimal and binary prefixes. Bit and byte are dimensionless, as the radian is. */
    static String information() {
        UnitFormat units = UnitFormat.standard();
        QuantityFormat format = new QuantityFormat(units);
        Quantity<?> file = format.parse("1.5 GiB");
        Quantity<?> rate = format.parse("100 Mbit/s");
        Time transfer = file.divide(rate).as(Time.class);
        Dimensionless size = file.as(Dimensionless.class);
        return format.format(file) + " / " + format.format(rate) + " = " + transfer.to(SI.SECOND) + " | "
                + format.format(size.to(Information.MEGABYTE.asType(Dimensionless.class))) + " | "
                + units.format(Information.BYTE.multiply(1024)) + " vs "
                + units.format(Information.BYTE.multiply(1000));
    }

    /** Units and quantities are Serializable; a round trip gives equal objects. */
    static String serialization() {
        Unit<?> unit = SI.KILOMETRE.divide(SI.HOUR);
        Length length = Length.of(new BigDecimal("1.50"), SI.KILOMETRE);
        return "unit: " + roundTrip(unit).equals(unit) + " " + roundTrip(unit) + " | quantity: "
                + roundTrip(length).equals(length) + " " + roundTrip(length);
    }

    /** The filter SECURITY.md recommends for streams that are not trusted; it covers both APIs. */
    static final String SERIAL_FILTER = "maxarray=65536;maxbytes=1048576;maxdepth=20;maxrefs=10000;"
            + "org.tinygears.uom.**;javax.measure.**;java.lang.*;java.math.*;!*";

    static Object roundTrip(Object value) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
                out.writeObject(value);
            }
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                in.setObjectInputFilter(ObjectInputFilter.Config.createFilter(SERIAL_FILTER));
                return in.readObject();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    /** withLocale localizes the number, never the unit symbol. */
    static String locales() {
        QuantityFormat german = QuantityFormat.standard().withLocale(Locale.GERMANY);
        String printed = german.format(Length.of(1.5, SI.KILOMETRE));
        Quantity<?> parsed = german.parse(printed);
        return printed + " | " + parsed.value() + " " + parsed.unit();
    }

    /** A shifted unit without a symbol formats as a formula that parses back to an equal unit. */
    static String offsetUnits() {
        UnitFormat format = UnitFormat.standard();
        Unit<?> shifted = SI.KELVIN.shift(5);
        String text = format.format(shifted);
        return text + " | parses back equal: " + format.parse(text).equals(shifted) + " | "
                + format.format(SI.DEGREE_CELSIUS.multiply(2));
    }

    /**
     * Parsing gives double quantities by default; withDecimals gives BigDecimal quantities that keep every digit and
     * the scale of the text. The parsed quantity has the type of its unit, so as(Length.class) narrows it.
     */
    static String decimalParse() {
        QuantityFormat format = QuantityFormat.standard();
        // The default reads the number as a double: 1.50 becomes 1.5.
        Quantity<?> plain = format.parse("1.50 km");
        // The context, 7 digits here, applies to later arithmetic; the parsed value is never rounded to it.
        QuantityFormat decimals = format.withDecimals(MathContext.DECIMAL32);
        Length length = decimals.parse("1.234567890 km").as(Length.class);
        BigDecimal value = (BigDecimal) length.value();
        // A unit without a quantity type gives an untyped decimal quantity.
        Untyped momentum = decimals.parse("2.0 kg·m/s").as(Untyped.class);
        return plain + " " + plain.value().getClass().getSimpleName() + " | " + length + " scale " + value.scale()
                + " | /3 " + length.divide(3) + " | " + momentum + " " + momentum.value().getClass().getSimpleName();
    }

    /** User units: a .units text loaded at run time, used in parsing and as a target system. */
    static String userUnits() {
        String file = """
                @system Medieval
                league:league   3 mi
                furlong:furlong 220 yd
                @preferred Length furlong
                """;
        SystemOfUnits medieval = SystemOfUnits.load("medieval.units", new StringReader(file), null);
        QuantityFormat format = new QuantityFormat(UnitFormat.standard().with(medieval));
        Length distance = format.parse("2 league").as(Length.class);
        return medieval.name() + " | " + distance + " = " + distance.to(SI.KILOMETRE) + " = "
                + distance.toSystem(medieval);
    }

    /** Jsr converts to and from the JSR-385 quantities of uom-jsr: same engine unit, value, context and scale. */
    static String bridge() {
        Length length = Length.of(new BigDecimal("1.50"), SI.KILOMETRE);
        org.tinygears.uom.jsr.quantity.Length jsr = Jsr.toJsr(length, org.tinygears.uom.jsr.quantity.Length.class);
        Length back = Jsr.fromJsr(jsr, Length.class);
        return jsr + " " + jsr.getClass().getInterfaces()[0].getSimpleName() + " | back equal: " + back.equals(length);
    }

    static Map<String, String> all() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("basics", basics());
        out.put("speed", speed());
        out.put("temperature", temperature());
        out.put("equality", equality());
        out.put("closeness", closeness());
        out.put("formats", formats());
        out.put("work", work());
        out.put("decimals", decimals());
        out.put("mixedPrecision", mixedPrecision());
        out.put("contexts", contexts());
        out.put("decimalEquality", decimalEquality());
        out.put("decimalFormat", decimalFormat());
        out.put("systems", systems());
        out.put("information", information());
        out.put("serialization", serialization());
        out.put("locales", locales());
        out.put("offsetUnits", offsetUnits());
        out.put("decimalParse", decimalParse());
        out.put("userUnits", userUnits());
        out.put("bridge", bridge());
        return out;
    }

    public static void main(String[] args) {
        all().forEach((name, result) -> System.out.println(name + ": " + result));
        JsrExamples.all().forEach((name, result) -> System.out.println("jsr." + name + ": " + result));
    }
}
