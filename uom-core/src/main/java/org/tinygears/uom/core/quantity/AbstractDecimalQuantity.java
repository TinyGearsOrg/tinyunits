// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.quantity;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;
import javax.measure.Quantity;
import javax.measure.UnconvertibleException;
import javax.measure.Unit;
import org.tinygears.uom.core.UomConverter;
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

    /**
     * Adds as if both operands were in the system unit, with the result back in this unit; linear units skip the
     * system round trip. Rounds to the lower context of the two; a {@code double} operand gives a {@code double}
     * result. Throws {@link UnconvertibleException} for a foreign unit or incompatible dimensions.
     */
    @Override
    public R add(Quantity<Q> addend) {
        return combine(addend, BigDecimal::add, false);
    }

    /** Subtracts by the same rule as {@link #add}. */
    @Override
    public R subtract(Quantity<Q> subtrahend) {
        return combine(subtrahend, BigDecimal::subtract, true);
    }

    /**
     * Multiplies by {@code multiplicand} as a decimal ({@code double} and {@code Float} as written) in this context;
     * an offset unit goes through the system unit.
     */
    @Override
    public R multiply(Number multiplicand) {
        return scalar(Precision.toDecimal(multiplicand), mathContext, BigDecimal::multiply);
    }

    /**
     * Divides as {@link #multiply(Number)}; an unlimited context divides with {@code DECIMAL128}. Division by zero
     * throws {@link ArithmeticException}.
     */
    @Override
    public R divide(Number divisor) {
        return scalar(Precision.toDecimal(divisor), Precision.calculation(mathContext), BigDecimal::divide);
    }

    /**
     * Compares the system-unit values under the lower context, or with {@link Double#compare} against a
     * {@code double} operand; not consistent with equals.
     */
    @Override
    public int compareTo(Quantity<Q> that) {
        UomUnit<Q> thatUnit = uomUnit(that.getUnit());
        if (!unit.getDimension().equals(thatUnit.getDimension())) {
            throw new UnconvertibleException("incompatible dimensions: " + unit + " and " + thatUnit);
        }
        Number n = that.getValue();
        if (Precision.kind(n) == Precision.Kind.DOUBLE) {
            return Double.compare(unit.toSystemConverter().convert(Precision.toDouble(value)),
                    thatUnit.toSystemConverter().convert(n.doubleValue()));
        }
        MathContext mc = contextWith(that);
        return toSystem(value, unit, mc).compareTo(toSystem(Precision.toDecimal(n), thatUnit, mc));
    }

    /**
     * Converts {@code that} into this unit and compares within the relative tolerance {@code 10^(1-p)}, {@code p}
     * the lowest non-zero precision of both contexts (34 if unlimited); against a {@code double} operand the
     * {@code double} rule applies. Incompatible dimensions give false (compareTo throws).
     */
    @Override
    public boolean isEquivalentTo(Quantity<Q> that) {
        UomUnit<Q> thatUnit = uomUnit(that.getUnit());
        if (!unit.getDimension().equals(thatUnit.getDimension())) {
            return false;
        }
        Number n = that.getValue();
        if (Precision.kind(n) == Precision.Kind.DOUBLE) {
            return toDouble().isEquivalentTo(that);
        }
        MathContext mc = Precision.calculation(contextWith(that));
        BigDecimal b = thatUnit.getConverterTo(unit).convert(Precision.toDecimal(n), mc);
        BigDecimal bound = value.abs().max(b.abs()).scaleByPowerOfTen(1 - mc.getPrecision());
        return value.compareTo(b) == 0 || value.subtract(b).abs().compareTo(bound) <= 0;
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

    /** The result context against {@code that}: the lower one; an exact value adopts this context. */
    private MathContext contextWith(Quantity<Q> that) {
        if (Precision.kind(that.getValue()) == Precision.Kind.EXACT) {
            return mathContext;
        }
        // A foreign BigDecimal has no context and counts as unlimited.
        return Precision.lower(mathContext,
                that instanceof AbstractDecimalQuantity<?, ?> d ? d.mathContext : MathContext.UNLIMITED);
    }

    @SuppressWarnings("unchecked")
    private R combine(Quantity<Q> that, DecimalOp op, boolean subtract) {
        UomUnit<Q> thatUnit = uomUnit(that.getUnit());
        UomConverter toThisUnit = thatUnit.getConverterTo(unit);
        Number n = that.getValue();
        if (Precision.kind(n) == Precision.Kind.DOUBLE) {
            // R is the typed interface, which the double quantity's add and subtract return.
            R d = toDouble();
            return (R) (subtract ? d.subtract(that) : d.add(that));
        }
        MathContext mc = contextWith(that);
        BigDecimal b = Precision.toDecimal(n);
        BigDecimal r = unit.toSystemConverter().isLinear() && thatUnit.toSystemConverter().isLinear()
                ? op.apply(value, toThisUnit.convert(b, mc), mc)
                : fromSystem(op.apply(toSystem(value, unit, mc), toSystem(b, thatUnit, mc), mc), mc);
        return create(r, unit, scale, mc);
    }

    /** {@code op} computes in {@code opContext}; the result keeps this context. */
    private R scalar(BigDecimal n, MathContext opContext, DecimalOp op) {
        BigDecimal r = unit.toSystemConverter().isLinear()
                ? op.apply(value, n, opContext)
                : fromSystem(op.apply(toSystem(value, unit, mathContext), n, opContext), mathContext);
        return create(r, unit, scale, mathContext);
    }

    private static BigDecimal toSystem(BigDecimal value, UomUnit<?> unit, MathContext mc) {
        return unit.toSystemConverter().convert(value, mc);
    }

    private BigDecimal fromSystem(BigDecimal systemValue, MathContext mc) {
        return unit.toSystemConverter().inverse().convert(systemValue, mc);
    }

    private interface DecimalOp {
        BigDecimal apply(BigDecimal a, BigDecimal b, MathContext mc);
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
