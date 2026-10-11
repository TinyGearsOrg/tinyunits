package org.tinygears.tinyunits;

/** Thrown when units of different dimensions are combined. */
public class IncommensurableException extends IllegalArgumentException {

    public IncommensurableException(String message) {
        super(message);
    }
}
