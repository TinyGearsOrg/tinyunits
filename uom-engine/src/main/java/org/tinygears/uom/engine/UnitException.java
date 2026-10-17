/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.engine;

import java.io.Serial;

/**
 * A unit definition the engine refuses: an invalid or reused symbol, or a definition that contradicts its dimension.
 * The JSR-385 implementation reports it as a {@code javax.measure.MeasurementException}.
 */
public class UnitException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * An exception with a message.
     *
     * @param message the message
     */
    public UnitException(String message) {
        super(message);
    }
}
