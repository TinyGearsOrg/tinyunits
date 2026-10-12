// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.quantity;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;
import javax.measure.Quantity;
import javax.measure.UnconvertibleException;
import javax.measure.Unit;
import org.tinygears.uom.core.UomUnit;

/**
 * Base class of the generated {@link BigDecimal} quantities; {@code R} is the typed interface returned by
 * conversions. The {@link MathContext} rounds computed results and is not part of equality. Immutable.
 */
public abstract class AbstractDecimalQuantity<Q extends Quantity<Q>, R extends Q>
        implements Quantity<Q>, Comparable<Quantity<Q>> {

    private final BigDecimal value;
    private final UomUnit<Q> unit;
    private final Quantity.Scale scale;
    private final MathContext mathContext;

    /** Throws {@link ArithmeticException} if the scale of {@code value} exceeds 100000 in magnitude. */
    protected AbstractDecimalQuantity(BigDecimal value, UomUnit<Q> unit, Quantity.Scale scale,
            MathContext mathContext) {
        this.value = Precision.checked(Objects.requireNonNull(value, "value"));
        this.unit = Objects.requireNonNull(unit, "unit");
        this.scale = Objects.requireNonNull(scale, "scale");
        this.mathContext = Objects.requireNonNull(mathContext, "mathContext");
    }

    protected abstract R create(BigDecimal value, UomUnit<Q> unit, Quantity.Scale scale, MathContext mc);

    /** Creates the {@code double} quantity of the same typed interface. */
    protected abstract R createDouble(double value, UomUnit<Q> unit, Quantity.Scale scale);

    @Override
    public final BigDecimal getValue() {
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

    /** The context that rounds results computed from this quantity. */
    public final MathContext mathContext() {
        return mathContext;
    }

    /**
     * Converts exactly where the conversion is exact, otherwise rounds to {@link #mathContext()}. Throws
     * {@link ArithmeticException} if the result's scale exceeds the bound of ±100000 (conversion or rounding can
     * push a value past it).
     */
    @Override
    public R to(Unit<Q> unit) {
        UomUnit<Q> target = uomUnit(unit);
        return create(this.unit.getConverterTo(target).convert(value, mathContext), target, scale, mathContext);
    }

    /** Returns a quantity whose value is {@code -getValue()}, in this unit, scale and context. */
    @Override
    public R negate() {
        return create(value.negate(), unit, scale, mathContext);
    }

    /** The same quantity as a {@code double}; a value outside the double range throws {@link ArithmeticException}. */
    public R toDouble() {
        return createDouble(Precision.toDouble(value), unit, scale);
    }

    /**
     * The same quantity with its value rounded to {@code mc}, which becomes its context. Throws
     * {@link ArithmeticException} if the result's scale exceeds the bound of ±100000 (conversion or rounding can
     * push a value past it).
     */
    public R toDecimal(MathContext mc) {
        return create(value.round(mc), unit, scale, mc);
    }

    @Override
    public R add(Quantity<Q> addend) {
        throw new UnsupportedOperationException("milestone 4, task 4");
    }

    @Override
    public R subtract(Quantity<Q> subtrahend) {
        throw new UnsupportedOperationException("milestone 4, task 4");
    }

    @Override
    public R multiply(Number multiplicand) {
        throw new UnsupportedOperationException("milestone 4, task 4");
    }

    @Override
    public R divide(Number divisor) {
        throw new UnsupportedOperationException("milestone 4, task 4");
    }

    @Override
    public int compareTo(Quantity<Q> that) {
        throw new UnsupportedOperationException("milestone 4, task 4");
    }

    @Override
    public boolean isEquivalentTo(Quantity<Q> that) {
        throw new UnsupportedOperationException("milestone 4, task 4");
    }

    @Override
    public Quantity<?> multiply(Quantity<?> multiplicand) {
        throw new UnsupportedOperationException("milestone 4, task 5");
    }

    @Override
    public Quantity<?> divide(Quantity<?> divisor) {
        throw new UnsupportedOperationException("milestone 4, task 5");
    }

    @Override
    public Quantity<?> inverse() {
        throw new UnsupportedOperationException("milestone 4, task 5");
    }

    @Override
    public <T extends Quantity<T>> Quantity<T> asType(Class<T> type) {
        throw new UnsupportedOperationException("milestone 4, task 5");
    }

    private static <Q extends Quantity<Q>> UomUnit<Q> uomUnit(Unit<Q> unit) {
        if (Objects.requireNonNull(unit, "unit") instanceof UomUnit<Q> u) {
            return u;
        }
        throw new UnconvertibleException("foreign unit implementation: " + unit.getClass().getName());
    }

    /** Same class, unit and scale and a numerically equal value ({@code 1.0 m} equals {@code 1.00 m}). */
    @Override
    public boolean equals(Object o) {
        return o instanceof AbstractDecimalQuantity<?, ?> q && getClass() == q.getClass()
                && value.compareTo(q.value) == 0 && unit.equals(q.unit) && scale == q.scale;
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(unit, scale) + value.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        String symbol = unit.getSymbol();
        return value + " " + (symbol != null ? symbol : unit.toString());
    }
}
