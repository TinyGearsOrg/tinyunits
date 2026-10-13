// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.quantity;

import javax.measure.Quantity;
import org.tinygears.uom.core.Converters;
import org.tinygears.uom.core.UomConverter;

/** The rules for quantities of scale {@code RELATIVE}, which are differences and take no offset. */
final class Deltas {

    private Deltas() {
    }

    /** {@code c} without its offset: how a difference converts. A linear converter is returned as is. */
    static UomConverter converter(UomConverter c) {
        return c.isLinear() ? c : Converters.scale(c.factor());
    }

    /** The scale of a sum or difference: {@code ABSOLUTE} unless both operands are {@code RELATIVE}. */
    static Quantity.Scale scale(Quantity.Scale a, Quantity.Scale b) {
        return a == Quantity.Scale.RELATIVE && b == Quantity.Scale.RELATIVE ? Quantity.Scale.RELATIVE
                : Quantity.Scale.ABSOLUTE;
    }

    /** Whether a sum or difference of these scales treats the operands as differences. */
    static boolean isDelta(Quantity.Scale a, Quantity.Scale b) {
        return a == Quantity.Scale.RELATIVE || b == Quantity.Scale.RELATIVE;
    }

    /** Whether two quantities of these scales are both differences. */
    static boolean both(Quantity.Scale a, Quantity.Scale b) {
        return a == Quantity.Scale.RELATIVE && b == Quantity.Scale.RELATIVE;
    }
}
