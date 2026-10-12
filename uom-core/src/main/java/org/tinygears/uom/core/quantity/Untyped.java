// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.quantity;

import javax.measure.Quantity;

/** Phantom quantity type of products, quotients and inverses; narrow with {@link Quantity#asType}. */
public interface Untyped extends Quantity<Untyped> {
}
