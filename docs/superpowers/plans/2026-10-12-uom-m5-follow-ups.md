# Milestone 5 follow-ups

Findings from the per-task reviews of milestone 5 (other unit systems and `quantity.to(system)`) that were triaged as
follow-up work, not merge blockers. Milestone 5 state: seven systems (`SI` 128, `USCustomary` 35, `Imperial` 42, `CGS` 16,
`ESU` 22, `EMU` 29, `Gaussian` 26 distinct units), 33 quantity types (133 generated quantity files), TCK 113 of 113 tests
passing, 869 tests in `mvn -q -B clean verify` (core 430, codegen 138, quantities 29, systems 158, tck 113, examples 1).
"D1" to "D8" refer to the decisions of `2026-10-12-uom-m5-systems.md`; "M6" refers to the milestones in `docs/DESIGN.md`
section 7.

## Done by milestone 5 (from earlier follow-ups)

- M3, DESIGN section 8 "exact list of extra quantity types the catalogs need": decided in D6. None are added; poise, stokes
  and oersted are untyped units (see below), the gilbert is typed `ElectricCurrent`.
- M3, `ft`, `at`, `ct` and `dt` read as prefixed tonnes: `UomUnitFormat.create()` now covers all seven systems and exact
  symbols win over prefix readings, so `ft` is the foot. The only string that changes from the SI-only reading is `ft`
  (the shadow set, pinned in the format tests); a format over `SI` alone still reads `ft` as femtotonne.
- M3, `@preferred` had no real-data coverage and an `@include` override needed the duplicate-preferred rule: the six new
  files carry `@preferred` lines, layered by source file (own lines first, then those inherited through `@extends`; files
  that are only `@include`d contribute none). `SI` still has no `@preferred` line and keeps the factory system units.
- M3, F6 and F8 status: the Imperial, US customary, CGS and CGS electromagnetic catalogs and `quantity.to(system)` exist.
- M3 and M4 "provider priority", `asType` and the other core items are not touched by this milestone and stay open in their
  files.

## Open owner decisions

Unchanged, tracked with options in `docs/TODO.md`. Two more are visible through `to(system)`.

- Offset-unit arithmetic (D5 of milestone 2), `asType` and alternate units, offset-unit products: unchanged.
- `RELATIVE` temperature through `to(system)`: the conversion applies the offset (`Scale.RELATIVE` is kept, but a delta
  is not scaled as a difference), the same literal rule as for arithmetic; it follows the decision on offset
  arithmetic.
- `to(null)` is ambiguous at source level (`Unit` or `SystemOfUnits` overload): a bare `null` literal no longer compiles.
  Accepted in Task 6 because D5 mandates the name `to(SystemOfUnits)` and the module dependency direction rules out a
  common type. Owner choice: rename the system overload (for example `toSystem`) if the ambiguity matters.
- A unit of another quantity type with the same dimension, returned by a foreign `SystemOfUnits`, is accepted silently by
  `to(system)` (only a different dimension gives `UnconvertibleException`).

## Systems and data

- Run-time loading of `.units` resources and user files (F17, F18) is still open; the catalogs are generated only.
- A label that equals a prefix reading of another unit (`label(SECOND, "Gm")`) is accepted; it predates this milestone but
  is widened by the new prefixable units `G`, `Gal`, `P` and `St`. Reject labels that `lookup` already resolves.
- Equal units of different systems format to the first system's symbol: `abH` equals SI `nH`, `cm³` equals `mL`
  (`UnitSystems.all()` order decides; pinned by the equal-unit format order tests).
- Poise, stokes and oersted are untyped `UomUnit<?>` constants. Extra quantity types (`DynamicViscosity`,
  `KinematicViscosity`, `MagneticFieldStrength`, ...) would type them and extend `quantity-types.txt` (follow-up to D6).
- Gaussian prefixed gauss: members keep the owning system's prefixable status, so `G` is prefixable in `Gaussian` and
  `mG`, `µG`, `nG` resolve at run time to the units of EMU's constants; no constants `mG` and the like exist in `Gaussian`
  (D3).
- `CGS.PREFIXABLE` includes `g` and `s` through the members `cm g s` (intended): `kg` and `ms` resolve in CGS terms too.
- The `UNITS` of a child system flatten its parents' units including their `@member` units (D3): `Imperial.UNITS` holds the
  `USCustomary` constants and `Gaussian.UNITS` the `EMU` ones; equal units collapse to the first listed.
- The fallback of an unlisted preferred type to SI (D4) relies on `si.units` being first in `Generate.CATALOGS`.
- A `.units` file missing from `Generate.CATALOGS` is detected by the data-versus-list test, not by the generator (accepted
  in Task 4, Review Focus 5).
- `@member` of a unit defined in the root file is an error (ruling, Task 1): members are units of included files only.
- A nested file with two `@system` lines is not rejected when included (only when parsed as the root); no
  `SystemDirective` record exists, `Definitions.system` is a plain `String`.
- Imperial redundantly states `@include si.units` (inherited through `@extends`); harmless, the Task 2 report's reason for
  keeping it was wrong.
- The Task 2 data test reads US units only through the Imperial parse, and its symbol-uniqueness check cannot fail (the
  parser already rejects duplicates).
- Data gap: only 25 units are pinned by `SystemConversionTest`; the others (from `INCH` to `OERSTED` in data order) are
  pinned independently only by the data tests (`UsCustomaryDataTest`, `CgsDataTest`) and the reviewers' re-derivation
  (68 of 68 data mutations killed repo-wide). No cross-system numeric test covers them.
- The `PREFERRED` swaps of CGS Time and Length, Gaussian Time, Imperial Volume and Length, `USCustomary` Temperature and
  ESU Capacitance escape `SystemConversionTest` (`SystemsCatalogTest` and `ToSystemTest` catch them).
- The cross-system parity test of double against decimal cannot detect a wrong factor (both share it); the named-value
  tests do.
- Round trip through SI for every type and system uses a tolerance of 7.25e-32 (observed maximum 1e-33) instead of the 1e-32
  asked for; accepted.

## Generator (`uom-codegen`)

- Generated lines exceed 120 columns (long `@prefixed` and `UNITS` lines, as in `SI`); the wrapping rule is
  unchanged (milestone 3 item).
- `to(system)` interface Javadoc in the 33 generated typed interfaces uses plain text for the exceptions and omits the
  `NullPointerException`; fix in `SourceEmitter` and regenerate.
- Task 4 fixed an existing emitter bug (empty `PREFIXABLE` emitted `Set.of(())`); the new samples cover SI fallback, the
  first extended parent and members from included-only files.

## Formats (`uom-systems`)

- The milestone 3 format items (labels per instance, `kiloohm` names, `5.0 1` for dimensionless, number grammar of the
  quantity parse) stay open as they are.
- `UomQuantityFormat.parse` still returns `double` quantities (decimal-preserving parse: M6).

## Tests and process

- Pinned counts: the format and catalog tests pin unit counts and shadow-enumeration sizes (Task 5 minor round reduced
  them); they need an update with every added unit.
- Task 1 ruling: published `uom-core` must not require `java.compiler`; the identifier check (including restricted names
  `var`, `yield`, `record`, `sealed`, `permits`) is hand-written. `uom-codegen` still requires it (its `module-info`); the `--add-reads` test-compile arguments of its pom are still redundant (milestone 3 item).
- Task 3 trailer fix: the commit trailer named a wrong model and the local commit was amended before review; commit trailer
  text comes from the session attribution reminder, check it before the first commit.
- Task 6 changes a base-class hook (`quantityType()`) and regenerates 99 files in one commit; the build is green at the end
  of each task (no red window like milestone 4).

## Build

- The TCK run still prints "SLF4J: No SLF4J providers were found" (milestone 3 item: add `slf4j-nop` at test scope).
