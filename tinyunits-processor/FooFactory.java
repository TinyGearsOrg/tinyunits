

import org.tinygears.tinyunits.Dimension;
import org.tinygears.tinyunits.IncommensurableException;
import org.tinygears.tinyunits.Unit;

public final class FooFactory {
    public static final Dimension DIMENSION = new Dimension(1, 0, 0, 0, 0, 0, 0);
    public static final Unit<Foo> SYSTEM_UNIT = unit("f", 1);

    private FooFactory() {}

    public static Foo of(double value, Unit<Foo> unit) {
        return new FooImpl(value, unit);
    }

    public static Unit<Foo> unit(String symbol, double factor) {
        return unit(symbol, factor, 0);
    }

    public static Unit<Foo> unit(String symbol, double factor, double offset) {
        return new Unit<>(symbol, DIMENSION, factor, offset);
    }

    /** Types a derived unit, e.g. {@code metre.divide(second)}; fails if the dimension differs. */
    public static Unit<Foo> from(Unit<?> unit) {
        if (!DIMENSION.equals(unit.dimension())) {
            throw new IncommensurableException(unit + " is not a unit of Foo");
        }
        return unit(unit.symbol(), unit.factor(), unit.offset());
    }
}
