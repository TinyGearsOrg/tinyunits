/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.jsr;

import javax.measure.spi.ServiceProvider;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Makes {@link TestServiceProvider} the current provider for each test of a class and restores the previous one
 * after it: the tests of the provider mechanism (asType, deserialization through the current provider, odd or failing
 * factories) need factories of their own, next to the real provider of this module.
 */
// Test code compiled into the module: JUnit's types are not the module's to export.
@SuppressWarnings("exports")
public final class TestProviderExtension implements BeforeEachCallback, AfterEachCallback {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace
            .create(TestProviderExtension.class);

    /** Created by JUnit. */
    public TestProviderExtension() {
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        context.getStore(NAMESPACE).put("before", ServiceProvider.current());
        ServiceProvider.setCurrent(new TestServiceProvider());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        ServiceProvider.setCurrent(context.getStore(NAMESPACE).get("before", ServiceProvider.class));
    }
}
