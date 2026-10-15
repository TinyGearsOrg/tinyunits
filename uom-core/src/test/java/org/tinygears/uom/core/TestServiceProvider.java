/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.spi.FormatService;
import javax.measure.spi.QuantityFactory;
import javax.measure.spi.ServiceProvider;
import javax.measure.spi.SystemOfUnitsService;

/**
 * Test-only provider: returns the factory a test put in {@link #FACTORIES}, otherwise one that only reports the
 * system unit put in {@link #SYSTEM_UNITS}.
 */
public class TestServiceProvider extends ServiceProvider {

    public static final Map<Class<?>, Unit<?>> SYSTEM_UNITS = new ConcurrentHashMap<>();
    public static final Map<Class<?>, QuantityFactory<?>> FACTORIES = new ConcurrentHashMap<>();

    @Override
    public SystemOfUnitsService getSystemOfUnitsService() {
        return null;
    }

    @Override
    public FormatService getFormatService() {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked") // tests register each factory under its quantity class
    public <Q extends Quantity<Q>> QuantityFactory<Q> getQuantityFactory(Class<Q> type) {
        QuantityFactory<?> factory = FACTORIES.get(type);
        if (factory != null) {
            return (QuantityFactory<Q>) factory;
        }
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
            public Unit<Q> getSystemUnit() {
                return (Unit<Q>) unit;
            }
        };
    }
}
