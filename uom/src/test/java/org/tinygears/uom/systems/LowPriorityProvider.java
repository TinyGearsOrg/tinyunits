/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.systems;

import javax.measure.Quantity;
import javax.measure.spi.FormatService;
import javax.measure.spi.QuantityFactory;
import javax.measure.spi.ServiceProvider;
import javax.measure.spi.SystemOfUnitsService;

/** Test-only provider that must never win over the real one. */
public final class LowPriorityProvider extends ServiceProvider {
    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public SystemOfUnitsService getSystemOfUnitsService() {
        return null;
    }

    @Override
    public FormatService getFormatService() {
        return null;
    }

    @Override
    public <Q extends Quantity<Q>> QuantityFactory<Q> getQuantityFactory(Class<Q> quantity) {
        return null;
    }
}
