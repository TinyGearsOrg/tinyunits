// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.quantity;

import java.math.BigDecimal;
import java.math.MathContext;
import javax.measure.Quantity;
import org.tinygears.uom.core.UomUnit;

/** A quantity of no standard type: a product, quotient or inverse, or a parsed value in an untyped unit. */
public final class UntypedDoubleQuantity extends AbstractDoubleQuantity<Untyped, Untyped> implements Untyped {

    UntypedDoubleQuantity(double value, UomUnit<Untyped> unit, Quantity.Scale scale) {
        super(value, unit, scale);
    }

    public static UntypedDoubleQuantity of(double value, UomUnit<?> unit) {
        return new UntypedDoubleQuantity(value, UomUnit.unchecked(unit), Quantity.Scale.ABSOLUTE);
    }

    @Override
    protected Untyped create(double value, UomUnit<Untyped> unit, Quantity.Scale scale) {
        return new UntypedDoubleQuantity(value, unit, scale);
    }

    @Override
    protected Untyped createDecimal(BigDecimal value, UomUnit<Untyped> unit, Quantity.Scale scale, MathContext mc) {
        return new UntypedDecimalQuantity(value, unit, scale, mc);
    }
}
