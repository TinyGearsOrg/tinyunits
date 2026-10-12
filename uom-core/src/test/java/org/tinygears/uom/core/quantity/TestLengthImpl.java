// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.quantity;

import javax.measure.Quantity;
import javax.measure.quantity.Length;
import org.tinygears.uom.core.UomUnit;

/** Stand-in for a generated double implementation; arithmetic is stubbed until the base class has it. */
class TestLengthImpl extends AbstractDoubleQuantity<Length, TestLength> implements TestLength {

    TestLengthImpl(double value, UomUnit<Length> unit, Quantity.Scale scale) {
        super(value, unit, scale);
    }

    TestLengthImpl(double value, UomUnit<Length> unit) {
        this(value, unit, Quantity.Scale.ABSOLUTE);
    }

    @Override
    protected TestLength create(double value, UomUnit<Length> unit, Quantity.Scale scale) {
        return new TestLengthImpl(value, unit, scale);
    }

    @Override
    public Quantity<Length> add(Quantity<Length> addend) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Quantity<Length> subtract(Quantity<Length> subtrahend) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Quantity<?> divide(Quantity<?> divisor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Quantity<Length> divide(Number divisor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Quantity<?> multiply(Quantity<?> multiplier) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Quantity<Length> multiply(Number multiplier) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Quantity<?> inverse() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Quantity<Length> negate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends Quantity<T>> Quantity<T> asType(Class<T> type) {
        throw new UnsupportedOperationException();
    }
}
