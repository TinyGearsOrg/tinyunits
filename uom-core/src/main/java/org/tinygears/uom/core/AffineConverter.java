// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.measure.UnitConverter;

/**
 * Serializable through a proxy: one step is rebuilt with {@link Converters}; several are validated and rebuilt
 * through the package-private constructor, keeping the stored factor, offset and step order.
 */
final class AffineConverter implements UomConverter, Serializable {

    private final Factor factor;
    private final Factor offset;
    private final double factorD;
    private final double offsetD;
    private final List<UnitConverter> steps;

    AffineConverter(Factor factor, Factor offset) {
        this(factor, offset, null);
    }

    AffineConverter(Factor factor, Factor offset, List<UnitConverter> steps) {
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

        private static final String TOO_MANY_STEPS = "invalid converter: too many conversion steps";
        private static final MathContext FOLD = new MathContext(60);
        private static final BigDecimal TOLERANCE = new BigDecimal("1e-40");
        private static final int MAX_DECIMAL_BITS = 4096;

        private transient Factor factor;
        private transient Factor offset;
        private transient Factor[] stepFactors;
        private transient Factor[] stepOffsets;

        Proxy(Factor factor, Factor offset, Factor[] stepFactors, Factor[] stepOffsets) {
            this.factor = factor;
            this.offset = offset;
            this.stepFactors = stepFactors;
            this.stepOffsets = stepOffsets;
        }

        @Serial
        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            out.writeUnshared(factor);
            out.writeUnshared(offset);
            SerialForm.writeObjects(out, stepFactors, TOO_MANY_STEPS);
            SerialForm.writeObjects(out, stepOffsets, TOO_MANY_STEPS);
        }

        @Serial
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            factor = SerialForm.read(in, Factor.class);
            offset = SerialForm.read(in, Factor.class);
            stepFactors = SerialForm.readObjects(in, Factor.class, Factor[]::new, TOO_MANY_STEPS);
            stepOffsets = SerialForm.readObjects(in, Factor.class, Factor[]::new, TOO_MANY_STEPS);
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
                List<UnitConverter> steps = new ArrayList<>(stepFactors.length);
                Factor f = null;
                Factor o = null;
                boolean folded = true;
                for (int i = 0; i < stepFactors.length; i++) {
                    Factor sf = Objects.requireNonNull(stepFactors[i], "null step");
                    Factor so = Objects.requireNonNull(stepOffsets[i], "null step");
                    steps.add(Converters.affine(sf, so));
                    if (!folded) {
                        continue;
                    }
                    try {
                        o = f == null ? so : f.multiply(so).add(o);
                        f = f == null ? sf : f.multiply(sf);
                    } catch (ArithmeticException inexact) {
                        folded = false;
                    }
                }
                // Steps grouped otherwise may not fold exactly left to right; a decimal fold checks them then.
                if (folded ? !f.equals(factor) || !o.equals(offset) : !decimalFoldAgrees()) {
                    throw new IllegalArgumentException(
                            "steps do not compose to x -> " + factor + " * x + " + offset);
                }
                // Validated above; the package-private constructor keeps the stored values and the step list.
                return new AffineConverter(factor, offset, List.copyOf(steps));
            } catch (RuntimeException e) {
                throw new InvalidObjectException("invalid converter: " + e.getMessage(), e);
            }
        }

        /**
         * Left fold of the steps to 60 digits against the stored values, within 1e-40 relative to the largest
         * term (1e-40 absolute if all are zero). Parts over 4096 bits or values without a decimal skip the check.
         */
        private boolean decimalFoldAgrees() {
            List<Factor> all = new ArrayList<>(List.of(factor, offset));
            all.addAll(List.of(stepFactors));
            all.addAll(List.of(stepOffsets));
            if (all.stream().anyMatch(x -> x.numerator().bitLength() > MAX_DECIMAL_BITS
                    || x.denominator().bitLength() > MAX_DECIMAL_BITS)) {
                return true;
            }
            try {
                BigDecimal f = stepFactors[0].decimalValue(FOLD);
                BigDecimal o = stepOffsets[0].decimalValue(FOLD);
                BigDecimal terms = o.abs();
                for (int i = 1; i < stepFactors.length; i++) {
                    BigDecimal t = f.multiply(stepOffsets[i].decimalValue(FOLD), FOLD);
                    o = o.add(t, FOLD);
                    terms = terms.add(t.abs(), FOLD);
                    f = f.multiply(stepFactors[i].decimalValue(FOLD), FOLD);
                }
                return close(f, factor.decimalValue(FOLD), f.abs()) && close(o, offset.decimalValue(FOLD), terms);
            } catch (ArithmeticException e) {
                return true;
            }
        }

        private static boolean close(BigDecimal a, BigDecimal b, BigDecimal scale) {
            BigDecimal bound = scale.max(a.abs()).max(b.abs()).multiply(TOLERANCE);
            return a.subtract(b).abs().compareTo(bound.signum() == 0 ? TOLERANCE : bound) <= 0;
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
