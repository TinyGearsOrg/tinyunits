// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.codegen;

/** An error in a generator input file; the message reads {@code source:line: text}. */
public final class GenerationException extends RuntimeException {
    private final String source;
    private final int line;

    public GenerationException(String source, int line, String text) {
        this(source, line, text, null);
    }

    public GenerationException(String source, int line, String text, Throwable cause) {
        super(source + ":" + line + ": " + text, cause);
        this.source = source;
        this.line = line;
    }

    public String source() {
        return source;
    }

    public int line() {
        return line;
    }
}
