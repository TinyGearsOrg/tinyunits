

import org.tinygears.tinyunits.Unit;

import java.util.Objects;

final class FooImpl implements Foo {
    private final double value;
    private final Unit<Foo> unit;

    FooImpl(double value, Unit<Foo> unit) {
        this.value = value;
        this.unit = Objects.requireNonNull(unit);
    }

    @Override public double value() { return value; }

    @Override public Unit<Foo> unit() { return unit; }

    @Override public Foo to(Unit<Foo> target) {
        return target.equals(unit) ? this : new FooImpl(unit.convert(value, target), target);
    }

    @Override public Foo add(Foo addend) {
        return new FooImpl(value + addend.value(unit), unit);
    }

    @Override public Foo subtract(Foo subtrahend) {
        return new FooImpl(value - subtrahend.value(unit), unit);
    }

    @Override public Foo negate() { return new FooImpl(-value, unit); }

    @Override public int compareTo(Foo other) {
        return Double.compare(unit.toSystem(value), other.unit().toSystem(other.value()));
    }

    @Override public boolean equals(Object o) {
        return o instanceof FooImpl q && Double.compare(value, q.value) == 0 && unit.equals(q.unit);
    }

    @Override public int hashCode() { return Objects.hash(value, unit); }

    @Override public String toString() { return value + " " + unit.symbol(); }
}
