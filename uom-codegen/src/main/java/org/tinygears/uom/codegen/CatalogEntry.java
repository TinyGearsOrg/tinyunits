// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.codegen;

import org.tinygears.uom.core.parse.Definition;

/**
 * A catalog constant; {@code quantityType} is null for an untyped unit, {@code shared} names the factory system unit
 * the constant reuses ({@code org.tinygears.uom.quantity.LengthFactory.SYSTEM_UNIT}) or is null.
 */
public record CatalogEntry(Definition definition, String constantName, String quantityType, String shared) {
}
