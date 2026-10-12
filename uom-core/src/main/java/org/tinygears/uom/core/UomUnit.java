package org.tinygears.uom.core;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.measure.Prefix;
import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.UnitConverter;

/** A product of atoms with integer exponents, an affine converter to the system unit, and labels. */
public final class UomUnit<Q extends Quantity<Q>> implements Unit<Q> {

    private final Map<UnitAtom, Integer> atoms;
    private final UomConverter toSystem;
    private final String symbol;
    private final String name;

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

    // Implemented in later tasks.

    private static UnsupportedOperationException notYet() {
        return new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public boolean isEquivalentTo(Unit<Q> that) {
        throw notYet();
    }

    @Override
    public <T extends Quantity<T>> Unit<T> asType(Class<T> type) {
        throw notYet();
    }

    @Override
    public UnitConverter getConverterTo(Unit<Q> that) {
        throw notYet();
    }

    @Override
    public UnitConverter getConverterToAny(Unit<?> that) {
        throw notYet();
    }

    @Override
    public Unit<Q> alternate(String symbol) {
        throw notYet();
    }

    @Override
    public Unit<Q> shift(Number offset) {
        throw notYet();
    }

    @Override
    public Unit<Q> shift(double offset) {
        throw notYet();
    }

    @Override
    public Unit<Q> multiply(Number multiplier) {
        throw notYet();
    }

    @Override
    public Unit<Q> multiply(double multiplier) {
        throw notYet();
    }

    @Override
    public Unit<?> multiply(Unit<?> multiplier) {
        throw notYet();
    }

    @Override
    public Unit<?> inverse() {
        throw notYet();
    }

    @Override
    public Unit<Q> divide(Number divisor) {
        throw notYet();
    }

    @Override
    public Unit<Q> divide(double divisor) {
        throw notYet();
    }

    @Override
    public Unit<?> divide(Unit<?> divisor) {
        throw notYet();
    }

    @Override
    public Unit<?> root(int n) {
        throw notYet();
    }

    @Override
    public Unit<?> pow(int n) {
        throw notYet();
    }

    @Override
    public Unit<Q> transform(UnitConverter operation) {
        throw notYet();
    }

    @Override
    public Unit<Q> prefix(Prefix prefix) {
        throw notYet();
    }
}
