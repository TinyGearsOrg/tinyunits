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

/** A unit or quantity text that does not parse, with the text and the index of the error in it. */
public final class ParseException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    /** The parsed text. */
    private final String text;
    /** The index of the error. */
    private final int position;

    /**
     * An error at {@code position} of {@code text}.
     *
     * @param message the message
     * @param text the parsed text
     * @param position the index of the error
     */
    public ParseException(String message, CharSequence text, int position) {
        super(message);
        this.text = text.toString();
        this.position = position;
    }

    /**
     * The parsed text.
     *
     * @return the text
     */
    public String text() {
        return text;
    }

    /**
     * The index of the error in {@link #text()}.
     *
     * @return the index
     */
    public int position() {
        return position;
    }
}
