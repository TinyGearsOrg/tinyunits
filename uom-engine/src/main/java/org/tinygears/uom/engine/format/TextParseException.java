/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.engine.format;

import java.io.Serial;

/** A text that does not parse, with the index of the error in the parsed text. */
public final class TextParseException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    /** The index of the error. */
    private final int position;

    /**
     * An error at {@code position}.
     *
     * @param message the message
     * @param position the index of the error in the parsed text
     */
    public TextParseException(String message, int position) {
        super(message);
        this.position = position;
    }

    /**
     * The index of the error in the parsed text.
     *
     * @return the index
     */
    public int position() {
        return position;
    }
}
