/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.core.spi;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Length;
import javax.measure.spi.FormatService;
import javax.measure.spi.QuantityFactory;
import javax.measure.spi.ServiceProvider;
import javax.measure.spi.SystemOfUnitsService;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The lookup order of {@link Providers}: our provider if current, else ours among the available, else current. */
class ProvidersTest {

    @SuppressWarnings("unchecked")
    private static QuantityFactory<Length> factory() {
        return (QuantityFactory<Length>) Proxy.newProxyInstance(
                ProvidersTest.class.getClassLoader(),
                new Class<?>[] { QuantityFactory.class },
                (p, m, a) -> null);
    }

    private static class Foreign extends ServiceProvider {
        final QuantityFactory<Length> factory = factory();

        @Override
        public SystemOfUnitsService getSystemOfUnitsService() {
            return null;
        }

        @Override
        public FormatService getFormatService() {
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <Q extends Quantity<Q>> QuantityFactory<Q> getQuantityFactory(Class<Q> quantity) {
            return (QuantityFactory<Q>) factory;
        }
    }

    private static final class Ours extends Foreign implements QuantityFactories {
    }

    private static class Texting extends Foreign implements UnitTexts {
        @Override
        public String unitText(Unit<?> unit) {
            return "text of " + unit;
        }
    }

    @Test
    void theCurrentProviderIsUsedIfItIsOurs() {
        Ours ours = new Ours();
        Providers providers = new Providers(() -> ours, () -> {
            throw new AssertionError("the available providers must not be listed");
        });
        assertSame(ours.factory, providers.factory(Length.class));
    }

    @Test
    void ourProviderIsFoundAndKeptWhenAnotherIsCurrent() {
        Foreign foreign = new Foreign();
        Ours ours = new Ours();
        AtomicInteger listed = new AtomicInteger();
        Providers providers = new Providers(() -> foreign, () -> {
            listed.incrementAndGet();
            return List.of(foreign, ours);
        });
        assertSame(ours.factory, providers.factory(Length.class));
        assertSame(ours.factory, providers.factory(Length.class));
        assertEquals(1, listed.get());
    }

    @Test
    void theCurrentProviderIsTheFallback() {
        Foreign foreign = new Foreign();
        Providers providers = new Providers(() -> foreign, () -> List.of(foreign));
        assertSame(foreign.factory, providers.factory(Length.class));
    }

    @Test
    void theTextOfAUnitComesFromOurProviderAndTheProviderIsKept() {
        Unit<?> unit = org.tinygears.uom.core.BaseUnits.METRE;
        AtomicInteger listed = new AtomicInteger();
        Providers providers = new Providers(() -> new Foreign(), () -> {
            listed.incrementAndGet();
            return List.of(new Foreign(), new Texting());
        });
        assertEquals("text of m", providers.text(unit));
        assertEquals("text of m", providers.text(unit));
        assertEquals(1, listed.get());
    }

    @Test
    void aMissIsKeptToo() {
        AtomicInteger listed = new AtomicInteger();
        Providers providers = new Providers(Foreign::new, () -> {
            listed.incrementAndGet();
            return List.of(new Foreign());
        });
        assertNull(providers.text(org.tinygears.uom.core.BaseUnits.METRE));
        assertNull(providers.text(org.tinygears.uom.core.BaseUnits.METRE));
        assertEquals(1, listed.get());
    }

    @Test
    void aUnitAlwaysPrintsWhateverTheProvidersDo() {
        Providers none = new Providers(() -> {
            throw new IllegalStateException("No Measurement ServiceProvider found.");
        }, () -> {
            throw new IllegalStateException("No Measurement ServiceProvider found.");
        });
        assertNull(none.text(org.tinygears.uom.core.BaseUnits.METRE));
        Providers failing = new Providers(Foreign::new, () -> List.of(new Texting() {
            @Override
            public String unitText(Unit<?> unit) {
                throw new UnsupportedOperationException("broken");
            }
        }));
        assertNull(failing.text(org.tinygears.uom.core.BaseUnits.METRE));
    }

    private static class Loading extends Foreign implements UnitCatalogs {
        final AtomicInteger loads = new AtomicInteger();

        @Override
        public void loadCatalogs() {
            loads.incrementAndGet();
        }
    }

    @Test
    void ourProviderLoadsTheCatalogsAndIsKept() {
        Loading loading = new Loading();
        AtomicInteger listed = new AtomicInteger();
        Providers providers = new Providers(Foreign::new, () -> {
            listed.incrementAndGet();
            return List.of(new Foreign(), loading);
        });
        providers.load();
        providers.load();
        assertEquals(2, loading.loads.get());
        assertEquals(1, listed.get());
    }

    @Test
    void loadingTheCatalogsNeverThrows() {
        Providers none = new Providers(Foreign::new, () -> {
            throw new IllegalStateException("No Measurement ServiceProvider found.");
        });
        none.load();
        Loading failing = new Loading() {
            @Override
            public void loadCatalogs() {
                super.loadCatalogs();
                throw new IllegalStateException("broken");
            }
        };
        Providers broken = new Providers(Foreign::new, () -> List.of(failing));
        broken.load();
        assertEquals(1, failing.loads.get());
    }

    @Test
    void noProviderIsAnError() {
        Providers providers = new Providers(() -> {
            throw new IllegalStateException("No Measurement ServiceProvider found.");
        }, List::of);
        assertThrows(IllegalStateException.class, () -> providers.factory(Length.class));
    }

    @Test
    void aNullFactoryMeansNoFactory() {
        ServiceProvider none = new Foreign() {
            @Override
            public <Q extends Quantity<Q>> QuantityFactory<Q> getQuantityFactory(Class<Q> quantity) {
                return null;
            }
        };
        Providers providers = new Providers(() -> none, () -> List.of(none));
        assertEquals(
                "no quantity factory for javax.measure.quantity.Length",
                assertThrows(IllegalArgumentException.class, () -> providers.factory(Length.class)).getMessage());
    }
}
