// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Objects;
import org.tinygears.uom.core.internal.Decimals;

/**
 * Exact number {@code sign(n) * (|n|/d * pi^k)^(1/r)}, kept in a normal form: gcd-reduced, {@code d > 0},
 * and {@code r} as small as possible (no prime p dividing r leaves the radicand a perfect p-th power).
 * Operations whose radicand numerator or denominator would exceed 65_536 bits throw {@link ArithmeticException}.
 * Serializable through a proxy that rebuilds with {@link #of(BigInteger, BigInteger, int, int)}; the serialized
 * form may change before 1.0.
 */
// ponytail: equality is structural on that normal form; coefficients fold into the radicand
// (2 * 2^(1/2) is stored as 8^(1/2)), so radicands grow. Split out square-free parts if size matters.
public final class Factor implements Serializable {

    private static final int MAX_EXPONENT = 1000;
    private static final int MAX_BITS = 65_536;
    static final int PI_CACHE_SIZE = 16;
    private static final BoundedCache<Integer, BigDecimal> PI_CACHE = new BoundedCache<>(PI_CACHE_SIZE);

    /** Zero. */
    public static final Factor ZERO = new Factor(BigInteger.ZERO, BigInteger.ONE, 0, 1);
    /** One. */
    public static final Factor ONE = new Factor(BigInteger.ONE, BigInteger.ONE, 0, 1);
    /** Pi. */
    public static final Factor PI = new Factor(BigInteger.ONE, BigInteger.ONE, 1, 1);

    /** Radicand numerator, with the sign. */
    private final BigInteger num;
    /** Radicand denominator, positive. */
    private final BigInteger den;
    /** Exponent of pi in the radicand. */
    private final int piExp;
    /** Root index. */
    private final int index;

    private Factor(BigInteger num, BigInteger den, int piExp, int index) {
        this.num = num;
        this.den = den;
        this.piExp = piExp;
        this.index = index;
    }

    /**
     * The fraction {@code n/d}; a zero {@code d} throws ArithmeticException.
     *
     * @param n the numerator
     * @param d the denominator
     * @return the factor
     */
    public static Factor of(long n, long d) {
        return of(BigInteger.valueOf(n), BigInteger.valueOf(d));
    }

    /**
     * The fraction {@code n/d}; a zero {@code d} throws ArithmeticException.
     *
     * @param n the numerator
     * @param d the denominator
     * @return the factor
     */
    public static Factor of(BigInteger n, BigInteger d) {
        return make(n, d, 0, 1);
    }

    /**
     * Builds {@code (n/d * pi^k)^(1/r)} from any parts, in normal form: the fraction is reduced to lowest terms with a
     * positive denominator, and the root index is reduced together with the exponent of pi by every prime that divides
     * both and for which numerator and denominator have exact roots ({@code of(4, 1, 0, 2)} equals {@code ofLong(2)},
     * {@code of(1, 1, 2, 2)} equals {@link #PI}). The sign of the numerator stays outside the root:
     * {@code of(-4, 1, 0, 2)} is {@code -2}. The limit on the exponent of {@link #pow} and {@link #root} does not apply
     * here.
     *
     * @param numerator the radicand numerator {@code n}, with the sign
     * @param denominator the radicand denominator {@code d}
     * @param piExponent the exponent {@code k} of pi
     * @param rootIndex the root index {@code r}, positive
     * @return the factor
     * @throws IllegalArgumentException if {@code rootIndex} is not positive
     * @throws ArithmeticException if {@code denominator} is zero, or the reduced numerator or denominator has more than
     *     65536 bits
     */
    public static Factor of(BigInteger numerator, BigInteger denominator, int piExponent, int rootIndex) {
        if (rootIndex <= 0) {
            throw new IllegalArgumentException("root index must be positive: " + rootIndex);
        }
        return make(numerator, denominator, piExponent, rootIndex);
    }

    /**
     * The integer {@code n}.
     *
     * @param n the value
     * @return the factor
     */
    public static Factor ofLong(long n) {
        return of(n, 1);
    }

    /**
     * The exact value of a decimal literal in the syntax of {@link BigDecimal#BigDecimal(String)}.
     *
     * @param literal the literal
     * @return the factor
     */
    public static Factor decimal(String literal) {
        BigDecimal value = new BigDecimal(literal);
        checkSize(Math.abs((long) value.scale()) * 3);
        return value.scale() >= 0
                ? of(value.unscaledValue(), BigInteger.TEN.pow(value.scale()))
                : of(value.unscaledValue().multiply(BigInteger.TEN.pow(-value.scale())), BigInteger.ONE);
    }

    private static Factor make(BigInteger n, BigInteger d, int k, int r) {
        if (d.signum() == 0) {
            throw new ArithmeticException("division by zero");
        }
        if (n.signum() == 0) {
            return ZERO;
        }
        if (d.signum() < 0) {
            n = n.negate();
            d = d.negate();
        }
        BigInteger g = n.gcd(d);
        n = n.divide(g);
        d = d.divide(g);
        if (n.bitLength() > MAX_BITS || d.bitLength() > MAX_BITS) {
            throw new ArithmeticException("factor too large");
        }
        // Trial division over r's prime factors in long arithmetic: O(sqrt(r)), no int overflow.
        long rest = r;
        for (long f = 2; f <= rest; f++) {
            if (f * f > rest) {
                f = rest;
            }
            if (rest % f != 0) {
                continue;
            }
            while (rest % f == 0) {
                rest /= f;
            }
            int p = (int) f;
            while (r % p == 0 && k % p == 0) {
                BigInteger a = exactRoot(n.abs(), p);
                BigInteger b = exactRoot(d, p);
                if (a == null || b == null) {
                    break;
                }
                n = n.signum() < 0 ? a.negate() : a;
                d = b;
                k /= p;
                r /= p;
            }
        }
        return new Factor(n, d, k, r);
    }

    /** Integer p-th root of x >= 0, or null if x is not a perfect p-th power. */
    private static BigInteger exactRoot(BigInteger x, int p) {
        if (x.compareTo(BigInteger.ONE) <= 0) {
            return x;
        }
        if (x.bitLength() <= p) {
            return null;
        }
        BigInteger y = BigInteger.ONE.shiftLeft(x.bitLength() / p + 1);
        BigInteger pm1 = BigInteger.valueOf(p - 1);
        BigInteger bp = BigInteger.valueOf(p);
        while (true) {
            BigInteger t = y.multiply(pm1).add(x.divide(y.pow(p - 1))).divide(bp);
            if (t.compareTo(y) >= 0) {
                break;
            }
            y = t;
        }
        return y.pow(p).equals(x) ? y : null;
    }

    /**
     * The product.
     *
     * @param other the other factor
     * @return this times {@code other}
     */
    public Factor multiply(Factor other) {
        if (isZero() || other.isZero()) {
            return ZERO;
        }
        int l = Math.multiplyExact(index / gcd(index, other.index), other.index);
        int a = l / index;
        int b = l / other.index;
        int sign = signum() * other.signum();
        checkSize((long) num.bitLength() * a + (long) other.num.bitLength() * b);
        checkSize((long) den.bitLength() * a + (long) other.den.bitLength() * b);
        BigInteger n = num.abs().pow(a).multiply(other.num.abs().pow(b));
        BigInteger d = den.pow(a).multiply(other.den.pow(b));
        int k = Math.addExact(Math.multiplyExact(piExp, a), Math.multiplyExact(other.piExp, b));
        return make(sign < 0 ? n.negate() : n, d, k, l);
    }

    /**
     * The quotient; a zero divisor throws ArithmeticException.
     *
     * @param other the divisor
     * @return this divided by {@code other}
     */
    public Factor divide(Factor other) {
        return multiply(other.inverse());
    }

    /**
     * The exact sum; terms with different roots or powers of pi that do not combine throw ArithmeticException.
     *
     * @param other the other term
     * @return this plus {@code other}
     */
    public Factor add(Factor other) {
        if (isZero()) {
            return other;
        }
        if (other.isZero()) {
            return this;
        }
        if (index != other.index || piExp != other.piExp) {
            throw new ArithmeticException("cannot add " + this + " and " + other + " exactly");
        }
        if (index == 1) {
            return make(num.multiply(other.den).add(other.num.multiply(den)), den.multiply(other.den), piExp, 1);
        }
        Factor magnitude = signum() < 0 ? negate() : this;
        if (!magnitude.equals(other.signum() < 0 ? other.negate() : other)) {
            throw new ArithmeticException("cannot add " + this + " and " + other + " exactly");
        }
        return ofLong(signum() + other.signum()).multiply(magnitude);
    }

    /**
     * The negation.
     *
     * @return {@code -this}
     */
    public Factor negate() {
        return new Factor(num.negate(), den, piExp, index);
    }

    /**
     * The {@code n}-th power.
     *
     * @param n the exponent
     * @return this to the power {@code n}
     * @throws ArithmeticException if {@code |n|} exceeds 1000, if the result is too large, or if this is zero and
     *     {@code n} is negative
     */
    public Factor pow(int n) {
        checkExponent(n);
        if (n == 0) {
            return ONE;
        }
        Factor base = n < 0 ? inverse() : this;
        int m = Math.abs(n);
        checkSize((long) Math.max(base.num.bitLength(), base.den.bitLength()) * m);
        return make(base.num.pow(m), base.den.pow(m), Math.multiplyExact(base.piExp, m), base.index);
    }

    /**
     * The {@code n}-th root, for a negative {@code n} the root of the inverse.
     *
     * @param n the root index
     * @return the root
     * @throws ArithmeticException if {@code n} is zero, if {@code |n|} exceeds 1000, if {@code n} is even and this is
     *     negative, if this is zero and {@code n} is negative, or if the result is too large
     */
    public Factor root(int n) {
        checkExponent(n);
        if (n == 0) {
            throw new ArithmeticException("zeroth root");
        }
        Factor base = n < 0 ? inverse() : this;
        if (base.isZero()) {
            return ZERO;
        }
        if (base.signum() < 0 && n % 2 == 0) {
            throw new ArithmeticException("even root of a negative number");
        }
        return make(base.num, base.den, base.piExp, Math.multiplyExact(base.index, Math.abs(n)));
    }

    private static void checkExponent(int n) {
        if (n > MAX_EXPONENT || n < -MAX_EXPONENT) {
            throw new ArithmeticException("exponent " + n + " exceeds " + MAX_EXPONENT);
        }
    }

    /** Rejects an operand estimate before computing it; {@link #make} checks the reduced result exactly. */
    private static void checkSize(long bits) {
        if (bits > 2L * MAX_BITS) {
            throw new ArithmeticException("factor too large");
        }
    }

    private Factor inverse() {
        if (isZero()) {
            throw new ArithmeticException("division by zero");
        }
        return make(signum() < 0 ? den.negate() : den, num.abs(), -piExp, index);
    }

    private static int gcd(int a, int b) {
        return BigInteger.valueOf(a).gcd(BigInteger.valueOf(b)).intValue();
    }

    /**
     * Radicand numerator {@code n}; it carries the sign, which applies outside the root.
     *
     * @return the numerator
     */
    public BigInteger numerator() {
        return num;
    }

    /**
     * Radicand denominator {@code d}, always positive.
     *
     * @return the denominator
     */
    public BigInteger denominator() {
        return den;
    }

    /**
     * Exponent {@code k} of pi inside the radicand.
     *
     * @return the exponent
     */
    public int piExponent() {
        return piExp;
    }

    /**
     * Root index {@code r}, 1 for a rational multiple of a power of pi.
     *
     * @return the root index
     */
    public int rootIndex() {
        return index;
    }

    /**
     * Whether this is a rational number.
     *
     * @return true if the root index is 1 and the pi exponent 0
     */
    public boolean isExact() {
        return index == 1 && piExp == 0;
    }

    /**
     * Whether this is zero.
     *
     * @return true for zero
     */
    public boolean isZero() {
        return num.signum() == 0;
    }

    /**
     * Whether this is one.
     *
     * @return true for one
     */
    public boolean isOne() {
        return equals(ONE);
    }

    /**
     * The sign.
     *
     * @return -1, 0 or 1
     */
    public int signum() {
        return num.signum();
    }

    /**
     * The nearest {@code double}.
     *
     * @return the value correctly rounded
     */
    public double doubleValue() {
        if (isExact()) {
            // Quotient of >= 56 bits with a sticky low bit: never a binary midpoint, so one exact rounding.
            BigInteger n = num.abs();
            int shift = Math.max(0, 56 + den.bitLength() - n.bitLength());
            BigInteger[] qr = n.shiftLeft(shift).divideAndRemainder(den);
            BigInteger q = qr[1].signum() == 0 ? qr[0] : qr[0].setBit(0);
            double d = Decimals.divide(new BigDecimal(q), new BigDecimal(BigInteger.ONE.shiftLeft(shift)),
                    MathContext.UNLIMITED).doubleValue();
            return signum() < 0 ? -d : d;
        }
        // An irrational value is never a midpoint: widen until a +-2 ulp bracket rounds to one double.
        for (int p = 25; ; p *= 2) {
            BigDecimal x = decimalValue(new MathContext(p));
            BigDecimal err = x.ulp().multiply(BigDecimal.valueOf(2));
            double lo = x.subtract(err).doubleValue();
            if (lo == x.add(err).doubleValue()) {
                return lo;
            }
        }
    }

    /**
     * The value rounded to {@code mc}; an irrational value with precision 0, or a non-terminating rational one, throws ArithmeticException.
     *
     * @param mc the rounding
     * @return the decimal value
     */
    public BigDecimal decimalValue(MathContext mc) {
        if (isExact()) {
            return Decimals.divide(new BigDecimal(num), new BigDecimal(den), mc);
        }
        if (mc.getPrecision() == 0) {
            throw new ArithmeticException("irrational value needs a finite precision");
        }
        MathContext work = new MathContext(mc.getPrecision() + 5);
        BigDecimal x = Decimals.divide(new BigDecimal(num.abs()), new BigDecimal(den), work);
        if (piExp != 0) {
            x = x.multiply(pi(mc.getPrecision() + 10).pow(piExp, work), work);
        }
        BigDecimal result = nthRoot(x, index, work);
        return (signum() < 0 ? result.negate() : result).round(mc);
    }

    /** Pi to at least {@code digits} digits; precisions round up to multiples of 64 and the cache holds 16 of them. */
    private static BigDecimal pi(int digits) {
        return PI_CACHE.get((digits + 63) / 64 * 64, p -> {
            // Chudnovsky: pi = 426880 sqrt(10005) Q / T, about 14.18 digits per term; binary splitting keeps it fast.
            MathContext mc = new MathContext(p + 10);
            BigInteger[] pqt = chudnovsky(0, p / 14 + 2);
            // The truncated root rounded HALF_UP is the correctly rounded root (irrational: never a tie), and
            // BigInteger.sqrt is twice as fast as BigDecimal.sqrt on JDK 21.
            BigDecimal root = new BigDecimal(BigInteger.valueOf(10_005).multiply(BigInteger.TEN.pow(2 * (p + 10)))
                    .sqrt(), p + 10).round(mc);
            BigDecimal pi = new BigDecimal(pqt[1].multiply(BigInteger.valueOf(426_880)))
                    .multiply(root, mc).divide(new BigDecimal(pqt[2]), mc);
            return pi.round(new MathContext(p));
        });
    }

    static int piCacheSize() {
        return PI_CACHE.size();
    }

    static boolean piCacheHolds(int bucket) {
        return PI_CACHE.contains(bucket);
    }

    static void clearPiCache() {
        PI_CACHE.clear();
    }

    private static final BigInteger CHUDNOVSKY_C3_24 = BigInteger.valueOf(640_320).pow(3).divide(BigInteger.valueOf(24));

    /** P, Q and T of the Chudnovsky series over terms {@code [a, b)}. */
    private static BigInteger[] chudnovsky(long a, long b) {
        if (b - a == 1) {
            if (a == 0) {
                return new BigInteger[] {BigInteger.ONE, BigInteger.ONE, BigInteger.valueOf(13_591_409)};
            }
            BigInteger p = BigInteger.valueOf(6 * a - 5).multiply(BigInteger.valueOf(2 * a - 1))
                    .multiply(BigInteger.valueOf(6 * a - 1));
            BigInteger q = BigInteger.valueOf(a).pow(3).multiply(CHUDNOVSKY_C3_24);
            BigInteger t = p.multiply(BigInteger.valueOf(13_591_409 + 545_140_134 * a));
            return new BigInteger[] {p, q, a % 2 == 0 ? t : t.negate()};
        }
        long m = (a + b) / 2;
        BigInteger[] l = chudnovsky(a, m);
        BigInteger[] r = chudnovsky(m, b);
        return new BigInteger[] {l[0].multiply(r[0]), l[1].multiply(r[1]),
                l[2].multiply(r[1]).add(l[0].multiply(r[2]))};
    }

    /** r-th root of a > 0 by Newton iteration from a double-precision guess. */
    private static BigDecimal nthRoot(BigDecimal a, int r, MathContext mc) {
        if (r == 1) {
            return a;
        }
        int e = a.precision() - a.scale();
        double lg = (e + Math.log10(a.scaleByPowerOfTen(-e).doubleValue())) / r;
        int ie = (int) Math.floor(lg);
        BigDecimal y = BigDecimal.valueOf(Math.pow(10, lg - ie)).scaleByPowerOfTen(ie);
        BigDecimal rm1 = BigDecimal.valueOf(r - 1);
        BigDecimal br = BigDecimal.valueOf(r);
        for (int i = 0; i < 100; i++) {
            BigDecimal next = y.multiply(rm1).add(a.divide(y.pow(r - 1, mc), mc)).divide(br, mc);
            boolean done = next.subtract(y).abs().compareTo(next.ulp()) <= 0;
            y = next;
            if (done) {
                break;
            }
        }
        return y;
    }

    /**
     * The serialization proxy.
     *
     * @return the proxy
     */
    @Serial
    private Object writeReplace() {
        return new Proxy(num, den, piExp, index);
    }

    /**
     * Rejects a stream without the proxy.
     *
     * @param in the stream
     * @throws InvalidObjectException always
     */
    @Serial
    private void readObject(ObjectInputStream in) throws InvalidObjectException {
        throw new InvalidObjectException("proxy required");
    }

    /** Serialized form of a factor; package-private so tests can forge streams. */
    static final class Proxy implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private transient BigInteger numerator;
        private transient BigInteger denominator;
        private transient int piExponent;
        private transient int rootIndex;

        Proxy(BigInteger numerator, BigInteger denominator, int piExponent, int rootIndex) {
            this.numerator = numerator;
            this.denominator = denominator;
            this.piExponent = piExponent;
            this.rootIndex = rootIndex;
        }

        @Serial
        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            SerialForm.writeBytes(out, numerator == null ? null : numerator.toByteArray());
            SerialForm.writeBytes(out, denominator == null ? null : denominator.toByteArray());
            out.writeInt(piExponent);
            out.writeInt(rootIndex);
        }

        @Serial
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            numerator = readNumber(in);
            denominator = readNumber(in);
            piExponent = in.readInt();
            rootIndex = in.readInt();
        }

        private static BigInteger readNumber(ObjectInputStream in) throws IOException {
            byte[] b = SerialForm.readBytes(in, MAX_BITS / 8 + 1, "invalid factor: factor too large");
            if (b != null && b.length == 0) {
                throw new InvalidObjectException("invalid factor: empty number");
            }
            return b == null ? null : new BigInteger(b);
        }

        @Serial
        private Object readResolve() throws InvalidObjectException {
            try {
                Objects.requireNonNull(numerator, "null numerator");
                Objects.requireNonNull(denominator, "null denominator");
                // A normalised factor never exceeds the limit; checking first keeps gcd off forged huge values.
                if (numerator.bitLength() > MAX_BITS || denominator.bitLength() > MAX_BITS) {
                    throw new ArithmeticException("factor too large");
                }
                return of(numerator, denominator, piExponent, rootIndex);
            } catch (RuntimeException e) {
                throw (InvalidObjectException) new InvalidObjectException("invalid factor: " + e.getMessage()).initCause(e);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Factor f
                && num.equals(f.num) && den.equals(f.den) && piExp == f.piExp && index == f.index;
    }

    @Override
    public int hashCode() {
        return Objects.hash(num, den, piExp, index);
    }

    @Override
    public String toString() {
        String s = den.equals(BigInteger.ONE) ? num.toString() : num + "/" + den;
        if (piExp != 0) {
            s += "*pi^" + piExp;
        }
        return index == 1 ? s : "(" + s + ")^(1/" + index + ")";
    }
}
