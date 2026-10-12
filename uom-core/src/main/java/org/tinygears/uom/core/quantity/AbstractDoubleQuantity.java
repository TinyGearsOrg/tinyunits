// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.quantity;

import java.util.Objects;
import java.util.function.DoubleBinaryOperator;
import javax.measure.Quantity;
import javax.measure.UnconvertibleException;
import javax.measure.Unit;
import javax.measure.spi.ServiceProvider;
import org.tinygears.uom.core.UomConverter;
import org.tinygears.uom.core.UomUnit;

/**
 * Base class of the generated {@code double} quantities; {@code R} is the typed interface returned by
 * conversions. Immutable.
 */
public abstract class AbstractDoubleQuantity<Q extends Quantity<Q>, R extends Q>
        implements Quantity<Q>, Comparable<Quantity<Q>> {

    /** Relative tolerance of {@link #isEquivalentTo}. */
    public static final double EQUIVALENCE_TOLERANCE = 1e-12;

    private final double value;
    private final UomUnit<Q> unit;
    private final Quantity.Scale scale;

    protected AbstractDoubleQuantity(double value, UomUnit<Q> unit, Quantity.Scale scale) {
        this.value = value;
        this.unit = Objects.requireNonNull(unit, "unit");
        this.scale = Objects.requireNonNull(scale, "scale");
    }

    protected abstract R create(double value, UomUnit<Q> unit, Quantity.Scale scale);

    @Override
    public final Double getValue() {
        return value;
    }

    public final double doubleValue() {
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

    @Override
    public R to(Unit<Q> unit) {
        UomUnit<Q> target = uomUnit(unit);
        return create(this.unit.getConverterTo(target).convert(value), target, scale);
    }

    /**
     * Converts {@code that} into this unit and compares within the relative {@link #EQUIVALENCE_TOLERANCE}, which
     * is asymmetric near zero for offset units. NaN is equivalent to nothing; incompatible dimensions give false
     * (compareTo throws).
     */
    @Override
    public boolean isEquivalentTo(Quantity<Q> that) {
        UomUnit<Q> thatUnit = uomUnit(that.getUnit());
        if (!unit.getDimension().equals(thatUnit.getDimension())) {
            return false;
        }
        double a = value;
        double b = thatUnit.getConverterTo(unit).convert(that.getValue().doubleValue());
        if (a == b) {
            return true;
        }
        if (Double.isInfinite(a) || Double.isInfinite(b)) {
            return false;
        }
        return Math.abs(a - b) <= EQUIVALENCE_TOLERANCE * Math.max(Math.abs(a), Math.abs(b));
    }

    /** Compares the system-unit values with {@link Double#compare}; not consistent with equals. */
    @Override
    public int compareTo(Quantity<Q> that) {
        UomUnit<Q> thatUnit = uomUnit(that.getUnit());
        if (!unit.getDimension().equals(thatUnit.getDimension())) {
            throw new UnconvertibleException("incompatible dimensions: " + unit + " and " + thatUnit);
        }
        return Double.compare(toSystem(value, unit), toSystem(that.getValue().doubleValue(), thatUnit));
    }

    /**
     * Adds as if both operands were in the system unit, with the result back in this unit; linear units skip the
     * system round trip. Throws {@link UnconvertibleException} for a foreign unit or incompatible dimensions.
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

    /** Multiplies by {@code multiplicand.doubleValue()}; an offset unit goes through the system unit. */
    @Override
    public R multiply(Number multiplicand) {
        return scalar(multiplicand.doubleValue(), (a, n) -> a * n);
    }

    /** Divides by {@code divisor.doubleValue()} as {@link #multiply(Number)}; division by zero follows IEEE 754. */
    @Override
    public R divide(Number divisor) {
        return scalar(divisor.doubleValue(), (a, n) -> a / n);
    }

    /**
     * Multiplies the system-unit values; the result is an {@link Untyped} quantity in the product of the system
     * units, scale {@code ABSOLUTE}. Throws {@link UnconvertibleException} for a foreign unit.
     */
    @Override
    public Quantity<?> multiply(Quantity<?> multiplicand) {
        UomUnit<?> u = uomUnit(multiplicand.getUnit());
        return untyped(toSystem(value, unit) * toSystem(multiplicand, u),
                unit.getSystemUnit().multiply(u.getSystemUnit()));
    }

    /** Divides by the same rule as {@link #multiply(Quantity)}; division by zero follows IEEE 754. */
    @Override
    public Quantity<?> divide(Quantity<?> divisor) {
        UomUnit<?> u = uomUnit(divisor.getUnit());
        return untyped(toSystem(value, unit) / toSystem(divisor, u), unit.getSystemUnit().divide(u.getSystemUnit()));
    }

    /** Returns {@code 1 / systemValue} in the inverse system unit, as an {@link Untyped} quantity. */
    @Override
    public Quantity<?> inverse() {
        return untyped(1 / toSystem(value, unit), unit.getSystemUnit().inverse());
    }

    /**
     * Returns this quantity if it already is a {@code type}, otherwise one created by the provider's factory; a
     * unit that does not measure {@code type} gives {@link ClassCastException}.
     */
    @Override
    public <T extends Quantity<T>> Quantity<T> asType(Class<T> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        Unit<T> typed = unit.asType(type);
        return ServiceProvider.current().getQuantityFactory(type).create(value, typed, scale);
    }

    private static Quantity<?> untyped(double value, UomUnit<?> unit) {
        return new UntypedDoubleQuantity(value, UomUnit.unchecked(unit), Quantity.Scale.ABSOLUTE);
    }

    private static double toSystem(Quantity<?> quantity, UomUnit<?> unit) {
        return unit.toSystemConverter().convert(quantity.getValue().doubleValue());
    }

    private R scalar(double n, DoubleBinaryOperator op) {
        double r = unit.toSystemConverter().isLinear()
                ? op.applyAsDouble(value, n)
                : fromSystem(op.applyAsDouble(toSystem(value, unit), n), unit);
        return create(r, unit, scale);
    }

    private R combine(Quantity<Q> that, DoubleBinaryOperator op) {
        UomUnit<Q> thatUnit = uomUnit(that.getUnit());
        UomConverter toThisUnit = thatUnit.getConverterTo(unit);
        double b = that.getValue().doubleValue();
        double r = unit.toSystemConverter().isLinear() && thatUnit.toSystemConverter().isLinear()
                ? op.applyAsDouble(value, toThisUnit.convert(b))
                : fromSystem(op.applyAsDouble(toSystem(value, unit), toSystem(b, thatUnit)), unit);
        return create(r, unit, scale);
    }

    protected double toSystem(double value, UomUnit<Q> unit) {
        return unit.toSystemConverter().convert(value);
    }

    protected double fromSystem(double systemValue, UomUnit<Q> unit) {
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
