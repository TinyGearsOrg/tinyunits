# Milestone 3 follow-ups

Findings from the per-task reviews of milestone 3 that were triaged as follow-up work, not merge blockers. Milestone 3
state: 33 quantity types, 128 SI catalog entries, TCK 113 of 113 tests passing, 554 tests in `mvn -q -B clean verify`
(core 242, codegen 95, quantities 15, systems 86, tck 113). "M4" and later refer to the milestones in `docs/DESIGN.md`
section 7.

## Done by milestone 3 (from the milestone 2 follow-ups)

- Registry isolation: `QuantityTypes` builds factories lazily per type, so one conflicting alternate-unit registration
  fails only that type.
- `·` labels: `·` is a multiplication operator of the formula grammar, so system unit labels such as `kg·m/s^2` parse.
- The Javadoc discrepancy (274.15 versus 276.15 degC) is asserted in `QuantityContractTest`; the `-am` requirement for
  `-pl uom-codegen` is in the README regeneration command.

## Partly done (from the milestone 2 follow-ups)

- Provider priority: `UomServiceProvider.getPriority()` is 100, so `ServiceProvider.current()` selects it over lower-priority
  providers. Remainder, open (core): `UomUnit.asType` (`UomUnit.java:185`) and `AbstractDoubleQuantity` (line 163) still
  call `ServiceProvider.current()` (so does `AbstractDecimalQuantity`), so a provider of higher priority breaks `asType`. Resolve our provider explicitly.
- Typed-quantity coverage gaps of the milestone 2 file. Covered by milestone 3: `Scale.RELATIVE` through `of`
  (`QuantityContractTest:207`), `compareTo` of 1 km and 1000 m (`QuantityContractTest:218`, `GeneratedQuantitiesTest:125`),
  `create(Number, Unit)` with a foreign unit (`GeneratedQuantitiesTest:139`), `isEquivalentTo` through the typed interface
  (`QuantityContractTest:46`, `GeneratedQuantitiesTest:83`), a typed result of quantity times quantity narrowed with
  `asType` (`GeneratedQuantitiesTest:112`). Still open: `to(foreignUnit)` through the typed interface, `compareTo` across
  different types, `create(Number, Unit, Scale)` with `BigDecimal` and `Integer` (covered by milestone 4, `GeneratedDecimalQuantitiesTest`;
  `Integer` only through the 2-argument `create`), `Dimensionless` from `m/m` (covered for decimals by milestone 4,
  `asTypeFromProductsStaysDecimal`), `equals` of
  1 km against 1000 m.

## Open owner decisions

Tracked with options in `docs/TODO.md` (the first three); the code keeps the current behaviour.

- Offset-unit arithmetic (D5): literal Javadoc rule, `2 degC + 1 degC` is 276.15 degC. Asserted as the known discrepancy
  with the Javadoc example 274.15 in the contract tests.
- `asType` and alternate units: structural system-unit comparison, so `1/s` does not narrow to `Frequency`.
- Offset-unit products: `UomUnit.multiply(Unit)`/`divide(Unit)` of an offset unit throw (DESIGN section 3), while the `Unit` Javadoc
  says multiplication of offset units is supported.

## Other open questions

- A unit already prefixed can be prefixed again in the data: `@prefixed km k` gives `kkm`, `@prefixed kg m` gives `mkg`.
  Reject in the parser or document it.
- `UomUnitFormat.format` refuses units of other JSR-385 implementations (ruled: keep, because `UomUnit.equals` needs
  `UomUnit`), so `UomQuantityFormat.format` throws for foreign quantities. Decide whether a best-effort symbol is wanted.
- `ServiceProvider.of(name)` can not find `UomServiceProvider`: unit-api 2.2 takes the name from `@jakarta.inject.Named` or
  `@javax.inject.Named`, else from the provider's `toString()`; we depend on neither inject API and do not override
  `toString`. Pick a name and expose it one of the two ways.
- Prefixed ohm names are `kiloohm` and `megaohm` (prefix name plus unit name); the SI Brochure contracts them to `kilohm` and
  `megohm`. Decide whether `@prefixed` should apply the SI elisions.
- Dimensionless quantities format as `5.0 1` (the symbol of `ONE` is `1`), in both `toString` and `UomQuantityFormat`.
  Decide whether to print `5.0` alone and parse it back.
- Ruled, kept: `UomServiceProvider.getQuantityFactory` throws `IllegalArgumentException` for an unsupported quantity type,
  because unit-api 2.2 specifies no exception. `getUnitFormat(name, variant)` ignores the variant per the `FormatService`
  Javadoc.

## Core defects to fix later (`uom-core`)

- `UomUnit.prefix` builds misleading symbols: `SI.SQUARE_METRE.prefix(KILO)` is labelled `km²` (reads (km)², is 10^3 m²),
  and `kmin`, `kkm` appear. A prefix on a non-prefixable or exponent-bearing symbol must give a null symbol.
- `UomUnit.prefix` symbols can collide with another unit: `SI.DAY.prefix(CENTI)` is `cd` (candela),
  `SI.METRE.prefix(ATTO).prefix(DECI)` is `dam`. `UomUnitFormat.format` guards against these by a verification parse; the
  naming itself is unfixed.
- `getSystemUnit()` on a system unit returns an equal unit with a null name, not `this` as the Javadoc says. Possible fix:
  return `this` when the converter is the identity and the symbol is not null.
- `UomUnit.asType` with an alternate unit (see the decision above); its fix in `AbstractDoubleQuantity.asType` depends on the
  provider throwing `IllegalArgumentException` for an unknown type and looks the factory up twice.
- `UomUnitFormat.label` bookkeeping: the label and alias maps are updated non-atomically.
- `UomSystemOfUnits.getUnit(Class)`: the `@SuppressWarnings("unchecked")` has no rationale comment (the preferred map is not
  type-checked).

## Formatting (`uom-systems`)

- Labels are per `UomUnitFormat` instance; the `UnitFormat.label` Javadoc says system-wide. Document or make static.
- The number grammar of `UomQuantityFormat.parse` is loose: `1f m`, `1d m` and hex floats are accepted. Restrict to a decimal
  pattern, keep `NaN` and `Infinity`.
- `UomQuantityFormat` swallows a failing factory initialisation silently and parses the quantity as untyped. Log it through
  `System.Logger`.
- A label can shadow a prefix-resolved symbol: `label(SECOND, "Gm")` is accepted, then "Gm" parses as seconds. Reject labels
  that `lookup` already resolves.
- `format` does not memoise the verification parse (about 5 µs per call, about 50 µs worst reported); add a per-instance memo
  if it matters.
- An unlabelled shifted or scaled unit prints with the system label: `2 km` built as `METRE.multiply(1000)` prints `2.0 m`
  (`AbstractDoubleQuantity.toString` uses `unit.toString()`); `AbstractDoubleQuantityTest:227` asserts only
  `startsWith("1.5 ")`. Print through `UomUnitFormat` or a label rule, then assert the full text.
- `FormulaEvaluator.MAX_LENGTH` is not exposed; the verification parse in `format` refuses over-long formulas instead.
- Offset-unit and root-index-factor units can not be formatted faithfully (`format` throws `IllegalArgumentException`); a
  faithful form for derived offset units is milestone 6 work. The message names `unit.toString()` (the system label), so
  `DEGREE_CELSIUS.multiply(2)` reads "unit can not be formatted: K" plus factor and offset.
- The foreign-unit refusal message gives no reason; the `UomQuantityFormat` Javadoc does not mention foreign units; no test
  covers that branch. The factor in the message is unabbreviated for huge factors.
- The over-long-formula test checks only the message prefix.
- Whitespace-shaped symbols are emitted verbatim (cosmetic); the evaluator guard wraps all `RuntimeException`s.
- Any metric prefix applies at run time to a prefixable unit, so in an SI-only format `ft`, `at`,
  `ct` and `dt` parse as femto-, atto-, centi- and decitonne. Exact symbols win in a multi-system
  format, but consider restricting `t` to the prefixes listed in its `@prefixed` line. Done for the default format by milestone 5
  (`UomUnitFormat.create()` covers all seven systems; `ft` is the foot); an SI-only format still reads `ft` as femtotonne.
- `UomServiceProvider.toString()` could return the provider name so that `ServiceProvider.of(name)`
  finds it (unit-api 2.2 falls back to `toString()` without `@Named`).

## Generator and data (`uom-codegen`, `data/`)

- Constant names replace non-ASCII letters with `_` (`électron` becomes `_LECTRON`): deliberate, but neither commented nor
  pinned by a test.
- 114 generated lines exceed 120 columns; the wrapping rule is documented only on `wrap`. `CatalogEmitter` hard-codes the
  header and the Javadoc text "The SI units" instead of using `className`/`systemName`. IO error messages print
  `e.getMessage()` only (use `e.toString()`).
- `CatalogEmitterTest` compiles against a stand-in `UomSystemOfUnits` that can drift from the real class; the `uom-systems`
  compile catches it. `--check` flags extra files only when they carry the generated header (intentional, same as `Generate`).
- `DefinitionParser.prefixed` finds the base unit by scanning the definitions list although a symbol map exists.
- `QuantityTypes.factory` is a linear if-chain: fine for 33 types, reconsider at 10 times as many.
- Initialising `SI` loads all 33 factories through `PREFERRED`, so a conflicting alternate registration breaks `SI`.
- `SI.PREFERRED` entries are `XFactory.SYSTEM_UNIT` (equal to, not the same object as, the catalog constants) because no
  `@preferred` line exists yet; `@preferred` has no real-data coverage until milestone 5 and an `@include` override of the SI
  file would need the duplicate-preferred rule revisited. Partly done by milestone 5: the six new files carry `@preferred`
  lines, layered by source file; `SI` still has none.
- The codegen pom's `--add-modules`/`--add-reads java.compiler` test-compile arguments are redundant now that main code
  `requires java.compiler`.
- Open items carried over from milestone 2 that still apply: see `2026-10-12-uom-m2-follow-ups.md`, "Generator".

## Tests and process

- `UnitLabelRoundTripTest` (uom-core): the non-vacuity bound `>= 400` of 500 is loose (all 500 have labels); the generator has no prefix,
  shift, `multiply(Number)` or 5-6 operation chains; `dotIsMultiplication` lacks `2·m`, `1|2·m`, `m·(s)` probes.
- The Task 4 minors (derived-unit dimension table, full expected `@prefixed` symbol set) were assigned to Task 5 and carried
  out there: `SiDimensionTest` and `SiDataTest.prefixedSymbolsArePinned`. Remaining: Gy/Sv dimension mutation is covered by
  one of the two tables.
- No test names the `p` plus `i` reserved-symbol collision of `@prefixed`.
- `QuantityTypes` no-init assertion (`findLoadedClass` null after `types()`) has no positive control and checks loading, not
  initialisation; a generated Javadoc line exceeds 100 characters.
- The no-double-literal regex misses `2e3`, `1e-5`, `.5`, `5.`; `emitsScaledUnits` checks equality, not `hashCode`; no test
  for the `long` boundaries (1/2^63, `Long.MIN_VALUE`).
- No real-catalog test ties constant names to definitions beyond samples; `assertEquals(128, SI.UNITS.size())` is brittle.
- `UomSystemOfUnits`: `isPrefixable(null)` throws `NullPointerException` while `getUnit(null)` and `lookup(null)` are lenient; a
  preferred unit need not be in `units`; a null map value throws in `Map.copyOf` (untested); no test for
  `getUnits(Dimension)` with no match.
- `UomSystemOfUnitsTest`: import order.
- Format tests: lookalike symbols (`μm`, `kkg`, `kkm`, `hz`, `KM`) are untested though correct; the concurrency test never
  runs `label` or prefix parses; the brute-force round trip asserts lower bounds only.
- Provider tests: the service-file test reads `src/main` instead of the class-path resource; nothing asserts that
  `available()` is priority-ordered; `LowPriorityProvider` returns null everywhere.
- Contract tests: `pow(0) == ONE` is unasserted; the comment at `UnitContractTest` line 331 cites DESIGN section 3 but should
  cite `UomUnitTest.systemUnitSymbols`; a test writes the static `TestServiceProvider.SYSTEM_UNITS`.
  The `asType` unknown-type fix depends on the provider throwing `IllegalArgumentException` and looks the factory up twice.
- The provider tests were not seen failing before the implementation (red run skipped).
- Spike doc wording on `BaseUnits` versus `SI` is loose (`docs/spikes/2026-tck-m1.md`).

## Build

- The TCK run prints "SLF4J: No SLF4J providers were found". Add `slf4j-nop` at test scope in `uom-tck`.
- The milestone 3 plan names a nonexistent artifact `maven-surefire-testng`; the real one is
  `org.apache.maven.surefire:surefire-testng` (the pom is right).
