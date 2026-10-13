// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.parse;

/** A malformed or unevaluable unit formula; {@link #column()} is 1-based. */
public final class FormulaException extends Exception {
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
