package org.tinygears.uom.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.measure.IncommensurableException;
import javax.measure.Prefix;
import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.UnconvertibleException;
import javax.measure.UnitConverter;
import javax.measure.spi.ServiceProvider;

/** A product of atoms with integer exponents, an affine converter to the system unit, and labels. */
public final class UomUnit<Q extends Quantity<Q>> implements Unit<Q> {

    private final Map<UnitAtom, Integer> atoms;
    private final UomConverter toSystem;
    private final String symbol;
    private final String name;
    private final Map<UomUnit<?>, UomConverter> converterCache = new ConcurrentHashMap<>();

    private UomUnit(Map<UnitAtom, Integer> atoms, UomConverter toSystem, String symbol, String name) {
        this.atoms = atoms;
        this.toSystem = toSystem;
        this.symbol = symbol;
        this.name = name;
    }

    public static <Q extends Quantity<Q>> UomUnit<Q> of(
            Map<UnitAtom, Integer> atoms, UomConverter toSystem, String symbol, String name) {
        Objects.requireNonNull(toSystem, "toSystem");
        Map<UnitAtom, Integer> normalized = new LinkedHashMap<>();
        atoms.entrySet().stream()
                .filter(e -> Objects.requireNonNull(e.getValue(), "exponent") != 0)
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(UnitAtom::symbol)))
                .forEach(e -> normalized.put(Objects.requireNonNull(e.getKey(), "atom"), e.getValue()));
        return new UomUnit<>(Collections.unmodifiableMap(normalized), toSystem, symbol, name);
    }

    public UomUnit<Q> named(String symbol, String name) {
        return new UomUnit<>(atoms, toSystem, symbol, name);
    }

    public Map<UnitAtom, Integer> atoms() {
        return atoms;
    }

    public UomConverter toSystemConverter() {
        return toSystem;
    }

    @Override
    public String getSymbol() {
        return symbol;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public UomDimension getDimension() {
        UomDimension d = UomDimension.NONE;
        for (Map.Entry<UnitAtom, Integer> e : atoms.entrySet()) {
            d = d.multiply(e.getKey().dimension().pow(e.getValue()));
        }
        return d;
    }

    @Override
    public UomUnit<Q> getSystemUnit() {
        return new UomUnit<>(atoms, Converters.IDENTITY, systemLabel(), null);
    }

    private String systemLabel() {
        String num = join(true);
        String den = join(false);
        if (den.isEmpty()) {
            return num.isEmpty() ? "1" : num;
        }
        boolean several = atoms.values().stream().filter(n -> n < 0).count() > 1;
        return (num.isEmpty() ? "1" : num) + "/" + (several ? "(" + den + ")" : den);
    }

    private String join(boolean positive) {
        return atoms.entrySet().stream()
                .filter(e -> e.getValue() > 0 == positive)
                .map(e -> e.getKey().symbol() + (Math.abs(e.getValue()) == 1 ? "" : "^" + Math.abs(e.getValue())))
                .collect(Collectors.joining("·"));
    }

    /** Null for a base unit: ONE or a single atom with exponent 1, both with the identity converter. */
    @Override
    public Map<UomUnit<?>, Integer> getBaseUnits() {
        boolean simple = atoms.isEmpty() || atoms.size() == 1 && atoms.containsValue(1);
        if (simple && toSystem.equals(Converters.IDENTITY)) {
            return null;
        }
        Map<UomUnit<?>, Integer> m = new LinkedHashMap<>();
        atoms.forEach((atom, n) -> m.put(
                new UomUnit<>(Map.of(atom, 1), Converters.IDENTITY, atom.symbol(), null), n));
        return Collections.unmodifiableMap(m);
    }

    @Override
    public boolean isCompatible(Unit<?> that) {
        return that.getDimension().equals(getDimension());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UomUnit<?> u && atoms.equals(u.atoms) && toSystem.equals(u.toSystem);
    }

    @Override
    public int hashCode() {
        return Objects.hash(atoms, toSystem);
    }

    @Override
    public String toString() {
        return symbol != null ? symbol : systemLabel();
    }

    @Override
    public boolean isEquivalentTo(Unit<Q> that) {
        return that instanceof UomUnit<?> u
                && atoms.equals(u.atoms)
                && isCompatible(u)
                && Converters.IDENTITY.equals(converterTo(u));
    }

    @Override
    public UomConverter getConverterTo(Unit<Q> that) {
        UomUnit<?> target = uomUnit(that);
        if (!isCompatible(target)) {
            throw new UnconvertibleException("incompatible dimensions: " + this + " and " + target);
        }
        return converterTo(target);
    }

    @Override
    public UomConverter getConverterToAny(Unit<?> that) throws IncommensurableException {
        UomUnit<?> target = uomUnit(that);
        if (!isCompatible(target)) {
            throw new IncommensurableException(this + " is not compatible with " + target);
        }
        return converterTo(target);
    }

    private static UomUnit<?> uomUnit(Unit<?> that) {
        if (that instanceof UomUnit<?> u) {
            return u;
        }
        throw new UnconvertibleException("foreign unit implementation: " + that.getClass().getName());
    }

    /** Caller has checked compatibility, so only valid targets are cached. */
    private UomConverter converterTo(UomUnit<?> that) {
        return converterCache.computeIfAbsent(that, t -> t.toSystem.inverse().concatenate(toSystem));
    }

    @Override
    public <T extends Quantity<T>> Unit<T> asType(Class<T> type) {
        ServiceProvider provider;
        try {
            provider = ServiceProvider.current();
        } catch (IllegalStateException e) {
            throw new IllegalStateException("no javax.measure ServiceProvider available", e);
        }
        if (!getSystemUnit().equals(provider.getQuantityFactory(type).getSystemUnit())) {
            throw new ClassCastException(this + " is not a unit of " + type.getName());
        }
        @SuppressWarnings("unchecked") // the system units match, so this unit measures T
        Unit<T> typed = (Unit<T>) this;
        return typed;
    }

    @Override
    public UomUnit<Q> alternate(String symbol) {
        Objects.requireNonNull(symbol, "symbol");
        if (!toSystem.equals(Converters.IDENTITY)) {
            throw new IllegalArgumentException("not an unscaled standard unit: " + this);
        }
        return new UomUnit<>(Map.of(AlternateUnits.register(symbol, this), 1), Converters.IDENTITY, symbol, null);
    }

    @Override
    public UomUnit<Q> shift(Number offset) {
        return transform(Converters.shift(toFactor(offset)));
    }

    @Override
    public UomUnit<Q> shift(double offset) {
        return shift((Number) offset);
    }

    @Override
    public UomUnit<Q> multiply(Number multiplier) {
        return transform(Converters.scale(toFactor(multiplier)));
    }

    @Override
    public UomUnit<Q> multiply(double multiplier) {
        return multiply((Number) multiplier);
    }

    public UomUnit<?> scaledBy(Factor factor) {
        return transform(Converters.scale(factor));
    }

    @Override
    public UomUnit<Q> divide(Number divisor) {
        return transform(Converters.scale(Factor.ONE.divide(toFactor(divisor))));
    }

    @Override
    public UomUnit<Q> divide(double divisor) {
        return divide((Number) divisor);
    }

    /** {@code operation} converts from the new unit to this one. */
    @Override
    public UomUnit<Q> transform(UnitConverter operation) {
        UomConverter c = toSystem.concatenate(operation);
        if (c.factor().isZero()) {
            throw new ArithmeticException("zero scale factor: " + this);
        }
        return new UomUnit<>(atoms, c, null, null);
    }

    @Override
    public UomUnit<Q> prefix(Prefix prefix) {
        Factor f = Factor.decimal(prefix.getValue().toString()).pow(prefix.getExponent());
        UomUnit<Q> u = transform(Converters.scale(f));
        // A prefix on a compound symbol would bind to its first atom only.
        return symbol == null || !symbol.equals(group(symbol, "·/^")) ? u : u.named(prefix.getSymbol() + symbol, null);
    }

    @Override
    public UomUnit<?> multiply(Unit<?> multiplier) {
        return combine(multiplier, false);
    }

    @Override
    public UomUnit<?> divide(Unit<?> divisor) {
        return combine(divisor, true);
    }

    private UomUnit<?> combine(Unit<?> other, boolean quotient) {
        UomUnit<?> that = requireLinear(uomUnit(other));
        requireLinear(this);
        Map<UnitAtom, Integer> m = new LinkedHashMap<>(atoms);
        that.atoms.forEach((a, n) -> {
            int e = m.getOrDefault(a, 0);
            m.put(a, quotient ? Math.subtractExact(e, n) : Math.addExact(e, n));
        });
        Factor f = quotient
                ? toSystem.factor().divide(that.toSystem.factor())
                : toSystem.factor().multiply(that.toSystem.factor());
        String s = symbol == null || that.symbol == null ? null
                : group(symbol, "/") + (quotient ? "/" + group(that.symbol, "·/") : "·" + group(that.symbol, "/"));
        return of(m, Converters.scale(f), s, null);
    }

    @Override
    public UomUnit<?> inverse() {
        return pow(-1);
    }

    @Override
    public UomUnit<?> pow(int n) {
        if (n == 0) {
            return BaseUnits.ONE;
        }
        if (n == 1) {
            return this;
        }
        requireLinear(this);
        Factor f = toSystem.factor().pow(n);
        Map<UnitAtom, Integer> m = new LinkedHashMap<>();
        atoms.forEach((a, e) -> m.put(a, Math.multiplyExact(e, n)));
        return of(m, Converters.scale(f), symbol == null ? null : group(symbol, "·/^") + "^" + n, null);
    }

    @Override
    public UomUnit<?> root(int n) {
        if (n == 0) {
            throw new ArithmeticException("zeroth root");
        }
        if (n == 1) {
            return this;
        }
        requireLinear(this);
        Map<UnitAtom, Integer> m = new LinkedHashMap<>();
        atoms.forEach((a, e) -> {
            if (e % n != 0) {
                throw new ArithmeticException("exponent " + e + " of " + a.symbol() + " not divisible by " + n);
            }
            m.put(a, Math.divideExact(e, n));
        });
        return of(m, Converters.scale(toSystem.factor().root(n)), null, null);
    }

    /** Parenthesizes {@code s} if it contains any of {@code operators}. */
    private static String group(String s, String operators) {
        return s.chars().anyMatch(c -> operators.indexOf(c) >= 0) ? "(" + s + ")" : s;
    }

    private static UomUnit<?> requireLinear(UomUnit<?> u) {
        if (!u.toSystem.isLinear()) {
            throw new UnconvertibleException("unit with an offset cannot be combined: " + u);
        }
        return u;
    }

    private static Factor toFactor(Number n) {
        if (n instanceof Byte || n instanceof Short || n instanceof Integer || n instanceof Long
                || n instanceof AtomicInteger || n instanceof AtomicLong) {
            return Factor.ofLong(n.longValue());
        }
        if (n instanceof BigInteger b) {
            return Factor.of(b, BigInteger.ONE);
        }
        if (n instanceof BigDecimal b) {
            return Factor.decimal(b.toPlainString());
        }
        double d = n.doubleValue();
        if (!Double.isFinite(d)) {
            throw new IllegalArgumentException("not a finite number: " + n);
        }
        return Factor.decimal(BigDecimal.valueOf(d).toPlainString());
    }
}
