// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.spi;

import javax.measure.Unit;

/**
 * Implemented by the service provider of this implementation, so that an unnamed unit prints as its system's format
 * writes it ({@code mHz}, {@code km}) without the core knowing any system; exported to {@code org.tinygears.uom} only,
 * not API.
 */
public interface UnitTexts {

    /**
     * The text of {@code unit} in the standard format.
     *
     * @param unit the unit
     * @return the text, or null if the format has none for the unit
     */
    String unitText(Unit<?> unit);
}
