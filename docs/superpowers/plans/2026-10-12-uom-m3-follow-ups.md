# Milestone 3 follow-ups

Findings from the per-task reviews of milestone 3 that were triaged as follow-up work, not merge blockers. Milestone 3
state: 33 quantity types, 111 SI catalog entries, TCK 113 of 113 tests passing, 551 tests in `mvn -q -B clean verify`
(core 242, codegen 95, quantities 15, systems 86, tck 113). "M4" and later refer to the milestones in `docs/DESIGN.md`
section 7.

## Done by milestone 3 (from the milestone 2 follow-ups)

- Registry isolation: `QuantityTypes` builds factories lazily per type, so one conflicting alternate-unit registration
  fails only that type.
- Provider priority: `UomServiceProvider.getPriority()` is 100, so `ServiceProvider.current()` selects it over lower-priority
  providers. `UomUnit.asType` still calls `current()`; a provider of higher priority on the class path still breaks it.
- `·` labels: `·` is a multiplication operator of the formula grammar, so system unit labels such as `kg·m/s^2` parse.
- The Javadoc discrepancy (274.15 versus 276.15 degC) is asserted in `QuantityContractTest`; the `-am` requirement for
  `-pl uom-codegen` is in the README regeneration command.

## Open owner decisions

The first three are tracked with options in `docs/TODO.md`; the code keeps the current behaviour.

- Offset-unit arithmetic (D5): literal Javadoc rule, `2 degC + 1 degC` is 276.15 degC. Asserted as the known discrepancy
  with the Javadoc example 274.15 in the contract tests.
- `asType` and alternate units: structural system-unit comparison, so `1/s` does not narrow to `Frequency`.
- Offset-unit products: `UomUnit.multiply(Unit)`/`divide(Unit)` of an offset unit throw (DESIGN section 3), while the `Unit` Javadoc
  says multiplication of offset units is supported.
- A unit already prefixed can be prefixed again in the data: `@prefixed km k` gives `kkm`, `@prefixed kg m` gives `mkg`.
  Reject in the parser or document it.
- `UomUnitFormat.format` refuses units of other JSR-385 implementations (`UomUnit.equals` needs `UomUnit`), so
  `UomQuantityFormat.format` throws for foreign quantities. Decide whether a best-effort symbol is wanted.

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

## Formatting (`uom-systems`)

- Labels are per `UomUnitFormat` instance; the `UnitFormat.label` Javadoc says system-wide. Document or make static.
- The number grammar of `UomQuantityFormat.parse` is loose: `1f m`, `1d m` and hex floats are accepted. Restrict to a decimal
  pattern, keep `NaN` and `Infinity`.
- `UomQuantityFormat` swallows a failing factory initialisation silently and parses the quantity as untyped. Log it through
  `System.Logger`.
- Offset-unit and root-index-factor units can not be formatted faithfully (`format` throws `IllegalArgumentException`); a
  faithful form for derived offset units is milestone 6 work. The message names `unit.toString()` (the system label), so
  `DEGREE_CELSIUS.multiply(2)` reads "unit can not be formatted: K" plus factor and offset.
- The foreign-unit refusal message gives no reason; the `UomQuantityFormat` Javadoc does not mention foreign units; no test
  covers that branch. The factor in the message is unabbreviated for huge factors.
- Whitespace-shaped symbols are emitted verbatim (cosmetic); the evaluator guard wraps all `RuntimeException`s.

## Generator and data (`uom-codegen`, `data/`)

- Constant names replace non-ASCII letters with `_` (`électron` becomes `_LECTRON`): deliberate, but neither commented nor
  pinned by a test.
- 114 generated lines exceed 120 columns; the wrapping rule is documented only on `wrap`. `CatalogEmitter` hard-codes the
  header and the Javadoc text "The SI units" instead of using `className`/`systemName`. IO error messages print
  `e.getMessage()` only (use `e.toString()`).
- Unit name `degree_celsius` should be `degree_Celsius` (SI spelling); the constant name stays.
- Initialising `SI` loads all 33 factories through `PREFERRED`, so a conflicting alternate registration breaks `SI`.
- `SI.PREFERRED` entries are `XFactory.SYSTEM_UNIT` (equal to, not the same object as, the catalog constants) because no
  `@preferred` line exists yet; `@preferred` has no real-data coverage until milestone 5 and an `@include` override of the SI
  file would need the duplicate-preferred rule revisited.
- The codegen pom's `--add-modules`/`--add-reads java.compiler` test-compile arguments are redundant now that main code
  `requires java.compiler`.
- Open items carried over from milestone 2 that still apply: see `2026-10-12-uom-m2-follow-ups.md`, "Generator".

## Tests and process

- `UnitLabelRoundTripTest` (uom-core): the non-vacuity bound `>= 400` of 500 is loose (all 500 have labels); the generator has no prefix,
  shift, `multiply(Number)` or 5-6 operation chains; `dotIsMultiplication` lacks `2·m`, `1|2·m`, `m·(s)` probes.
- No test pins the base decomposition of the derived SI units (T as Wb/m, Pa as N/m^3, lx as cd/m^2, kat as mol s would stay
  green because `si.units` and `quantity-types.txt` share symbols); the `@prefixed` list is sampled only (dropping `G` from
  `@prefixed Pa h k M G` passes); `SiDataTest` skips prefixed expansions; Gy/Sv mutations are covered by one of two tables.
- No test names the `p` plus `i` reserved-symbol collision of `@prefixed`.
- `QuantityTypes` no-init assertion (`findLoadedClass` null after `types()`) has no positive control and checks loading, not
  initialisation; a generated Javadoc line exceeds 100 characters.
- The no-double-literal regex misses `2e3`, `1e-5`, `.5`, `5.`; `emitsScaledUnits` checks equality, not `hashCode`; no test
  for the `long` boundaries (1/2^63, `Long.MIN_VALUE`).
- No real-catalog test ties constant names to definitions beyond samples; `assertEquals(111, SI.UNITS.size())` is brittle.
- `UomSystemOfUnits`: `isPrefixable(null)` throws `NullPointerException` while `getUnit(null)` and `lookup(null)` are lenient; a
  preferred unit need not be in `units`; a null map value throws in `Map.copyOf` (untested); no test for
  `getUnits(Dimension)` with no match.
- Format tests: lookalike symbols (`μm`, `kkg`, `kkm`, `hz`, `KM`) are untested though correct; the concurrency test never
  runs `label` or prefix parses; the brute-force round trip asserts lower bounds only.
- Provider tests: the service-file test reads `src/main` instead of the class-path resource; nothing asserts that
  `available()` is priority-ordered; `LowPriorityProvider` returns null everywhere.
- Contract tests: `pow(0) == ONE` is unasserted; the comment at `UnitContractTest` line 331 cites DESIGN section 3 but should
  cite `UomUnitTest.systemUnitSymbols`; a test writes the static `TestServiceProvider.SYSTEM_UNITS`.
- The provider tests were not seen failing before the implementation (red run skipped).
- Spike doc wording on `BaseUnits` versus `SI` is loose (`docs/spikes/2026-tck-m1.md`).

## Build

- The TCK run prints "SLF4J: No SLF4J providers were found". Add `slf4j-nop` at test scope in `uom-tck`.
- The milestone 3 plan names a nonexistent artifact `maven-surefire-testng`; the real one is
  `org.apache.maven.surefire:surefire-testng` (the pom is right).
