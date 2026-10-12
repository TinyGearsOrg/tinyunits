# uom: Design

Status: draft for owner review; milestones 1 to 5 are implemented (section 7). Companion to `REQUIREMENTS.md` (requirement IDs F1 to F24 and
N1 to N8 refer to it). Target API: `javax.measure:unit-api` 2.2. Java 21. Maven, multi-module.

## 1. Decisions

| Topic | Decision |
|-------|----------|
| API | Implement JSR-385 (unit-api 2.2) ourselves; no Indriya dependency (F1, F2). |
| Typed quantities | Own typed interfaces extending the standard ones, plus generated `double` and `BigDecimal` implementations (F3 to F5). |
| Code generation | An internal generator reads data files and writes plain Java sources, which are committed. CI fails if regenerating produces a diff. No annotation processor, no proxies. |
| Catalogs | Text data files are the single source of truth (F15). They generate typed catalogs; parsing them at run time (F17) is not implemented yet (section 5). |
| User extension | Users load their own `.units` files at run time and get `Unit<?>` back (F18; not implemented yet). User-defined quantity types are out of scope. |
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
 data/*.units ────────────┴─> planned: copied into uom as resources, parsed at run time (not done)
```

| Module | Contents | Published |
|--------|----------|-----------|
| `uom-core` | `Dimension`, `Unit`, converters, prefixes, the `.units` parser, shared quantity base classes (`AbstractDoubleQuantity`, `AbstractDecimalQuantity`, `Precision`) | yes |
| `uom` | Package `org.tinygears.uom.quantity`: generated typed interfaces, `double` and `BigDecimal` implementations, and one factory per quantity type. Package `org.tinygears.uom.systems`: generated catalogs and `SystemOfUnits` objects (`SI`, `USCustomary`, `Imperial`, `CGS`, `ESU`, `EMU`, `Gaussian`, `UomSystemOfUnits`, `UnitSystems`), the basic unit and quantity formats, the JSR-385 `ServiceProvider`; `.units` resources are not copied yet | yes |
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
   (D11). Milestone 5 adds `to(SystemOfUnits)` (F8, D5). Units stay `Unit<javax.measure.quantity.Length>`, so our quantities
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

- **`to(SystemOfUnits)` (F8, D5 of milestone 5).** The base classes implement `R to(SystemOfUnits)` once, as
  `to(system.getUnit(quantityType()))`, through the generated hook `protected abstract Class<Q> quantityType()` (the
  standard interface of the type, `Untyped` for untyped quantities). Scale and `MathContext` are kept. A `null` unit from the
  system (and any untyped quantity) throws `UnsupportedOperationException("system N has no unit for type T")`; a unit of
  another dimension gives `UnconvertibleException`; a `null` system gives `NullPointerException`. Every system of this library
  has a preferred unit for all 33 types (D4 fallback: own `@preferred`, else the first extended system's, else SI's). Offsets
  apply: `Temperature.of(100, degC).to(USCustomary.SYSTEM)` is `212.0 °F`, exactly `212 °F` for a decimal. Because `to(Unit)`
  and `to(SystemOfUnits)` overload, `to(null)` with a bare literal is ambiguous (`docs/TODO.md` item 4).
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
    so absurd scales stay fast.
  - **Explicit conversions (D10).** `toDouble()` throws `ArithmeticException` for a value outside the `double` range;
    `toDecimal(mc)` rounds to `mc`, on both implementations. `asType` keeps the receiver's `MathContext` and never changes
    the value.
  - **Factories (D11) and formats (D12).** As in section 4 item 1 and 4; `UomQuantityFormat.format` prints
    `BigDecimal.toString()`, `parse` still returns `double` quantities (decimal-preserving parse: milestone 6).
  - Irrational factors (a pi or root in the converter) are best effort for decimal arithmetic; see
    `docs/superpowers/plans/2026-10-12-uom-m4-follow-ups.md`.
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
`imperial.units`, `cgs.units`, `cgs-esu.units`, `cgs-emu.units` and `cgs-gaussian.units`. Planned: copy the `.units` files into
`uom` as resources for run-time loading (not done yet).

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
- **Run time (F17, F18).** The parser ships in `uom-core`. Through milestone 5 the library does not load `.units`
  resources at run time: `UomUnitFormat` resolves symbols from the generated catalogs and
  evaluates formulas with `FormulaEvaluator`. Loading user `.units` files (F18) is to do.
- **Prefixes.** `@prefixed` generates the curated prefixed units (`SI.KILOMETRE`). At run time
  `UomUnitFormat` also resolves a metric prefix on any unit in `SI.PREFIXABLE`, so `Mm` and `dam`
  parse without a constant; binary prefixes (`MiB`) are not resolved yet. Only units named in a
  `@prefixed` line are in `SI.PREFIXABLE`, so only they take prefixes at run time (`Gm`, but not
  `kmin`). Prefixable symbols of all systems apply (D3). A prefixed power is written with `^`: `km^2` parses as `(km)^2`, while `km²` does not
  parse (`m²` is a symbol of its own, not a prefixable unit).
- **Symbol resolution across systems (D7).** `UomUnitFormat.standard()` resolves labels, then exact symbols across all systems of
  `UnitSystems.all()` (SI first), then a metric prefix on a prefixable unit. Exact symbols win over prefix readings, so `ft` is the
  foot, not the femtotonne; this is the only string that parsed in SI-only mode and now reads differently (the shadow set `{ft}`,
  pinned by a test over all strings of that form). `abH` equals `nH` and `cm³` equals `mL`: a unit with its
  own symbol formats to it (`format(EMU.ABHENRY)` is `abH`), an unnamed equal unit to the first system's symbol (`nH`).
- **Consistency test.** `SiCatalogTest` checks every definition of `si.units` against the
  catalog; `UnitFormatTest` formats and parses every `SI.UNITS` entry back to an equal unit.

## 6. Testing and conformance

- `unit-tck` 2.2 against our provider (N1): module `uom-tck`, 113 of 113 tests pass, 0 skipped
  (`docs/spikes/2026-tck-m3.md`). Own contract tests from the Javadoc: `QuantityContractTest`
  (`uom`) and the `contract` package of `uom-core`.
- Parsed units equal generated constants; catalog round trip for every unit (N6).
- Generator golden tests: sample inputs compile, behave, and report errors with file and line.
- CI drift check: regenerate and fail on a non-empty `git diff`.
- Semantics tests: precision mixing, equality and equivalence, alternate units, offset units,
  roots of non-perfect powers.
- Serialization round trip; module-path and class-path runs; CI on JDK 21 and the current JDK.
- **Serialization (F12).** A unit serializes as decomposition, converter, symbol and name;
  alternate atoms resolve back to the registry on read. A quantity serializes as value, unit,
  scale and `MathContext`.

## 7. Milestones (each releasable as a snapshot)

1. Core and parser, plus the TCK spike.
2. Quantities for `double`: base class, generator for quantity types, a handful of types end
   to end.
3. SI catalog: generator for `si.units`, prefixes, the service provider, basic unit and quantity
   formats (symbols, prefixes, formulas); the TCK runs for real. Done: 33 types, 128 SI catalog
   entries, TCK 113 of 113 tests, 554 tests in `mvn -q -B clean verify`. Follow-ups:
   `docs/superpowers/plans/2026-10-12-uom-m3-follow-ups.md`.
4. `BigDecimal`: decimal base class and the precision rule. Done: `AbstractDecimalQuantity`, `Precision`, 33 `XDecimal`
   classes (133 generated quantity files), 33 types, 128 SI catalog entries, TCK 113 of 113 tests, 708 tests in
   `mvn -q -B clean verify` (core 350, codegen 97, quantities 28, systems 119, tck 113, examples 1). Follow-ups:
   `docs/superpowers/plans/2026-10-12-uom-m4-follow-ups.md`.
5. Other systems: Imperial/US customary, CGS, CGS electromagnetic; `quantity.to(system)`. Done: `USCustomary`, `Imperial`, `CGS`,
   `ESU`, `EMU`, `Gaussian` catalogs (35, 42, 16, 22, 29, 26 distinct units, with `SI` 128), the directives `@system`,
   `@extends`, `@member`, `to(SystemOfUnits)`, 33 types (133 generated quantity files), TCK 113 of 113 tests, 901 tests in
   `mvn -q -B clean verify` (core 430, codegen 162, uom 195, tck 113, examples 1; counted after merging the quantities and
   systems modules into `uom`). Follow-ups:
   `docs/superpowers/plans/2026-10-12-uom-m5-follow-ups.md`.
6. Locale-aware formatting (and a faithful form for derived offset units), serialization, JPMS
   polish, documentation, Maven Central release.

## 8. Open items for the implementation plan

- Resolved in milestone 4: the decimal `equals` scale rule and the decimal `isEquivalentTo` tolerance (section 4). The
  `double` tolerance and the `Scale` rules were resolved earlier.
- Extra quantity types for the untyped poise, stokes and oersted (`DynamicViscosity`, `KinematicViscosity`, `MagneticFieldStrength`, ...): the milestone 5 catalogs leave them untyped (D6).
- Run-time loading of `.units` resources and user files (F17, F18): still open after milestone 5.
- The open owner decisions in `docs/TODO.md` (offset-unit arithmetic, `asType` and alternate
  units, offset-unit products).
- Repository rename: the GitHub repository is `tinyunits`, the project is `uom`.
