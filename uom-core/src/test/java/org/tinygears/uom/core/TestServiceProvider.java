package org.tinygears.uom.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.spi.FormatService;
import javax.measure.spi.QuantityFactory;
import javax.measure.spi.ServiceProvider;
import javax.measure.spi.SystemOfUnitsService;

/** Test-only provider whose quantity factories report the system units tests put in {@link #SYSTEM_UNITS}. */
public class TestServiceProvider extends ServiceProvider {

    static final Map<Class<?>, Unit<?>> SYSTEM_UNITS = new ConcurrentHashMap<>();

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
        Unit<?> unit = SYSTEM_UNITS.get(type);
        if (unit == null) {
            throw new IllegalArgumentException("no system unit for " + type.getName());
        }
        return new QuantityFactory<>() {
            @Override
            public Quantity<Q> create(Number value, Unit<Q> u, Quantity.Scale scale) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Quantity<Q> create(Number value, Unit<Q> u) {
                throw new UnsupportedOperationException();
            }

            @Override
            @SuppressWarnings("unchecked") // the test registers the unit under its quantity class
            public Unit<Q> getSystemUnit() {
                return (Unit<Q>) unit;
            }
        };
    }
}
