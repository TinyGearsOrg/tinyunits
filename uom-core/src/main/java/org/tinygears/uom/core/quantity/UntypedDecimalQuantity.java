// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.quantity;

import java.io.Serial;
import java.math.BigDecimal;
import java.math.MathContext;
import javax.measure.Quantity;
import org.tinygears.uom.core.UomUnit;

/** The {@link BigDecimal} counterpart of {@link UntypedDoubleQuantity}: a product, quotient or inverse. */
public final class UntypedDecimalQuantity extends AbstractDecimalQuantity<Untyped, Untyped> implements Untyped {

    @Serial
    private static final long serialVersionUID = 1L;

    UntypedDecimalQuantity(BigDecimal value, UomUnit<Untyped> unit, Quantity.Scale scale, MathContext mc) {
        super(value, unit, scale, mc);
    }

    /**
     * An {@code ABSOLUTE} quantity; throws {@link ArithmeticException} if the value's scale exceeds ±100000 or the
     * precision of {@code mc} exceeds 100000.
     *
     * @param value the value
     * @param unit the unit
     * @param mc the context
     * @return the quantity
     */
    public static UntypedDecimalQuantity of(BigDecimal value, UomUnit<?> unit, MathContext mc) {
        return new UntypedDecimalQuantity(value, UomUnit.unchecked(unit), Quantity.Scale.ABSOLUTE, mc);
    }

    @Override
    protected Class<Untyped> quantityType() {
        return Untyped.class;
    }

    @Override
    protected Untyped create(BigDecimal value, UomUnit<Untyped> unit, Quantity.Scale scale, MathContext mc) {
        return new UntypedDecimalQuantity(value, unit, scale, mc);
    }

    @Override
    protected Untyped createDouble(double value, UomUnit<Untyped> unit, Quantity.Scale scale) {
        return new UntypedDoubleQuantity(value, unit, scale);
    }
}
