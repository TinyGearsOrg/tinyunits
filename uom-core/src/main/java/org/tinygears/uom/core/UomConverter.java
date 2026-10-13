// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core;

import java.math.BigDecimal;
import java.math.MathContext;
import javax.measure.UnitConverter;

/** An affine converter {@code x -> factor() * x + offset()}. */
public interface UomConverter extends UnitConverter {

    /**
     * Converts a decimal value.
     *
     * @param value the value
     * @param mc the rounding; precision 0 is exact where the converter allows, else {@code DECIMAL128}
     * @return the converted value
     */
    BigDecimal convert(BigDecimal value, MathContext mc);

    @Override
    UomConverter inverse();

    /** Converts by {@code other} first, then by this converter. */
    @Override
    UomConverter concatenate(UnitConverter other);

    /**
     * The factor.
     *
     * @return the factor
     */
    Factor factor();

    /**
     * The offset.
     *
     * @return the offset
     */
    Factor offset();
}
