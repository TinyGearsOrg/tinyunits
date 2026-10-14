// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.quantity;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;
import java.util.function.DoubleBinaryOperator;
import javax.measure.Quantity;
import javax.measure.UnconvertibleException;
import javax.measure.Unit;
import javax.measure.spi.QuantityFactory;
import javax.measure.spi.SystemOfUnits;
import org.tinygears.uom.core.UomConverter;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.core.spi.Providers;

/**
 * Base class of the generated {@code double} quantities; {@code R} is the typed interface returned by
 * conversions. Operands of any precision are read as {@code double}s, so results stay {@code double}; a
 * {@link BigDecimal} or {@link java.math.BigInteger} outside the {@code double} range throws
 * {@link ArithmeticException}. Immutable.
 * <p>
 * Serializable through a proxy (see {@link #writeReplace()}); the serialized form may change before 1.0. Reading
 * rebuilds the quantity through the factory of the provider current at read time, then restores the exact value and
 * scale: a user subclass, or a quantity read under another top-priority provider, comes back as that provider's
 * class (so it no longer equals the original), and a provider without factories for the standard types makes typed
 * reads fail.
 *
 * @param <Q> the quantity type
 * @param <R> the typed interface returned by conversions
 */
public abstract class AbstractDoubleQuantity<Q extends Quantity<Q>, R extends Q>
        implements Quantity<Q>, Comparable<Quantity<Q>>, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Relative tolerance of {@link #isEquivalentTo}. */
    public static final double EQUIVALENCE_TOLERANCE = 1e-12;

    /** The value. */
    private final double value;
    /** The unit. */
    private final UomUnit<Q> unit;
    /** The scale. */
    private final Quantity.Scale scale;

    /**
     * A quantity.
     *
     * @param value the value
     * @param unit the unit
     * @param scale the scale
     */
    protected AbstractDoubleQuantity(double value, UomUnit<Q> unit, Quantity.Scale scale) {
        this.value = value;
        this.unit = Objects.requireNonNull(unit, "unit");
        this.scale = Objects.requireNonNull(scale, "scale");
    }

    /**
     * Creates a {@code double} quantity of the same typed interface.
     *
     * @param value the value
     * @param unit the unit
     * @param scale the scale
     * @return the quantity
     */
    protected abstract R create(double value, UomUnit<Q> unit, Quantity.Scale scale);

    /**
     * Creates the {@link BigDecimal} quantity of the same typed interface.
     *
     * @param value the value
     * @param unit the unit
     * @param scale the scale
     * @param mc the context
     * @return the quantity
     */
    protected abstract R createDecimal(BigDecimal value, UomUnit<Q> unit, Quantity.Scale scale, MathContext mc);

    /**
     * The standard quantity type of this class, looked up by {@link #toSystem(SystemOfUnits)}; {@link Untyped} if none.
     *
     * @return the quantity type
     */
    protected abstract Class<Q> quantityType();

    @Override
    public final Double getValue() {
        return value;
    }

    @Override
    public final UomUnit<Q> getUnit() {
        return unit;
    }

    @Override
    public final Quantity.Scale getScale() {
        return scale;
    }

    /**
     * Converts to {@code unit}; an {@code ABSOLUTE} quantity converts through the offsets, a {@code RELATIVE} one (a
     * difference) by the factor only, and the scale is kept (docs/OFFSET-UNITS.md).
     */
    @Override
    public R to(Unit<Q> unit) {
        UomUnit<Q> target = uomUnit(unit);
        UomConverter c = this.unit.getConverterTo(target);
        return create((scale == Quantity.Scale.RELATIVE ? Deltas.converter(c) : c).convert(value), target, scale);
    }

    /**
     * Converts to the unit {@code system} gives for {@link #quantityType()} as {@link #to(Unit)} does, so
     * the scale is kept; a foreign unit or one of another dimension throws {@link UnconvertibleException}. A system
     * without a unit for the type ({@code getUnit} returns null) throws {@link UnsupportedOperationException}.
     * Untyped quantities look up {@link Untyped}, so they throw too unless the system maps it (no system of this
     * library does); those systems have a unit for each of the 33 standard types.
     *
     * @param system the system of units
     * @return the converted quantity
     */
    public R toSystem(SystemOfUnits system) {
        Objects.requireNonNull(system, "system");
        Unit<Q> target = system.getUnit(quantityType());
        if (target == null) {
            throw new UnsupportedOperationException("system " + system.getName() + " has no unit for type "
                    + quantityType().getSimpleName());
        }
        return to(target);
    }

    /**
     * The same quantity: an equal {@code double} quantity.
     *
     * @return the double quantity
     */
    public R toDouble() {
        return create(value, unit, scale);
    }

    /**
     * The same quantity as a decimal: the value as written ({@link BigDecimal#valueOf(double)}), rounded to
     * {@code mc}, which becomes its context. NaN, infinity or a precision of {@code mc} over 100000 throws
     * {@link ArithmeticException}.
     *
     * @param mc the context of the result
     * @return the decimal quantity
     */
    public R toDecimal(MathContext mc) {
        return createDecimal(Precision.toDecimal(value).round(mc), unit, scale, mc);
    }

    /**
     * Converts {@code that} into this unit and compares within the relative {@link #EQUIVALENCE_TOLERANCE}, which
     * is asymmetric near zero for offset units. Two {@code RELATIVE} quantities are compared as differences (factor
     * only, no offset), otherwise as points. NaN is equivalent to nothing; incompatible dimensions give false
     * (compareTo throws). A decimal operand outside the double range throws {@link ArithmeticException}.
     */
    @Override
    public boolean isEquivalentTo(Quantity<Q> that) {
        UomUnit<Q> thatUnit = uomUnit(that.getUnit());
        if (!unit.getDimension().equals(thatUnit.getDimension())) {
            return false;
        }
        double a = value;
        UomConverter c = thatUnit.getConverterTo(unit);
        double b = (Deltas.both(scale, that.getScale()) ? Deltas.converter(c) : c)
                .convert(Precision.toDouble(that.getValue()));
        if (a == b) {
            return true;
        }
        if (Double.isInfinite(a) || Double.isInfinite(b)) {
            return false;
        }
        return Math.abs(a - b) <= EQUIVALENCE_TOLERANCE * Math.max(Math.abs(a), Math.abs(b));
    }

    /**
     * Compares the system-unit values with {@link Double#compare}, or, if both are {@code RELATIVE}, the system-unit
     * differences (factor only); not consistent with equals. A decimal operand outside the double range throws
     * {@link ArithmeticException}.
     */
    @Override
    public int compareTo(Quantity<Q> that) {
        UomUnit<Q> thatUnit = uomUnit(that.getUnit());
        if (!unit.getDimension().equals(thatUnit.getDimension())) {
            throw new UnconvertibleException("incompatible dimensions: " + unit + " and " + thatUnit);
        }
        boolean deltas = Deltas.both(scale, that.getScale());
        return Double.compare(systemValue(value, unit, deltas),
                systemValue(Precision.toDouble(that.getValue()), thatUnit, deltas));
    }

    /**
     * Adds as if both operands were in the system unit, with the result back in this unit; linear units skip the
     * system round trip. If either operand is {@code RELATIVE}, {@code addend} is a difference: it is converted by
     * the factor only and added to this value in this unit. The result is {@code RELATIVE} only if both operands
     * are, otherwise {@code ABSOLUTE} (docs/OFFSET-UNITS.md). Throws {@link UnconvertibleException} for a foreign
     * unit or incompatible dimensions.
     */
    @Override
    public R add(Quantity<Q> addend) {
        return combine(addend, Double::sum);
    }

    /** Subtracts by the same rule as {@link #add}. */
    @Override
    public R subtract(Quantity<Q> subtrahend) {
        return combine(subtrahend, (a, b) -> a - b);
    }

    /** Returns a quantity whose value is {@code -getValue()}, in this unit and scale. */
    @Override
    public R negate() {
        return create(-value, unit, scale);
    }

    /**
     * Multiplies by {@code multiplicand} as a {@code double}; an offset unit goes through the system unit, unless
     * this quantity is {@code RELATIVE}, which is scaled as a plain value. A {@link BigDecimal} or
     * {@link java.math.BigInteger} outside the {@code double} range throws {@link ArithmeticException}.
     */
    @Override
    public R multiply(Number multiplicand) {
        return scalar(Precision.toDouble(multiplicand), (a, n) -> a * n);
    }

    /** Divides by {@code divisor} as {@link #multiply(Number)}; division by zero follows IEEE 754. */
    @Override
    public R divide(Number divisor) {
        return scalar(Precision.toDouble(divisor), (a, n) -> a / n);
    }

    /**
     * Multiplies the system-unit values (offsets count, except for a {@code RELATIVE} operand, which is a difference
     * and counts by its factor only); the result is an {@link Untyped} quantity in the product of the system units,
     * scale {@code ABSOLUTE}. Throws {@link UnconvertibleException} for a foreign unit.
     */
    @Override
    public Quantity<?> multiply(Quantity<?> multiplicand) {
        UomUnit<?> u = uomUnit(multiplicand.getUnit());
        return untyped(systemValue(value, unit, scale == Quantity.Scale.RELATIVE) * systemValue(multiplicand, u),
                unit.getSystemUnit().multiply(u.getSystemUnit()));
    }

    /** Divides by the same rule as {@link #multiply(Quantity)}; division by zero follows IEEE 754. */
    @Override
    public Quantity<?> divide(Quantity<?> divisor) {
        UomUnit<?> u = uomUnit(divisor.getUnit());
        return untyped(systemValue(value, unit, scale == Quantity.Scale.RELATIVE) / systemValue(divisor, u),
                unit.getSystemUnit().divide(u.getSystemUnit()));
    }

    /** Returns {@code 1 / systemValue} in the inverse system unit, as an {@link Untyped} quantity. */
    @Override
    public Quantity<?> inverse() {
        return untyped(1 / systemValue(value, unit, scale == Quantity.Scale.RELATIVE), unit.getSystemUnit().inverse());
    }

    /**
     * Returns this quantity if it already is a {@code type}, otherwise one created by the provider's factory; a
     * unit that does not measure {@code type} gives {@link ClassCastException}, a type the provider has no
     * factory for gives {@link UnsupportedOperationException}.
     */
    @Override
    public <T extends Quantity<T>> Quantity<T> asType(Class<T> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        QuantityFactory<T> factory;
        try {
            factory = Providers.quantityFactory(type);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedOperationException("no quantity factory for " + type.getName(), e);
        }
        Unit<T> typed = unit.asType(type);
        return factory.create(value, typed, scale);
    }

    /** A quantity of this class with the given value, unit and scale. */
    R with(double v, UomUnit<?> u, Quantity.Scale s) {
        return create(v, UomUnit.unchecked(u), s);
    }

    /**
     * Writes the type, unit, scale and raw value bits; reading creates the quantity through
     * the factory of this implementation's provider. A type outside the 33 standard quantity
     * interfaces and {@link Untyped} throws {@link java.io.NotSerializableException}.
     *
     * @return the proxy
     * @throws ObjectStreamException if the quantity cannot be serialized
     */
    @Serial
    protected final Object writeReplace() throws ObjectStreamException {
        return QuantityProxy.ofDouble(quantityType(), unit, scale, value);
    }

    /**
     * Rejects a stream without the proxy.
     *
     * @param in the stream
     * @throws InvalidObjectException always
     */
    @Serial
    private void readObject(ObjectInputStream in) throws InvalidObjectException {
        throw new InvalidObjectException("proxy required");
    }

    /**
     * Called instead of readObject when a stream names a subclass without this class.
     *
     * @throws InvalidObjectException always
     */
    @Serial
    private void readObjectNoData() throws InvalidObjectException {
        throw new InvalidObjectException("proxy required");
    }

    private static Quantity<?> untyped(double value, UomUnit<?> unit) {
        return new UntypedDoubleQuantity(value, UomUnit.unchecked(unit.getSystemUnit()), Quantity.Scale.ABSOLUTE);
    }

    private static double systemValue(Quantity<?> quantity, UomUnit<?> unit) {
        return systemValue(Precision.toDouble(quantity.getValue()), unit,
                quantity.getScale() == Quantity.Scale.RELATIVE);
    }

    private R scalar(double n, DoubleBinaryOperator op) {
        double r = scale == Quantity.Scale.RELATIVE || unit.toSystemConverter().isLinear()
                ? op.applyAsDouble(value, n)
                : fromSystem(op.applyAsDouble(toSystem(value, unit), n), unit);
        return create(r, unit, scale);
    }

    private R combine(Quantity<Q> that, DoubleBinaryOperator op) {
        UomUnit<Q> thatUnit = uomUnit(that.getUnit());
        UomConverter toThisUnit = thatUnit.getConverterTo(unit);
        double b = Precision.toDouble(that.getValue());
        boolean delta = Deltas.isDelta(scale, that.getScale());
        double r;
        if (delta) {
            r = op.applyAsDouble(value, Deltas.converter(toThisUnit).convert(b));
        } else if (unit.toSystemConverter().isLinear() && thatUnit.toSystemConverter().isLinear()) {
            r = op.applyAsDouble(value, toThisUnit.convert(b));
        } else {
            r = fromSystem(op.applyAsDouble(toSystem(value, unit), toSystem(b, thatUnit)), unit);
        }
        return create(r, unit, Deltas.scale(scale, that.getScale()));
    }

    private static double systemValue(double value, UomUnit<?> unit, boolean delta) {
        UomConverter c = unit.toSystemConverter();
        return (delta ? Deltas.converter(c) : c).convert(value);
    }

    private double toSystem(double value, UomUnit<Q> unit) {
        return unit.toSystemConverter().convert(value);
    }

    private double fromSystem(double systemValue, UomUnit<Q> unit) {
        return unit.toSystemConverter().inverse().convert(systemValue);
    }

    private static <Q extends Quantity<Q>> UomUnit<Q> uomUnit(Unit<Q> unit) {
        if (Objects.requireNonNull(unit, "unit") instanceof UomUnit<Q> u) {
            return u;
        }
        throw new UnconvertibleException("foreign unit implementation: " + unit.getClass().getName());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AbstractDoubleQuantity<?, ?> q && getClass() == q.getClass()
                && Double.compare(value, q.value) == 0 && unit.equals(q.unit) && scale == q.scale;
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(unit, scale) + Double.hashCode(value);
    }

    @Override
    public String toString() {
        String symbol = unit.getSymbol();
        return value + " " + (symbol != null ? symbol : unit.toString());
    }
}
