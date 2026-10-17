/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.jsr.spi;

import javax.measure.Quantity;
import javax.measure.spi.QuantityFactory;

/**
 * Implemented by the service provider of this implementation, so that the core finds its quantity factories without
 * depending on which provider is current; exported to {@code org.tinygears.uom.jsr} only, not API.
 */
public interface QuantityFactories {

    /**
     * The factory of this implementation for {@code quantity}.
     *
     * @param <Q> the quantity type
     * @param quantity the quantity type
     * @return the factory
     */
    <Q extends Quantity<Q>> QuantityFactory<Q> getQuantityFactory(Class<Q> quantity);
}
