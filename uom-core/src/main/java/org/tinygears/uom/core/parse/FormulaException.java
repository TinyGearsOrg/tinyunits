package org.tinygears.uom.core.parse;

/** A malformed or unevaluable unit formula; {@link #column()} is 1-based. */
public final class FormulaException extends Exception {
    private final int column;

    public FormulaException(String message, int column) {
        this(message, column, null);
    }

    public FormulaException(String message, int column, Throwable cause) {
        super(message, cause);
        this.column = column;
    }

    public int column() {
        return column;
    }
}
