// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.spi;

import java.util.List;
import java.util.function.Supplier;
import javax.measure.Quantity;
import javax.measure.spi.QuantityFactory;
import javax.measure.spi.ServiceProvider;

/**
 * Finds the quantity factories of this implementation: those of the current provider if it is ours, else those of
 * ours among the available providers, else the current provider's. Not exported, not API.
 */
public final class Providers {
    private static final Providers DEFAULT = new Providers(ServiceProvider::current, ServiceProvider::available);

    private final Supplier<ServiceProvider> current;
    private final Supplier<List<ServiceProvider>> available;
    // available() builds new providers on every call, so the one found is kept.
    private volatile QuantityFactories found;

    Providers(Supplier<ServiceProvider> current, Supplier<List<ServiceProvider>> available) {
        this.current = current;
        this.available = available;
    }

    /**
     * The factory for {@code type}.
     *
     * @param <Q> the quantity type
     * @param type the quantity type
     * @return the factory
     * @throws IllegalStateException if no provider is available
     */
    public static <Q extends Quantity<Q>> QuantityFactory<Q> quantityFactory(Class<Q> type) {
        return DEFAULT.factory(type);
    }

    <Q extends Quantity<Q>> QuantityFactory<Q> factory(Class<Q> type) {
        ServiceProvider provider = current.get();
        if (provider instanceof QuantityFactories ours) {
            return ours.getQuantityFactory(type);
        }
        QuantityFactories ours = found;
        if (ours == null) {
            for (ServiceProvider candidate : available.get()) {
                if (candidate instanceof QuantityFactories f) {
                    found = ours = f;
                    break;
                }
            }
        }
        return ours != null ? ours.getQuantityFactory(type) : provider.getQuantityFactory(type);
    }
}
