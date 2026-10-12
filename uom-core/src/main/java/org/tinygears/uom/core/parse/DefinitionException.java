// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.parse;

/** An error in a definition file; the message reads {@code source:line: text}. */
public final class DefinitionException extends RuntimeException {
    private final String source;
    private final int line;

    public DefinitionException(String source, int line, String text, Throwable cause) {
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
