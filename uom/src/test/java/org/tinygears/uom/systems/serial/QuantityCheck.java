/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.systems.serial;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.measure.Quantity;

import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.core.quantity.AbstractDecimalQuantity;
import org.tinygears.uom.core.quantity.Untyped;
import org.tinygears.uom.core.quantity.UntypedDecimalQuantity;
import org.tinygears.uom.core.quantity.UntypedDoubleQuantity;
import org.tinygears.uom.quantity.Angle;
import org.tinygears.uom.quantity.Frequency;
import org.tinygears.uom.quantity.Length;
import org.tinygears.uom.quantity.Temperature;
import org.tinygears.uom.systems.SI;
import org.tinygears.uom.systems.UnitSystems;
import org.tinygears.uom.systems.UomSystemOfUnits;

/**
 * Subprocess check, also the source of the golden quantities and the unit catalog. {@code golden <dir>} reads the
 * golden files before any catalog class is touched; {@code units <file>} reads a stream of catalog units the same
 * way. No nested classes: the module-path run copies this one class file.
 */
public final class QuantityCheck {

    /** Golden file names and the typed interface each quantity must implement. */
    public static final Map<String, Class<?>> GOLDEN_TYPES = goldenTypes();

    private QuantityCheck() {
    }

    private static Map<String, Class<?>> goldenTypes() {
        Map<String, Class<?>> m = new LinkedHashMap<>();
        m.put("length-double", Length.class);
        m.put("length-decimal32", Length.class);
        m.put("temperature-relative", Temperature.class);
        m.put("celsius-decimal", Temperature.class);
        m.put("untyped-double", Untyped.class);
        m.put("untyped-decimal", Untyped.class);
        m.put("frequency-kilohertz", Frequency.class);
        m.put("angle-degree", Angle.class);
        return m;
    }

    /** The eight golden quantities, in the order of {@link #GOLDEN_TYPES}; touches SI. */
    public static Map<String, Quantity<?>> golden() {
        Map<String, Quantity<?>> m = new LinkedHashMap<>();
        m.put("length-double", Length.of(2.5, SI.KILOMETRE));
        m.put(
                "length-decimal32",
                Length.of(new BigDecimal("1.23456789012"), SI.METRE, Quantity.Scale.ABSOLUTE, MathContext.DECIMAL32));
        m.put("temperature-relative", Temperature.of(-5.0, SI.KELVIN, Quantity.Scale.RELATIVE));
        m.put("celsius-decimal", Temperature.of(new BigDecimal("21.50"), SI.DEGREE_CELSIUS));
        m.put("untyped-double", UntypedDoubleQuantity.of(3.0, SI.METRE.multiply(SI.SECOND)));
        m.put(
                "untyped-decimal",
                UntypedDecimalQuantity.of(
                        new BigDecimal("-0.0010"),
                        SI.NEWTON.multiply(SI.METRE),
                        new MathContext(10, RoundingMode.HALF_DOWN)));
        m.put("frequency-kilohertz", Frequency.of(50.0, SI.KILOHERTZ));
        m.put("angle-degree", Angle.of(new BigDecimal("90"), SI.DEGREE));
        return m;
    }

    /** Every unit of every system, in system order: 298 entries, some equal across systems. */
    public static List<UomUnit<?>> catalog() {
        List<UomUnit<?>> units = new ArrayList<>();
        for (UomSystemOfUnits system : UnitSystems.all()) {
            system.getUnits().forEach(u -> units.add((UomUnit<?>) u));
        }
        return units;
    }

    public static void main(String[] args) throws Exception {
        if (args[0].equals("golden")) {
            golden(Path.of(args[1]));
        } else if (args[0].equals("forged")) {
            forged(Path.of(args[1]), Boolean.parseBoolean(args[2]));
        } else {
            units(Path.of(args[1]));
        }
    }

    private static void golden(Path dir) throws Exception {
        Map<String, Object> read = new LinkedHashMap<>();
        for (Map.Entry<String, Class<?>> e : GOLDEN_TYPES.entrySet()) {
            Object q = read(Files.readAllBytes(dir.resolve(e.getKey() + ".ser")));
            String name = q.getClass().getName();
            check(e.getValue().isInstance(q), e.getKey() + " is not a " + e.getValue().getName() + ": " + name);
            check(
                    e.getValue() == Untyped.class
                            ? name.startsWith("org.tinygears.uom.core.quantity.Untyped")
                            : name.startsWith("org.tinygears.uom.quantity.")
                                    && (name.endsWith("Double") || name.endsWith("Decimal")),
                    e.getKey() + " has class " + name);
            read.put(e.getKey(), q);
        }
        Map<String, Quantity<?>> expected = golden();
        for (Map.Entry<String, Quantity<?>> e : expected.entrySet()) {
            Object q = read.get(e.getKey());
            check(e.getValue().equals(q) && e.getValue().getClass() == q.getClass(), e.getKey() + " differs: " + q);
            check(e.getValue().getValue().equals(((Quantity<?>) q).getValue()), e.getKey() + " value differs");
            if (q instanceof AbstractDecimalQuantity<?, ?> d) {
                check(
                        ((AbstractDecimalQuantity<?, ?>) e.getValue()).mathContext().equals(d.mathContext()),
                        e.getKey() + " context differs");
            }
            check(
                    Arrays.equals(Files.readAllBytes(dir.resolve(e.getKey() + ".ser")), write(q)),
                    e.getKey() + " written differently");
        }
        System.out.println("ok golden " + read.size() + " named=" + Length.class.getModule().isNamed());
    }

    private static void units(Path file) throws Exception {
        List<UomUnit<?>> read = new ArrayList<>();
        try (InputStream bytes = Files.newInputStream(file); ObjectInputStream in = new ObjectInputStream(bytes)) {
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                read.add((UomUnit<?>) in.readObject());
            }
        }
        List<UomUnit<?>> catalog = catalog();
        check(read.size() == catalog.size(), "read " + read.size() + " of " + catalog.size());
        for (int i = 0; i < catalog.size(); i++) {
            UomUnit<?> c = catalog.get(i);
            UomUnit<?> u = read.get(i);
            check(
                    c.equals(u) && c.hashCode() == u.hashCode() && c.getSymbol().equals(u.getSymbol())
                            && java.util.Objects.equals(c.getName(), u.getName()),
                    "unit " + i + ": " + c + " read as " + u);
        }
        System.out.println("ok units " + read.size());
    }

    /** Reads one quantity, after touching SI if {@code touchFirst}; prints whether it was accepted. */
    private static void forged(Path file, boolean touchFirst) throws Exception {
        if (touchFirst) {
            check(SI.HERTZ.getSymbol().equals("Hz"), "SI not loaded");
        }
        try {
            System.out.println("accepted " + read(Files.readAllBytes(file)));
        } catch (java.io.InvalidObjectException e) {
            System.out.println("rejected " + e.getMessage());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static byte[] write(Object o) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(o);
        }
        return bytes.toByteArray();
    }

    public static Object read(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return in.readObject();
        }
    }
}
