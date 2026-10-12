// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.codegen;

import org.tinygears.uom.core.parse.Definition;

/** A catalog constant; {@code quantityType} is null for an untyped unit. */
public record CatalogEntry(Definition definition, String constantName, String quantityType) {
}
