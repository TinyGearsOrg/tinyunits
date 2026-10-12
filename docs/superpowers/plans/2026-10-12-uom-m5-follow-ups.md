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
- M3, `ft`, `at`, `ct` and `dt` read as prefixed tonnes: `UomUnitFormat.standard()` now covers all seven systems and exact
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
- `abH` equals SI `nH` and `cm³` equals `mL`. A unit with its own symbol formats to it (`format(EMU.ABHENRY)` is `abH`,
  `format(CGS.CUBIC_CENTIMETRE)` is `cm³`); an unnamed equal unit (`SI.HENRY.divide(1e9)`) formats to the first system's symbol
  in `UnitSystems.all()` order (`nH`), pinned by the equal-unit format order tests.
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
- Data gap (Task 7 review): these units are pinned independently only by the data tests (`UsCustomaryDataTest`,
  `CgsDataTest`), not by `SystemConversionTest`: INCH, YARD, NAUTICAL_MILE, FATHOM, SQUARE_INCH, SQUARE_FOOT, CUBIC_INCH,
  CUBIC_FOOT, QUART_US, PINT_US, CUP_US, FLUID_OUNCE_US, TABLESPOON_US, TEASPOON_US, OUNCE, GRAIN, TON_US, POUND_FORCE,
  BRITISH_THERMAL_UNIT_TH, MILE_PER_HOUR, STONE, TON_UK, QUART_UK, PINT_UK, GILL_UK, FLUID_OUNCE_UK, GALILEO, POISE, STOKES,
  CENTIPOISE, MILLIPOISE, CENTISTOKES, MILLIGALILEO, MICROGALILEO, STATOHM, STATFARAD, STATHENRY, ABOHM, ABFARAD, GILBERT,
  OERSTED, MILLIGAUSS, MICROGAUSS (ABHENRY and NANOGAUSS also by `UnitFormatSystemsTest`). All data mutations were killed.
- Fallback preferred units are not members: `USCustomary.SYSTEM.getUnit(Time.class)` is `s`, but `getUnit("s")` is null
  and `getUnits()` lacks `s` (123 (system, type) pairs fall back to a unit outside the system's `UNITS`; SI itself for
  `m/s^2`, `1` and `m/s`).
- The `PREFERRED` swaps of CGS Time and Length, Gaussian Time, Imperial Volume and Length, `USCustomary` Temperature and
  ESU Capacitance escape `SystemConversionTest` (`SystemsCatalogTest` and `ToSystemTest` catch them).
- The cross-system parity test of double against decimal cannot detect a wrong factor (both share it); the named-value
  tests do.
- Round trip through SI for every type and system uses a tolerance of 7.25e-32 (observed maximum 1e-33) instead of the 1e-32
  asked for; accepted.

## Generator (`uom-codegen`)

- Generated lines exceed 120 columns: the header comment and the class Javadoc (for example `Gaussian.java` lines 2 and
  6), as in `SI`; the wrapping rule is unchanged (milestone 3 item).
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
- Task 5 user-visible change: `label(unit, "ft")` (or any exact symbol of the new systems) is now rejected; the SI-only
  format accepted it.
- Pre-flight note: constant names in the plan text (for example `Imperial.GALLON_UK`) are examples; the tests derive the
  expected constants from the data.
- Final review, open: `UNLIMITED` is not exact for some terminating results where the converter decides by coefficient
  (`BTU_th`, denominator 9: `-1553526887993091996259.1645232` gives `...093.214515390` instead of `...093.21451539011392`).
  It predates the milestone; the F23 wording needs to say so or the converter needs fixing.
- Final review, observations: only U+00B5 and U+03A9 parse as micro and ohm (U+03BC and U+2126 are rejected, as for SI); any
  metric prefix works on a prefixable unit (`kP`, `aSt`); the symbol choices `Gb`, `BTU_IT` and `fathom` are the data
  author's.

## Build

- The TCK run still prints "SLF4J: No SLF4J providers were found" (milestone 3 item: add `slf4j-nop` at test scope).
