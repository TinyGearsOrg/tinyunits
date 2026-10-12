// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.quantity;

import java.math.BigDecimal;
import java.math.MathContext;
import javax.measure.Quantity;
import javax.measure.quantity.Length;
import org.tinygears.uom.core.UomUnit;

/** Stand-in for a generated decimal implementation; shares the typed interface with {@link TestLengthImpl}. */
class TestLengthDecimalImpl extends AbstractDecimalQuantity<Length, TestLength> implements TestLength {

    TestLengthDecimalImpl(BigDecimal value, UomUnit<Length> unit, Quantity.Scale scale, MathContext mc) {
        super(value, unit, scale, mc);
    }

    TestLengthDecimalImpl(BigDecimal value, UomUnit<Length> unit) {
        this(value, unit, Quantity.Scale.ABSOLUTE, MathContext.DECIMAL128);
    }

    @Override
    protected Class<Length> quantityType() {
        return Length.class;
    }

    @Override
    protected TestLength create(BigDecimal value, UomUnit<Length> unit, Quantity.Scale scale, MathContext mc) {
        return new TestLengthDecimalImpl(value, unit, scale, mc);
    }

    @Override
    protected TestLength createDouble(double value, UomUnit<Length> unit, Quantity.Scale scale) {
        return new TestLengthImpl(value, unit, scale);
    }
}
