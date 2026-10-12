// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.quantity;

import java.util.Objects;
import javax.measure.Quantity;
import javax.measure.UnconvertibleException;
import javax.measure.Unit;
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
