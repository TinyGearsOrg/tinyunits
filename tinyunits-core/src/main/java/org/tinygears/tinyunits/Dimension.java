package org.tinygears.tinyunits;

/** Exponents of the seven SI base dimensions. */
public record Dimension(int length, int mass, int time, int current, int temperature, int amount, int luminosity) {

    public Dimension multiply(Dimension o) {
        return new Dimension(length + o.length, mass + o.mass, time + o.time, current + o.current,
                             temperature + o.temperature, amount + o.amount, luminosity + o.luminosity);
    }

    public Dimension divide(Dimension o) {
        return new Dimension(length - o.length, mass - o.mass, time - o.time, current - o.current,
                             temperature - o.temperature, amount - o.amount, luminosity - o.luminosity);
    }
}
