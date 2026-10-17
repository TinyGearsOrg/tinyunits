# A native API next to the JSR-385 implementation

**Status:** proposal, not decided (TODO item 33). Section 1 describes the code as it is, sections 2 to 6 propose. A
decision gets a record in [DESIGN.md](DESIGN.md) section 1.

**Progress** (branch `native-prototype`): step 1 has started. `uom-engine` exists and holds `Factor` and the
`internal` helpers (decimals, irrational enclosures, caches, serial form); `uom-core` depends on it. Next: dimensions,
atoms and the alternate-unit registry, then the unit algebra and the converters.

## 1. The constraint

uom implements JSR-385 (unit-api 2.2). Its generics are `Unit<Q extends Quantity<Q>>` and `Quantity<Q extends
Quantity<Q>>`: the type parameter must be a quantity of itself. The typed interfaces of uom extend the standard ones
(`org.tinygears.uom.quantity.Length extends javax.measure.quantity.Length`), so they inherit
`Quantity<javax.measure.quantity.Length>` and cannot also be a `Quantity` of themselves; Java forbids implementing one
generic interface with two arguments. As a consequence:

- Units are typed with the standard types: `SI.KILOMETRE` is a `UomUnit<javax.measure.quantity.Length>`. Our
  `Length` cannot be a unit's type parameter, and a file that imports our `Length` cannot also import the standard one,
  so units are declared with `var`, `Unit<?>` or the qualified standard name (README, "Typed quantities and unit
  declarations").
- Type tokens are the standard classes (`asType(javax.measure.quantity.Speed.class)`,
  `getQuantityFactory(javax.measure.quantity.Mass.class)`); `as(Speed.class)` and `Speed.from` exist to narrow back
  (DR-10, DR-19).
- The JSR signatures fix what an operation can return: `multiply(Quantity<?>)` returns a quantity of unknown type
  (`UomQuantity<?, ?>` at best), so `Length.divide(Time)` cannot return a `Speed` (typed products, rejected in DR-10).

Inside the JSR-385 API these constraints cannot be lifted. The proposal is a second API that is not bound to it.

## 2. Modules

```
uom-engine   no JSR-385 types: exact factors, atoms and alternate atoms, dimensions, converters, unit algebra,
             the .units parser and formula evaluator, the catalog data
uom          the native API on the engine: its own Unit<Q>, Quantity<Q>, Dimension, formats and systems, where Q
             is one of our typed interfaces (Length, Speed, ... : the 33 JSR-385 types, under their names)
uom-jsr      the JSR-385 implementation on the engine: javax.measure adapters, the ServiceProvider, the TCK
```

`uom` and `uom-jsr` both depend on `uom-engine`; `uom-jsr` may depend on `uom` to convert between the two APIs. The
artifact names are open (section 6).

What the engine would take from `uom-core`, as it is today:

| Today (`uom-core`) | Engine | Native API / JSR adapter |
|---|---|---|
| `Factor`, `internal` (decimals, irrational enclosures, caches, serial form) | as is | |
| `UnitAtom`, `AlternateUnits`, `BaseUnits` | as is, without the JSR types | typed constants |
| `AffineConverter`, `Converters` | the arithmetic | `UomConverter` implements `javax.measure.UnitConverter` only in `uom-jsr` |
| `UomDimension` (a record implementing `javax.measure.Dimension`) | the exponents | a `Dimension` per API |
| `UomUnit` (implements `javax.measure.Unit`) | the atoms, converter, symbol and name | `Unit<Q>` per API |
| `parse` (`DefinitionParser`, `FormulaEvaluator`) | as is, evaluating to engine units | |
| quantity base classes, `UomQuantity`, `spi` | | per API |

## 3. The native API, sketched

```java
// org.tinygears.uom (native)
public interface Quantity<Q extends Quantity<Q>> extends Comparable<Q> {
    Number value();
    Unit<Q> unit();
    Q add(Q other);                       // the same type, not Quantity<Q>
    Q to(Unit<Q> unit);
    boolean isCloseTo(Q other, double relativeTolerance);
    <T extends Quantity<T>> T as(Class<T> type);
}

public interface Length extends Quantity<Length> {
    static Length of(double value, Unit<Length> unit) { ... }
    Speed divide(Time time);              // typed products from declared relations
    Area multiply(Length length);
}

Unit<Length> km = SI.KILOMETRE;           // our type as the type parameter, no name clash
Speed pace = Length.of(42.195, km).divide(Time.of(3, SI.HOUR));
```

- Typed products and quotients need relations between the types (`Speed = Length / Time`, `Energy = Force · Length`),
  declared in a data file next to `quantity-types.txt` and generated; a product without a relation is untyped, as
  now.
- What the current API settled stays: exact factors, offsets and `Scale`, `double` and `BigDecimal` quantities with
  the precision rule (DR-14), exact decimal equivalence (DR-18), the catalogs and the `.units` files.
- Open: whether `Quantity` names follow JSR-385 (`getValue`, `getUnit`) for familiarity or not.

## 4. The JSR-385 side

A class cannot be a `Unit<our Length>` and a `javax.measure.Unit<javax.measure.quantity.Length>` without a second
type parameter, so the JSR module has its own types over the engine:

- Units: a `javax.measure.Unit` implementation over the engine unit (what `UomUnit` is today).
- Quantities: the generated JSR typed classes over the engine, as today, without the native API's typed products.
- Conversion between the APIs at their boundary: `Jsr.of(nativeLength)` gives a `javax.measure.quantity.Length`, and
  `Length.from(jsrQuantity)` the native one; both copy value, unit (by engine identity), scale and context.
- The TCK keeps running against `uom-jsr`.

## 5. Costs and risks

- Two public APIs to design, document, test and keep in step (formats, systems, factories, serialization), and the
  generator emits for both.
- Users of the native API reach other JSR-385 libraries only through the conversion of section 4.
- The size: the engine extraction is a refactor without behaviour change; the native API is a design project. Today
  `uom-core` has about 6,800 lines of main code and `uom` about 16,000, most of them generated.
- Nothing is released, so module boundaries are cheap to change now; after 1.0 this is a major version (API.md).

## 6. Plan

1. Prototype on a branch: extract `uom-engine` from `uom-core` (no behaviour change, all tests green), then the
   native API for a slice (`Length`, `Time`, `Speed`; their SI units; `Length.divide(Time)` returning `Speed`) and the
   JSR adapters for that slice.
2. Judge the prototype by: unit declarations without workarounds, typed products, the cost of a boundary conversion,
   and how much code the two APIs share.
3. Decide, with a decision record: complete the split; keep the single JSR-385 implementation; or ship only the
   native API with `uom-jsr` as an adapter. The artifact names (`uom`, `uom-jsr`, `uom-engine`) are decided then.
