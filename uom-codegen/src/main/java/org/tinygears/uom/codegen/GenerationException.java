// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.codegen;

/** An error in a generator input; with a file position the message reads {@code source:line: text}. */
public final class GenerationException extends RuntimeException {
    private final String source;
    private final int line;

    /** An error without a file position; the message is {@code text}. */
    public GenerationException(String text) {
        super(text);
        this.source = null;
        this.line = 0;
    }

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
