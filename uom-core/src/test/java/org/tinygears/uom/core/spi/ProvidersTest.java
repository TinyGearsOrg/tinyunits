// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.measure.Quantity;
import javax.measure.quantity.Length;
import javax.measure.spi.FormatService;
import javax.measure.spi.QuantityFactory;
import javax.measure.spi.ServiceProvider;
import javax.measure.spi.SystemOfUnitsService;
import org.junit.jupiter.api.Test;

/** The lookup order of {@link Providers}: our provider if current, else ours among the available, else current. */
class ProvidersTest {

    @SuppressWarnings("unchecked")
    private static QuantityFactory<Length> factory() {
        return (QuantityFactory<Length>) Proxy.newProxyInstance(ProvidersTest.class.getClassLoader(),
                new Class<?>[] {QuantityFactory.class}, (p, m, a) -> null);
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
    void noProviderIsAnError() {
        Providers providers = new Providers(() -> {
            throw new IllegalStateException("No Measurement ServiceProvider found.");
        }, List::of);
        assertThrows(IllegalStateException.class, () -> providers.factory(Length.class));
    }
}
