// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.parse;

/** An error in a definition file; the message reads {@code source:line: text}. */
public final class DefinitionException extends RuntimeException {
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
