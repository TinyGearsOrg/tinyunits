// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.quantity;

import javax.measure.Quantity;
import javax.measure.spi.FormatService;
import javax.measure.spi.QuantityFactory;
import javax.measure.spi.ServiceProvider;
import javax.measure.spi.SystemOfUnitsService;

/** Test-only provider backed by the generated {@link QuantityTypes} registry. */
public class TestServiceProvider extends ServiceProvider {

    @Override
    public SystemOfUnitsService getSystemOfUnitsService() {
        return null;
    }

    @Override
    public FormatService getFormatService() {
        return null;
    }

    @Override
    public <Q extends Quantity<Q>> QuantityFactory<Q> getQuantityFactory(Class<Q> type) {
        return QuantityTypes.factory(type);
    }
}
