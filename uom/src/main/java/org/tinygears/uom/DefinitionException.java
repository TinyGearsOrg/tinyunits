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

/** An error in a {@code .units} file, with the file and line: the message is {@code file:line: text}. */
public final class DefinitionException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    /** The file of the error. */
    private final String source;
    /** The 1-based line of the error, 0 if the file as a whole. */
    private final int line;

    /**
     * An error at {@code source:line}.
     *
     * @param message the message, {@code file:line: text}
     * @param source the file
     * @param line the 1-based line, 0 for the file as a whole
     * @param cause the cause, or null
     */
    public DefinitionException(String message, String source, int line, Throwable cause) {
        super(message, cause);
        this.source = source;
        this.line = line;
    }

    /**
     * The file of the error.
     *
     * @return the file name
     */
    public String source() {
        return source;
    }

    /**
     * The line of the error.
     *
     * @return the 1-based line, 0 for the file as a whole
     */
    public int line() {
        return line;
    }
}
