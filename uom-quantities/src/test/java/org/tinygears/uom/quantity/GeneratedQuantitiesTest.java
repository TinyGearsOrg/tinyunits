// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.quantity;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.UnconvertibleException;
import javax.measure.spi.ServiceProvider;
import org.junit.jupiter.api.Test;
import org.tinygears.uom.core.UomUnit;

class GeneratedQuantitiesTest {

    static final UomUnit<javax.measure.quantity.Length> METRE = LengthFactory.SYSTEM_UNIT;
    static final UomUnit<javax.measure.quantity.Length> KILOMETRE = METRE.multiply(1000);
    static final UomUnit<javax.measure.quantity.Time> SECOND = TimeFactory.SYSTEM_UNIT;
    static final UomUnit<javax.measure.quantity.Time> HOUR = SECOND.multiply(3600);
    static final UomUnit<javax.measure.quantity.Temperature> KELVIN = TemperatureFactory.SYSTEM_UNIT;
    static final UomUnit<javax.measure.quantity.Temperature> CELSIUS = KELVIN.shift(273.15);
    static final UomUnit<javax.measure.quantity.Frequency> HZ = FrequencyFactory.SYSTEM_UNIT;
    static final UomUnit<javax.measure.quantity.Radioactivity> BQ = RadioactivityFactory.SYSTEM_UNIT;

    @SuppressWarnings("unchecked")
    static <Q extends Quantity<Q>> Unit<Q> foreignUnit() {
        return (Unit<Q>) Proxy.newProxyInstance(GeneratedQuantitiesTest.class.getClassLoader(),
                new Class<?>[] {Unit.class}, (p, m, a) -> {
                    if (m.getName().equals("toString")) {
                        return "foreign";
                    }
                    throw new UnsupportedOperationException(m.getName());
                });
    }

    @Test
    void typedFactoryAndArithmetic() {
        Length sum = Length.of(1, KILOMETRE).add(Length.of(500, METRE));
        assertEquals(1.5, sum.getValue().doubleValue());
        assertEquals(KILOMETRE, sum.getUnit());
        Length diff = sum.subtract(Length.of(500, METRE));
        Length neg = sum.negate();
        Length twice = sum.multiply(2);
        Length quarter = sum.divide(4);
        assertEquals(1.0, diff.getValue().doubleValue());
        assertEquals(-1.5, neg.getValue().doubleValue());
        assertEquals(3.0, twice.getValue().doubleValue());
        assertEquals(0.375, quarter.getValue().doubleValue());
        Length m = sum.to(METRE);
        assertEquals(1500.0, m.getValue().doubleValue());
        assertEquals(METRE, m.getUnit());
    }

    // The parameter Quantity<javax Length> rejects other quantity types at compile time.
    @Test
    void typesDoNotMix() throws Exception {
        assertAddTakes(Length.class, javax.measure.quantity.Length.class);
        assertAddTakes(Mass.class, javax.measure.quantity.Mass.class);
    }

    private static void assertAddTakes(Class<?> typed, Class<?> javaxType) throws Exception {
        var add = typed.getMethod("add", Quantity.class);
        assertEquals(typed, add.getReturnType());
        var param = assertInstanceOf(java.lang.reflect.ParameterizedType.class, add.getGenericParameterTypes()[0]);
        assertEquals(Quantity.class, param.getRawType());
        assertArrayEquals(new Object[] {javaxType}, param.getActualTypeArguments());
    }

    @Test
    void temperatureOffsets() {
        assertEquals(373.15, Temperature.of(100, CELSIUS).to(KELVIN).getValue().doubleValue(), 1e-12);
        assertTrue(Temperature.of(2, CELSIUS).add(Temperature.of(2, KELVIN))
                .isEquivalentTo(Temperature.of(4, CELSIUS)));
        // Javadoc rule applied literally: 275.15 K + 274.15 K = 549.3 K = 276.15 degC
        assertEquals(276.15, Temperature.of(2, CELSIUS).add(Temperature.of(1, CELSIUS)).getValue().doubleValue(),
                1e-12);
    }

    @Test
    void speedFromLengthAndTime() {
        Quantity<?> q = Length.of(36, KILOMETRE).divide(Time.of(1, HOUR));
        Speed v = (Speed) q.asType(javax.measure.quantity.Speed.class);
        assertEquals(10.0, v.getValue().doubleValue(), 1e-12);
        assertEquals(SpeedFactory.SYSTEM_UNIT, v.getUnit());
        assertThrows(ClassCastException.class, () -> q.asType(javax.measure.quantity.Mass.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void alternateUnits() {
        Frequency freq = Frequency.of(2, HZ);
        assertEquals(2.0, Radioactivity.of(2, BQ).getValue().doubleValue());
        assertNotEquals(HZ, BQ);
        assertThrows(ClassCastException.class, () -> freq.asType(javax.measure.quantity.Radioactivity.class));
        assertTrue(((Unit) HZ).isEquivalentTo(BQ));
        assertTrue(((Unit) BQ).isEquivalentTo(HZ));
        assertTrue(((Unit) HZ).getConverterTo(BQ).isIdentity());
        assertTrue(((Unit) BQ).getConverterTo(HZ).isIdentity());
    }

    @Test
    void dimensionlessAndArea() {
        assertEquals(0.5, Dimensionless.of(0.5, DimensionlessFactory.SYSTEM_UNIT).getValue().doubleValue());
        assertEquals(2.0, Area.of(2, AreaFactory.SYSTEM_UNIT).getValue().doubleValue());
        Quantity<?> q = Length.of(2, METRE).multiply(Length.of(3, METRE));
        assertEquals(AreaFactory.SYSTEM_UNIT, q.getUnit());
        Area a = (Area) q.asType(javax.measure.quantity.Area.class);
        assertEquals(6.0, a.getValue().doubleValue());
    }

    @Test
    void equalsCompareToString() {
        assertEquals(LengthFactory.INSTANCE.of(2, METRE), LengthFactory.INSTANCE.create(2, METRE));
        assertTrue(Length.of(1, KILOMETRE).compareTo(Length.of(999, METRE)) > 0);
        assertEquals(0, Length.of(1, KILOMETRE).compareTo(Length.of(1000, METRE)));
        Unit<javax.measure.quantity.Length> km = KILOMETRE.named("km", "kilometre");
        assertEquals("1.5 km", Length.of(1.5, km).toString());
        assertEquals(Length.of(1, METRE).hashCode(), Length.of(1, METRE).hashCode());
        assertNotEquals(Length.of(1, METRE), Mass.of(1, MassFactory.SYSTEM_UNIT));
    }

    @Test
    void serviceProviderRoundTrip() {
        var factory = ServiceProvider.current().getQuantityFactory(javax.measure.quantity.Speed.class);
        Quantity<javax.measure.quantity.Speed> s = factory.create(3, SpeedFactory.SYSTEM_UNIT);
        assertInstanceOf(Speed.class, s);
        assertEquals(3.0, s.getValue().doubleValue());
        Unit<javax.measure.quantity.Speed> foreign = foreignUnit();
        assertThrows(UnconvertibleException.class, () -> factory.create(3, foreign));
        assertThrows(NullPointerException.class, () -> factory.create(null, SpeedFactory.SYSTEM_UNIT));
    }

    @Test
    void foreignUnitIsRejected() {
        Unit<javax.measure.quantity.Length> foreign = foreignUnit();
        assertThrows(UnconvertibleException.class, () -> Length.of(1, foreign));
        assertThrows(NullPointerException.class, () -> Length.of(1, null));
    }

    @Test
    void threadSafety() throws Exception {
        Length a = Length.of(1, KILOMETRE);
        Length b = Length.of(500, METRE);
        Length expected = a.add(b);
        CountDownLatch gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                results.add(pool.submit(() -> {
                    gate.await();
                    boolean ok = true;
                    for (int i = 0; i < 10_000; i++) {
                        ok &= expected.equals(a.add(b));
                    }
                    return ok;
                }));
            }
            gate.countDown();
            for (Future<Boolean> f : results) {
                assertTrue(f.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // Fresh loaders, so the first touch of a generated class really is the one under test.
    @Test
    void staticInitOrder() throws Exception {
        URL[] urls = {codeSource(Length.class), codeSource(org.tinygears.uom.core.quantity.AbstractDoubleQuantity.class),
            codeSource(Unit.class)};
        try (Loader l = new Loader(urls)) {
            assertNull(l.loaded("org.tinygears.uom.quantity.QuantityTypes"));
            assertNull(l.loaded("org.tinygears.uom.quantity.Length"));
            initOrderCheck(l, "QuantityTypes", "Length");
        }
        try (Loader l = new Loader(urls)) {
            initOrderCheck(l, "Length", "QuantityTypes");
        }
    }

    private static URL codeSource(Class<?> c) {
        return c.getProtectionDomain().getCodeSource().getLocation();
    }

    private static final class Loader extends URLClassLoader {
        Loader(URL[] urls) {
            super(urls, ClassLoader.getPlatformClassLoader());
        }

        Class<?> loaded(String name) {
            return findLoadedClass(name);
        }
    }

    private static void initOrderCheck(ClassLoader l, String first, String second) throws Exception {
        touch(l, first);
        touch(l, second);
        String pkg = "org.tinygears.uom.quantity.";
        Class<?> factory = Class.forName(pkg + "LengthFactory", true, l);
        assertNotNull(factory.getField("SYSTEM_UNIT").get(null));
        assertNotNull(factory.getField("INSTANCE").get(null));
        Class<?> types = Class.forName(pkg + "QuantityTypes", true, l);
        assertEquals(10, ((java.util.Set<?>) types.getMethod("types").invoke(null)).size());
    }

    private static void touch(ClassLoader l, String simple) throws Exception {
        String pkg = "org.tinygears.uom.quantity.";
        Class<?> c = Class.forName(pkg + simple, true, l);
        if (simple.equals("Length")) {
            Class<?> unitClass = Class.forName("javax.measure.Unit", true, l);
            Object unit = Class.forName(pkg + "LengthFactory", true, l).getField("SYSTEM_UNIT").get(null);
            assertNotNull(c.getMethod("of", double.class, unitClass).invoke(null, 1.5, unit));
        } else {
            Class<?> lt = Class.forName("javax.measure.quantity.Length", true, l);
            assertNotNull(c.getMethod("factory", Class.class).invoke(null, lt));
        }
    }
}
