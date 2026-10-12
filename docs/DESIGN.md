# uom: Design

Status: draft for owner review. Companion to `REQUIREMENTS.md` (requirement IDs F1 to F23 and
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
| Equality | Strict `equals` and `hashCode`; standard `isEquivalentTo`; no `Comparable` (F20 to F22). |

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
| `uom-core` | `Dimension`, `Unit`, converters, prefixes, the `.units` parser, unit and quantity formatters, `QuantityComparator`, shared quantity base classes (`AbstractDoubleQuantity`, `AbstractDecimalQuantity`) | yes |
| `uom-quantities` | Generated typed interfaces, `double` and `BigDecimal` implementations, and one factory per quantity type | yes |
| `uom-systems` | `.units` resources, generated catalogs and `SystemOfUnits` objects, the JSR-385 `ServiceProvider` | yes |
| `uom-codegen` | The generator program; reuses the parser from `uom-core` | no |

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
    means the converter between the two units is the identity.
  - `alternate(String)` creates an atom. The standard requires an exception when the symbol
    is already bound to a different unit, so alternate symbols use one small static registry.
    This is the only global state in the units core.
- **Converters.** Identity, affine (`factor * x + offset`) and concatenation. Factors are exact
  rationals, optionally times a power of pi, so degrees and gradians are exact (N2). Each
  factor also caches a rounded `double` and a `BigDecimal`, computed once. Additions to the
  standard interface: `convert(BigDecimal, MathContext)` (F23). Units with an offset can not be
  multiplied or divided and throw.
- **Powers and roots.** `pow(n)` raises factor and exponents. `root(n)` is exact when the
  factor is a perfect nth power, otherwise it uses a numeric converter.
- **Typing.** `asType(Class<T>)` compares the unit's system unit with
  `ServiceProvider.current().getQuantityFactory(type).getSystemUnit()`. This distinguishes
  hertz from becquerel. `uom-core` holds no table of quantity types.
- **Caching.** Each unit caches its converter to a given target unit in a small per-unit map
  (N3). Units created on the fly take their cache with them, so no weak references.
- **Out of scope:** non-affine converters such as logarithmic units.

## 4. Quantities and generated code

Per quantity type the generator writes:

1. **A typed interface** in `org.tinygears.uom.quantity`, e.g. `Length extends
   javax.measure.quantity.Length`, adding: strict overloads `Length add(Length)` and
   `subtract(Length)`; covariant returns for the standard methods (`add(Quantity<Length>)`,
   `to(Unit)`, `negate()`, `multiply(Number)`, `divide(Number)` all return `Length`);
   `toDouble()` and `toDecimal(MathContext)` (F23); `to(SystemOfUnits)` (F8); static factories
   `Length.of(double, unit)` and `Length.of(BigDecimal, unit)`. Units stay
   `Unit<javax.measure.quantity.Length>`, so our quantities work with any JSR-385 code (F4).
   The shared simple name is a deliberate trade-off: everyday code never mentions both types,
   and declaring a unit variable needs `var` or the fully qualified standard type.
2. **`LengthDouble` and `LengthDecimal`**, tiny final classes. All logic lives in the
   hand-written base classes: arithmetic, conversion, `equals` and `hashCode`, `Serializable`,
   and the precision rule. The generated class supplies typed signatures and a reference to
   its type's factory.
3. **A factory** per type, which is also its `QuantityFactory` for the provider. The base
   classes use it to build a result of the right type and precision.

Volume: about 140 committed files of 30 to 60 lines (34 standard types plus the extras the
catalogs need, such as viscosity).

- **Precision (F23).** Base classes accept any `Quantity<Q>`, including foreign ones. `Double`
  and `Float` values count as `double`, `BigDecimal` as decimal, integral types as exact (they
  do not lower the precision). The result type follows the lower-precision rule, the result
  unit is the left operand's unit, scalar `Number` arguments adopt the receiver's precision.
- **Equality (F20).** `equals`: same implementation type, same unit, same value (`Double.compare`
  for `double`; for decimal, `compareTo == 0` with a defined scale rule to be fixed in the
  plan). `isEquivalentTo`: same value after conversion to the same unit within a tolerance
  (relative epsilon for `double`, the `MathContext` for decimal; exact values to be fixed in
  the plan).
- **Ordering (F22).** `QuantityComparator` converts to a common unit under the same precision
  rule; documented as not consistent with `equals`.
- **Scale.** Every quantity carries a `Scale` (default absolute). The detailed rules follow the
  `Quantity` Javadoc and are written in the plan.

## 5. Data files, generator and parser

Files live in `data/`: `quantity-types.txt` (quantity types and their system units, built from
the standard's 34 types plus extras) and one `.units` file per unit system (`si.units`,
`imperial.units`, `cgs.units`, ...). The `.units` files are also copied into `uom-systems` as
resources.

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
```

- **Syntax.** `symbol:name formula [Type] # comment`. Space or `*` multiplies, `/` divides,
  `^n` is an integer power, `a|b` an exact fraction, decimals are exact, parentheses group,
  `!` marks a base unit, `~` an alternate unit, `=` an affine function, `@include` includes a
  file, `@preferred` sets the preferred unit of a type. Underscores in names stand for spaces.
- **Type resolution (F16).** A trailing `[Type]` states the quantity type. Otherwise the
  generator looks up the unit's system-unit decomposition in `quantity-types.txt`. Hertz and
  becquerel differ by their alternate atoms; energy and torque share a decomposition and need
  `[Energy]` or `[Torque]`. An unresolved ambiguity is a build error.
- **Evaluation.** One pass; a definition must precede its use, so circular definitions are
  impossible. Every error carries file and line (F19): unknown unit, duplicate symbol,
  dimension mismatch, unparsable formula. Symbols are unique across all files; clashes such as
  gallon use distinct symbols (`gal_US`, `gal_UK`).
- **Output.** The generator evaluates each formula to a decomposition, an exact rational
  factor and an offset, and emits plain constructor calls. Each `X.units` becomes a class `X`
  with typed constants (`Unit<Length> INCH`) and a `SystemOfUnits` object (F8, F6). It also
  emits the quantity sources of section 4.
- **Run time (F17, F18).** The same parser ships in `uom-core`, loads the resources on first
  use, and backs `UnitFormat` and `QuantityFormat`. Users load their own `.units` files and
  get `Unit<?>`, typed with `asType`.
- **Prefixes.** The parser resolves metric and binary prefixes dynamically (`km`, `MiB`) using
  the standard's prefix enums. Which prefixed constants are generated (e.g. `SI.KILOMETRE`)
  needs a directive in the data; the form is decided in the plan.
- **Consistency test.** Every parsed unit equals its generated constant.

## 6. Testing and conformance

- `unit-tck` 2.2 against our provider (N1). A spike in milestone 1 checks that the TCK runs
  against a non-Indriya implementation; if not, own contract tests from the Javadoc.
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
3. SI catalog: generator for `si.units`, prefixes, the service provider; the TCK runs for real.
4. `BigDecimal`: decimal base class and the precision rule.
5. Other systems: Imperial/US customary, CGS, CGS electromagnetic; `quantity.to(system)`.
6. Formatting, serialization, JPMS, documentation, Maven Central release.

## 8. Open items for the implementation plan

- `isEquivalentTo` tolerance values and the decimal `equals` scale rule.
- Detailed `Scale` rules for offset units.
- The prefix directive in the data files.
- Result of the TCK spike (milestone 1).
- Exact list of extra quantity types the catalogs need.
- Repository rename: the GitHub repository is `tinyunits`, the project is `uom`.
