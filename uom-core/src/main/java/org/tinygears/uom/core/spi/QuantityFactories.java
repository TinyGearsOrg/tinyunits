// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.spi;

import javax.measure.Quantity;
import javax.measure.spi.QuantityFactory;

/**
 * Implemented by the service provider of this implementation, so that the core finds its quantity factories without
 * depending on which provider is current; exported to {@code org.tinygears.uom} only, not API.
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
