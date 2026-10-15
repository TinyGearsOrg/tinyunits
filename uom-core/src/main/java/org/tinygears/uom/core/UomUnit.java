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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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
import javax.measure.spi.QuantityFactory;
import org.tinygears.uom.core.spi.Providers;

/**
 * A product of atoms with integer exponents, an affine converter to the system unit, and labels. Serializable
 * through a proxy (atoms, converter, symbol and name); the serialized form may change before 1.0.
 *
 * @param <Q> the quantity type
 */
public final class UomUnit<Q extends Quantity<Q>> implements Unit<Q>, Serializable {

    /** Atoms and their exponents, sorted by symbol. */
    private final Map<UnitAtom, Integer> atoms;
    /** Converter to the system unit. */
    private final UomConverter toSystem;
    /** Symbol, or null. */
    private final String symbol;
    /** Name, or null. */
    private final String name;
    /** Dimension of the atoms. */
    private final UomDimension dimension;
    private final transient Map<UomUnit<?>, UomConverter> converterCache = new ConcurrentHashMap<>();

    private UomUnit(Map<UnitAtom, Integer> atoms, UomConverter toSystem, String symbol, String name) {
        this.atoms = atoms;
        this.toSystem = toSystem;
        this.symbol = symbol;
        this.name = name;
        UomDimension d = UomDimension.NONE;
        for (Map.Entry<UnitAtom, Integer> e : atoms.entrySet()) {
            d = d.multiply(e.getKey().dimension().pow(e.getValue()));
        }
        this.dimension = d;
    }

    /**
     * Caller guarantees the unit belongs to Q; for generated code.
     *
     * @param <Q> the quantity type
     * @param unit the unit
     * @return the same unit
     */
    @SuppressWarnings("unchecked")
    public static <Q extends Quantity<Q>> UomUnit<Q> unchecked(UomUnit<?> unit) {
        return (UomUnit<Q>) Objects.requireNonNull(unit, "unit");
    }

    /**
     * A unit of atoms with non-zero exponents (zero exponents are dropped).
     *
     * @param <Q> the quantity type
     * @param atoms the atoms and their exponents
     * @param toSystem the converter to the system unit
     * @param symbol the symbol, or null
     * @param name the name, or null
     * @return the unit
     */
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

    /**
     * This unit with other labels.
     *
     * @param symbol the symbol, or null
     * @param name the name, or null
     * @return the relabelled unit
     */
    public UomUnit<Q> named(String symbol, String name) {
        return new UomUnit<>(atoms, toSystem, symbol, name);
    }

    /**
     * The atoms and their exponents, sorted by symbol.
     *
     * @return an unmodifiable map
     */
    public Map<UnitAtom, Integer> atoms() {
        return atoms;
    }

    /**
     * The converter to the system unit.
     *
     * @return the converter
     */
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
        return dimension;
    }

    /**
     * This unit if it already is a named system unit (it keeps its symbol and name: {@code N}, not
     * {@code kg·m/s^2}), else the unscaled unit of the same atoms, whose symbol is the label of the atoms. A symbol
     * built from other units ({@code m·m}) is not a name, so the product of two lengths is {@code m^2}.
     */
    @Override
    public UomUnit<Q> getSystemUnit() {
        if (toSystem.isIdentity() && name != null) {
            return this;
        }
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

    // Guards the provider's format from printing the unit it is formatting again.
    private static final ThreadLocal<Boolean> PRINTING = ThreadLocal.withInitial(() -> false);

    /**
     * The symbol, or for an unnamed unit its text in the standard format of the provider of this implementation
     * ({@code mHz}, {@code km}), or without one its definition: {@code 1|1000·m} or {@code 5|9·K + 45967|100}.
     */
    @Override
    public String toString() {
        if (symbol != null) {
            return symbol;
        }
        if (!PRINTING.get()) {
            PRINTING.set(true);
            try {
                String text = Providers.unitText(this);
                if (text != null) {
                    return text;
                }
            } finally {
                PRINTING.set(false);
            }
        }
        return definition();
    }

    /** {@code f·S + s}: the factor {@code f} and the shift {@code s = offset / f} of the converter to the system unit {@code S}. */
    private String definition() {
        String system = systemLabel();
        if (toSystem.isIdentity()) {
            return system;
        }
        String f = text(toSystem.factor());
        StringBuilder out = new StringBuilder(f.equals("1") ? system : f + "·" + system);
        if (!toSystem.isLinear()) {
            Factor shift = toSystem.offset().divide(toSystem.factor());
            out.append(shift.signum() < 0 ? " - " : " + ").append(text(shift.signum() < 0 ? shift.negate() : shift));
        }
        return out.toString();
    }

    /** An exact fraction as {@code n|d}, any other factor as {@link Factor#toString()}. */
    private static String text(Factor f) {
        String t = f.toString();
        return t.matches("-?\\d+(/\\d+)?") ? t.replace('/', '|') : t;
    }

    /** Same dimension and an identity converter; alternate atoms may differ, so rad is equivalent to one. */
    @Override
    public boolean isEquivalentTo(Unit<Q> that) {
        return Objects.requireNonNull(that, "that") instanceof UomUnit<?> u && isCompatible(u) && converterTo(u).isIdentity();
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

    /**
     * This unit as a unit of {@code type} if its dimension is the dimension of the type, as the {@code Unit} Javadoc
     * says; alternate units count by dimension, so {@code 1/s} and {@code Bq} are also units of {@code Frequency}, and
     * {@code rad·m} is a unit of {@code Length}. Throws {@link ClassCastException} if the dimensions differ.
     */
    @Override
    public <T extends Quantity<T>> Unit<T> asType(Class<T> type) {
        QuantityFactory<T> factory;
        try {
            factory = Providers.quantityFactory(type);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("no javax.measure ServiceProvider available", e);
        }
        if (!getDimension().equals(factory.getSystemUnit().getDimension())) {
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

    /**
     * This unit multiplied by an exact factor.
     *
     * @param factor the factor
     * @return the scaled unit
     */
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

    /**
     * This unit scaled by the value of {@code prefix}, without a symbol or name: whether a prefix applies to a unit
     * ({@code km} but not {@code kmin} or {@code km²}) and whether the result collides with another symbol is
     * known to a system of units, not to the unit. Name the result with {@link #named}, or let a system
     * ({@code @prefixed}) or {@code UomUnitFormat} resolve it.
     */
    @Override
    public UomUnit<Q> prefix(Prefix prefix) {
        return transform(Converters.scale(Factor.decimal(prefix.getValue().toString()).pow(prefix.getExponent())));
    }

    /**
     * The product with {@code multiplier}. A unit with an offset takes part by its factor only, so {@code °C·m}
     * is {@code K·m}: the offset belongs to points, and a product unit measures differences (docs/OFFSET-UNITS.md).
     */
    @Override
    public UomUnit<?> multiply(Unit<?> multiplier) {
        return combine(multiplier, false);
    }

    /** The quotient by {@code divisor}; an offset is dropped as in {@link #multiply(Unit)}. */
    @Override
    public UomUnit<?> divide(Unit<?> divisor) {
        return combine(divisor, true);
    }

    private UomUnit<?> combine(Unit<?> other, boolean quotient) {
        UomUnit<?> that = uomUnit(other);
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
        Map<UnitAtom, Integer> m = new LinkedHashMap<>();
        atoms.forEach((a, e) -> {
            if (e % n != 0) {
                throw new ArithmeticException("exponent " + e + " of " + a.symbol() + " not divisible by " + n);
            }
            m.put(a, Math.toIntExact((long) e / n));
        });
        return of(m, Converters.scale(toSystem.factor().root(n)), null, null);
    }

    /**
     * The serialization proxy.
     *
     * @return the proxy
     */
    @Serial
    private Object writeReplace() {
        return new Proxy(atoms.keySet().toArray(UnitAtom[]::new),
                atoms.values().stream().mapToInt(Integer::intValue).toArray(), toSystem, symbol, name);
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

    /** Pairs parallel arrays from a stream, rejecting a length mismatch, null and duplicate atoms. */
    static Map<UnitAtom, Integer> atomMap(UnitAtom[] atoms, int[] exponents) {
        if (atoms == null || exponents == null || atoms.length != exponents.length) {
            throw new IllegalArgumentException("atoms and exponents do not match");
        }
        Map<UnitAtom, Integer> m = new HashMap<>();
        for (int i = 0; i < atoms.length; i++) {
            if (m.put(Objects.requireNonNull(atoms[i], "null atom"), exponents[i]) != null) {
                throw new IllegalArgumentException("duplicate atom " + atoms[i].symbol());
            }
        }
        return m;
    }

    /** Serialized form of a unit; package-private so tests can forge streams. */
    static final class Proxy implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private static final String TOO_MANY_ATOMS = "invalid unit: too many atoms";
        private static final String TOO_MANY_EXPONENTS = "invalid unit: too many exponents";

        private transient UnitAtom[] atoms;
        private transient int[] exponents;
        private transient UomConverter toSystem;
        private transient String symbol;
        private transient String name;

        Proxy(UnitAtom[] atoms, int[] exponents, UomConverter toSystem, String symbol, String name) {
            this.atoms = atoms;
            this.exponents = exponents;
            this.toSystem = toSystem;
            this.symbol = symbol;
            this.name = name;
        }

        @Serial
        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            SerialForm.writeObjects(out, atoms, TOO_MANY_ATOMS);
            SerialForm.writeInts(out, exponents, TOO_MANY_EXPONENTS);
            out.writeUnshared(toSystem);
            out.writeUnshared(symbol);
            out.writeUnshared(name);
        }

        @Serial
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            atoms = SerialForm.readObjects(in, UnitAtom.class, UnitAtom[]::new, TOO_MANY_ATOMS);
            exponents = SerialForm.readInts(in, TOO_MANY_EXPONENTS);
            toSystem = SerialForm.read(in, UomConverter.class);
            symbol = SerialForm.read(in, String.class);
            name = SerialForm.read(in, String.class);
        }

        @Serial
        private Object readResolve() throws InvalidObjectException {
            try {
                Objects.requireNonNull(toSystem, "null converter");
                if (toSystem.factor().isZero()) {
                    throw new ArithmeticException("zero scale factor");
                }
                return of(atomMap(atoms, exponents), toSystem, symbol, name);
            } catch (RuntimeException e) {
                throw (InvalidObjectException) new InvalidObjectException("invalid unit: " + e.getMessage()).initCause(e);
            }
        }
    }

    /** Parenthesizes {@code s} if it contains any of {@code operators}. */
    private static String group(String s, String operators) {
        return s.chars().anyMatch(c -> operators.indexOf(c) >= 0) ? "(" + s + ")" : s;
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
        String text = n instanceof Float f ? new BigDecimal(Float.toString(f)).toPlainString()
                : BigDecimal.valueOf(d).toPlainString();
        return Factor.decimal(text);
    }
}
