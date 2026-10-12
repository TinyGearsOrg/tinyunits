// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.quantity;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;
import javax.measure.Quantity;
import javax.measure.UnconvertibleException;
import javax.measure.Unit;
import javax.measure.spi.QuantityFactory;
import javax.measure.spi.ServiceProvider;
import org.tinygears.uom.core.Factor;
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
     * system round trip. Evaluated exactly and rounded once to the lower context of the two (irrational unit
     * factors with guard digits, best effort); a {@code double} operand gives a {@code double} result. Throws
     * {@link UnconvertibleException} for a foreign unit or incompatible dimensions.
     */
    @Override
    public R add(Quantity<Q> addend) {
        return combine(addend, false);
    }

    /** Subtracts by the same rule as {@link #add}. */
    @Override
    public R subtract(Quantity<Q> subtrahend) {
        return combine(subtrahend, true);
    }

    /**
     * Multiplies by {@code multiplicand} as a decimal ({@code double} and {@code Float} as written); an offset unit
     * goes through the system unit. Evaluated exactly and rounded once to this context.
     */
    @Override
    public R multiply(Number multiplicand) {
        return scalar(Precision.toDecimal(multiplicand), false);
    }

    /**
     * Divides as {@link #multiply(Number)}, rounded once; an unlimited context rounds a non-terminating quotient
     * to {@code DECIMAL128}. Division by zero throws {@link ArithmeticException}.
     */
    @Override
    public R divide(Number divisor) {
        return scalar(Precision.toDecimal(divisor), true);
    }

    /**
     * Compares the exact system-unit values (a consistent total preorder), or with {@link Double#compare} against a
     * {@code double} operand; not consistent with equals. Exact for units with exact factors; irrational factors
     * are evaluated with guard digits, best effort.
     */
    @Override
    public int compareTo(Quantity<Q> that) {
        UomUnit<Q> thatUnit = uomUnit(that.getUnit());
        requireCompatible(thatUnit);
        Number n = that.getValue();
        if (Precision.kind(n) == Precision.Kind.DOUBLE) {
            return Double.compare(unit.toSystemConverter().convert(Precision.toDouble(value)),
                    thatUnit.toSystemConverter().convert(n.doubleValue()));
        }
        // sign(sys(a) - sys(b)) = sign(fa) * sign(a - (fb/fa) b - (ob - oa)/fa)
        UomConverter ta = unit.toSystemConverter();
        UomConverter tb = thatUnit.toSystemConverter();
        BigDecimal b = Precision.toDecimal(n);
        int digits = guardDigits(contextWith(that), b);
        Ratio g = Ratio.of(tb.factor().divide(ta.factor()), digits);
        Ratio h = Ratio.of(tb.offset().add(ta.offset().negate()).divide(ta.factor()), digits);
        BigDecimal d = value.multiply(g.den).multiply(h.den)
                .subtract(b.multiply(g.num).multiply(h.den)).subtract(h.num.multiply(g.den));
        return ta.factor().signum() * d.signum();
    }

    /**
     * Converts {@code that} into this unit and compares within the relative tolerance {@code 10^(1-p)}, {@code p}
     * the lowest non-zero precision of both contexts (34 if unlimited); against a {@code double} operand the
     * {@code double} rule applies. At precision 1 the tolerance is 10^0 (degenerate by design). Incompatible
     * dimensions give false (compareTo throws).
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

    /**
     * Multiplies the system-unit values (operands are taken to the system unit, so offsets count), evaluated exactly
     * and rounded once to the lower context of the two; the result is an {@link Untyped} quantity in the product of
     * the system units, scale {@code ABSOLUTE}. A {@code double} operand gives an {@link UntypedDoubleQuantity}.
     * Throws {@link UnconvertibleException} for a foreign unit.
     */
    @Override
    public Quantity<?> multiply(Quantity<?> multiplicand) {
        return untyped(multiplicand, false);
    }

    /**
     * Divides by the same rule as {@link #multiply(Quantity)}; an unlimited context rounds a non-terminating quotient
     * to {@code DECIMAL128}. Division by zero throws {@link ArithmeticException}.
     */
    @Override
    public Quantity<?> divide(Quantity<?> divisor) {
        return untyped(divisor, true);
    }

    /**
     * Returns {@code 1 / systemValue} in the inverse system unit as an {@link Untyped} quantity, rounded to this
     * context ({@code DECIMAL128} if unlimited and non-terminating). Zero throws {@link ArithmeticException}.
     */
    @Override
    public Quantity<?> inverse() {
        Ratio a = system(value, unit, guardDigits(mathContext, BigDecimal.ONE));
        return UntypedDecimalQuantity.of(roundOnce(a.den, a.num, mathContext, a.exact),
                unit.getSystemUnit().inverse().getSystemUnit(), mathContext);
    }

    /**
     * Returns this quantity if it already is a {@code type}, otherwise one created by the provider's factory; a
     * unit that does not measure {@code type} gives {@link ClassCastException}, a type the provider has no
     * factory for gives {@link UnsupportedOperationException}. A decimal result gets this context; a factory
     * result of another kind is returned as is.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T extends Quantity<T>> Quantity<T> asType(Class<T> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        QuantityFactory<T> factory;
        try {
            factory = ServiceProvider.current().getQuantityFactory(type);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedOperationException("no quantity factory for " + type.getName(), e);
        }
        Quantity<T> q = factory.create(value, unit.asType(type), scale);
        // toDecimal returns the factory's typed interface, which is a Quantity<T>.
        return q instanceof AbstractDecimalQuantity<?, ?> d ? (Quantity<T>) d.toDecimal(mathContext) : q;
    }

    /** {@code sys(a)·sys(b)} or {@code sys(a)/sys(b)} as one exact quotient, rounded once. */
    private Quantity<?> untyped(Quantity<?> that, boolean divide) {
        UomUnit<?> thatUnit = uomUnit(that.getUnit());
        Number n = that.getValue();
        if (Precision.kind(n) == Precision.Kind.DOUBLE) {
            R d = toDouble();
            return divide ? d.divide(that) : d.multiply(that);
        }
        MathContext mc = contextWith(that);
        BigDecimal b = Precision.toDecimal(n);
        int digits = guardDigits(mc, b);
        Ratio sa = system(value, unit, digits);
        Ratio sb = system(b, thatUnit, digits);
        BigDecimal num = divide ? sa.num.multiply(sb.den) : sa.num.multiply(sb.num);
        BigDecimal den = divide ? sa.den.multiply(sb.num) : sa.den.multiply(sb.den);
        UomUnit<?> product = divide ? unit.getSystemUnit().divide(thatUnit.getSystemUnit())
                : unit.getSystemUnit().multiply(thatUnit.getSystemUnit());
        return UntypedDecimalQuantity.of(roundOnce(num, den, mc, sa.exact && sb.exact), product.getSystemUnit(), mc);
    }

    /** The system value {@code f·v + o} as numerator over denominator. */
    private static Ratio system(BigDecimal v, UomUnit<?> u, int digits) {
        UomConverter c = u.toSystemConverter();
        Ratio f = Ratio.of(c.factor(), digits);
        Ratio o = Ratio.of(c.offset(), digits);
        return new Ratio(v.multiply(f.num).multiply(o.den).add(o.num.multiply(f.den)), f.den.multiply(o.den),
                f.exact && o.exact);
    }

    /** The result context against {@code that}: the lower one; an exact value adopts this context. */
    private MathContext contextWith(Quantity<?> that) {
        if (Precision.kind(that.getValue()) == Precision.Kind.EXACT) {
            return mathContext;
        }
        // A foreign BigDecimal has no context and counts as unlimited.
        return Precision.lower(mathContext,
                that instanceof AbstractDecimalQuantity<?, ?> d ? d.mathContext : MathContext.UNLIMITED);
    }

    /** The literal rule {@code r = a ± (fb·b + ob)/fa} as one exact quotient, rounded once. */
    @SuppressWarnings("unchecked")
    private R combine(Quantity<Q> that, boolean subtract) {
        UomUnit<Q> thatUnit = uomUnit(that.getUnit());
        requireCompatible(thatUnit);
        Number n = that.getValue();
        if (Precision.kind(n) == Precision.Kind.DOUBLE) {
            // R is the typed interface, which the double quantity's add and subtract return.
            R d = toDouble();
            return (R) (subtract ? d.subtract(that) : d.add(that));
        }
        MathContext mc = contextWith(that);
        UomConverter ta = unit.toSystemConverter();
        UomConverter tb = thatUnit.toSystemConverter();
        BigDecimal b = Precision.toDecimal(n);
        int digits = guardDigits(mc, b);
        Ratio f = Ratio.of(tb.factor().divide(ta.factor()), digits);
        Ratio o = Ratio.of(tb.offset().divide(ta.factor()), digits);
        BigDecimal term = b.multiply(f.num).multiply(o.den).add(o.num.multiply(f.den));
        BigDecimal ad = value.multiply(f.den).multiply(o.den);
        BigDecimal num = subtract ? ad.subtract(term) : ad.add(term);
        return create(roundOnce(num, f.den.multiply(o.den), mc, f.exact && o.exact), unit, scale, mc);
    }

    /** {@code r = a·s + q·(s - 1)} with {@code q = oa/fa} (or {@code 1/s} to divide), as one exact quotient. */
    private R scalar(BigDecimal s, boolean divide) {
        UomConverter ta = unit.toSystemConverter();
        Ratio q = Ratio.of(ta.offset().divide(ta.factor()), guardDigits(mathContext, s));
        BigDecimal aq = value.multiply(q.den).add(q.num);
        BigDecimal num = divide ? aq.subtract(q.num.multiply(s)) : aq.multiply(s).subtract(q.num);
        BigDecimal den = divide ? q.den.multiply(s) : q.den;
        return create(roundOnce(num, den, mathContext, q.exact), unit, scale, mathContext);
    }

    /**
     * {@code num / den} rounded once to {@code mc}; an unlimited context rounds a non-terminating or approximated
     * quotient to {@code DECIMAL128}. A zero {@code den} throws {@link ArithmeticException}.
     */
    private static BigDecimal roundOnce(BigDecimal num, BigDecimal den, MathContext mc, boolean exact) {
        if (mc.getPrecision() == 0 && exact) {
            try {
                return num.divide(den);
            } catch (ArithmeticException nonTerminating) {
                if (den.signum() == 0) {
                    throw nonTerminating;
                }
            }
        }
        return num.divide(den, Precision.calculation(mc));
    }

    /** Digits for approximating an irrational factor: the context's plus both operands', so cancellation shows. */
    private int guardDigits(MathContext mc, BigDecimal other) {
        return Precision.calculation(mc).getPrecision() + value.precision() + other.precision() + 25;
    }

    /** An exact factor as numerator over a positive denominator; an irrational one (pi, roots) approximated over 1. */
    private record Ratio(BigDecimal num, BigDecimal den, boolean exact) {

        static Ratio of(Factor f, int digits) {
            return f.isExact() ? new Ratio(new BigDecimal(f.numerator()), new BigDecimal(f.denominator()), true)
                    : new Ratio(f.decimalValue(new MathContext(digits)), BigDecimal.ONE, false);
        }
    }

    private void requireCompatible(UomUnit<Q> thatUnit) {
        if (!unit.getDimension().equals(thatUnit.getDimension())) {
            throw new UnconvertibleException("incompatible dimensions: " + unit + " and " + thatUnit);
        }
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
