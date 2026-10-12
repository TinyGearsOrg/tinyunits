# uom: Design

Status: draft for owner review; milestones 1 to 3 are implemented (section 7). Companion to `REQUIREMENTS.md` (requirement IDs F1 to F24 and
N1 to N8 refer to it). Target API: `javax.measure:unit-api` 2.2. Java 21. Maven, multi-module.

## 1. Decisions

| Topic | Decision |
|-------|----------|
| API | Implement JSR-385 (unit-api 2.2) ourselves; no Indriya dependency (F1, F2). |
| Typed quantities | Own typed interfaces extending the standard ones, plus generated `double` and `BigDecimal` implementations (F3 to F5). |
| Code generation | An internal generator reads data files and writes plain Java sources, which are committed. CI fails if regenerating produces a diff. No annotation processor, no proxies. |
| Catalogs | Text data files are the single source of truth (F15). They generate typed catalogs and are also parsed at run time (F17). |
| User extension | Users load their own `.units` files at run time and get `Unit<?>` back (F18). User-defined quantity types are out of scope. |
| Precision | The lower precision wins, symmetric (F23). |
| Equality | Strict `equals` and `hashCode`; standard `isEquivalentTo`; `Comparable` by system-unit value, not consistent with `equals` (F20 to F22). |

## 2. Modules and data flow

```
 data/quantity-types.txt ─┐
 data/*.units ────────────┼─> uom-codegen (internal tool, not published)
                          │        │ writes sources, committed to the repo
                          │        v
                          │   uom-quantities   typed interfaces, LengthDouble, LengthDecimal, ...
                          │   uom-systems      generated catalogs: SI, Imperial, CGS, ...
                          │
 data/*.units ────────────┴─> copied into uom-systems as resources, parsed at run time
```

| Module | Contents | Published |
|--------|----------|-----------|
| `uom-core` | `Dimension`, `Unit`, converters, prefixes, the `.units` parser, unit and quantity formatters, shared quantity base classes (`AbstractDoubleQuantity`, `AbstractDecimalQuantity`) | yes |
| `uom-quantities` | Generated typed interfaces, `double` and `BigDecimal` implementations, and one factory per quantity type | yes |
| `uom-systems` | Generated catalogs and `SystemOfUnits` objects (`SI`, `UomSystemOfUnits`), the basic unit and quantity formats, the JSR-385 `ServiceProvider`; `.units` resources are not copied yet | yes |
| `uom-codegen` | The generator program; reuses the parser from `uom-core` | no |
| `uom-tck` | Runs the JSR-385 TCK against `uom-systems`; test only | no |

Dependencies: `uom-systems` -> `uom-quantities` -> `uom-core` -> `unit-api`. `uom-codegen` ->
`uom-core`. Module names: `org.tinygears.uom.core`, `.quantities`, `.systems`; they require
`java.measure`. The provider is declared with `provides` and `META-INF/services`.

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

The hand-written base class `AbstractDoubleQuantity<Q, R extends Q>` (package
`org.tinygears.uom.core.quantity`, `uom-core`) holds all `double` quantity logic: `to`, `add`,
`subtract`, `negate`, `multiply`, `divide`, `inverse`, `asType`, `equals`, `hashCode`,
`compareTo`, `isEquivalentTo`. `R` is the typed interface returned by the operations that keep
the quantity type; the typed interfaces only add covariant return types. The design's strict
overloads such as `Length add(Length)` are not generated: generics already reject quantities of
other types, so they add no safety.

Per quantity type the generator writes three files:

1. **A typed interface** in `org.tinygears.uom.quantity`, e.g. `Length extends
   javax.measure.quantity.Length, Comparable<Quantity<Length>>`, with covariant returns for
   `add(Quantity<Length>)`, `subtract`, `to(Unit)`, `negate()`, `multiply(Number)` and
   `divide(Number)` (all return `Length`) and static factories `Length.of(double, unit)` and
   `Length.of(double, unit, Scale)`. Planned for later milestones: `toDouble()` and
   `toDecimal(MathContext)` (F23, milestone 4), `to(SystemOfUnits)` (F8, milestone 5) and
   `BigDecimal` factories. Units stay `Unit<javax.measure.quantity.Length>`, so our quantities
   work with any JSR-385 code (F4). The shared simple name is a deliberate trade-off: everyday
   code never mentions both types, and declaring a unit variable needs `var` or the fully
   qualified standard type.
2. **`LengthDouble`**, a tiny package-private final class extending the base class with
   `R = Length`; it only supplies the typed `create`. (`LengthDecimal` follows in milestone 4.)
3. **`LengthFactory`**, a final class with a singleton `INSTANCE` that implements
   `QuantityFactory<javax.measure.quantity.Length>` and holds the type's `SYSTEM_UNIT`. It
   rejects a unit that is not a `UomUnit` with `UnconvertibleException` ("foreign unit
   implementation"). One class `QuantityTypes` is also generated: a registry from the standard
   interface class to the factory (`factory(Class)`, `types()`). Factories are built lazily per type, so a
   conflicting registration breaks only its own type. `UomServiceProvider` uses it.

Milestone 3 generates all 33 types of `data/quantity-types.txt` (100 files: three per type plus
`QuantityTypes`). Volume at the end: about 140 committed files of 30 to 60 lines (the 33 standard
types plus `BigDecimal` implementations and any extras the catalogs need).

- **Untyped results.** `multiply(Quantity)`, `divide(Quantity)` and `inverse()` return an
  `UntypedDoubleQuantity` (phantom type `Untyped`) whose unit is built from the system units of
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

- **Precision (F23).** Base classes accept any `Quantity<Q>` whose unit is a `UomUnit` (foreign units are rejected, D8). `Double`
  and `Float` values count as `double`, `BigDecimal` as decimal, integral types as exact (they
  do not lower the precision). The result type follows the lower-precision rule; for `add`,
  `subtract` and the scalar operations the result unit is the left operand's unit (quantity-by-quantity
  `multiply`/`divide` and `inverse` return untyped results in system units); scalar `Number`
  arguments adopt the receiver's precision.
- **Equality (F20).** `equals`: same implementation class, same unit, same scale, same value (`Double.compare`
  for `double`; for decimal, `compareTo == 0` with a defined scale rule to be fixed in the
  plan). `isEquivalentTo`: same value after conversion to the same unit within a tolerance
  (relative epsilon for `double`, the `MathContext` for decimal). For `double` (D4) the values
  are equivalent when equal or when `|a - b| <= 1e-12 * max(|a|, |b|)` after converting `that`
  into this unit (`AbstractDoubleQuantity.EQUIVALENCE_TOLERANCE`); NaN is equivalent to nothing.
  There is no absolute floor, so the test is asymmetric near zero for offset units.
- **Ordering (D1, F22).** Quantities implement `Comparable<Quantity<Q>>`, as the `Quantity`
  Javadoc requires; `compareTo` compares system-unit values with `Double.compare` and is not
  consistent with `equals` (1 km and 1000 m compare equal but are not `equals`).

## 5. Data files, generator and parser

Files live in `data/`: `quantity-types.txt` (quantity types and their system units, built from
the standard's 33 types, no extras so far) and one `.units` file per unit system (`si.units`,
`imperial.units`, `cgs.units`, ... in later milestones). Planned: copy the `.units` files into
`uom-systems` as resources for run-time loading (not done yet).

**Generator (milestones 2 and 3).** `uom-codegen` (not published) reads `data/quantity-types.txt`
and `data/si.units` and writes the sources of section 4 into
`uom-quantities/src/main/java/org/tinygears/uom/quantity` and the catalog class `SI` into
`uom-systems/src/main/java/org/tinygears/uom/systems/SI.java`; they are committed. `Generate` takes
three arguments: `<dataDir> <quantitiesOut> <systemsOut>`.

- `data/si.units` holds the SI definitions (BIPM SI Brochure, 9th edition) and the curated
  `@prefixed` lines; it replaced the milestone 2 bootstrap set `base.units` and yields 111
  catalog entries, declared in `SI` as typed constants (`SI.METRE`, `SI.KILOMETRE`).
- `data/quantity-types.txt` has one `Name formula` per line, e.g. `Speed m / s`. `Name` is a
  `javax.measure.quantity` interface that must exist; the formula is the unscaled, non-offset
  system unit over units of `si.units`. `#` starts a comment. An empty file, a duplicate, an
  unknown name or unit, or a malformed line is an error with file and line (F19).
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

- **Syntax.** `symbol:name formula [Type] # comment`. Space or `*` multiplies, `/` divides;
  juxtaposition binds tighter than `*` and `/`, so `J / kg K` is `J / (kg K)`;
  `^n` is an integer power, `a|b` an exact fraction, decimals are exact, parentheses group,
  `!` marks a base unit, `~` an alternate unit, `=` an affine function, `@include` includes a
  file, `@preferred` sets the preferred unit of a type, `@prefixed <symbol> <prefix>...` generates
  prefixed units from metric prefix symbols. Underscores in names stand for spaces.
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
  and `PREFIXABLE`, and the `SystemOfUnits` object `SI.SYSTEM` (F8, F6). Other systems follow
  in milestone 5. It also emits the quantity sources of section 4.
- **Run time (F17, F18).** The parser ships in `uom-core`. Milestone 3 does not load `.units`
  resources at run time: `UomUnitFormat` resolves symbols from the generated catalogs and
  evaluates formulas with `FormulaEvaluator`. Loading user `.units` files (F18) is to do.
- **Prefixes.** `@prefixed` generates the curated prefixed units (`SI.KILOMETRE`). At run time
  `UomUnitFormat` also resolves a metric prefix on any unit in `SI.PREFIXABLE`, so `km` parses
  even without a constant; binary prefixes (`MiB`) are not resolved yet.
- **Consistency test.** `SiCatalogTest` checks every definition of `si.units` against the
  catalog; `UnitFormatTest` formats and parses every `SI.UNITS` entry back to an equal unit.

## 6. Testing and conformance

- `unit-tck` 2.2 against our provider (N1): module `uom-tck`, 113 of 113 tests pass, 0 skipped
  (`docs/spikes/2026-tck-m3.md`). Own contract tests from the Javadoc: `QuantityContractTest`
  (`uom-systems`) and the `contract` package of `uom-core`.
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
   formats (symbols, prefixes, formulas); the TCK runs for real. Done: 33 types, 111 SI catalog
   entries, TCK 113 of 113 tests, 551 tests in `mvn -q -B clean verify`. Follow-ups:
   `docs/superpowers/plans/2026-10-12-uom-m3-follow-ups.md`.
4. `BigDecimal`: decimal base class and the precision rule.
5. Other systems: Imperial/US customary, CGS, CGS electromagnetic; `quantity.to(system)`.
6. Locale-aware formatting (and a faithful form for derived offset units), serialization, JPMS
   polish, documentation, Maven Central release.

## 8. Open items for the implementation plan

- The decimal `equals` scale rule. (The `double` `isEquivalentTo` tolerance is resolved, see
  section 4. The `Scale` rules are resolved, see section 4.)
- Exact list of extra quantity types the catalogs need (milestone 5).
- Run-time loading of `.units` resources and user files (F17, F18).
- The open owner decisions in `docs/TODO.md` (offset-unit arithmetic, `asType` and alternate
  units, offset-unit products).
- Repository rename: the GitHub repository is `tinyunits`, the project is `uom`.
