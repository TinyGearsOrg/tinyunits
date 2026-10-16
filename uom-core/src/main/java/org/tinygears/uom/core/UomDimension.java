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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.measure.Dimension;

/**
 * Product of the seven SI base dimensions with integer exponents; serializable through a proxy.
 *
 * @param length the exponent of length
 * @param mass the exponent of mass
 * @param time the exponent of time
 * @param current the exponent of electric current
 * @param temperature the exponent of temperature
 * @param amount the exponent of amount of substance
 * @param luminosity the exponent of luminous intensity
 */
public record UomDimension(int length, int mass, int time, int current, int temperature, int amount, int luminosity)
        implements Dimension, Serializable {

    public static final UomDimension NONE = new UomDimension(0, 0, 0, 0, 0, 0, 0);
    public static final UomDimension LENGTH = new UomDimension(1, 0, 0, 0, 0, 0, 0);
    public static final UomDimension MASS = new UomDimension(0, 1, 0, 0, 0, 0, 0);
    public static final UomDimension TIME = new UomDimension(0, 0, 1, 0, 0, 0, 0);
    public static final UomDimension CURRENT = new UomDimension(0, 0, 0, 1, 0, 0, 0);
    public static final UomDimension TEMPERATURE = new UomDimension(0, 0, 0, 0, 1, 0, 0);
    public static final UomDimension AMOUNT = new UomDimension(0, 0, 0, 0, 0, 1, 0);
    public static final UomDimension LUMINOSITY = new UomDimension(0, 0, 0, 0, 0, 0, 1);

    private static final String SYMBOLS = "LMTIΘNJ";

    private static final UomDimension[] BASES = { LENGTH, MASS, TIME, CURRENT, TEMPERATURE, AMOUNT, LUMINOSITY };

    private int[] exponents() {
        return new int[] { length, mass, time, current, temperature, amount, luminosity };
    }

    private static UomDimension of(int[] e) {
        return new UomDimension(e[0], e[1], e[2], e[3], e[4], e[5], e[6]);
    }

    private static UomDimension from(Dimension d) {
        if (d instanceof UomDimension u) {
            return u;
        }
        Map<? extends Dimension, Integer> m = d.getBaseDimensions();
        if (m == null) {
            throw new IllegalArgumentException("Unsupported dimension: " + d);
        }
        UomDimension result = NONE;
        for (Map.Entry<? extends Dimension, Integer> en : m.entrySet()) {
            if (!(en.getKey() instanceof UomDimension k)) {
                throw new IllegalArgumentException("Unsupported dimension: " + d);
            }
            if (en.getValue() == null) {
                throw new IllegalArgumentException("Unsupported dimension: " + d);
            }
            result = result.multiply(k.pow(en.getValue()));
        }
        return result;
    }

    @Override
    public UomDimension multiply(Dimension multiplicand) {
        int[] a = exponents();
        int[] b = from(multiplicand).exponents();
        for (int i = 0; i < a.length; i++) {
            a[i] = Math.addExact(a[i], b[i]);
        }
        return of(a);
    }

    @Override
    public UomDimension divide(Dimension divisor) {
        int[] a = exponents();
        int[] b = from(divisor).exponents();
        for (int i = 0; i < a.length; i++) {
            a[i] = Math.subtractExact(a[i], b[i]);
        }
        return of(a);
    }

    @Override
    public UomDimension pow(int n) {
        int[] a = exponents();
        for (int i = 0; i < a.length; i++) {
            a[i] = Math.multiplyExact(a[i], n);
        }
        return of(a);
    }

    @Override
    public UomDimension root(int n) {
        if (n == 0) {
            throw new ArithmeticException("Zeroth root");
        }
        int[] a = exponents();
        for (int i = 0; i < a.length; i++) {
            if (a[i] % n != 0) {
                throw new ArithmeticException("Dimension " + this + " has no integer " + n + "-th root");
            }
            a[i] = Math.toIntExact((long) a[i] / n);
        }
        return of(a);
    }

    @Override
    public Map<UomDimension, Integer> getBaseDimensions() {
        int[] e = exponents();
        Map<UomDimension, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < e.length; i++) {
            if (e[i] != 0) {
                m.put(BASES[i], e[i]);
            }
        }
        // Per the Dimension contract a base dimension reports null.
        return m.size() == 1 && m.containsValue(1) ? null : m;
    }

    /**
     * The serialization proxy.
     *
     * @return the proxy
     */
    @Serial
    private Object writeReplace() {
        return new Proxy(exponents());
    }

    /**
     * Rejects a stream without the proxy; a record ignores readObject, so the check is here.
     *
     * @return never
     * @throws InvalidObjectException always
     */
    @Serial
    private Object readResolve() throws InvalidObjectException {
        throw new InvalidObjectException("proxy required");
    }

    /** Serialized form of a dimension; package-private so tests can forge streams. */
    static final class Proxy implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private static final String TOO_MANY = "invalid dimension: too many exponents";

        private transient int[] exponents;

        Proxy(int[] exponents) {
            this.exponents = exponents;
        }

        @Serial
        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            SerialForm.writeInts(out, exponents, TOO_MANY);
        }

        @Serial
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            exponents = SerialForm.readInts(in, TOO_MANY);
        }

        @Serial
        private Object readResolve() throws InvalidObjectException {
            if (exponents == null) {
                throw new InvalidObjectException("invalid dimension: null exponents");
            }
            if (exponents.length != BASES.length) {
                throw new InvalidObjectException(
                        "invalid dimension: expected " + BASES.length + " exponents, got " + exponents.length);
            }
            return of(exponents);
        }
    }

    @Override
    public String toString() {
        int[] e = exponents();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < e.length; i++) {
            if (e[i] != 0) {
                if (!sb.isEmpty()) {
                    sb.append('·');
                }
                sb.append(SYMBOLS.charAt(i));
                if (e[i] != 1) {
                    sb.append('^').append(e[i]);
                }
            }
        }
        return sb.isEmpty() ? "1" : sb.toString();
    }
}
