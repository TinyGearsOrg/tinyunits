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

import org.tinygears.uom.engine.Converter;
import org.tinygears.uom.engine.Dimension;
import org.tinygears.uom.engine.Factor;
import org.tinygears.uom.engine.UnitAtom;
import org.tinygears.uom.engine.internal.BoundedCache;

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

    /** The proxy of an engine converter, as {@code Converter.Proxy(factor, offset, stepFactors, stepOffsets)}. */
    static Serializable converter(Factor factor, Factor offset, Factor[] stepFactors, Factor[] stepOffsets) {
        try {
            Constructor<?> k = Class.forName("org.tinygears.uom.engine.Converter$Proxy")
                    .getDeclaredConstructor(Factor.class, Factor.class, Factor[].class, Factor[].class);
            k.setAccessible(true);
            return (Serializable) k.newInstance(factor, offset, stepFactors, stepOffsets);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The proxy of an engine unit, as {@code Unit.Proxy(atoms, exponents, toSystem, symbol, name)}. */
    static Serializable unit(UnitAtom[] atoms, int[] exponents, Converter toSystem, String symbol, String name) {
        try {
            Constructor<?> k = Class.forName("org.tinygears.uom.engine.Unit$Proxy")
                    .getDeclaredConstructor(UnitAtom[].class, int[].class, Converter.class, String.class, String.class);
            k.setAccessible(true);
            return (Serializable) k.newInstance(atoms, exponents, toSystem, symbol, name);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The converter cache of the engine unit of {@code u}. */
    @SuppressWarnings("unchecked") // the field's declared type
    static BoundedCache<org.tinygears.uom.engine.Unit, ?> cache(UomUnit<?> u) {
        try {
            java.lang.reflect.Field f = org.tinygears.uom.engine.Unit.class.getDeclaredField("converterCache");
            f.setAccessible(true);
            return (BoundedCache<org.tinygears.uom.engine.Unit, ?>) f.get(u.engine());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
