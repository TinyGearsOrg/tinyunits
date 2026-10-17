/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.engine.parse;

import java.io.Serial;

/** A malformed or unevaluable unit formula; {@link #column()} is 1-based. */
public final class FormulaException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    /** The 1-based column. */
    private final int column;

    /**
     * An error at a column.
     *
     * @param message the message
     * @param column the 1-based column
     */
    public FormulaException(String message, int column) {
        this(message, column, null);
    }

    /**
     * An error at a column with a cause.
     *
     * @param message the message
     * @param column the 1-based column
     * @param cause the cause, or null
     */
    public FormulaException(String message, int column, Throwable cause) {
        super(message, cause);
        this.column = column;
    }

    /**
     * The column.
     *
     * @return the 1-based column
     */
    public int column() {
        return column;
    }
}
