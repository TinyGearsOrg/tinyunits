// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.systems;

import javax.measure.Quantity;
import javax.measure.spi.FormatService;
import javax.measure.spi.QuantityFactory;
import javax.measure.spi.ServiceProvider;
import javax.measure.spi.SystemOfUnitsService;
import org.tinygears.uom.core.spi.QuantityFactories;
import org.tinygears.uom.quantity.QuantityTypes;

/** The JSR-385 service provider of this implementation. */
public final class UomServiceProvider extends ServiceProvider implements QuantityFactories {
    private final SystemOfUnitsService systems = new UomSystemOfUnitsService();
    private final FormatService formats = new UomFormatService();

    /**
     * Called by the {@link java.util.ServiceLoader}.
     */
    public UomServiceProvider() {
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public SystemOfUnitsService getSystemOfUnitsService() {
        return systems;
    }

    @Override
    public FormatService getFormatService() {
        return formats;
    }

    @Override
    public <Q extends Quantity<Q>> QuantityFactory<Q> getQuantityFactory(Class<Q> quantity) {
        return QuantityTypes.factory(quantity);
    }

    /**
     * The name {@code uom}, by which {@code ServiceProvider.of("uom")} finds this provider.
     *
     * @return the name
     */
    @Override
    public String toString() {
        return "uom";
    }
}
