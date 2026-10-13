# uom: Design

Status: draft for owner review; milestones 1 to 6 are implemented (section 7). Companion to `REQUIREMENTS.md` (requirement IDs F1 to F24 and
N1 to N8 refer to it). Target API: `javax.measure:unit-api` 2.2. Java 21. Maven, multi-module.

## 1. Decisions

| Topic | Decision |
|-------|----------|
| API | Implement JSR-385 (unit-api 2.2) ourselves; no Indriya dependency (F1, F2). |
| Typed quantities | Own typed interfaces extending the standard ones, plus generated `double` and `BigDecimal` implementations (F3 to F5). |
| Code generation | An internal generator reads data files and writes plain Java sources, which are committed. CI fails if regenerating produces a diff. No annotation processor, no proxies. |
| Catalogs | Text data files are the single source of truth (F15). They generate typed catalogs; the same parser loads user files at run time (F17, section 5). |
| User extension | Users load their own `.units` files at run time (`UomSystemOfUnits.load`) and get `Unit<?>` back (F18). User-defined quantity types are out of scope. |
| Precision | The lower precision wins, symmetric (F23). |
| Equality | Strict `equals` and `hashCode`; standard `isEquivalentTo`; `Comparable` by system-unit value, not consistent with `equals` (F20 to F22). |

## 2. Modules and data flow

```
 data/quantity-types.txt ─┐
 data/*.units ────────────┼─> uom-codegen (internal tool, not published)
   si, us-customary,      │        │ writes sources, committed to the repo
   imperial, cgs,         │        v
   cgs-esu, cgs-emu,      │   uom  .quantity   typed interfaces, LengthDouble, LengthDecimal, ...
   cgs-gaussian           │        .systems    generated catalogs: SI, USCustomary, Imperial, CGS,
                          │                    ESU, EMU, Gaussian
                          │
 user *.units ───────────────> UomSystemOfUnits.load at run time (no built-in resources shipped)
```

| Module | Contents | Published |
|--------|----------|-----------|
| `uom-core` | `Dimension`, `Unit`, converters, prefixes, the `.units` parser, shared quantity base classes (`AbstractDoubleQuantity`, `AbstractDecimalQuantity`, `Precision`) | yes |
| `uom` | Package `org.tinygears.uom.quantity`: generated typed interfaces, `double` and `BigDecimal` implementations, and one factory per quantity type. Package `org.tinygears.uom.systems`: generated catalogs and `SystemOfUnits` objects (`SI`, `USCustomary`, `Imperial`, `CGS`, `ESU`, `EMU`, `Gaussian`, `UomSystemOfUnits`, `UnitSystems`), the unit and quantity formats, run-time loading of user `.units` files, the JSR-385 `ServiceProvider`; no `.units` resources are shipped | yes |
| `uom-codegen` | The generator program; reuses the parser from `uom-core` | no |
| `uom-tck` | Runs the JSR-385 TCK against `uom`; test only | no |
| `uom-examples` | Runnable usage examples (`Examples`) and a test of their output | no |

Dependencies: `uom` -> `uom-core` -> `unit-api`. `uom-codegen` -> `uom-core`. Module names:
`org.tinygears.uom.core` and `org.tinygears.uom` (requires the core transitively); they require
`java.measure`. The quantity types and the catalogs are one module because the typed quantities
need the provider (`asType`, products, factories through `ServiceProvider`). The provider is declared with `provides` and `META-INF/services`.

## 3. Units core (`uom-core`)

- **Dimension.** Immutable record of the seven SI base-dimension exponents as `int` (the
  standard uses `Integer`; `root` throws on a fractional result, so no `Fraction` type is
  needed). Equality on the exponents.
- **Unit.** One implementation class holding a system-unit decomposition (a map from atoms to
  integer exponents; atoms are the seven base units and the alternate units), a converter to
  that system unit, and a symbol and name. `getSystemUnit()` is the same decomposition with an
  identity converter.
  - `equals` compares decomposition and converter, ignoring symbol and name (F21), so
    `RADIAN != ONE` and `METRE.divide(SECOND)` equals the `m/s` constant. `isEquivalentTo`
    means same dimension and an identity converter between the two units; alternate atoms may
    differ, so `RADIAN` is equivalent to `ONE` and hertz to becquerel.
  - `alternate(String)` creates an atom. The standard requires an exception when the symbol
    is already bound to a different unit, so alternate symbols use one small static registry.
    This is the only global state in the units core.
- **Converters.** Identity, affine (`factor * x + offset`) and concatenation. Factors are exact
  rationals, optionally times a power of pi, so degrees and gradians are exact (N2). Each
  converter also caches the rounded `double` of its factor and offset, computed once.
  Additions to the standard interface: `convert(BigDecimal, MathContext)` (F23). Units with an
  offset can not be multiplied or divided and throw.
- **Powers and roots.** `pow(n)` raises factor and exponents; `root(n)` divides the exponents
  and roots the factor exactly. A factor is `(n/d * pi^k)^(1/r)` with the sign of `n` outside
  the root, so every converter stays affine and equality is structural. Ceiling: radicals are
  only normalized to a canonical form, and `n` and `d` are capped at 65_536 bits.
- **Typing.** `asType(Class<T>)` compares the unit's system unit with
  `ServiceProvider.current().getQuantityFactory(type).getSystemUnit()`. This distinguishes
  hertz from becquerel. `uom-core` holds no table of quantity types. Our provider has priority
  100, so `current()` selects it unless a provider of higher priority is on the class path.
- **Caching.** Each unit caches its converter to a given target unit in a small per-unit map
  (N3). Units created on the fly take their cache with them, so no weak references.
- **Out of scope:** non-affine converters such as logarithmic units.

## 4. Quantities and generated code

The hand-written base classes `AbstractDoubleQuantity<Q, R extends Q>` and
`AbstractDecimalQuantity<Q, R extends Q>` (package `org.tinygears.uom.core.quantity`, `uom-core`)
hold all quantity logic for `double` and `BigDecimal` values: `to`, `add`, `subtract`, `negate`,
`multiply`, `divide`, `inverse`, `asType`, `equals`, `hashCode`, `compareTo`, `isEquivalentTo`,
`toDouble`, `toDecimal`. A package-private `Precision` helper classifies operand kinds and converts
between `Number` kinds. `R` is the typed interface returned by the operations that keep
the quantity type; the typed interfaces only add covariant return types. The design's strict
overloads such as `Length add(Length)` are not generated: generics already reject quantities of
other types, so they add no safety.

Per quantity type the generator writes four files:

1. **A typed interface** in `org.tinygears.uom.quantity`, e.g. `Length extends
   javax.measure.quantity.Length, Comparable<Quantity<Length>>`, with covariant returns for
   `add(Quantity<Length>)`, `subtract`, `to(Unit)`, `negate()`, `multiply(Number)` and
   `divide(Number)` (all return `Length`), `toDouble()` and `toDecimal(MathContext)` (F23, D10), and static factories
   `Length.of(double, unit)`, `Length.of(double, unit, Scale)` and, for `BigDecimal`,
   `of(BigDecimal, unit)`, `of(BigDecimal, unit, Scale)`, `of(BigDecimal, unit, Scale, MathContext)`
   (D11). Milestone 5 adds `toSystem(SystemOfUnits)` (F8, D5). Units stay `Unit<javax.measure.quantity.Length>`, so our quantities
   work with any JSR-385 code (F4). The shared simple name is a deliberate trade-off: everyday
   code never mentions both types, and declaring a unit variable needs `var` or the fully
   qualified standard type.
2. **`LengthDouble`**, a tiny package-private final class extending the base class with
   `R = Length`; it only supplies the typed `create` and `createDecimal`.
3. **`LengthDecimal`**, the same for `AbstractDecimalQuantity`, with `create` and `createDouble`
   (the hook used when a `double` operand makes the result a `double` quantity).
4. **`LengthFactory`**, a final class with a singleton `INSTANCE` that implements
   `QuantityFactory<javax.measure.quantity.Length>` and holds the type's `SYSTEM_UNIT`.
   `create(Number, ...)` returns a decimal quantity (`DECIMAL128`) for a `BigDecimal` or `BigInteger` value and a `double` quantity for any other `Number`; the factory also has
   `of(BigDecimal, ...)` for the three typed statics. It rejects a unit that is not a `UomUnit` with `UnconvertibleException` ("foreign unit
   implementation"). One class `QuantityTypes` is also generated: a registry from the standard
   interface class to the factory (`factory(Class)`, `types()`). Factories are built lazily per type, so a
   conflicting registration breaks only its own type. `UomServiceProvider` uses it.

The generator writes all 33 types of `data/quantity-types.txt`: 133 files (four per type plus
`QuantityTypes`; 100 before milestone 4). No extra types were added in milestone 5.

- **`toSystem(SystemOfUnits)` (F8, D5 of milestone 5).** The base classes implement `R toSystem(SystemOfUnits)` once, as
  `to(system.getUnit(quantityType()))`, through the generated hook `protected abstract Class<Q> quantityType()` (the
  standard interface of the type, `Untyped` for untyped quantities). Scale and `MathContext` are kept. A `null` unit from the
  system (and any untyped quantity) throws `UnsupportedOperationException("system N has no unit for type T")`; a unit of
  another dimension gives `UnconvertibleException`; a `null` system gives `NullPointerException`. Every system of this library
  has a preferred unit for all 33 types (D4 fallback: own `@preferred`, else the first extended system's, else SI's). Offsets
  apply: `Temperature.of(100, degC).toSystem(USCustomary.SYSTEM)` is `212.0 °F`, exactly `212 °F` for a decimal. The method is named
  `toSystem` so that it does not overload `to(Unit)` (a bare `to(null)` stays unambiguous).
- **Untyped results.** `multiply(Quantity)`, `divide(Quantity)` and `inverse()` return an
  `UntypedDoubleQuantity` or `UntypedDecimalQuantity` (phantom type `Untyped`) whose unit is built from the system units of
  the operands, scale `ABSOLUTE`. `asType(Class)` returns `this` when the quantity is already
  of that type; otherwise it narrows through
  `ServiceProvider.current().getQuantityFactory(type)` and the unit's `asType`.
- **Offset units.** Arithmetic follows the `Quantity` Javadoc literally (F24): operands are
  taken to the system unit, combined, and the result converted back to the left operand's unit;
  linear units skip the round trip. `negate` is `-getValue()` in the same unit.
- **Scale (D3).** `Quantity.Scale` is a stored flag, default `ABSOLUTE`; arithmetic never
  depends on it. `to`, `add`, `subtract`, `negate`, `multiply(Number)` and `divide(Number)`
  keep the left operand's scale; quantity-by-quantity results and `inverse` are `ABSOLUTE`.
- **Foreign arguments (D8).** A `Quantity` or `Unit` whose unit is not a `UomUnit` is rejected
  with `UnconvertibleException`; `null` gives `NullPointerException`; an incompatible dimension
  gives `UnconvertibleException` (`compareTo`, `add`, ...) or `false` (`isEquivalentTo`).

- **Precision (F23).** Base classes accept any `Quantity<Q>` whose unit is a `UomUnit` (foreign units are rejected, D8).
  Decimal quantities carry a `MathContext`, default `DECIMAL128`; `getValue()` is a `BigDecimal` (decimal) or a
  `Double`. Every computed result (conversion, arithmetic, `toDecimal`) is rounded to the result context, so `DECIMAL32`
  `1.23456789 m + 0 m` is `1.234568 m`; creation and `negate` keep the value as given. `UNLIMITED` is exact where the
  result terminates and falls back to `DECIMAL128` otherwise. Details (milestone 4, D2 to D11):
  - **Operand kinds (D2).** `Byte`, `Short`, `Integer`, `Long`, `BigInteger`, `AtomicInteger` and `AtomicLong` are exact
    and never lower the precision; `BigDecimal` is decimal; `Double`, `Float` and any other `Number` count as `double`.
    A foreign `BigDecimal` value has no context and counts as unlimited.
  - **Result type (D3).** If either operand is `double`, the result is a `double` quantity of the same typed interface,
    in both argument orders: a decimal receiver builds it through `createDouble`, a `double` receiver through `create`
    (`createDecimal` serves only `toDecimal`). Two decimals give the
    lower `MathContext` precision (0, unlimited, is highest; equal precision keeps the receiver's rounding mode). Scalar
    `Number` arguments adopt the receiver's precision. A `BigDecimal` outside the `double` range gives
    `ArithmeticException`, never `Infinity`. The result unit is the left operand's unit for `add`, `subtract` and the scalar
    operations; quantity-by-quantity `multiply`/`divide` and `inverse` return untyped results in system units.
  - **Single rounding (D6a).** `add`, `subtract` and the scalar operations on offset units are one affine function of the
    operand, rounded once to the result context, never converting each operand first.
  - **Division (D7).** Rounds to the receiver's context; an unlimited context uses `DECIMAL128` for the division only, so
    `1 m / 3` never throws. Division by zero throws `ArithmeticException`. Generally, `UNLIMITED` keeps a result exact
    where it terminates; non-terminating quotients and irrational conversions fall back to `DECIMAL128` with
    `HALF_EVEN`, whatever the context's rounding mode.
  - **Scalar conversion (D8).** A `double` or `Float` scalar becomes the decimal the user wrote
    (`BigDecimal.valueOf(double)`); `NaN` and infinity throw `ArithmeticException`.
  - **Bounds (D9).** The scale of a decimal value is limited to `|scale| <= 100_000` at construction and for every
    `BigDecimal` operand value or scalar (`ArithmeticException`, before any arithmetic); products and quotients whose
    scale passes the bound throw, zeros included. Zero offsets are skipped, so a result may have a different scale
    than before (`toString` and `scale()` only). Exact conversions do not align far-apart scales (`AffineConverter`),
    so absurd scales stay fast. Likewise the precision of a decimal quantity's `MathContext` is limited to 100_000
    (`ArithmeticException` at construction, so in `toDecimal(mc)` and every factory taking a context; a serialized
    form beyond it is an `InvalidObjectException`). The bound caps the work of every rounded result; it is separate
    from the scale bound, which it does not imply (a value near 1e60000 with 150000 digits has a scale under 100000).
    At precision 100000 rational conversions and arithmetic take milliseconds; the first use of pi at that precision computes
    it (about 0.6 s on JDK 25, 2.5 s on JDK 21). Exact quotients avoid JDK 21's quadratic trailing-zero stripping in
    `BigDecimal.divide` (`Decimals.divide`, a non-exported package).
  - **Explicit conversions (D10).** `toDouble()` throws `ArithmeticException` for a value outside the `double` range;
    `toDecimal(mc)` rounds to `mc`, on both implementations. `asType` keeps the receiver's `MathContext` and never changes
    the value.
  - **Factories (D11) and formats (D12).** As in section 4 item 1 and 4; `UomQuantityFormat.format` prints
    `BigDecimal.toString()`; `parse` returns `double` quantities unless the format has `withDecimals` (section 5,
    milestone 6). `withMathContext(mc)` (public on `AbstractDecimalQuantity`, not on the typed interfaces) changes the
    context without rounding the value; the decimal parse uses it.
  - Irrational factors (a pi or root in the converter) are best effort for decimal arithmetic; see
    `docs/TODO.md` (item 22).
- **Equality (F20).** `equals`: same implementation class, same unit, same scale, same value. `double`: `Double.compare`.
  Decimal (D4): `compareTo == 0`, so `1.0 m` equals `1.00 m`; the `MathContext` is not part of equality; `hashCode` uses
  `stripTrailingZeros()`. `LengthDouble` never equals `LengthDecimal`. `isEquivalentTo`: same value after conversion to the
  same unit within a relative tolerance, with no absolute floor (asymmetric near zero for offset units). `double`
  (`AbstractDoubleQuantity.EQUIVALENCE_TOLERANCE`): equal or `|a - b| <= 1e-12 * max(|a|, |b|)`; NaN is equivalent to nothing.
  Decimal (D5): after converting `that` into this unit, equal by `compareTo` or `|a - b| <= max(|a|, |b|) * 10^(1 - p)`,
  where `p` is the lowest non-zero precision of the two contexts (34 when both are unlimited); against a `double` operand
  the `double` rule applies. At precision 1 the tolerance is 1 (degenerate, parked).
- **Ordering (D1, D6, F22).** Quantities implement `Comparable<Quantity<Q>>`, as the `Quantity` Javadoc requires. `double`:
  `Double.compare` of the system-unit values. Decimal: the difference of the system-unit values is evaluated once, with no
  context rounding, and its sign decides, so the ordering is a consistent total preorder; against a `double` operand it
  compares as `double`. Not consistent with `equals` (1 km and 1000 m compare equal but are not `equals`).

## 5. Data files, generator and parser

Files live in `data/`: `quantity-types.txt` (quantity types and their system units, built from
the standard's 33 types, no extras) and one `.units` file per unit system: `si.units`, `us-customary.units`,
`imperial.units`, `cgs.units`, `cgs-esu.units`, `cgs-emu.units` and `cgs-gaussian.units`. They are not shipped as
resources: the catalogs are generated only, and users load their own files at run time (below).

**Generator (milestones 2, 3 and 5).** `uom-codegen` (not published) reads `data/quantity-types.txt`
and the seven `.units` files listed in `Generate.CATALOGS` (`si.units` first) and writes the sources of section 4 into
`uom/src/main/java/org/tinygears/uom/quantity` and one catalog class per file (`SI`, `USCustomary`, `Imperial`,
`CGS`, `ESU`, `EMU`, `Gaussian`) into `uom/src/main/java/org/tinygears/uom/systems/`; they are committed. A `.units`
file missing from the list is caught by the data-versus-list test, not by the generator. `Generate` takes
three arguments: `<dataDir> <quantitiesOut> <systemsOut>`.

- `data/si.units` holds the SI definitions (BIPM SI Brochure, 9th edition) and the curated
  `@prefixed` lines; it replaced the milestone 2 bootstrap set `base.units` and yields 128
  catalog entries, declared in `SI` as typed constants (`SI.METRE`, `SI.KILOMETRE`).
- The other files (values authored from NIST SP 811, the SI Brochure and Jackson, not copied from other catalogs): `us-customary.units`
  (`@system USCustomary`; international and survey foot, US gallon, `lb`, `lbf`, `psi`, `BTU_IT`, `hp`, `°F`, `°R`, 35 distinct units),
  `imperial.units` (`@extends us-customary.units`; imperial gallon and ton, stone; 42), `cgs.units` (`@system CGS`,
  `@member cm g s`; dyne, erg, barye, galileo, poise, stokes; 16), `cgs-esu.units` (statcoulomb and the other `stat` units; 22),
  `cgs-emu.units` (the `ab` units, gauss, maxwell, oersted, gilbert; 29) and `cgs-gaussian.units` (`@extends cgs-esu.units`,
  `@include cgs-emu.units`, `@member G Mx Oe Gb`, franklin; 26). Units that differ between systems have distinct symbols
  (`gal_US`, `gal_UK`, `ft`, `ft_US`). Poise, stokes and oersted are untyped (D6): no standard quantity type exists.
- `data/quantity-types.txt` has one `Name formula` per line, e.g. `Speed m / s`. `Name` is a
  `javax.measure.quantity` interface that must exist; the formula is the unscaled, non-offset
  system unit over units of `si.units`. A formula that is not a single definition names its unit with a trailing
  `=> symbol:name` (`Speed m / s => m/s:metre_per_second`, `_` for a space), so every factory system unit has a symbol
  and a name. The symbol must evaluate back to the unit (`m/s^2`, not `m/s²`): formats and `toString` print
  `getSymbol()` whenever it parses back. `#` starts a comment. An empty file, a duplicate, an unknown name or unit, or a
  malformed line is an error with file and line (F19).
- Output is deterministic (stable order, `\n` line endings, no timestamps). Generated files
  start with the SPDX line and a "Generated by uom-codegen" line; regeneration deletes stale
  files only when they carry that header.
- Regenerate from the repository root after changing `data/` (always with `-am`):
  `mvn -q -B -Pgenerate -pl uom-codegen -am process-classes`.
- `GeneratedSourcesUpToDateTest` compares the committed sources with a fresh generation and
  fails on drift. The root `.gitattributes` (`* text=auto eol=lf`) keeps checkouts byte-identical
  so the comparison also holds on Windows.

```
m:metre              !L                       # base unit of the length dimension
rad:radian           ~                        # alternate unit (own identity)
Hz:hertz             ~ 1/s       [Frequency]
Bq:becquerel         ~ 1/s       [Radioactivity]
J:joule              N m         [Energy]     # explicit type: Energy or Torque?
in:inch              0.0254 m                 # exact decimal
deg:degree           pi|180 rad               # pi is a token
statC:statcoulomb    1|2997924580 C           # exact fraction
°F:degree_fahrenheit °C = (x - 32) * 5|9      # affine
@preferred Length cm                          # the system's default unit for a type
@prefixed m k c m µ n p                       # generate km, cm, mm, µm, nm, pm
```

- **Syntax.** `symbol:name formula [Type] # comment`. Space, `*` or `·` (U+00B7) multiplies, `/`
  divides; `*`, `·` and `/` share one precedence and associate to the left; juxtaposition binds
  tighter than them, so `J / kg K` is `J / (kg K)`;
  `^n` is an integer power, `a|b` an exact fraction, decimals are exact, parentheses group,
  `!` marks a base unit, `~` an alternate unit, `=` an affine function, `@include` includes a
  file (definitions only; every file includes `si.units`), `@preferred` sets the preferred unit of a type,
  `@prefixed <symbol> <prefix>...` generates prefixed units from metric prefix symbols. Underscores in names stand for
  spaces. Milestone 5 adds `@system Name` (once per catalog file: names the class and the system; a Java identifier),
  `@extends file` (implies `@include`; the units of that file and, transitively, of the files it extends become members, and
  its `@preferred` lines are inherited) and `@member symbol...` (units of an included file that become members without a
  new constant; a unit defined in the root file itself is an error).
- **Type resolution (F16).** A trailing `[Type]` states the quantity type; it must name a type in
  `quantity-types.txt` whose system unit equals the unit's system unit (`CatalogBuilder`).
  Otherwise the generator looks up the unit's system-unit decomposition in `quantity-types.txt`.
  Hertz and becquerel differ by their alternate atoms; energy and torque share a decomposition
  and need `[Energy]` or `[Torque]`. More than one match without `[Type]` is a build error with
  file and line; no match leaves the unit untyped.
- **Evaluation.** One pass; a definition must precede its use, so circular definitions are
  impossible. Every error carries file and line (F19): unknown unit, duplicate symbol,
  dimension mismatch, unparsable formula. Symbols are unique across all files; clashes such as
  gallon use distinct symbols (`gal_US`, `gal_UK`).
- **Output.** The generator evaluates each formula to a decomposition, an exact rational
  factor and an offset, and emits plain constructor calls. `si.units` becomes the class `SI`
  with typed constants (`UomUnit<javax.measure.quantity.Length> METRE`; names come from the unit
  names, upper case, non-ASCII letters replaced by `_`), the collections `UNITS`, `PREFERRED`
  and `PREFIXABLE`, and the `SystemOfUnits` object `SI.SYSTEM` (F8, F6). Every catalog file gives such a class
  (`USCustomary.FOOT`, `Imperial.GALLON_UK`, `CGS.DYNE`, `ESU.STATCOULOMB`, `Gaussian.FRANKLIN`, ...). It also emits the
  quantity sources of section 4.
- **Catalog classes (D3).** A class has constants for its own definitions only. Its `UNITS` list is its own constants, then the
  flattened `UNITS` of the systems it extends, then the `@member` units through the owning class (`CGS.UNITS` ends with
  `SI.CENTIMETRE`, `SI.GRAM`, `SI.SECOND`; `Gaussian.UNITS` holds `EMU.GAUSS`); equal units collapse to the first listed.
  `PREFIXABLE` is the own and inherited `@prefixed` symbols restricted to members, and a member keeps the prefixable status
  of its owner, so `G` is prefixable in `Gaussian` and `g` and `s` in `CGS`.
- **Shared instances.** A unit is built once. A factory's `SYSTEM_UNIT` is the `BaseUnits` constant itself when the
  symbol and name agree (`LengthFactory.SYSTEM_UNIT == BaseUnits.METRE`), and a catalog constant equal to a type's system
  unit with the same symbol and name is that factory's unit (`SI.NEWTON == ForceFactory.SYSTEM_UNIT`), so `PREFERRED` and
  the fallback of the other catalogs hold the same objects. The first such definition of a type must agree in symbol and
  name (else a generation error with file and line); a later alias with another symbol or name keeps its own unit.
  "First" is file order across all catalogs in `Generate.CATALOGS` order (SI first), so an alias in a later catalog of a
  unit SI already defines keeps its own unit. A system unit named by `=>` has a symbol no definition can carry (`m/s`),
  so every definition equal to it is an alias (`mps`, `gee`) and keeps its own unit. Factories never reference catalogs,
  so class initialisation runs from a catalog to the factories only.
- **Preferred layering (D4).** `PREFERRED` has all 33 types: the file's own `@preferred` lines, else those of the first
  extended system (in `@extends` order), else `SI`'s (the fallback relies on `si.units` being first in `Generate.CATALOGS`).
  Layering is by source file; files that are only `@include`d contribute no lines. `SI` itself keeps the factory system unit
  for a type without `@preferred`. Fallback units are not members: `USCustomary.SYSTEM.getUnit(Time.class)` is `s`, but
  `getUnit("s")` is null and `getUnits()` lacks `s` (123 (system, type) pairs; SI itself for `m/s^2`, `1` and `m/s`).
- **Run time (F17, F18, D5 of milestone 6).** The parser ships in `uom-core`. `UomUnitFormat` resolves symbols from the
  generated catalogs and evaluates formulas with `FormulaEvaluator`. User files load at run time, with no global state:
  - `DefinitionParser.parse(source, reader, resolver, known)` looks up symbols the file does not define through
    `known`; a file defining a symbol `known` resolves is an error (`symbol 'm' is already defined`, file and line).
    The known lookup is `UomUnitFormat.resolve(String)`: exact catalog symbols and metric prefixes, never labels.
  - `UomSystemOfUnits.load(String name, Reader, Resolver)` and `load(Path)` return an immutable system of the file's
    definitions (then the `@extends`-ed systems' units, then `@member`s). Constants are `Unit<?>`. `@preferred Type
    symbol` names one of the 33 standard types by simple name; an unlisted type falls back to the first extended
    system's preferred unit, else `SI`'s, so `to(userSystem)` works. The name is `@system`, else the file name without
    extension (`load(Path)`) or `name` as given (Reader variant).
  - `load(Path)` resolves every `@include`/`@extends` name, in nested files too, against the root file's directory (one
    flat namespace). A name must be relative and stay inside that directory lexically and by real path (symbolic links
    resolved). The resolver is called at most once per name per `load` call; parents are cached per call. No built-in
    files exist: `@include si.units` fails unless the user's resolver provides it.
  - `UomUnitFormat.with(UomSystemOfUnits...)` returns a new format over the existing systems followed by the given
    ones, labels copied. A symbol of a given system that resolves (exactly or as a prefixed form) or as a label to a
    different unit is an `IllegalArgumentException` naming both systems; equal units are fine. Two formats can hold
    different systems with the same symbol.
  - Exception: `~` lines register their alternate symbols in the JVM-wide registry permanently (section 3).
- **Prefixes.** `@prefixed` generates the curated prefixed units (`SI.KILOMETRE`). At run time
  `UomUnitFormat` also resolves a metric prefix on any unit in `SI.PREFIXABLE`, so `Mm` and `dam`
  parse without a constant; binary prefixes (`MiB`) are not resolved (no byte or bit units either). Only units named in a
  `@prefixed` line are in `SI.PREFIXABLE`, so only they take prefixes at run time (`Gm`, but not
  `kmin`). Prefixable symbols of all systems apply (D3). A prefixed power is written with `^`: `km^2` parses as `(km)^2`, while `km²` does not
  parse (`m²` is a symbol of its own, not a prefixable unit).
- **Symbol resolution across systems (D7).** `UomUnitFormat.standard()` resolves labels, then exact symbols across all systems of
  `UnitSystems.all()` (SI first), then a metric prefix on a prefixable unit. Exact symbols win over prefix readings, so `ft` is the
  foot, not the femtotonne; this is the only string that parsed in SI-only mode and now reads differently (the shadow set `{ft}`,
  pinned by a test over all strings of that form). `abH` equals `nH` and `cm³` equals `mL`: a unit with its
  own symbol formats to it (`format(EMU.ABHENRY)` is `abH`), an unnamed equal unit to the first system's symbol (`nH`).
- **Shift grammar (D2 of milestone 6).** A formula may end in `+ n` or `- n` (top level only, never inside
  parentheses): `expr + n` is `expr.shift(n)`, with `n` a decimal literal or an exact fraction `a|b`. Spaces are allowed
  around the sign and the number. `+` and `-` are delimiters, so labels and data symbols containing them are rejected. A
  `-` with no whitespace on either side followed by a plain integer (`s-1`, `kg m-3`, `m^2-1`) is an error
  (`ambiguous 's-1': write 's^-1' for a negative exponent or 's - 1' for a shift`), because UDUNITS and CF read it as a
  negative exponent; `K-273.15`, `K-1|3` and `K - 5` are valid. A `.units` body can therefore define `K + 273.15`.
- **Faithful offset format.** After the label, symbol and catalog-symbol rules, `UomUnitFormat.format` writes an offset
  unit with factor `f > 0` (no root) and offset `o` in system unit `S` as `<f-formula>·S + o/f` with spaces around the
  sign (`K + 5`, `5|9·K + 459.67`, `2·K + 136.575`). The shift is a plain decimal when it terminates and is not longer
  than the fraction `a|b` (ties: decimal), else the fraction (`K + 1|1024`). Every text is verified by parsing it back.
  Four categories have no faithful form and throw `IllegalArgumentException`: a negative factor, a root factor, an
  irrational shift `o/f` (a power of pi or a root), a formula over 10,000 characters. A brute force over every catalog
  unit and 20,000 random units finds no mismatch.
- **Quantity format (D3, D4 of milestone 6).** `UomQuantityFormat` is immutable: `withLocale`, `withGrouping`,
  `withDecimals`, `getLocale`, `getDecimalContext`, `isLocaleSensitive`, value `equals`. The default is `Locale.ROOT`,
  no grouping, `double` parse, with output identical to milestone 5. In another locale the value text is the same
  `Double.toString`/`BigDecimal.toString` with ASCII digits, `.`, `-` and `E` mapped through `DecimalFormatSymbols`; unit
  symbols are not localized; no grouping or bidi marks are written. Parsing accepts locale and ASCII digits, the
  locale's decimal separator, minus sign (or `-`, `+`) and exponent separator (or `E`, `e`), skips bidi marks around
  the number, and rejects a grouping separator unless `withGrouping(true)` (then only between digits of the integer
  part). A locale whose symbols collide is refused by `withLocale`; every available locale of JDK 21 and 25 round
  trips. `withDecimals(mc)` parses to typed decimal quantities through the standard factories plus `withMathContext`
  (else `UntypedDecimalQuantity`), keeping every digit and the scale (`1.50 km` has scale 2), scale `ABSOLUTE`; NaN,
  infinity, hex and `d`/`f` suffixes are errors, ROOT accepts ASCII digits only, and a value scale beyond ±100000 is a
  `MeasurementParseException` checked in linear time before the unit. `UomFormatService` keeps the ROOT format.
- **Consistency test.** `SiCatalogTest` checks every definition of `si.units` against the
  catalog; `UnitFormatTest` formats and parses every `SI.UNITS` entry back to an equal unit.

## 6. Testing and conformance

- `unit-tck` 2.2 against our provider (N1): module `uom-tck`, 113 of 113 tests pass, 0 skipped
  (`mvn -q -B clean verify`). Own contract tests from the Javadoc: `QuantityContractTest`
  (`uom`) and the `contract` package of `uom-core`.
- Parsed units equal generated constants; catalog round trip for every unit (N6).
- Generator golden tests: sample inputs compile, behave, and report errors with file and line.
- CI drift check (`.github/workflows/ci.yml`, job `generated`): regenerate and fail on a non-empty `git diff` or an
  untracked file.
- Semantics tests: precision mixing, equality and equivalence, alternate units, offset units,
  roots of non-perfect powers.
- Serialization round trip (every catalog unit; 33 types x {double, decimal} x {ABSOLUTE, RELATIVE}; golden files);
  module-path and class-path runs, including fresh-JVM reads. Milestone 6 ran the full build on JDK 25 and, for the
  locale work, on JDK 21; since milestone 7 the CI workflow runs `mvn -B verify` on JDK 21 and 25, and
  `scripts/verify-release.sh` checks a consumer project on the class path, the module path and in a `jlink` image.
- **Serialization (F12, D1 of milestone 6).** Serialization proxies for `Factor`, `UomDimension`, `UnitAtom`, the
  converters, `UomUnit` and the quantity base classes. Each class returns its proxy from `writeReplace` and throws
  `InvalidObjectException("proxy required")` from `readObject`; each proxy (`serialVersionUID = 1L`) rebuilds the object
  through the public factories in `readResolve`, so every invariant is re-checked and any violation is an
  `InvalidObjectException`. Proxies write values unshared with bounded lengths (at most 1000 atoms or conversion
  steps, `NotSerializableException` on write), so the bytes depend on values only.
  - A unit is its atoms and exponents, converter factor and offset (with the conversion steps), symbol and name; the
    converter cache is transient. A multi-step converter is checked against its steps when they fold exactly, else by
    a 60-digit decimal fold within 1e-40.
  - An alternate atom carries its symbol, dimension and definition. On read the dimension must match the definition
    and a registered symbol must have the identical definition; it resolves to the registered atom. An unregistered
    symbol gives an equal, unregistered atom. Reading never registers (untrusted data must not bind symbols or grow the
    registry), so a fresh JVM can read without touching `SI`.
  - A quantity is its kind (`double` or decimal, typed or untyped), standard type (an allow-list of the 33 interfaces
    and `Untyped`, never `Class.forName`), unit, `Scale`, value (raw bits, or unscaled bytes and scale) and
    `MathContext`. A typed read resolves the provider first, creates through
    `ServiceProvider.current().getQuantityFactory(type)` and restores the exact value, scale and context; a user
    subclass reads back as the provider's class. Scale and precision beyond 100000, a unit of another dimension, an
    unknown type or null fields are `InvalidObjectException`s.
  - The JDK allocates stream arrays and strings before the proxies run, so untrusted streams need an
    `ObjectInputFilter` (README). Systems, formats, services and converter caches are not serializable. The form is
    pinned by golden files and is not promised stable before 1.0.

## 7. Milestones (each releasable as a snapshot)

1. Core and parser, plus the TCK spike.
2. Quantities for `double`: base class, generator for quantity types, a handful of types end
   to end.
3. SI catalog: generator for `si.units`, prefixes, the service provider, basic unit and quantity
   formats (symbols, prefixes, formulas); the TCK runs for real. Done: 33 types, 128 SI catalog
   entries, TCK 113 of 113 tests, 554 tests in `mvn -q -B clean verify`. Follow-ups:
   `docs/TODO.md`.
4. `BigDecimal`: decimal base class and the precision rule. Done: `AbstractDecimalQuantity`, `Precision`, 33 `XDecimal`
   classes (133 generated quantity files), 33 types, 128 SI catalog entries, TCK 113 of 113 tests, 708 tests in
   `mvn -q -B clean verify` (core 350, codegen 97, quantities 28, systems 119, tck 113, examples 1). Follow-ups:
   `docs/TODO.md`.
5. Other systems: Imperial/US customary, CGS, CGS electromagnetic; `quantity.toSystem(system)`. Done: `USCustomary`, `Imperial`, `CGS`,
   `ESU`, `EMU`, `Gaussian` catalogs (35, 42, 16, 22, 29, 26 distinct units, with `SI` 128), the directives `@system`,
   `@extends`, `@member`, `toSystem(SystemOfUnits)`, 33 types (133 generated quantity files), TCK 113 of 113 tests, 901 tests in
   `mvn -q -B clean verify` (core 430, codegen 162, uom 195, tck 113, examples 1; counted after merging the quantities and
   systems modules into `uom`). Follow-ups:
   `docs/TODO.md`.
6. Serialization, locale-aware formats, a faithful form for offset units, decimal-preserving parse, user units at
   run time. Done: serialization proxies for units and quantities (F12), the shift grammar and offset format (F9),
   `withLocale`/`withGrouping`/`withDecimals` (F9), `UomSystemOfUnits.load`, `UomUnitFormat.with` and `resolve`
   (F17, F18), TCK 113 of 113 tests, 1070 tests in `mvn -q -B clean verify` (core 506, codegen 162, uom 288, tck 113,
   examples 1). Follow-ups: `docs/TODO.md`.
7. Release engineering: POM metadata, sources and javadoc jars, a signing profile, CI on JDK 21 and the current JDK, the
   custom-unit guide and unit table (N7), the versioning policy and public API definition (N8), a JPMS review. Done
   (prepared, nothing published): POM metadata and an inactive `release` profile (sources, javadoc, signing, Central
   upload with `autoPublish=false`), enforcer rules, reproducible jars, javadoc without warnings, the API pin
   (`docs/api/public-api.txt`, [API.md](API.md)), the generated [UNITS.md](UNITS.md), the documentation set
   ([CUSTOM-UNITS.md](CUSTOM-UNITS.md), [RELEASING.md](RELEASING.md), `CHANGELOG.md`, `SECURITY.md`,
   `CONTRIBUTING.md`), `scripts/verify-release.sh` (release artifacts, reproducibility, `jlink`, a consumer project),
   the CI workflow (JDK 21 and 25) and dependabot; TCK 113 of 113 tests, 1112 tests in `mvn -q -B clean verify` (core
   506, codegen 169, uom 322, tck 113, examples 2). Not done: the repository rename and the publication itself (the
   owner's action, [TODO.md](TODO.md), "Release readiness"). Follow-ups:
   `docs/TODO.md`.

## 8. Open items for the implementation plan

- Resolved in milestone 4: the decimal `equals` scale rule and the decimal `isEquivalentTo` tolerance (section 4). The
  `double` tolerance and the `Scale` rules were resolved earlier.
- Extra quantity types for the untyped poise, stokes and oersted (`DynamicViscosity`, `KinematicViscosity`, `MagneticFieldStrength`, ...): the milestone 5 catalogs leave them untyped (D6).
- Run-time loading: user files are done (milestone 6); shipping the built-in `.units` files as resources is not
  planned (the catalogs are generated).
- Binary prefixes (`MiB`) in parsing and `@prefixed`, and byte and bit units: not implemented.
- Typed `from(Quantity<?>)` on the 33 typed interfaces and `@prefixed` catalog units emitted through `prefix(...)`:
  owner-requested follow-ups after milestone 6.
- The open owner decisions in `docs/TODO.md` (offset-unit arithmetic, `asType` and alternate
  units, offset-unit products).
- Repository rename: the GitHub repository is `tinyunits`, the project is `uom`; the POM's `url`, `scm` and
  `issueManagement` point to `TinyGearsOrg/tinyunits` until then.
- The first release: prepared in milestone 7, executed by the owner ([RELEASING.md](RELEASING.md)).
