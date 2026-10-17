/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.jsr;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;
import javax.measure.UnitConverter;

import org.tinygears.uom.engine.Converter;
import org.tinygears.uom.engine.Factor;
import org.tinygears.uom.engine.internal.SerialForm;

/**
 * The JSR-385 converter over an engine {@link Converter}, which does the arithmetic. Serializable through a proxy
 * that carries the engine converter.
 */
final class AffineConverter implements UomConverter, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Converter engine;
    // the facades this converter was concatenated from, so the steps keep their identity; null: wrap the engine's
    @SuppressWarnings("serial") // the class is written through its Proxy
    private final List<UnitConverter> steps;

    AffineConverter(Converter engine) {
        this(engine, null);
    }

    private AffineConverter(Converter engine, List<UnitConverter> steps) {
        this.engine = engine;
        this.steps = steps;
    }

    /** {@code c} as a JSR-385 converter; the engine's identity gives {@link Converters#IDENTITY}. */
    static UomConverter of(Converter c) {
        return c == Converter.IDENTITY ? Converters.IDENTITY : new AffineConverter(c);
    }

    /** The engine converter of {@code c}, a converter of this library (the interface is sealed). */
    static Converter engine(UomConverter c) {
        return ((AffineConverter) c).engine;
    }

    @Override
    public Factor factor() {
        return engine.factor();
    }

    @Override
    public Factor offset() {
        return engine.offset();
    }

    @Override
    public boolean isIdentity() {
        return engine.isIdentity();
    }

    @Override
    public boolean isLinear() {
        return engine.isLinear();
    }

    @Override
    public UomConverter inverse() {
        return of(engine.inverse());
    }

    @Override
    public UomConverter concatenate(UnitConverter other) {
        if (!(other instanceof UomConverter o)) {
            throw new IllegalArgumentException("not a UomConverter: " + other);
        }
        List<UnitConverter> all = new java.util.ArrayList<>(getConversionSteps());
        all.addAll(o.getConversionSteps());
        return new AffineConverter(engine.concatenate(engine(o)), List.copyOf(all));
    }

    @Override
    public List<UnitConverter> getConversionSteps() {
        if (steps != null) {
            return steps;
        }
        List<Converter> e = engine.steps();
        return e.size() == 1 && e.get(0) == engine
                ? List.of(this)
                : e.stream().map(s -> (UnitConverter) of(s)).toList();
    }

    @Override
    public double convert(double value) {
        return engine.convert(value);
    }

    /**
     * A {@link BigDecimal} or {@link BigInteger} converts exactly where it can, rounded to {@code DECIMAL128}, and
     * gives a {@code BigDecimal}, as the quantity factories make a decimal quantity of both; any other number converts
     * as a {@code double} and gives a {@code Double}.
     *
     * @throws NullPointerException if {@code value} is null
     */
    @Override
    public Number convert(Number value) {
        Objects.requireNonNull(value, "value");
        if (value instanceof BigDecimal b) {
            return convert(b, MathContext.DECIMAL128);
        }
        if (value instanceof BigInteger i) {
            return convert(new BigDecimal(i), MathContext.DECIMAL128);
        }
        return convert(value.doubleValue());
    }

    @Override
    public BigDecimal convert(BigDecimal value, MathContext mc) {
        return engine.convert(value, mc);
    }

    @Serial
    private Object writeReplace() {
        return new Proxy(engine);
    }

    @Serial
    private void readObject(ObjectInputStream in) throws InvalidObjectException {
        throw new InvalidObjectException("proxy required");
    }

    /** Serialized form: the engine converter, which validates itself; package-private so tests can forge streams. */
    static final class Proxy implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        // any serializable object, so tests can forge the engine's proxy
        private transient Serializable engine;

        Proxy(Serializable engine) {
            this.engine = engine;
        }

        @Serial
        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            out.writeUnshared(engine);
        }

        @Serial
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            engine = SerialForm.read(in, Converter.class);
        }

        @Serial
        private Object readResolve() throws InvalidObjectException {
            if (engine == null) {
                throw new InvalidObjectException("invalid converter: null converter");
            }
            return of((Converter) engine);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AffineConverter c && engine.equals(c.engine);
    }

    @Override
    public int hashCode() {
        return engine.hashCode();
    }

    @Override
    public String toString() {
        return engine.toString();
    }
}
