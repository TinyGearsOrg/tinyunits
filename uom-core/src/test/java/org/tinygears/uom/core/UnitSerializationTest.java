// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tinygears.uom.core.BaseUnits.KELVIN;
import static org.tinygears.uom.core.BaseUnits.KILOGRAM;
import static org.tinygears.uom.core.BaseUnits.METRE;
import static org.tinygears.uom.core.BaseUnits.ONE;
import static org.tinygears.uom.core.BaseUnits.SECOND;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.UnaryOperator;
import javax.measure.MeasurementException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class UnitSerializationTest {

    private static final String GOLDEN = "/org/tinygears/uom/core/serial/";

    /** The twelve units pinned by golden files, by file name. */
    private static Map<String, UomUnit<?>> goldenUnits() {
        Map<String, UomUnit<?>> m = new LinkedHashMap<>();
        m.put("metre", METRE);
        m.put("kilogram", KILOGRAM);
        m.put("second", SECOND);
        m.put("ampere", BaseUnits.AMPERE);
        m.put("kelvin", KELVIN);
        m.put("mole", BaseUnits.MOLE);
        m.put("candela", BaseUnits.CANDELA);
        m.put("one", ONE);
        m.put("hertz", ONE.divide(SECOND).alternate("Hz").named("Hz", "hertz"));
        m.put("celsius", KELVIN.transform(Converters.affine(Factor.ONE, Factor.of(5463L, 20L)))
                .named("°C", "degree Celsius"));
        m.put("degree", ONE.alternate("rad").scaledBy(Factor.PI.divide(Factor.ofLong(180))).named("°", "degree"));
        m.put("root-speed", METRE.divide(SECOND).scaledBy(Factor.ofLong(2).root(2)));
        return m;
    }

    static byte[] serialize(Object o) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(o);
        }
        return bytes.toByteArray();
    }

    static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return in.readObject();
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T roundTrip(T o) throws Exception {
        return (T) deserialize(serialize(o));
    }

    private static void assertUnitRoundTrip(UomUnit<?> unit) throws Exception {
        UomUnit<?> copy = roundTrip(unit);
        assertEquals(unit, copy);
        assertEquals(unit.hashCode(), copy.hashCode());
        assertEquals(unit.getSymbol(), copy.getSymbol());
        assertEquals(unit.getName(), copy.getName());
        assertEquals(unit.atoms(), copy.atoms());
        assertEquals(unit.toSystemConverter(), copy.toSystemConverter());
        assertEquals(unit.getDimension(), copy.getDimension());
    }

    @Test
    void baseUnitsAndOne() throws Exception {
        for (UomUnit<?> u : List.of(METRE, KILOGRAM, SECOND, BaseUnits.AMPERE, KELVIN, BaseUnits.MOLE,
                BaseUnits.CANDELA, ONE)) {
            assertUnitRoundTrip(u);
        }
        UnitAtom metre = METRE.atoms().keySet().iterator().next();
        assertSame(metre, roundTrip(METRE).atoms().keySet().iterator().next());
        assertEquals("one", roundTrip(ONE).getName());
    }

    @Test
    void alternateUnits() throws Exception {
        UomUnit<?> hz = ONE.divide(SECOND).alternate("Hz").named("Hz", "hertz");
        assertUnitRoundTrip(hz);
        assertSame(hz.atoms().keySet().iterator().next(), roundTrip(hz).atoms().keySet().iterator().next());
        UomUnit<?> fresh = METRE.divide(SECOND).alternate("t1-alt-speed");
        assertUnitRoundTrip(fresh);
        assertEquals(Map.of(new UnitAtom("t1-alt-speed", new UomDimension(1, 0, -1, 0, 0, 0, 0)), 1),
                roundTrip(fresh).atoms());
        // An alternate atom defined through another alternate atom.
        assertUnitRoundTrip(hz.multiply(METRE).alternate("t1-alt-nested").named("t1-alt-nested", "nested"));
    }

    @Test
    void derivedOffsetPiAndRootUnits() throws Exception {
        UomUnit<?> newton = KILOGRAM.multiply(METRE).divide(SECOND.pow(2));
        assertEquals("kg·m/s^2", newton.getSymbol());
        assertNull(newton.getName());
        assertUnitRoundTrip(newton);
        assertUnitRoundTrip(newton.named("N", "newton"));
        assertUnitRoundTrip(KELVIN.shift(273.15));
        assertUnitRoundTrip(KELVIN.shift(273.15).named("°C", "degree Celsius"));
        assertUnitRoundTrip(METRE.scaledBy(Factor.PI.pow(2)));
        assertUnitRoundTrip(METRE.scaledBy(Factor.of(3, 7).root(3)));
        assertUnitRoundTrip(METRE.multiply(1000).named("km", null));
        for (UomUnit<?> u : goldenUnits().values()) {
            assertUnitRoundTrip(u);
        }
    }

    @Test
    void factors() throws Exception {
        for (Factor f : List.of(Factor.ZERO, Factor.ONE, Factor.PI, Factor.of(-1, 3), Factor.PI.pow(-2),
                Factor.ofLong(2).root(2), Factor.of(5, 3).multiply(Factor.PI).root(5),
                Factor.of(BigInteger.TWO.pow(60_000).add(BigInteger.ONE), BigInteger.valueOf(3)))) {
            Factor copy = roundTrip(f);
            assertEquals(f, copy);
            assertEquals(f.hashCode(), copy.hashCode());
            assertEquals(f.toString(), copy.toString());
        }
        assertSame(Factor.ZERO, roundTrip(Factor.ZERO));
    }

    @Test
    void converters() throws Exception {
        assertSame(Converters.IDENTITY, roundTrip(Converters.IDENTITY));
        UomConverter a = Converters.scale(Factor.ofLong(3));
        UomConverter b = Converters.affine(Factor.of(1, 2), Factor.ofLong(-7));
        UomConverter c = Converters.shift(Factor.of(1, 4));
        for (UomConverter conv : List.of(a, b, c, b.inverse())) {
            UomConverter copy = roundTrip(conv);
            assertEquals(conv, copy);
            assertEquals(List.of(copy), copy.getConversionSteps());
        }
        UomConverter chain = a.concatenate(b).concatenate(Converters.IDENTITY).concatenate(c);
        UomConverter copy = roundTrip(chain);
        assertEquals(chain, copy);
        assertEquals(List.of(a, b, Converters.IDENTITY, c), copy.getConversionSteps());
        assertEquals(chain.getConversionSteps(), copy.getConversionSteps());
        assertEquals(chain.convert(2.0), copy.convert(2.0));
        // A unit's converter keeps its steps too.
        UomUnit<?> celsius = KELVIN.shift(273.15);
        assertEquals(celsius.toSystemConverter().getConversionSteps(),
                roundTrip(celsius).toSystemConverter().getConversionSteps());
    }

    @Test
    void converterCacheIsNotSerialized() throws Exception {
        UomUnit<?> km = METRE.multiply(1000);
        km.getConverterToAny(METRE);
        assertEquals(1, cache(km).size());
        UomUnit<?> copy = roundTrip(km);
        assertTrue(cache(copy).isEmpty());
        assertEquals(Factor.ofLong(1000), copy.getConverterToAny(METRE).factor());
    }

    private static Map<?, ?> cache(UomUnit<?> u) throws Exception {
        Field f = UomUnit.class.getDeclaredField("converterCache");
        f.setAccessible(true);
        return (Map<?, ?>) f.get(u);
    }

    @Test
    void goldenFiles() throws Exception {
        Map<String, UomUnit<?>> units = goldenUnits();
        assertEquals(12, units.size());
        if (System.getProperty("uom.writeGolden") != null) {
            Path dir = Path.of("src/test/resources" + GOLDEN);
            Files.createDirectories(dir);
            for (Map.Entry<String, UomUnit<?>> e : units.entrySet()) {
                Files.write(dir.resolve(e.getKey() + ".ser"), serialize(e.getValue()));
            }
        }
        for (Map.Entry<String, UomUnit<?>> e : units.entrySet()) {
            byte[] golden;
            try (InputStream in = UnitSerializationTest.class.getResourceAsStream(GOLDEN + e.getKey() + ".ser")) {
                assertTrue(in != null, "missing golden file " + e.getKey());
                golden = in.readAllBytes();
            }
            UomUnit<?> read = (UomUnit<?>) deserialize(golden);
            assertEquals(e.getValue(), read, e.getKey());
            assertEquals(e.getValue().getSymbol(), read.getSymbol(), e.getKey());
            assertEquals(e.getValue().getName(), read.getName(), e.getKey());
            assertArrayEquals(golden, serialize(e.getValue()), "serialized form of " + e.getKey() + " changed");
        }
    }

    @Test
    void freshJvmReadsGoldenFilesOnTheClassPath() throws Exception {
        String jar = unitApiJar();
        Path classes = Path.of("target/classes").toAbsolutePath();
        Path testClasses = Path.of("target/test-classes").toAbsolutePath();
        Assumptions.assumeTrue(Files.isDirectory(classes) && Files.isDirectory(testClasses), "no target classes");
        String out = ModulePathTest.runBounded(List.of(java(), "-cp",
                String.join(File.pathSeparator, classes.toString(), testClasses.toString(), jar),
                "org.tinygears.uom.core.serial.SerialCheck", testClasses.resolve(GOLDEN.substring(1)).toString()),
                Duration.ofSeconds(60));
        assertTrue(out.contains("ok named=false atoms=[Hz]"), out);
    }

    @Test
    void roundTripOnTheModulePath() throws Exception {
        String jar = unitApiJar();
        Path classes = Path.of("target/classes").toAbsolutePath();
        Path testClasses = Path.of("target/test-classes").toAbsolutePath();
        Assumptions.assumeTrue(Files.isDirectory(classes) && Files.isDirectory(testClasses), "no target classes");
        String out = ModulePathTest.runBounded(List.of(java(), "--module-path", classes + File.pathSeparator + jar,
                "--add-modules", "org.tinygears.uom.core", "-cp", testClasses.toString(),
                "org.tinygears.uom.core.serial.SerialCheck", testClasses.resolve(GOLDEN.substring(1)).toString()),
                Duration.ofSeconds(60));
        assertTrue(out.contains("ok named=true atoms=[Hz]"), out);
    }

    private static InvalidObjectException assertInvalid(String message, Object forged) {
        InvalidObjectException e = assertThrows(InvalidObjectException.class, () -> deserialize(serialize(forged)));
        assertEquals(message, e.getMessage());
        return e;
    }

    private static UnitAtom atom(UomUnit<?> base) {
        return base.atoms().keySet().iterator().next();
    }

    @Test
    void forgedFactors() throws Exception {
        BigInteger one = BigInteger.ONE;
        InvalidObjectException e = assertInvalid("invalid factor: division by zero",
                new Factor.Proxy(one, BigInteger.ZERO, 0, 1));
        assertTrue(e.getCause() instanceof ArithmeticException, e.getCause().toString());
        assertInvalid("invalid factor: root index must be positive: -1", new Factor.Proxy(one, one, 0, -1));
        assertInvalid("invalid factor: root index must be positive: 0", new Factor.Proxy(one, one, 0, 0));
        assertInvalid("invalid factor: null numerator", new Factor.Proxy(null, one, 0, 1));
        assertInvalid("invalid factor: null denominator", new Factor.Proxy(one, null, 0, 1));
        // Non-normalised parts are normalised on read.
        assertEquals(Factor.of(1, 2), roundTrip(new Factor.Proxy(BigInteger.TWO, BigInteger.valueOf(4), 0, 1)));
        assertEquals(Factor.of(-3, 2), roundTrip(new Factor.Proxy(BigInteger.valueOf(6), BigInteger.valueOf(-4), 0, 1)));
        assertEquals(Factor.ofLong(2), roundTrip(new Factor.Proxy(BigInteger.valueOf(4), one, 0, 2)));
        assertEquals(Factor.PI, roundTrip(new Factor.Proxy(one, one, 2, 2)));
    }

    @Test
    void forgedHugeNumeratorIsRejectedQuickly() {
        // Two random coprime-looking 4M-bit values: reducing them by gcd first would take minutes.
        BigInteger n = new BigInteger(4_000_000, new Random(1));
        BigInteger d = new BigInteger(4_000_000, new Random(2));
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            assertInvalid("invalid factor: factor too large", new Factor.Proxy(n, d, 0, 1));
            assertInvalid("invalid factor: factor too large", new Factor.Proxy(n, BigInteger.ONE, 0, 1));
            assertInvalid("invalid factor: factor too large", new Factor.Proxy(BigInteger.ONE, d, 0, 1));
        });
    }

    @Test
    void forgedDimensions() throws Exception {
        assertInvalid("invalid dimension: expected 7 exponents, got 6", new UomDimension.Proxy(new int[6]));
        assertInvalid("invalid dimension: expected 7 exponents, got 8", new UomDimension.Proxy(new int[8]));
        assertInvalid("invalid dimension: null exponents", new UomDimension.Proxy(null));
        assertEquals(new UomDimension(1, 2, 3, 4, 5, 6, 7),
                roundTrip(new UomDimension.Proxy(new int[] {1, 2, 3, 4, 5, 6, 7})));
    }

    @Test
    void forgedAtoms() throws Exception {
        UnitAtom s = atom(SECOND);
        UnitAtom m = atom(METRE);
        UomDimension perSecond = new UomDimension(0, 0, -1, 0, 0, 0, 0);
        METRE.alternate("t1-forge-bound");
        assertInvalid("invalid unit atom: symbol t1-forge-bound is already associated to a different unit",
                new UnitAtom.Proxy("t1-forge-bound", UomDimension.TIME, new UnitAtom[] {s}, new int[] {1}));
        ONE.divide(SECOND).alternate("Hz");
        assertInvalid("invalid unit atom: symbol Hz is already associated to a different unit",
                new UnitAtom.Proxy("Hz", UomDimension.LENGTH, new UnitAtom[] {m}, new int[] {1}));
        assertInvalid("invalid unit atom: symbol m is already associated to a different unit",
                new UnitAtom.Proxy("m", UomDimension.TIME, null, null));
        assertInvalid("invalid unit atom: symbol m is already associated to a different unit",
                new UnitAtom.Proxy("m", UomDimension.TIME, new UnitAtom[] {s}, new int[] {1}));
        assertInvalid("invalid unit atom: dimension L of t1-forge-dim does not match its definition T",
                new UnitAtom.Proxy("t1-forge-dim", UomDimension.LENGTH, new UnitAtom[] {s}, new int[] {1}));
        // The rejected atom was not registered.
        assertEquals("t1-forge-dim", METRE.alternate("t1-forge-dim").getSymbol());
        assertInvalid("invalid unit atom: null symbol", new UnitAtom.Proxy(null, UomDimension.TIME, null, null));
        assertInvalid("invalid unit atom: null dimension", new UnitAtom.Proxy("t1-forge-x", null, null, null));
        assertInvalid("invalid unit atom: invalid unit symbol: ' '",
                new UnitAtom.Proxy(" ", UomDimension.TIME, null, null));
        assertInvalid("invalid unit atom: invalid unit symbol: 'a b'",
                new UnitAtom.Proxy("a b", UomDimension.TIME, null, null));
        assertInvalid("invalid unit atom: atoms and exponents do not match",
                new UnitAtom.Proxy("t1-forge-x", perSecond, new UnitAtom[] {s, m}, new int[] {-1}));
        assertInvalid("invalid unit atom: atoms and exponents do not match",
                new UnitAtom.Proxy("t1-forge-x", perSecond, new UnitAtom[] {s}, null));
        assertInvalid("invalid unit atom: null atom",
                new UnitAtom.Proxy("t1-forge-x", perSecond, new UnitAtom[] {null}, new int[] {-1}));
        assertInvalid("invalid unit atom: duplicate atom s",
                new UnitAtom.Proxy("t1-forge-x", perSecond, new UnitAtom[] {s, s}, new int[] {-1, 0}));
        // An invalid nested dimension fails the whole read.
        InvalidObjectException e = assertThrows(InvalidObjectException.class, () -> readReplacing(
                ONE.divide(SECOND).alternate("Hz"),
                o -> o instanceof UomDimension.Proxy ? new UomDimension.Proxy(new int[3]) : o));
        assertEquals("invalid dimension: expected 7 exponents, got 3", e.getMessage());
        // A valid forged definition registers its symbol.
        UnitAtom fresh = (UnitAtom) deserialize(serialize(
                new UnitAtom.Proxy("t1-forge-new", perSecond, new UnitAtom[] {s}, new int[] {-1})));
        assertEquals(new UnitAtom("t1-forge-new", perSecond), fresh);
        assertSame(fresh, atom(ONE.divide(SECOND).alternate("t1-forge-new")));
        assertThrows(MeasurementException.class, () -> METRE.alternate("t1-forge-new"));
    }

    @Test
    void forgedConverters() throws Exception {
        Factor two = Factor.ofLong(2);
        assertInvalid("invalid converter: null factor", new AffineConverter.Proxy(null, Factor.ZERO, null, null));
        assertInvalid("invalid converter: null offset", new AffineConverter.Proxy(Factor.ONE, null, null, null));
        assertInvalid("invalid converter: step factors and offsets do not match",
                new AffineConverter.Proxy(two, Factor.ZERO, new Factor[] {two}, new Factor[] {Factor.ZERO, Factor.ZERO}));
        assertInvalid("invalid converter: step factors and offsets do not match",
                new AffineConverter.Proxy(two, Factor.ZERO, new Factor[] {two}, null));
        assertInvalid("invalid converter: step factors and offsets do not match",
                new AffineConverter.Proxy(two, Factor.ZERO, new Factor[0], new Factor[0]));
        assertInvalid("invalid converter: null step",
                new AffineConverter.Proxy(two, Factor.ZERO, new Factor[] {two, null},
                        new Factor[] {Factor.ZERO, Factor.ZERO}));
        assertInvalid("invalid converter: steps do not compose to x -> 1 * x + 0", new AffineConverter.Proxy(
                Factor.ONE, Factor.ZERO, new Factor[] {two, Factor.ofLong(3)}, new Factor[] {Factor.ZERO, Factor.ZERO}));
        InvalidObjectException e = assertThrows(InvalidObjectException.class, () -> readReplacing(
                Converters.scale(two), o -> o instanceof Factor.Proxy
                        ? new Factor.Proxy(BigInteger.ONE, BigInteger.ZERO, 0, 1) : o));
        assertEquals("invalid factor: division by zero", e.getMessage());
        UomConverter copy = (UomConverter) deserialize(serialize(new AffineConverter.Proxy(Factor.ofLong(6), Factor.ZERO,
                new Factor[] {two, Factor.ofLong(3)}, new Factor[] {Factor.ZERO, Factor.ZERO})));
        assertEquals(List.of(Converters.scale(two), Converters.scale(Factor.ofLong(3))), copy.getConversionSteps());
    }

    @Test
    void forgedUnits() throws Exception {
        UnitAtom m = atom(METRE);
        UnitAtom s = atom(SECOND);
        UomConverter id = Converters.IDENTITY;
        assertInvalid("invalid unit: null converter",
                new UomUnit.Proxy(new UnitAtom[] {m}, new int[] {1}, null, "m", null));
        assertInvalid("invalid unit: zero scale factor",
                new UomUnit.Proxy(new UnitAtom[] {m}, new int[] {1}, Converters.scale(Factor.ZERO), null, null));
        assertInvalid("invalid unit: atoms and exponents do not match", new UomUnit.Proxy(null, null, id, null, null));
        assertInvalid("invalid unit: atoms and exponents do not match",
                new UomUnit.Proxy(new UnitAtom[] {m, s}, new int[] {1}, id, null, null));
        assertInvalid("invalid unit: null atom", new UomUnit.Proxy(new UnitAtom[] {null}, new int[] {1}, id, null, null));
        assertInvalid("invalid unit: duplicate atom m",
                new UomUnit.Proxy(new UnitAtom[] {m, m}, new int[] {1, 2}, id, null, null));
        // Unsorted atoms and zero exponents are normalised on read.
        UomUnit<?> speed = (UomUnit<?>) deserialize(serialize(new UomUnit.Proxy(
                new UnitAtom[] {s, atom(KILOGRAM), m}, new int[] {-1, 0, 1},
                id, "v", "speed")));
        assertEquals(METRE.divide(SECOND), speed);
        assertEquals(List.of(m, s), List.copyOf(speed.atoms().keySet()));
        assertEquals("v", speed.getSymbol());
        assertEquals("speed", speed.getName());
    }

    /** Twin of a class: same field names and types, no proxy. */
    static final class Empty implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    static final class DimensionTwin implements Serializable {
        private static final long serialVersionUID = 1L;
        int length = 1;
        int mass;
        int time;
        int current;
        int temperature;
        int amount;
        int luminosity;
    }

    static final class AtomTwin implements Serializable {
        private static final long serialVersionUID = 1L;
        String symbol = "m";
        UomDimension dimension = UomDimension.LENGTH;
    }

    /**
     * Writes {@code twin}'s fields under {@code target}'s class descriptor: the stream a direct (proxy-less)
     * serialization of {@code target} would produce. Classes reject it in readObject before reading fields.
     */
    private static byte[] forgeAs(Class<?> target, Serializable twin) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes) {
            @Override
            protected void writeClassDescriptor(ObjectStreamClass desc) throws IOException {
                super.writeClassDescriptor(desc.forClass() == twin.getClass() ? ObjectStreamClass.lookup(target) : desc);
            }
        }) {
            out.writeObject(twin);
        }
        return bytes.toByteArray();
    }

    @Test
    void directDeserializationRequiresTheProxy() {
        Map<Class<?>, Serializable> twins = new LinkedHashMap<>();
        twins.put(Factor.class, new Empty());
        twins.put(AffineConverter.class, new Empty());
        twins.put(UomUnit.class, new Empty());
        twins.put(UomDimension.class, new DimensionTwin());
        twins.put(UnitAtom.class, new AtomTwin());
        twins.forEach((target, twin) -> {
            InvalidObjectException e = assertThrows(InvalidObjectException.class,
                    () -> deserialize(forgeAs(target, twin)), target.getName());
            assertEquals("proxy required", e.getMessage(), target.getName());
        });
    }

    private static Object readReplacing(Object root, UnaryOperator<Object> replace) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes) {
            {
                enableReplaceObject(true);
            }

            @Override
            protected Object replaceObject(Object obj) {
                return replace.apply(obj);
            }
        }) {
            out.writeObject(root);
        }
        return deserialize(bytes.toByteArray());
    }

    private static String java() {
        return System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    }

    private static String unitApiJar() {
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (Path.of(entry).getFileName().toString().equals("unit-api-2.2.jar")) {
                return entry;
            }
        }
        Path m2 = Path.of(System.getProperty("user.home"), ".m2/repository/javax/measure/unit-api/2.2/unit-api-2.2.jar");
        Assumptions.assumeTrue(Files.isRegularFile(m2), "unit-api-2.2.jar not found");
        return m2.toString();
    }
}
