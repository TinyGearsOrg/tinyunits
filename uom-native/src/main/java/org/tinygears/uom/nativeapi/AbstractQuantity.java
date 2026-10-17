/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.nativeapi;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

import org.tinygears.uom.engine.arithmetic.DecimalArithmetic;
import org.tinygears.uom.engine.arithmetic.DoubleArithmetic;
import org.tinygears.uom.engine.arithmetic.Precision;

/**
 * The quantities of the slice: a {@code double} or a decimal with its context, and a unit; the arithmetic is the
 * engine's. The slice has no offset units, so every value is an absolute amount.
 *
 * @param <Q> the quantity type
 */
abstract class AbstractQuantity<Q extends Quantity<Q>> implements Quantity<Q> {
    private final double value;
    // null for a double quantity
    private final BigDecimal decimal;
    private final MathContext mathContext;
    private final Unit<Q> unit;

    AbstractQuantity(double value, BigDecimal decimal, MathContext mathContext, Unit<Q> unit) {
        this.value = value;
        this.decimal = decimal == null ? null : Precision.checked(decimal);
        this.mathContext = decimal == null ? null : Precision.checked(Objects.requireNonNull(mathContext, "mc"));
        this.unit = Objects.requireNonNull(unit, "unit");
    }

    /** A quantity of this type; {@code decimal} null for a {@code double} one. */
    abstract Q create(double value, BigDecimal decimal, MathContext mc, Unit<Q> unit);

    /** Creates quantities of a result type. */
    interface Factory<R extends Quantity<R>> {
        R create(double value, BigDecimal decimal, MathContext mc, Unit<R> unit);
    }

    @Override
    public Number value() {
        return decimal != null ? decimal : (Number) value;
    }

    @Override
    public MathContext mathContext() {
        return mathContext;
    }

    @Override
    public Unit<Q> unit() {
        return unit;
    }

    @Override
    public Q to(Unit<Q> target) {
        if (decimal == null) {
            return create(DoubleArithmetic.convert(value, unit.converterTo(target), false), null, null, target);
        }
        BigDecimal r = DecimalArithmetic.convert(decimal, unit.converterTo(target), false, mathContext);
        return create(0, r, mathContext, target);
    }

    @Override
    public Q add(Q other) {
        return combine(other, false);
    }

    @Override
    public Q subtract(Q other) {
        return combine(other, true);
    }

    private Q combine(Q other, boolean subtract) {
        Unit<Q> u = other.unit();
        if (!isDecimal(this, other)) {
            double r = DoubleArithmetic.combine(
                    value,
                    unit.engine(),
                    other.value().doubleValue(),
                    u.engine(),
                    u.converterTo(unit),
                    false,
                    subtract);
            return create(r, null, null, unit);
        }
        MathContext mc = contextWith(other);
        BigDecimal r = DecimalArithmetic
                .combine(decimal(), unit.engine(), Precision.toDecimal(other.value()), u.engine(), false, subtract, mc);
        return create(0, r, mc, unit);
    }

    @Override
    public Q multiply(Number factor) {
        return scalar(factor, false);
    }

    @Override
    public Q divide(Number divisor) {
        return scalar(divisor, true);
    }

    private Q scalar(Number n, boolean divide) {
        Objects.requireNonNull(n, "number");
        if (decimal == null) {
            return create(
                    DoubleArithmetic.scalar(value, unit.engine(), false, Precision.toDouble(n), divide),
                    null,
                    null,
                    unit);
        }
        BigDecimal r = DecimalArithmetic
                .scalar(decimal, unit.engine(), false, Precision.toDecimal(n), divide, mathContext);
        return create(0, r, mathContext, unit);
    }

    @Override
    public Q toDouble() {
        return create(decimal == null ? value : Precision.toDouble(decimal), null, null, unit);
    }

    @Override
    public Q toDecimal(MathContext mc) {
        return create(0, decimal().round(mc), mc, unit);
    }

    @Override
    public boolean isCloseTo(Q other, double relativeTolerance) {
        Precision.checkRelativeTolerance(relativeTolerance);
        Number n = other.value();
        if (decimalPath(other)) {
            return finite(n) && DecimalArithmetic.isCloseTo(
                    decimal(),
                    unit.engine(),
                    Precision.toDecimal(n),
                    other.unit().engine(),
                    false,
                    BigDecimal.valueOf(relativeTolerance),
                    null,
                    null,
                    contextWith(other));
        }
        return DoubleArithmetic.isCloseTo(
                doubleValue(),
                unit.engine(),
                Precision.toDouble(n),
                other.unit().engine(),
                false,
                relativeTolerance);
    }

    /** Exact if either is decimal; NaN and infinities keep the order of {@link Double#compare}. */
    @Override
    public int compareTo(Q other) {
        Number n = other.value();
        if (decimalPath(other) && finite(n)) {
            return DecimalArithmetic.compare(
                    decimal(),
                    unit.engine(),
                    Precision.toDecimal(n),
                    other.unit().engine(),
                    false,
                    contextWith(other));
        }
        return DoubleArithmetic
                .compare(doubleValue(), unit.engine(), Precision.toDouble(n), other.unit().engine(), false);
    }

    /**
     * {@code sys(this)·sys(other)} or {@code sys(this)/sys(other)} as a quantity of {@code type} in its system unit
     * {@code unit}; decimal if either is.
     */
    <R extends Quantity<R>> R product(Quantity<?> other, boolean divide, Unit<R> unit, Factory<R> type) {
        Number n = other.value();
        if (!isDecimal(this, other)) {
            double r = DoubleArithmetic
                    .product(value, this.unit.engine(), false, n.doubleValue(), other.unit().engine(), false, divide);
            return type.create(r, null, null, unit);
        }
        MathContext mc = contextWith(other);
        BigDecimal r = DecimalArithmetic.product(
                decimal(),
                this.unit.engine(),
                false,
                Precision.toDecimal(n),
                other.unit().engine(),
                false,
                divide,
                mc);
        return type.create(0, r, mc, unit);
    }

    private static boolean isDecimal(Quantity<?> a, Quantity<?> b) {
        return a.mathContext() != null || b.mathContext() != null;
    }

    /** Whether a comparison with {@code other} is decimal: either is, and this value is finite. */
    private boolean decimalPath(Quantity<?> other) {
        return isDecimal(this, other) && (decimal != null || Double.isFinite(value));
    }

    /** False for a NaN or infinite {@code double}; a decimal is finite whatever its magnitude. */
    private static boolean finite(Number n) {
        return Precision.kind(n) != Precision.Kind.DOUBLE || Double.isFinite(n.doubleValue());
    }

    private double doubleValue() {
        return decimal == null ? value : Precision.toDouble(decimal);
    }

    /** This value as a decimal: a double as its shortest decimal. */
    private BigDecimal decimal() {
        return decimal != null ? decimal : Precision.toDecimal(value);
    }

    /** The lower context of the two; a {@code double} operand adopts the other's. */
    private MathContext contextWith(Quantity<?> other) {
        MathContext o = other.mathContext();
        if (mathContext == null || o == null) {
            return mathContext != null ? mathContext : o;
        }
        return Precision.lower(mathContext, o);
    }

    /** Same class, unit and number type, and a numerically equal value ({@code 1.0 m} equals {@code 1.00 m}). */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AbstractQuantity<?> q) || getClass() != q.getClass() || !unit.equals(q.unit)) {
            return false;
        }
        return decimal == null
                ? q.decimal == null && Double.compare(value, q.value) == 0
                : q.decimal != null && decimal.compareTo(q.decimal) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * unit.hashCode()
                + (decimal == null ? Double.hashCode(value) : decimal.stripTrailingZeros().hashCode());
    }

    @Override
    public String toString() {
        return value() + " " + unit;
    }
}
