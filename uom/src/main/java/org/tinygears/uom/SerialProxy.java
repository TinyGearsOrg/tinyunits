/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.tinygears.uom.engine.arithmetic.Precision;
import org.tinygears.uom.engine.internal.SerialForm;
import org.tinygears.uom.quantity.QuantityTypes;

/**
 * The serialized forms of units and quantities: a unit as its type name and engine unit, a quantity as its type
 * name, unit, scale and exact value (the raw {@code double} bits, or the unscaled bytes and scale of a decimal with
 * its context). Reading names no class the stream chooses: types are looked up among the quantity types, and every
 * bound and the unit's dimension are checked. The form may change before 1.0.
 */
final class SerialProxy {
    /** Bound on the unscaled bytes of a decimal value. */
    static final int MAX_VALUE_BYTES = 65_536;

    private static final Map<String, Class<?>> TYPES = Stream
            .concat(QuantityTypes.types().stream(), Stream.of(Untyped.class))
            .collect(Collectors.toUnmodifiableMap(Class::getName, Function.identity()));

    private SerialProxy() {
    }

    static Class<?> type(String name, String what) throws InvalidObjectException {
        if (name == null) {
            throw new InvalidObjectException("invalid " + what + ": null type");
        }
        Class<?> t = TYPES.get(name);
        if (t == null) {
            throw new InvalidObjectException("invalid " + what + ": unknown type " + name);
        }
        return t;
    }

    /** Serialized form of a unit. */
    static final class OfUnit implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private transient String type;
        private transient org.tinygears.uom.engine.Unit engine;

        OfUnit(Unit<?> unit) {
            this.type = unit.type().getName();
            this.engine = unit.engine();
        }

        @Serial
        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            out.writeUnshared(type);
            out.writeUnshared(engine);
        }

        @Serial
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            type = SerialForm.readString(in, "invalid unit: type");
            engine = SerialForm.read(in, org.tinygears.uom.engine.Unit.class);
        }

        // the type was looked up among the quantity types
        @SuppressWarnings({ "unchecked", "rawtypes" })
        @Serial
        private Object readResolve() throws InvalidObjectException {
            Class t = type(type, "unit");
            if (engine == null) {
                throw new InvalidObjectException("invalid unit: null engine unit");
            }
            try {
                return Unit.of(t, engine);
            } catch (IllegalArgumentException e) {
                throw (InvalidObjectException) new InvalidObjectException("invalid unit: " + e.getMessage())
                        .initCause(e);
            }
        }
    }

    /** Serialized form of a quantity. */
    static final class OfQuantity implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private transient String type;
        private transient Unit<?> unit;
        private transient String scale;
        private transient boolean decimal;
        private transient long bits;
        private transient byte[] unscaled;
        private transient int decimalScale;
        private transient int precision;
        private transient String roundingMode;

        OfQuantity(Quantity<?> q) throws NotSerializableException {
            this.type = q.unit().type().getName();
            this.unit = q.unit();
            this.scale = q.scale().name();
            this.decimal = q.value() instanceof BigDecimal;
            if (q.value() instanceof BigDecimal d) {
                unscaled = d.unscaledValue().toByteArray();
                if (unscaled.length > MAX_VALUE_BYTES) {
                    throw new NotSerializableException("invalid quantity: value too large");
                }
                decimalScale = d.scale();
                precision = q.mathContext().getPrecision();
                roundingMode = q.mathContext().getRoundingMode().name();
            } else {
                bits = Double.doubleToRawLongBits(q.value().doubleValue());
            }
        }

        @Serial
        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            out.writeUnshared(type);
            out.writeUnshared(unit);
            out.writeUnshared(scale);
            out.writeBoolean(decimal);
            if (decimal) {
                SerialForm.writeBytes(out, unscaled);
                out.writeInt(decimalScale);
                out.writeInt(precision);
                out.writeUnshared(roundingMode);
            } else {
                out.writeLong(bits);
            }
        }

        @Serial
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            type = SerialForm.readString(in, "invalid quantity: type");
            unit = SerialForm.read(in, Unit.class);
            scale = SerialForm.readString(in, "invalid quantity: scale");
            decimal = in.readBoolean();
            if (decimal) {
                unscaled = SerialForm.readBytes(in, MAX_VALUE_BYTES, "invalid quantity: value too large");
                decimalScale = in.readInt();
                precision = in.readInt();
                roundingMode = SerialForm.readString(in, "invalid quantity: rounding mode");
            } else {
                bits = in.readLong();
            }
        }

        // the type was looked up among the quantity types, and the unit is checked to be one of it
        @SuppressWarnings({ "unchecked", "rawtypes" })
        @Serial
        private Object readResolve() throws InvalidObjectException {
            Class t = type(type, "quantity");
            if (unit == null) {
                throw invalid("null unit");
            }
            Scale s = scale();
            try {
                Unit u = unit.asType(t);
                if (!decimal) {
                    double v = Double.longBitsToDouble(bits);
                    return t == Untyped.class
                            ? Untyped.of(v, u, s)
                            : QuantityTypes.factory(t).create(v, null, null, u, s);
                }
                BigDecimal v = value();
                MathContext mc = mathContext();
                return t == Untyped.class ? Untyped.of(v, u, s, mc) : QuantityTypes.factory(t).create(0, v, mc, u, s);
            } catch (RuntimeException e) {
                throw (InvalidObjectException) invalid(e.getMessage() != null ? e.getMessage() : e.getClass().getName())
                        .initCause(e);
            }
        }

        private Scale scale() throws InvalidObjectException {
            for (Scale s : Scale.values()) {
                if (s.name().equals(scale)) {
                    return s;
                }
            }
            throw invalid(scale == null ? "null scale" : "unknown scale " + scale);
        }

        private BigDecimal value() throws InvalidObjectException {
            if (unscaled == null || unscaled.length == 0) {
                throw invalid(unscaled == null ? "null value" : "empty value");
            }
            return Precision.checked(new BigDecimal(new BigInteger(unscaled), decimalScale));
        }

        private MathContext mathContext() throws InvalidObjectException {
            if (precision < 0 || precision > Precision.MAX_PRECISION) {
                throw invalid("precision out of range: " + precision);
            }
            for (RoundingMode m : RoundingMode.values()) {
                if (m.name().equals(roundingMode)) {
                    return new MathContext(precision, m);
                }
            }
            throw invalid(roundingMode == null ? "null rounding mode" : "unknown rounding mode " + roundingMode);
        }

        private static InvalidObjectException invalid(String message) {
            return new InvalidObjectException("invalid quantity: " + message);
        }
    }
}
