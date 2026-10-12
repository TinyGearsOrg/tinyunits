// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.quantity;

import javax.measure.Quantity;
import org.tinygears.uom.core.UomUnit;

/** Result of a quantity product, quotient or inverse, in a system unit; created only by the base class. */
public final class UntypedDoubleQuantity extends AbstractDoubleQuantity<Untyped, Untyped> implements Untyped {

    UntypedDoubleQuantity(double value, UomUnit<Untyped> unit, Quantity.Scale scale) {
        super(value, unit, scale);
    }

    @Override
    protected Untyped create(double value, UomUnit<Untyped> unit, Quantity.Scale scale) {
        return new UntypedDoubleQuantity(value, unit, scale);
    }
}
