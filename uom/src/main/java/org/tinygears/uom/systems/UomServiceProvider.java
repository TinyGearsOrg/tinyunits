// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.systems;

import javax.measure.Quantity;
import javax.measure.spi.FormatService;
import javax.measure.spi.QuantityFactory;
import javax.measure.spi.ServiceProvider;
import javax.measure.spi.SystemOfUnitsService;
import org.tinygears.uom.quantity.QuantityTypes;

/** The JSR-385 service provider of this implementation. */
public final class UomServiceProvider extends ServiceProvider {
    private final SystemOfUnitsService systems = new UomSystemOfUnitsService();
    private final FormatService formats = new UomFormatService();

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
}
