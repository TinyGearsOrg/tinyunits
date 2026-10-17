/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom;

import java.io.Serial;

/** Units that cannot be converted to each other: different dimensions, or no exact converter. */
public final class UnitException extends RuntimeException {
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

    /**
     * An exception with a message and a cause.
     *
     * @param message the message
     * @param cause the cause
     */
    public UnitException(String message, Throwable cause) {
        super(message, cause);
    }
}
