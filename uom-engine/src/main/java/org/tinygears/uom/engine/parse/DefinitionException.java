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

/** An error in a definition file; the message reads {@code source:line: text}. */
public final class DefinitionException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    /** The file. */
    private final String source;
    /** The 1-based line number. */
    private final int line;

    /**
     * An error at a line.
     *
     * @param source the file
     * @param line the 1-based line number
     * @param text the message without the position
     * @param cause the cause, or null
     */
    public DefinitionException(String source, int line, String text, Throwable cause) {
        super(source + ":" + line + ": " + text, cause);
        this.source = source;
        this.line = line;
    }

    /**
     * The file.
     *
     * @return the file
     */
    public String source() {
        return source;
    }

    /**
     * The line.
     *
     * @return the 1-based line number
     */
    public int line() {
        return line;
    }
}
