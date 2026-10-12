// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.measure.UnitConverter;

/** Serializable through a proxy that rebuilds with {@link Converters} and keeps the conversion steps in order. */
final class AffineConverter implements UomConverter, Serializable {

    private final Factor factor;
    private final Factor offset;
    private final double factorD;
    private final double offsetD;
    private final List<UnitConverter> steps;

    AffineConverter(Factor factor, Factor offset) {
        this(factor, offset, null);
    }

    private AffineConverter(Factor factor, Factor offset, List<UnitConverter> steps) {
        this.factor = factor;
        this.offset = offset;
        this.factorD = factor.doubleValue();
        this.offsetD = offset.doubleValue();
        this.steps = steps != null ? steps : List.of(this);
    }

    @Override
    public Factor factor() {
        return factor;
    }

    @Override
    public Factor offset() {
        return offset;
    }

    @Override
    public boolean isIdentity() {
        return factor.isOne() && offset.isZero();
    }

    @Override
    public boolean isLinear() {
        return offset.isZero();
    }

    @Override
    public UomConverter inverse() {
        Factor f = Factor.ONE.divide(factor);
        return new AffineConverter(f, offset.negate().multiply(f));
    }

    @Override
    public UomConverter concatenate(UnitConverter other) {
        if (!(other instanceof UomConverter o)) {
            throw new IllegalArgumentException("not a UomConverter: " + other);
        }
        List<UnitConverter> all = new ArrayList<>(steps);
        all.addAll(o.getConversionSteps());
        return new AffineConverter(factor.multiply(o.factor()), factor.multiply(o.offset()).add(offset),
                List.copyOf(all));
    }

    @Override
    public List<UnitConverter> getConversionSteps() {
        return steps;
    }

    @Override
    public double convert(double value) {
        // Adding a zero offset would turn -0.0 into 0.0.
        return offsetD == 0 ? factorD * value : factorD * value + offsetD;
    }

    @Override
    public Number convert(Number value) {
        if (value instanceof BigDecimal b) {
            return convert(b, MathContext.DECIMAL128);
        }
        return convert(value.doubleValue());
    }

    @Override
    public BigDecimal convert(BigDecimal value, MathContext mc) {
        if (mc.getPrecision() == 0) {
            try {
                return value.multiply(factor.decimalValue(mc)).add(offset.decimalValue(mc));
            } catch (ArithmeticException inexact) {
                // Non-terminating or irrational coefficients: fall back to DECIMAL128.
                return convert(value, MathContext.DECIMAL128);
            }
        }
        if (factor.isExact() && offset.isExact()) {
            // value * nf/df + no/do as one exact quotient, rounded once.
            BigDecimal t = value.multiply(new BigDecimal(factor.numerator().multiply(offset.denominator())));
            BigDecimal b = new BigDecimal(offset.numerator().multiply(factor.denominator()));
            BigDecimal d = new BigDecimal(factor.denominator().multiply(offset.denominator()));
            int guard = mc.getPrecision() + d.precision() + 1;
            // Exact quotients take the scale of the sum aligned to scale max(value scale, 0); the zero-term and
            // negative-scale adjustments reproduce it without the alignment.
            BigDecimal n = b.signum() == 0 ? t
                    : t.signum() == 0 ? b.setScale(Math.max(0, Math.min(t.scale(), guard)))
                    : addFar(t, b, guard);
            BigDecimal q = n.divide(d, mc);
            return q.scale() >= 0 ? q : q.signum() == 0 ? BigDecimal.ZERO
                    : q.setScale(Math.min(0, q.scale() + mc.getPrecision() - q.precision()));
        }
        // Best effort for irrational coefficients: the product's relative error is independent of the
        // value's magnitude, so guard digits only cover cancellation against the offset.
        int guard = mc.getPrecision() + 10 + Math.max(0, Math.getExponent(offsetD) * 3 / 10 + 1);
        MathContext work = new MathContext(guard);
        return value.multiply(factor.decimalValue(work), work).add(offset.decimalValue(work), work).round(mc);
    }

    /**
     * {@code x + y} for the numerator of a quotient rounded to {@code guard - divisorDigits - 1} digits; a term
     * below 10^k, where k is under both the other term's last digit and the quotient's rounding grid (both
     * multiples of 10^k), becomes a same-signed 10^(k-1): it rounds identically and skips the scale alignment.
     */
    private static BigDecimal addFar(BigDecimal x, BigDecimal y, int guard) {
        long ex = (long) x.precision() - x.scale() - 1;
        long ey = (long) y.precision() - y.scale() - 1;
        BigDecimal big = ex >= ey ? x : y;
        BigDecimal small = ex >= ey ? y : x;
        long sticky = Math.min(Math.max(ex, ey) - guard - 1, -(long) big.scale()) - 1;
        if (Math.min(ex, ey) >= sticky) {
            return x.add(y);
        }
        return big.add(BigDecimal.valueOf(small.signum(), Math.toIntExact(-sticky)));
    }

    @Serial
    private Object writeReplace() {
        if (steps.size() == 1 && steps.get(0) == this) {
            return new Proxy(factor, offset, null, null);
        }
        Factor[] f = new Factor[steps.size()];
        Factor[] o = new Factor[steps.size()];
        for (int i = 0; i < f.length; i++) {
            UomConverter step = (UomConverter) steps.get(i);
            f[i] = step.factor();
            o[i] = step.offset();
        }
        return new Proxy(factor, offset, f, o);
    }

    @Serial
    private void readObject(ObjectInputStream in) throws InvalidObjectException {
        throw new InvalidObjectException("proxy required");
    }

    /** Serialized form of a converter, steps as parallel arrays (null for one step); package-private for tests. */
    static final class Proxy implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final Factor factor;
        private final Factor offset;
        private final Factor[] stepFactors;
        private final Factor[] stepOffsets;

        Proxy(Factor factor, Factor offset, Factor[] stepFactors, Factor[] stepOffsets) {
            this.factor = factor;
            this.offset = offset;
            this.stepFactors = stepFactors;
            this.stepOffsets = stepOffsets;
        }

        @Serial
        private Object readResolve() throws InvalidObjectException {
            try {
                Objects.requireNonNull(factor, "null factor");
                Objects.requireNonNull(offset, "null offset");
                if (stepFactors == null && stepOffsets == null) {
                    return Converters.affine(factor, offset);
                }
                if (stepFactors == null || stepOffsets == null || stepFactors.length != stepOffsets.length
                        || stepFactors.length == 0) {
                    throw new IllegalArgumentException("step factors and offsets do not match");
                }
                UomConverter c = null;
                for (int i = 0; i < stepFactors.length; i++) {
                    UomConverter step = Converters.affine(Objects.requireNonNull(stepFactors[i], "null step"),
                            Objects.requireNonNull(stepOffsets[i], "null step"));
                    c = c == null ? step : c.concatenate(step);
                }
                if (!c.factor().equals(factor) || !c.offset().equals(offset)) {
                    throw new IllegalArgumentException(
                            "steps do not compose to x -> " + factor + " * x + " + offset);
                }
                return c;
            } catch (RuntimeException e) {
                throw new InvalidObjectException("invalid converter: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UomConverter c && factor.equals(c.factor()) && offset.equals(c.offset());
    }

    @Override
    public int hashCode() {
        return Objects.hash(factor, offset);
    }

    @Override
    public String toString() {
        return "x -> " + factor + " * x + " + offset;
    }
}
