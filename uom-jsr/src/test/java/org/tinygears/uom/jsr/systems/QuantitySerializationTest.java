/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.jsr.systems;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.measure.Quantity;
import javax.measure.spi.QuantityFactory;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.jsr.UomUnit;
import org.tinygears.uom.jsr.quantity.AbstractDecimalQuantity;
import org.tinygears.uom.jsr.quantity.AbstractDoubleQuantity;
import org.tinygears.uom.jsr.quantity.Length;
import org.tinygears.uom.jsr.quantity.QuantityTypes;
import org.tinygears.uom.jsr.quantity.Untyped;
import org.tinygears.uom.jsr.quantity.UntypedDecimalQuantity;
import org.tinygears.uom.jsr.quantity.UntypedDoubleQuantity;
import org.tinygears.uom.jsr.systems.serial.QuantityCheck;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantitySerializationTest {

    private static final String GOLDEN = "/org/tinygears/uom/jsr/systems/serial/";
    private static final Path HERE = Path.of("").toAbsolutePath();
    private static final String JAVA = System.getProperty("java.home") + File.separator + "bin" + File.separator
            + "java";

    private static Object roundTrip(Object o) throws Exception {
        return QuantityCheck.read(QuantityCheck.write(o));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Quantity<?> create(Class<?> type, Number value, UomUnit<?> unit, Quantity.Scale scale) {
        QuantityFactory factory = QuantityTypes.factory((Class) type);
        return factory.create(value, unit, scale);
    }

    private static void assertSameQuantity(Quantity<?> q, Object read) {
        assertNotSame(q, read);
        assertSame(q.getClass(), read.getClass(), q.toString());
        assertEquals(q, read);
        assertEquals(q.hashCode(), read.hashCode());
        Quantity<?> copy = (Quantity<?>) read;
        assertEquals(q.getValue(), copy.getValue(), q.toString());
        assertEquals(q.getUnit(), copy.getUnit());
        assertEquals(q.getUnit().getSymbol(), copy.getUnit().getSymbol());
        assertEquals(q.getUnit().getName(), copy.getUnit().getName());
        assertSame(q.getScale(), copy.getScale());
        if (q instanceof AbstractDoubleQuantity<?, ?>) {
            assertEquals(
                    Double.doubleToRawLongBits((Double) q.getValue()),
                    Double.doubleToRawLongBits((Double) copy.getValue()));
        } else {
            assertEquals(
                    ((AbstractDecimalQuantity<?, ?>) q).mathContext(),
                    ((AbstractDecimalQuantity<?, ?>) copy).mathContext());
        }
    }

    /** Units of {@code type} other than its system unit: 2.5 times it, plus a catalog unit for some types. */
    @SuppressWarnings("unchecked") // any quantity type, through the raw cast
    private static List<UomUnit<?>> nonSystemUnits(Class<?> type) {
        UomUnit<?> system = (UomUnit<?>) QuantityTypes.factory(cast(type)).getSystemUnit();
        List<UomUnit<?>> units = new ArrayList<>(List.of(system.multiply(2.5)));
        Map<Class<?>, UomUnit<?>> extra = Map.of(
                javax.measure.quantity.Temperature.class,
                SI.DEGREE_CELSIUS,
                javax.measure.quantity.Length.class,
                SI.KILOMETRE,
                javax.measure.quantity.Frequency.class,
                SI.KILOHERTZ,
                javax.measure.quantity.Angle.class,
                SI.DEGREE,
                javax.measure.quantity.Mass.class,
                SI.MILLIGRAM);
        if (extra.containsKey(type)) {
            units.add(extra.get(type));
        }
        return units;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Class cast(Class<?> type) {
        return type;
    }

    @Test
    void everyTypeKindAndScaleRoundTrips() throws Exception {
        assertEquals(33, QuantityTypes.types().size());
        int n = 0;
        for (Class<?> type : QuantityTypes.types()) {
            for (UomUnit<?> unit : nonSystemUnits(type)) {
                for (Quantity.Scale scale : Quantity.Scale.values()) {
                    Quantity<?> d = create(type, -12.375, unit, scale);
                    assertTrue(d.getClass().getName().endsWith("Double"), d.getClass().getName());
                    assertSameQuantity(d, roundTrip(d));
                    // 40 digits: more than the factory's DECIMAL128 context.
                    Quantity<?> b = create(
                            type,
                            new BigDecimal("1234567890.123456789012345678901234567890"),
                            unit,
                            scale);
                    assertTrue(b.getClass().getName().endsWith("Decimal"), b.getClass().getName());
                    assertSameQuantity(b, roundTrip(b));
                    assertEquals(40, ((BigDecimal) ((Quantity<?>) roundTrip(b)).getValue()).precision());
                    Quantity<?> c = ((AbstractDecimalQuantity<?, ?>) b).toDecimal(MathContext.DECIMAL32);
                    assertSameQuantity(c, roundTrip(c));
                    n += 3;
                }
            }
        }
        assertEquals(33 * 2 * 3 + 5 * 2 * 3, n);
    }

    @Test
    void typedReadIsAnInstanceOfTheTypedInterface() throws Exception {
        Object read = roundTrip(Length.of(new BigDecimal("1.50"), SI.KILOMETRE));
        assertTrue(read instanceof Length);
        Length km = (Length) read;
        assertEquals(0, new BigDecimal("1500").compareTo((BigDecimal) km.to(SI.METRE).getValue()));
        assertEquals(new BigDecimal("1.50"), km.getValue());
        assertEquals(SI.METRE, km.toSystem(SI.SYSTEM).getUnit());
    }

    @Test
    void untypedProductsRoundTrip() throws Exception {
        Quantity<?> p = Length.of(2.0, SI.KILOMETRE).multiply(org.tinygears.uom.jsr.quantity.Force.of(3.0, SI.NEWTON));
        assertSame(UntypedDoubleQuantity.class, p.getClass());
        assertSameQuantity(p, roundTrip(p));
        Quantity<?> q = Length.of(new BigDecimal("2.0"), SI.KILOMETRE)
                .divide(org.tinygears.uom.jsr.quantity.Time.of(new BigDecimal("4"), SI.HOUR));
        assertSame(UntypedDecimalQuantity.class, q.getClass());
        assertSameQuantity(q, roundTrip(q));
        // Narrowing the copy works as on the original.
        assertEquals(
                q.asType(javax.measure.quantity.Speed.class),
                ((Quantity<?>) roundTrip(q)).asType(javax.measure.quantity.Speed.class));
        assertTrue(roundTrip(q) instanceof Untyped);
    }

    @Test
    @SuppressWarnings("unchecked") // any quantity type, through the raw cast
    void everyCatalogUnitRoundTrips() throws Exception {
        List<UomUnit<?>> catalog = QuantityCheck.catalog();
        // every distinct unit of every catalog (Gaussian lists Fr and statC, which are equal)
        assertEquals(
                SystemsCatalogTest.CATALOGS.stream().mapToInt(c -> new java.util.HashSet<>(c.units()).size()).sum(),
                catalog.size());
        for (UomUnit<?> u : catalog) {
            UomUnit<?> copy = (UomUnit<?>) roundTrip(u);
            assertEquals(u, copy);
            assertEquals(u.hashCode(), copy.hashCode());
            assertEquals(u.getSymbol(), copy.getSymbol());
            assertEquals(u.getName(), copy.getName());
            assertEquals(u.getDimension(), copy.getDimension());
            // In a quantity of the first type it measures, or untyped.
            Class<?> type = QuantityTypes.types().stream()
                    .filter(t -> QuantityTypes.factory(cast(t)).getSystemUnit().getDimension().equals(u.getDimension()))
                    .findFirst().orElse(null);
            Quantity<?> q = type == null
                    ? UntypedDoubleQuantity.of(7.5, u)
                    : create(type, 7.5, u, Quantity.Scale.ABSOLUTE);
            assertSameQuantity(q, roundTrip(q));
        }
    }

    @Test
    void everyCatalogUnitRoundTripsInAFreshJvm() throws Exception {
        Path file = Files.createTempFile("units", ".ser");
        try {
            List<UomUnit<?>> catalog = QuantityCheck.catalog();
            try (
                    OutputStream bytes = Files.newOutputStream(file);
                    ObjectOutputStream out = new ObjectOutputStream(bytes)
            ) {
                out.writeInt(catalog.size());
                for (UomUnit<?> u : catalog) {
                    out.writeObject(u);
                }
            }
            String out = run(
                    List.of(
                            JAVA,
                            "-XX:-UsePerfData",
                            "-cp",
                            System.getProperty("java.class.path"),
                            QuantityCheck.class.getName(),
                            "units",
                            file.toString()));
            assertTrue(out.contains("ok units " + QuantityCheck.catalog().size()), out);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void goldenFiles() throws Exception {
        Map<String, Quantity<?>> golden = QuantityCheck.golden();
        assertEquals(8, golden.size());
        assertEquals(QuantityCheck.GOLDEN_TYPES.keySet(), golden.keySet());
        boolean write = System.getProperty("uom.writeGolden") != null;
        Path dir = Path.of("src/test/resources" + GOLDEN);
        if (write) {
            Files.createDirectories(dir);
            for (Map.Entry<String, Quantity<?>> e : golden.entrySet()) {
                Files.write(dir.resolve(e.getKey() + ".ser"), QuantityCheck.write(e.getValue()));
            }
        }
        for (Map.Entry<String, Quantity<?>> e : golden.entrySet()) {
            // In write mode the class path still holds the previous copies.
            byte[] bytes = write ? Files.readAllBytes(dir.resolve(e.getKey() + ".ser")) : golden(e.getKey());
            assertSameQuantity(e.getValue(), QuantityCheck.read(bytes));
            assertTrue(QuantityCheck.GOLDEN_TYPES.get(e.getKey()).isInstance(QuantityCheck.read(bytes)), e.getKey());
            assertArrayEquals(
                    bytes,
                    QuantityCheck.write(e.getValue()),
                    "serialized form of " + e.getKey() + " changed");
        }
    }

    private static byte[] golden(String name) throws IOException {
        try (InputStream in = QuantitySerializationTest.class.getResourceAsStream(GOLDEN + name + ".ser")) {
            assertTrue(in != null, "missing golden file " + name);
            return in.readAllBytes();
        }
    }

    private static String goldenDir() throws Exception {
        return Path.of(QuantitySerializationTest.class.getResource(GOLDEN + "length-double.ser").toURI()).getParent()
                .toString();
    }

    @Test
    void freshJvmReadsGoldenFilesOnTheClassPath() throws Exception {
        String out = run(
                List.of(
                        JAVA,
                        "-XX:-UsePerfData",
                        "-cp",
                        System.getProperty("java.class.path"),
                        QuantityCheck.class.getName(),
                        "golden",
                        goldenDir()));
        assertTrue(out.contains("ok golden 8 named=false"), out);
    }

    @Test
    void freshJvmReadsGoldenFilesOnTheModulePath() throws Exception {
        Path classes = HERE.resolve("target/classes");
        Path core = HERE.resolve("../uom/target/classes").normalize();
        Path engine = HERE.resolve("../uom-engine/target/classes").normalize();
        assertTrue(
                Files.isDirectory(classes) && Files.isDirectory(core) && Files.isDirectory(engine),
                "module classes not built");
        String jar = null;
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (Path.of(entry).getFileName().toString().equals("unit-api-2.2.jar")) {
                jar = entry;
            }
        }
        assertTrue(jar != null, "unit-api-2.2.jar not on the class path");
        // Only the check class goes on the class path: test-classes would add the test provider's services file.
        String name = QuantityCheck.class.getName().replace('.', '/') + ".class";
        Path cp = Files.createTempDirectory("serial");
        Path target = cp.resolve(name);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(HERE.resolve("target/test-classes").resolve(name), target);
            String out = run(
                    List.of(
                            JAVA,
                            "-XX:-UsePerfData",
                            "--module-path",
                            String.join(
                                    File.pathSeparator,
                                    classes.toString(),
                                    core.toString(),
                                    engine.toString(),
                                    jar),
                            "--add-modules",
                            "org.tinygears.uom.jsr",
                            "-cp",
                            cp.toString(),
                            QuantityCheck.class.getName(),
                            "golden",
                            goldenDir()));
            assertTrue(out.contains("ok golden 8 named=true"), out);
        } finally {
            Files.deleteIfExists(target);
            for (Path dir = target.getParent(); !dir.equals(cp.getParent()); dir = dir.getParent()) {
                Files.deleteIfExists(dir);
            }
        }
    }

    @Test
    void forgedTypeMismatchIsRejected() throws Exception {
        // A metre quantity patched to name Time: the unit is valid, it does not measure the type.
        byte[] bytes = QuantityCheck.write(Length.of(1.0, SI.METRE));
        InvalidObjectException e = assertThrows(
                InvalidObjectException.class,
                () -> QuantityCheck
                        .read(replaceString(bytes, "javax.measure.quantity.Length", "javax.measure.quantity.Time")));
        assertEquals(
                "invalid quantity: unit m of dimension L does not measure javax.measure.quantity.Time",
                e.getMessage());
        e = assertThrows(
                InvalidObjectException.class,
                () -> QuantityCheck.read(
                        replaceString(
                                bytes,
                                "javax.measure.quantity.Length",
                                "org.tinygears.uom.jsr.quantity.Length")));
        assertEquals("invalid quantity: unknown type org.tinygears.uom.jsr.quantity.Length", e.getMessage());
        e = assertThrows(
                InvalidObjectException.class,
                () -> QuantityCheck.read(replaceString(bytes, "ABSOLUTE", "RELATIVE?")));
        assertEquals("invalid quantity: unknown scale RELATIVE?", e.getMessage());
    }

    @Test
    void forgedAlternateAtomIsRejectedWhetherOrNotTheCatalogIsLoaded() throws Exception {
        // A kHz whose Hz atom is defined as rad/s: same dimension, a different definition than the registered Hz.
        UomUnit<?> fake = SI.RADIAN.divide(SI.SECOND).alternate("Hzx").multiply(1000);
        // Reading the typed quantity loads its type, the bare unit loads nothing of the catalogs.
        assertForgedAtomRejected(
                replaceAll(
                        QuantityCheck.write(
                                create(javax.measure.quantity.Frequency.class, 50.0, fake, Quantity.Scale.ABSOLUTE)),
                        "Hzx",
                        "Hz"));
        assertForgedAtomRejected(replaceAll(QuantityCheck.write(fake), "Hzx", "Hz"));
    }

    private static void assertForgedAtomRejected(byte[] bytes) throws Exception {
        Path file = Files.createTempFile("forged", ".ser");
        try {
            Files.write(file, bytes);
            String expected = "rejected invalid unit atom: symbol Hz is already associated to a different unit";
            for (String touchFirst : List.of("false", "true")) {
                String out = run(
                        List.of(
                                JAVA,
                                "-XX:-UsePerfData",
                                "-cp",
                                System.getProperty("java.class.path"),
                                QuantityCheck.class.getName(),
                                "forged",
                                file.toString(),
                                touchFirst));
                assertEquals(expected, out.strip(), "SI touched first: " + touchFirst);
            }
            InvalidObjectException e = assertThrows(InvalidObjectException.class, () -> QuantityCheck.read(bytes));
            assertEquals(expected.substring("rejected ".length()), e.getMessage());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static byte[] replaceAll(byte[] bytes, String from, String to) {
        byte[] out = bytes;
        while (indexOf(out, utf(from)) >= 0) {
            out = replaceString(out, from, to);
        }
        return out;
    }

    private static int indexOf(byte[] bytes, byte[] part) {
        for (int i = 0; i + part.length <= bytes.length; i++) {
            if (Arrays.equals(bytes, i, i + part.length, part, 0, part.length)) {
                return i;
            }
        }
        return -1;
    }

    /** {@code bytes} with the modified-UTF string {@code from} replaced by {@code to}, length prefix included. */
    private static byte[] replaceString(byte[] bytes, String from, String to) {
        byte[] f = utf(from);
        byte[] t = utf(to);
        for (int i = 0; i + f.length <= bytes.length; i++) {
            if (Arrays.equals(bytes, i, i + f.length, f, 0, f.length)) {
                byte[] out = new byte[bytes.length - f.length + t.length];
                System.arraycopy(bytes, 0, out, 0, i);
                System.arraycopy(t, 0, out, i, t.length);
                System.arraycopy(bytes, i + f.length, out, i + t.length, bytes.length - i - f.length);
                return out;
            }
        }
        throw new AssertionError("not found: " + from);
    }

    private static byte[] utf(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(b.length + 2).putShort((short) b.length).put(b).array();
    }

    // Output goes to a file so waitFor can time out before anything blocks on the pipe.
    private static String run(List<String> command) throws Exception {
        Path log = Files.createTempFile("serial", ".log");
        Process p = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(p.waitFor(60, TimeUnit.SECONDS), "process did not finish within 60 s");
            String out = Files.readString(log);
            assertEquals(0, p.exitValue(), out);
            return out;
        } finally {
            if (p.isAlive()) {
                p.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            }
            Files.deleteIfExists(log);
        }
    }
}
