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

import java.io.Serializable;
import java.lang.reflect.Constructor;

import org.tinygears.uom.engine.Dimension;
import org.tinygears.uom.engine.UnitAtom;

/**
 * Builds the serialization proxies of the engine, which are package-private there, to forge streams. The tests run on
 * the class path, where reflection reaches them.
 */
final class EngineProxies {

    private EngineProxies() {
    }

    /** The proxy of a unit atom, as {@code UnitAtom.Proxy(symbol, dimension, definitionAtoms, definitionExponents)}. */
    static Serializable atom(
            String symbol,
            Dimension dimension,
            UnitAtom[] definitionAtoms,
            int[] definitionExponents
    ) {
        try {
            Class<?> c = Class.forName("org.tinygears.uom.engine.UnitAtom$Proxy");
            Constructor<?> k = c.getDeclaredConstructor(String.class, Dimension.class, UnitAtom[].class, int[].class);
            k.setAccessible(true);
            return (Serializable) k.newInstance(symbol, dimension, definitionAtoms, definitionExponents);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The proxy of an engine dimension, as {@code Dimension.Proxy(exponents)}. */
    static Serializable dimension(int[] exponents) {
        try {
            Constructor<?> k = Class.forName("org.tinygears.uom.engine.Dimension$Proxy")
                    .getDeclaredConstructor(int[].class);
            k.setAccessible(true);
            return (Serializable) k.newInstance((Object) exponents);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Whether {@code o} is the proxy of an engine dimension. */
    static boolean isDimension(Object o) {
        return o.getClass().getName().equals("org.tinygears.uom.engine.Dimension$Proxy");
    }
}
