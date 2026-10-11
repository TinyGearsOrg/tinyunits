package org.tinygears.tinyunits;

/**
 * A unit of quantity type Q, defined by its affine relation to the system unit:
 * {@code systemValue = value * factor + offset}.
 */
public record Unit<Q extends Quantity<Q>>(String symbol, Dimension dimension, double factor, double offset) {

    public double toSystem(double value) {
        return value * factor + offset;
    }

    public double convert(double value, Unit<Q> target) {
        return (toSystem(value) - target.offset) / target.factor;
    }

    /** Result is untyped; turn it into a typed unit with the generated {@code XxxFactory.from}. */
    public Unit<?> multiply(Unit<?> that) {
        return derive(symbol + "*" + that.symbol, dimension.multiply(that.dimension), factor * that.factor, that);
    }

    public Unit<?> divide(Unit<?> that) {
        return derive(symbol + "/" + that.symbol, dimension.divide(that.dimension), factor / that.factor, that);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Unit<?> derive(String symbol, Dimension dimension, double factor, Unit<?> that) {
        if (offset != 0 || that.offset != 0) {
            throw new IllegalArgumentException("units with an offset can not be multiplied or divided");
        }
        return new Unit(symbol, dimension, factor, 0);
    }
}
