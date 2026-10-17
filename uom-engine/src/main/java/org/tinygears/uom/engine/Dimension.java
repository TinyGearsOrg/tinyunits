/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.engine;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;

import org.tinygears.uom.engine.internal.SerialForm;

/**
 * Product of the seven SI base dimensions with integer exponents; arithmetic overflows throw
 * {@link ArithmeticException}. Serializable through a proxy.
 *
 * @param length the exponent of length
 * @param mass the exponent of mass
 * @param time the exponent of time
 * @param current the exponent of electric current
 * @param temperature the exponent of temperature
 * @param amount the exponent of amount of substance
 * @param luminosity the exponent of luminous intensity
 */
public record Dimension(int length, int mass, int time, int current, int temperature, int amount, int luminosity)
        implements Serializable {

    /** No dimension. */
    public static final Dimension NONE = new Dimension(0, 0, 0, 0, 0, 0, 0);
    /** Length, {@code L}. */
    public static final Dimension LENGTH = new Dimension(1, 0, 0, 0, 0, 0, 0);
    /** Mass, {@code M}. */
    public static final Dimension MASS = new Dimension(0, 1, 0, 0, 0, 0, 0);
    /** Time, {@code T}. */
    public static final Dimension TIME = new Dimension(0, 0, 1, 0, 0, 0, 0);
    /** Electric current, {@code I}. */
    public static final Dimension CURRENT = new Dimension(0, 0, 0, 1, 0, 0, 0);
    /** Temperature, {@code Θ}. */
    public static final Dimension TEMPERATURE = new Dimension(0, 0, 0, 0, 1, 0, 0);
    /** Amount of substance, {@code N}. */
    public static final Dimension AMOUNT = new Dimension(0, 0, 0, 0, 0, 1, 0);
    /** Luminous intensity, {@code J}. */
    public static final Dimension LUMINOSITY = new Dimension(0, 0, 0, 0, 0, 0, 1);

    private static final String SYMBOLS = "LMTIΘNJ";
    private static final int BASES = 7;

    /**
     * The exponents in the order of the components.
     *
     * @return a new array of seven exponents
     */
    public int[] exponents() {
        return new int[] { length, mass, time, current, temperature, amount, luminosity };
    }

    /**
     * The dimension with these exponents.
     *
     * @param e seven exponents in the order of the components
     * @return the dimension
     * @throws IllegalArgumentException if {@code e} does not have seven elements
     */
    public static Dimension of(int[] e) {
        if (e.length != BASES) {
            throw new IllegalArgumentException("expected " + BASES + " exponents, got " + e.length);
        }
        return new Dimension(e[0], e[1], e[2], e[3], e[4], e[5], e[6]);
    }

    /**
     * The product.
     *
     * @param other the other dimension
     * @return this times {@code other}
     */
    public Dimension multiply(Dimension other) {
        int[] a = exponents();
        int[] b = other.exponents();
        for (int i = 0; i < BASES; i++) {
            a[i] = Math.addExact(a[i], b[i]);
        }
        return of(a);
    }

    /**
     * The quotient.
     *
     * @param other the divisor
     * @return this divided by {@code other}
     */
    public Dimension divide(Dimension other) {
        int[] a = exponents();
        int[] b = other.exponents();
        for (int i = 0; i < BASES; i++) {
            a[i] = Math.subtractExact(a[i], b[i]);
        }
        return of(a);
    }

    /**
     * The {@code n}-th power.
     *
     * @param n the exponent
     * @return this to the power {@code n}
     */
    public Dimension pow(int n) {
        int[] a = exponents();
        for (int i = 0; i < BASES; i++) {
            a[i] = Math.multiplyExact(a[i], n);
        }
        return of(a);
    }

    /**
     * The {@code n}-th root; every exponent must be a multiple of {@code n}.
     *
     * @param n the root index, not zero
     * @return the root
     * @throws ArithmeticException if {@code n} is zero or an exponent is not a multiple of it
     */
    public Dimension root(int n) {
        if (n == 0) {
            throw new ArithmeticException("Zeroth root");
        }
        int[] a = exponents();
        for (int i = 0; i < BASES; i++) {
            if (a[i] % n != 0) {
                throw new ArithmeticException("Dimension " + this + " has no integer " + n + "-th root");
            }
            a[i] = Math.toIntExact((long) a[i] / n);
        }
        return of(a);
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

    /** Serialized form of a dimension. */
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
            if (exponents.length != BASES) {
                throw new InvalidObjectException(
                        "invalid dimension: expected " + BASES + " exponents, got " + exponents.length);
            }
            return of(exponents);
        }
    }

    @Override
    public String toString() {
        int[] e = exponents();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < BASES; i++) {
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
