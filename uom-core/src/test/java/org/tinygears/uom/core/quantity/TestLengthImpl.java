// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.quantity;

import javax.measure.Quantity;
import javax.measure.quantity.Length;
import org.tinygears.uom.core.UomUnit;

/** Stand-in for a generated double implementation. */
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
}
