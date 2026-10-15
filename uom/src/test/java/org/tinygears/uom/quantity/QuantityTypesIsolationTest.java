/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.quantity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;
import javax.measure.Unit;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.core.quantity.AbstractDoubleQuantity;

import static org.junit.jupiter.api.Assertions.*;

// Fresh loaders, so the alternate-unit registry and factory initialisation start clean.
class QuantityTypesIsolationTest {
    private static final String PKG = "org.tinygears.uom.quantity.";

    @Test
    void conflictingAlternateOnlyBreaksItsOwnType() throws Exception {
        try (Loader l = new Loader()) {
            Class<?> baseUnits = Class.forName("org.tinygears.uom.core.BaseUnits", true, l);
            Object metre = baseUnits.getField("METRE").get(null);
            metre.getClass().getMethod("alternate", String.class).invoke(metre, "Hz");

            Class<?> types = Class.forName(PKG + "QuantityTypes", true, l);
            Method factory = types.getMethod("factory", Class.class);
            Object length = factory.invoke(null, l.javax("Length"));
            assertSame(Class.forName(PKG + "LengthFactory", true, l).getField("INSTANCE").get(null), length);
            assertEquals(33, ((Set<?>) types.getMethod("types").invoke(null)).size());

            var first = assertThrows(InvocationTargetException.class, () -> factory.invoke(null, l.javax("Frequency")));
            assertInstanceOf(ExceptionInInitializerError.class, first.getCause());
            assertTrue(rootCause(first).getMessage().contains("Hz"), rootCause(first).toString());
            var second = assertThrows(
                    InvocationTargetException.class,
                    () -> factory.invoke(null, l.javax("Frequency")));
            assertInstanceOf(NoClassDefFoundError.class, second.getCause());

            for (String name : new String[] { "Mass", "Time", "Radioactivity" }) {
                assertNotNull(factory.invoke(null, l.javax(name)), name);
            }
        }
    }

    @Test
    void typesDoesNotInitialiseFactories() throws Exception {
        try (Loader l = new Loader()) {
            Class<?> types = Class.forName(PKG + "QuantityTypes", true, l);
            assertEquals(33, ((Set<?>) types.getMethod("types").invoke(null)).size());
            assertNull(l.loaded(PKG + "LengthFactory"));
            assertNull(l.loaded(PKG + "FrequencyFactory"));
        }
    }

    private static Throwable rootCause(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    private static URL codeSource(Class<?> c) {
        return c.getProtectionDomain().getCodeSource().getLocation();
    }

    private static final class Loader extends URLClassLoader {
        Loader() {
            super(new URL[] {
                codeSource(Length.class),
                codeSource(AbstractDoubleQuantity.class),
                codeSource(Unit.class) }, ClassLoader.getPlatformClassLoader());
        }

        Class<?> loaded(String name) {
            return findLoadedClass(name);
        }

        Class<?> javax(String simple) throws ClassNotFoundException {
            return Class.forName("javax.measure.quantity." + simple, false, this);
        }
    }
}
