// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.systems;

import java.util.Objects;
import java.util.Set;
import javax.measure.format.QuantityFormat;
import javax.measure.format.UnitFormat;
import javax.measure.spi.FormatService;

/** Provides one format of each kind, named {@code default}, shared by all instances so labels are system-wide. */
public final class UomFormatService implements FormatService {
    private static final String DEFAULT = "default";
    private static final UomUnitFormat UNIT_FORMAT = UomUnitFormat.standard();
    private static final UomQuantityFormat QUANTITY_FORMAT = new UomQuantityFormat(UNIT_FORMAT);

    @Override
    public QuantityFormat getQuantityFormat() {
        return QUANTITY_FORMAT;
    }

    /** The default format for {@code default}, else null. */
    @Override
    public QuantityFormat getQuantityFormat(String name) {
        return DEFAULT.equals(name) ? QUANTITY_FORMAT : null;
    }

    @Override
    public UnitFormat getUnitFormat() {
        return UNIT_FORMAT;
    }

    /** The default format for {@code default}, else null. */
    @Override
    public UnitFormat getUnitFormat(String name) {
        return DEFAULT.equals(name) ? UNIT_FORMAT : null;
    }

    /** As {@link #getUnitFormat(String)}: no variant is applicable, so the variant is ignored. */
    @Override
    public UnitFormat getUnitFormat(String name, String variant) {
        return getUnitFormat(name);
    }

    @Override
    public Set<String> getAvailableFormatNames(FormatType type) {
        Objects.requireNonNull(type, "type");
        return Set.of(DEFAULT);
    }
}
