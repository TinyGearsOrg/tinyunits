// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core;

import java.math.BigDecimal;
import java.math.MathContext;
import javax.measure.UnitConverter;

/** An affine converter {@code x -> factor() * x + offset()}. */
public interface UomConverter extends UnitConverter {

    BigDecimal convert(BigDecimal value, MathContext mc);

    @Override
    UomConverter inverse();

    /** Converts by {@code other} first, then by this converter. */
    @Override
    UomConverter concatenate(UnitConverter other);

    Factor factor();

    Factor offset();
}
