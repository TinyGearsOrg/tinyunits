/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.jsr.quantity;

import javax.measure.Quantity;

/** Phantom quantity type of products, quotients and inverses; narrow with {@link UomQuantity#as} or {@link Quantity#asType}. */
public interface Untyped extends UomQuantity<Untyped, Untyped> {
}
