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
import javax.measure.Quantity;
import javax.measure.UnconvertibleException;
import javax.measure.Unit;
import javax.measure.spi.QuantityFactory;
import javax.measure.spi.SystemOfUnits;
import org.tinygears.uom.core.Factor;
import org.tinygears.uom.core.UomConverter;
import org.tinygears.uom.core.UomUnit;
import org.tinygears.uom.core.internal.Decimals;
import org.tinygears.uom.core.spi.Providers;

/**
 * Base class of the generated {@link BigDecimal} quantities; {@code R} is the typed interface returned by
 * conversions. The {@link MathContext} rounds every computed result and is not part of equality; creation and
 * {@link #negate()} keep the value as given. {@link MathContext#UNLIMITED} keeps results exact where they terminate;
 * non-terminating quotients and irrational conversions fall back to {@code DECIMAL128} with {@code HALF_EVEN},
 * whatever the rounding mode. A {@link BigDecimal} operand value or scalar whose scale exceeds ±100000 throws
 * {@link ArithmeticException}, as does a {@link MathContext} precision over 100000 (at construction, so in
 * {@link #toDecimal(MathContext)} and every factory taking a context). {@link #toDecimal(MathContext)} rounds to a
 * new context; {@link #withMathContext(MathContext)}, used by the quantity format, keeps the value exactly. Immutable.
 * <p>
 * Serializable through a proxy (see {@link #writeReplace()}); the serialized form may change before 1.0. Reading
 * rebuilds the quantity through the factory of the provider current at read time, then restores the exact value,
 * scale and context: a user subclass, or a quantity read under another top-priority provider, comes back as that
 * provider's class (so it no longer equals the original), and a provider without factories for the standard types
 * makes typed reads fail.
 *
 * @param <Q> the quantity type
 * @param <R> the typed interface returned by conversions
 */
public abstract class AbstractDecimalQuantity<Q extends Quantity<Q>, R extends Q>
        implements Quantity<Q>, Comparable<Quantity<Q>>, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The value. */
    private final BigDecimal value;
    /** The unit. */
    private final UomUnit<Q> unit;
    /** The scale. */
    private final Quantity.Scale scale;
    /** The context that rounds results. */
    private final MathContext mathContext;

    /**
     * Throws {@link ArithmeticException} if the scale of {@code value} exceeds 100000 in magnitude or the precision
     * of {@code mathContext} exceeds 100000.
     *
     * @param value the value
     * @param unit the unit
     * @param scale the scale
     * @param mathContext the context that rounds results
     */
    protected AbstractDecimalQuantity(BigDecimal value, UomUnit<Q> unit, Quantity.Scale scale,
            MathContext mathContext) {
        this.value = Precision.checked(Objects.requireNonNull(value, "value"));
        this.unit = Objects.requireNonNull(unit, "unit");
        this.scale = Objects.requireNonNull(scale, "scale");
        this.mathContext = Precision.checked(Objects.requireNonNull(mathContext, "mathContext"));
    }

    /**
     * Creates a decimal quantity of the same typed interface.
     *
     * @param value the value
     * @param unit the unit
     * @param scale the scale
     * @param mc the context
     * @return the quantity
     */
    protected abstract R create(BigDecimal value, UomUnit<Q> unit, Quantity.Scale scale, MathContext mc);

    /**
     * Creates the {@code double} quantity of the same typed interface.
     *
     * @param value the value
     * @param unit the unit
     * @param scale the scale
     * @return the quantity
     */
    protected abstract R createDouble(double value, UomUnit<Q> unit, Quantity.Scale scale);

    /**
     * The standard quantity type of this class, looked up by {@link #toSystem(SystemOfUnits)}; {@link Untyped} if none.
     *
     * @return the quantity type
     */
    protected abstract Class<Q> quantityType();

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

    /**
     * The context that rounds results computed from this quantity.
     *
     * @return the context
     */
    public final MathContext mathContext() {
        return mathContext;
    }

    /**
     * Converts and rounds the result to {@link #mathContext()}, even for the identity conversion
     * ({@code 1.23456789 m} with {@code DECIMAL32} to metres gives {@code 1.234568 m}). An {@code ABSOLUTE} quantity
     * converts through the offsets, a {@code RELATIVE} one (a difference) by the factor only, and the scale is kept
     * (docs/OFFSET-UNITS.md). Throws
     * {@link ArithmeticException} if the result's scale exceeds the bound of ±100000 (conversion or rounding can
     * push a value past it).
     */
    @Override
    public R to(Unit<Q> unit) {
        UomUnit<Q> target = uomUnit(unit);
        UomConverter c = this.unit.getConverterTo(target);
        return create((scale == Quantity.Scale.RELATIVE ? Deltas.converter(c) : c).convert(value, mathContext),
                target, scale, mathContext);
    }

    /**
     * Converts to the unit {@code system} gives for {@link #quantityType()} as {@link #to(Unit)} does, so scale
     * and context are kept; a foreign unit or one of another dimension throws {@link UnconvertibleException}. A
     * system without a unit for the type ({@code getUnit} returns null) throws {@link UnsupportedOperationException}.
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

    /** Returns a quantity whose value is {@code -getValue()}, in this unit, scale and context. */
    @Override
    public R negate() {
        return create(value.negate(), unit, scale, mathContext);
    }

    /**
     * The same quantity as a {@code double}; a value outside the double range throws {@link ArithmeticException}.
     *
     * @return the double quantity
     */
    public R toDouble() {
        return createDouble(Precision.toDouble(value), unit, scale);
    }

    /**
     * The same quantity with its value rounded to {@code mc}, which becomes its context. Throws
     * {@link ArithmeticException} if the result's scale exceeds the bound of ±100000 (conversion or rounding can
     * push a value past it) or the precision of {@code mc} exceeds 100000.
     *
     * @param mc the context of the result
     * @return the rounded quantity
     */
    public R toDecimal(MathContext mc) {
        return create(value.round(mc), unit, scale, mc);
    }

    /**
     * The same quantity with context {@code mc}; unlike {@link #toDecimal(MathContext)} the value is kept exactly,
     * digits beyond the precision of {@code mc} included; used by the quantity format's decimal parse. Throws
     * {@link ArithmeticException} if the precision of {@code mc} exceeds 100000.
     *
     * @param mc the context of the result
     * @return the quantity with the new context
     */
    public R withMathContext(MathContext mc) {
        return create(value, unit, scale, mc);
    }

    /**
     * Adds as if both operands were in the system unit, with the result back in this unit; linear units skip the
     * system round trip. If either operand is {@code RELATIVE}, {@code addend} is a difference: it is converted by
     * the factor only and added to this value in this unit. The result is {@code RELATIVE} only if both operands
     * are, otherwise {@code ABSOLUTE} (docs/OFFSET-UNITS.md). Evaluated exactly and rounded once to the lower
     * context of the two (irrational unit factors with guard digits, best effort); a {@code double} operand gives
     * a {@code double} result. Throws {@link UnconvertibleException} for a foreign unit or incompatible dimensions.
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
     * goes through the system unit, unless this quantity is {@code RELATIVE}, which is scaled as a plain value.
     * Evaluated exactly and rounded once to this context.
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
     * Compares the exact system-unit values (a consistent total preorder; two {@code RELATIVE} quantities as
     * differences, by the factor only), or with {@link Double#compare} against a
     * {@code double} operand; not consistent with equals. Exact for units with exact factors; irrational factors
     * are evaluated with guard digits, best effort. Against a {@code double} operand, a value of this quantity
     * outside the double range throws {@link ArithmeticException}.
     */
    @Override
    public int compareTo(Quantity<Q> that) {
        UomUnit<Q> thatUnit = uomUnit(that.getUnit());
        requireCompatible(thatUnit);
        Number n = that.getValue();
        boolean deltas = Deltas.both(scale, that.getScale());
        UomConverter ta = deltas ? Deltas.converter(unit.toSystemConverter()) : unit.toSystemConverter();
        UomConverter tb = deltas ? Deltas.converter(thatUnit.toSystemConverter()) : thatUnit.toSystemConverter();
        if (Precision.kind(n) == Precision.Kind.DOUBLE) {
            return Double.compare(ta.convert(Precision.toDouble(value)), tb.convert(n.doubleValue()));
        }
        // sign(sys(a) - sys(b)) = sign(fa) * sign(a - (fb/fa) b - (ob - oa)/fa)
        BigDecimal b = Precision.toDecimal(n);
        int digits = guardDigits(contextWith(that), b);
        Ratio g = Ratio.of(tb.factor().divide(ta.factor()), digits);
        Ratio h = Ratio.of(tb.offset().add(ta.offset().negate()).divide(ta.factor()), digits);
        BigDecimal d = value.multiply(g.den).multiply(h.den)
                .subtract(b.multiply(g.num).multiply(h.den)).subtract(h.num.multiply(g.den));
        return ta.factor().signum() * d.signum();
    }

    /**
     * Converts {@code that} into this unit (by the factor only if both are {@code RELATIVE}) and compares within
     * the relative tolerance {@code 10^(1-p)}, {@code p}
     * the lowest non-zero precision of both contexts (34 if unlimited); against a {@code double} operand the
     * {@code double} rule applies, and a value of this quantity outside the double range throws
     * {@link ArithmeticException}. At precision 1 the tolerance is 10^0 (degenerate by design). Incompatible
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
        UomConverter c = thatUnit.getConverterTo(unit);
        BigDecimal b = (Deltas.both(scale, that.getScale()) ? Deltas.converter(c) : c)
                .convert(Precision.toDecimal(n), mc);
        BigDecimal bound = value.abs().max(b.abs()).scaleByPowerOfTen(1 - mc.getPrecision());
        return value.compareTo(b) == 0 || value.subtract(b).abs().compareTo(bound) <= 0;
    }

    /**
     * Multiplies the system-unit values (operands are taken to the system unit, so offsets count, except for a
     * {@code RELATIVE} operand, which is a difference and counts by its factor only), evaluated exactly
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
        Ratio a = system(value, unit, guardDigits(mathContext, BigDecimal.ONE), scale == Quantity.Scale.RELATIVE);
        return UntypedDecimalQuantity.of(roundOnce(a.den, a.num, mathContext, a.exact),
                unit.getSystemUnit().inverse().getSystemUnit(), mathContext);
    }

    /**
     * Returns this quantity if it already is a {@code type}, otherwise one created by the provider's factory; a
     * unit whose dimension is not that of {@code type} gives {@link ClassCastException} (alternate units count by dimension), a type the provider has no
     * factory for gives {@link UnsupportedOperationException}. The value is never changed: a decimal result
     * carries this value and context, a factory result of another kind is returned as is.
     */
    @Override
    @SuppressWarnings("unchecked")
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
        Quantity<T> q = factory.create(value, typed, scale);
        // with returns the factory's typed interface, which is a Quantity<T>.
        return q instanceof AbstractDecimalQuantity<?, ?> d
                ? (Quantity<T>) d.with(value, uomUnit(typed), scale, mathContext) : q;
    }

    /** A quantity of this class with the given value, unit, scale and context, unrounded. */
    R with(BigDecimal v, UomUnit<?> u, Quantity.Scale s, MathContext mc) {
        return create(v, UomUnit.unchecked(u), s, mc);
    }

    /**
     * Writes the type, unit, scale, the value's unscaled bytes and scale, and the context; reading creates the
     * quantity through the factory of this implementation's provider and keeps value and context
     * unrounded. A type outside the 33 standard quantity interfaces and {@link Untyped}, an unscaled value over
     * 65536 bytes or a context precision over 100000 throws {@link java.io.NotSerializableException}.
     *
     * @return the proxy
     * @throws ObjectStreamException if the quantity cannot be serialized
     */
    @Serial
    protected final Object writeReplace() throws ObjectStreamException {
        return QuantityProxy.ofDecimal(quantityType(), unit, scale, value, mathContext);
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
        Ratio sa = system(value, unit, digits, scale == Quantity.Scale.RELATIVE);
        Ratio sb = system(b, thatUnit, digits, that.getScale() == Quantity.Scale.RELATIVE);
        BigDecimal num = divide ? sa.num.multiply(sb.den) : sa.num.multiply(sb.num);
        BigDecimal den = divide ? sa.den.multiply(sb.num) : sa.den.multiply(sb.den);
        UomUnit<?> product = divide ? unit.getSystemUnit().divide(thatUnit.getSystemUnit())
                : unit.getSystemUnit().multiply(thatUnit.getSystemUnit());
        return UntypedDecimalQuantity.of(roundOnce(num, den, mc, sa.exact && sb.exact), product.getSystemUnit(), mc);
    }

    /** The system value {@code f·v + o} as numerator over denominator; {@code o} is 0 for a difference. */
    private static Ratio system(BigDecimal v, UomUnit<?> u, int digits, boolean delta) {
        UomConverter c = delta ? Deltas.converter(u.toSystemConverter()) : u.toSystemConverter();
        Ratio f = Ratio.of(c.factor(), digits);
        Ratio o = Ratio.of(c.offset(), digits);
        return new Ratio(plus(v.multiply(f.num).multiply(o.den), o.num.multiply(f.den)), f.den.multiply(o.den),
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
        UomConverter tb = Deltas.isDelta(scale, that.getScale()) ? Deltas.converter(thatUnit.toSystemConverter())
                : thatUnit.toSystemConverter();
        BigDecimal b = Precision.toDecimal(n);
        int digits = guardDigits(mc, b);
        Ratio f = Ratio.of(tb.factor().divide(ta.factor()), digits);
        Ratio o = Ratio.of(tb.offset().divide(ta.factor()), digits);
        BigDecimal term = plus(b.multiply(f.num).multiply(o.den), o.num.multiply(f.den));
        BigDecimal ad = value.multiply(f.den).multiply(o.den);
        BigDecimal num = plus(ad, subtract ? term.negate() : term);
        return create(roundOnce(num, f.den.multiply(o.den), mc, f.exact && o.exact), unit,
                Deltas.scale(scale, that.getScale()), mc);
    }

    /** {@code r = a·s + q·(s - 1)} with {@code q = oa/fa} (or {@code 1/s} to divide), as one exact quotient. */
    private R scalar(BigDecimal s, boolean divide) {
        UomConverter ta = scale == Quantity.Scale.RELATIVE ? Deltas.converter(unit.toSystemConverter())
                : unit.toSystemConverter();
        Ratio q = Ratio.of(ta.offset().divide(ta.factor()), guardDigits(mathContext, s));
        BigDecimal aq = plus(value.multiply(q.den), q.num);
        BigDecimal num = divide ? plus(aq, q.num.multiply(s).negate()) : plus(aq.multiply(s), q.num.negate());
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
                return Decimals.divide(num, den, MathContext.UNLIMITED);
            } catch (ArithmeticException nonTerminating) {
                // a zero den throws again below
            }
        }
        return Decimals.divide(num, den, Precision.calculation(mc));
    }

    /** {@code a + b}, skipping a zero {@code b} so far-apart scales are not aligned. */
    private static BigDecimal plus(BigDecimal a, BigDecimal b) {
        return b.signum() == 0 ? a : a.add(b);
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
