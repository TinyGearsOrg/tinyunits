package org.tinygears.uom.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exact number {@code sign(n) * (|n|/d * pi^k)^(1/r)}, kept in a normal form: gcd-reduced, {@code d > 0},
 * and {@code r} as small as possible (no prime p dividing r leaves the radicand a perfect p-th power).
 */
// ponytail: equality is structural on that normal form; coefficients fold into the radicand
// (2 * 2^(1/2) is stored as 8^(1/2)), so radicands grow. Split out square-free parts if size matters.
public final class Factor {

    private static final int MAX_EXPONENT = 1000;
    private static final Map<Integer, BigDecimal> PI_CACHE = new ConcurrentHashMap<>();

    public static final Factor ZERO = new Factor(BigInteger.ZERO, BigInteger.ONE, 0, 1);
    public static final Factor ONE = new Factor(BigInteger.ONE, BigInteger.ONE, 0, 1);
    public static final Factor PI = new Factor(BigInteger.ONE, BigInteger.ONE, 1, 1);

    private final BigInteger num;
    private final BigInteger den;
    private final int piExp;
    private final int index;

    private Factor(BigInteger num, BigInteger den, int piExp, int index) {
        this.num = num;
        this.den = den;
        this.piExp = piExp;
        this.index = index;
    }

    public static Factor of(long n, long d) {
        return of(BigInteger.valueOf(n), BigInteger.valueOf(d));
    }

    public static Factor of(BigInteger n, BigInteger d) {
        return make(n, d, 0, 1);
    }

    public static Factor ofLong(long n) {
        return of(n, 1);
    }

    public static Factor decimal(String literal) {
        BigDecimal value = new BigDecimal(literal);
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
        for (int p = 2; p <= r; p++) {
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

    public Factor multiply(Factor other) {
        if (isZero() || other.isZero()) {
            return ZERO;
        }
        int l = Math.multiplyExact(index / gcd(index, other.index), other.index);
        int a = l / index;
        int b = l / other.index;
        int sign = signum() * other.signum();
        BigInteger n = num.abs().pow(a).multiply(other.num.abs().pow(b));
        BigInteger d = den.pow(a).multiply(other.den.pow(b));
        int k = Math.addExact(Math.multiplyExact(piExp, a), Math.multiplyExact(other.piExp, b));
        return make(sign < 0 ? n.negate() : n, d, k, l);
    }

    public Factor divide(Factor other) {
        return multiply(other.inverse());
    }

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

    public Factor negate() {
        return new Factor(num.negate(), den, piExp, index);
    }

    public Factor pow(int n) {
        checkExponent(n);
        if (n == 0) {
            return ONE;
        }
        Factor base = n < 0 ? inverse() : this;
        int m = Math.abs(n);
        return make(base.num.pow(m), base.den.pow(m), Math.multiplyExact(base.piExp, m), base.index);
    }

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

    private Factor inverse() {
        if (isZero()) {
            throw new ArithmeticException("division by zero");
        }
        return make(signum() < 0 ? den.negate() : den, num.abs(), -piExp, index);
    }

    private static int gcd(int a, int b) {
        return BigInteger.valueOf(a).gcd(BigInteger.valueOf(b)).intValue();
    }

    BigInteger numerator() {
        return num;
    }

    BigInteger denominator() {
        return den;
    }

    public boolean isExact() {
        return index == 1 && piExp == 0;
    }

    public boolean isZero() {
        return num.signum() == 0;
    }

    public boolean isOne() {
        return equals(ONE);
    }

    public int signum() {
        return num.signum();
    }

    public double doubleValue() {
        if (isExact()) {
            // Quotient of >= 56 bits with a sticky low bit: never a binary midpoint, so one exact rounding.
            BigInteger n = num.abs();
            int shift = Math.max(0, 56 + den.bitLength() - n.bitLength());
            BigInteger[] qr = n.shiftLeft(shift).divideAndRemainder(den);
            BigInteger q = qr[1].signum() == 0 ? qr[0] : qr[0].setBit(0);
            double d = new BigDecimal(q).divide(new BigDecimal(BigInteger.ONE.shiftLeft(shift))).doubleValue();
            return signum() < 0 ? -d : d;
        }
        // An irrational value is never a midpoint: widen until a +-2 ulp bracket rounds to one double.
        for (int p = 25; ; p *= 2) {
            BigDecimal x = decimalValue(new MathContext(p));
            BigDecimal err = x.ulp().multiply(BigDecimal.TWO);
            double lo = x.subtract(err).doubleValue();
            if (lo == x.add(err).doubleValue()) {
                return lo;
            }
        }
    }

    public BigDecimal decimalValue(MathContext mc) {
        if (isExact()) {
            return new BigDecimal(num).divide(new BigDecimal(den), mc);
        }
        if (mc.getPrecision() == 0) {
            throw new ArithmeticException("irrational value needs a finite precision");
        }
        MathContext work = new MathContext(mc.getPrecision() + 5);
        BigDecimal x = new BigDecimal(num.abs()).divide(new BigDecimal(den), work);
        if (piExp != 0) {
            x = x.multiply(pi(mc.getPrecision() + 10).pow(piExp, work), work);
        }
        BigDecimal result = nthRoot(x, index, work);
        return (signum() < 0 ? result.negate() : result).round(mc);
    }

    private static BigDecimal pi(int digits) {
        return PI_CACHE.computeIfAbsent(digits, p -> {
            MathContext mc = new MathContext(p + 5);
            BigDecimal machin = atanInverse(5, mc).multiply(BigDecimal.valueOf(16))
                    .subtract(atanInverse(239, mc).multiply(BigDecimal.valueOf(4)));
            return machin.round(new MathContext(p));
        });
    }

    /** atan(1/x) by its Taylor series. */
    private static BigDecimal atanInverse(int x, MathContext mc) {
        BigDecimal x2 = BigDecimal.valueOf((long) x * x);
        BigDecimal eps = BigDecimal.ONE.movePointLeft(mc.getPrecision());
        BigDecimal power = BigDecimal.ONE.divide(BigDecimal.valueOf(x), mc);
        BigDecimal sum = power;
        for (int k = 1; ; k++) {
            power = power.divide(x2, mc);
            BigDecimal term = power.divide(BigDecimal.valueOf(2L * k + 1), mc);
            if (term.compareTo(eps) < 0) {
                return sum;
            }
            sum = k % 2 == 0 ? sum.add(term, mc) : sum.subtract(term, mc);
        }
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
