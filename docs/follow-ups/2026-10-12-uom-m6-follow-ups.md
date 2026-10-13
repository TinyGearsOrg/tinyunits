# Milestone 6 follow-ups

Findings from the per-task reviews of milestone 6 (serialization, locale-aware formats, faithful offset-unit form,
decimal-preserving parse, user units at run time) that were triaged as follow-up work, not merge blockers. Milestone 6
state: seven systems (`SI` 128, `USCustomary` 35, `Imperial` 42, `CGS` 16, `ESU` 22, `EMU` 29, `Gaussian` 26 distinct
units), 33 quantity types (133 generated quantity files), TCK 113 of 113 tests passing, 1070 tests in
`mvn -q -B clean verify` (core 506, codegen 162, uom 288, tck 113, examples 1). "D1" to "D6" refer to the decisions of
`2026-10-12-uom-m6-serialization-formats-user-units.md` (with the amendments recorded there); "M7" refers to the
milestones in `docs/DESIGN.md` section 7.

## Done by milestone 6 (from earlier follow-ups)

- M3, offset-unit units could not be formatted: an offset unit with a positive factor without a root formats as a
  shifted formula (`K + 5`, `2·K + 136.575`, Task 4); negative and root factors, irrational shifts and over-long formulas
  are still refused.
- M4 and M5, `UomQuantityFormat.parse` always returned `double`: `withDecimals(MathContext)` parses exact decimal
  quantities (`1.50 km` keeps scale 2, Task 6). The default format still parses to `double`; a decimal outside the
  `double` range now round-trips through a `withDecimals` format.
- M4, pi computed by Machin's series (O(n^2), more than 30 s at 20k digits): replaced by the Chudnovsky series with binary
  splitting (`Factor`, commit `11a6cd3`); 200100 digits take about 1.3 s.
- M5, run-time loading of user `.units` files (F17, F18): `UomSystemOfUnits.load`, `UomUnitFormat.with` and
  `UomUnitFormat.resolve` (Task 7). No `.units` resources are shipped.
- F12: units and quantities are `Serializable` (Tasks 1 and 2).
- The pending owner question of Task 1 (share system-unit instances between the `SI` constants and the factories) was
  done by the merge-modules change before Task 2 (`02173f8`): `SI.NEWTON == ForceFactory.SYSTEM_UNIT`, and `SI.METRE`
  is `LengthFactory.SYSTEM_UNIT` (DESIGN section 5, "Shared instances").

## Open owner decisions

Unchanged, tracked with options in `docs/TODO.md` (offset-unit arithmetic, `asType` and alternate units, offset-unit
products, `to(null)`).

- Owner decision (milestone 6): the typed quantity interfaces keep the simple names of the standard ones (no `I` prefix).
- Rulings of milestone 6 (recorded in D1 to D5): proxies write values unshared instead of interning strings, so the bytes
  depend on values only; at most 1000 atoms or conversion steps per proxy; the stored factor and offset of a converter
  are trusted and its steps checked against them (exactly when they fold exactly, else by a decimal fold whose
  tolerance is relative to the largest term); a wrong element type in a stream is an `InvalidObjectException`, not a
  `ClassCastException`; offset formats put spaces around the sign (`K + 5`) and print the shorter of decimal and
  fraction (ties: decimal); the ROOT decimal parse goes through the locale normalisation, so it accepts ASCII digits
  only.
- Owner-requested follow-ups, after milestone 6, in one small branch:
  - (a) Emit `@prefixed` catalog units as `<base constant>.prefix(MetricPrefix.X).named(...)` (sharing the base constant)
    instead of the decomposed `transform(Converters.affine(...))`.
  - (b) Generate `static X from(Quantity<?> quantity)` on each of the 33 typed interfaces: `asType` with the standard
    class, then a cast to our interface; the same exceptions as `asType`, plus `ClassCastException` for a quantity of a
    foreign provider (documented). Tests for 33 types x {double, decimal} and the error cases. README note: declare
    units with `Unit<?>` or `var`, and why (the simple names clash with the standard types).
  - (c) Binary prefixes (`BinaryPrefix`: `MiB`) are not resolved by the unit format or `@prefixed`, and no byte or bit
    units exist. `UomUnit.prefix(BinaryPrefix.KIBI)` works on a unit.
- The Acceleration system unit's symbol is `m/s^2`; `m/s²` would need superscript exponents in the formula evaluator.

## Serialization (`uom-core`, D1)

- Untrusted streams need an `ObjectInputFilter`. The JDK allocates arrays and strings found in a stream (a `TC_ARRAY` of
  length 2^31-1, a `TC_LONGSTRING`) before any of our checks run, so a forged stream can exhaust a small heap; a JDK type
  check can also throw `ClassCastException` or `ArrayStoreException` first. Example filter:
  `maxarray=65536;maxbytes=1048576;maxdepth=20;maxrefs=10000;org.tinygears.uom.core.**;org.tinygears.uom.quantity.*;javax.measure.**;java.lang.*;java.math.*;!*`
  (as `jdk.serialFilter` or per stream); our own payloads stay far below it (a unit about 0.5 KB and 6 levels deep; the
  largest decimal value 64 KiB, carried as bytes, not as an array object). Documented in the README and used by
  `Examples.serialization()`. The Task 2 report's filter lacked `org.tinygears.uom.quantity.*`: the filter also checks
  the class a proxy resolves to (`LengthDecimal`), so typed quantities were rejected (found in Task 8 on JDK 25).
- The decimal fold check of a multi-step converter costs about 0.75 s of CPU per MB of forged stream (about 100x
  amplification): `maxbytes`.
- Deep chains of alternate-atom definitions overflow the stack in `ObjectInputStream` at a depth of about 1000:
  `maxdepth`.
- Strings (symbols, names, kind, type, scale, rounding mode) are read with `readObject` and are unbounded except by the
  filter.
- Re-serializing an unregistered atom that was read drops its definition (reading never registers, D1 amendment): a
  later reader can only check its symbol and dimension.
- Definition-less atoms (base atoms, re-serialized unregistered atoms) are matched on symbol and dimension only: a weaker
  conflict check, harmless because `UnitAtom` equality is symbol and dimension.
- Untyped quantity reads still depend on SI being loaded: typed reads resolve the provider first (which registers the
  type's alternate atoms), but a forged `Hz` atom inside an untyped `Hz·m` is accepted in a fresh JVM that has not
  touched `SI`.
- A user subclass of the public abstract quantity classes, or a quantity read under another top-priority provider, reads
  back as the provider's class (documented on both base classes).
- `QuantityProxy` repeats a typed `read` and a bounded byte read of `SerialForm`, because `SerialForm` is package-private
  in an exported package. Option: move both into a non-exported `org.tinygears.uom.core.internal`.
- A multi-step converter whose inexact fold has a part over 4096 bits skips the decimal fold check, so the stored
  factor and offset stand unchecked: a forged identity converter with huge irrational step parts is accepted, and its
  `getConversionSteps()` then contradicts `convert()`. Harmless for conversion (the stored values are used), but the
  steps can be inconsistent.
- Typed quantity reads accept any unit whose dimension matches the factory's system unit (predates; the same rule as
  the factories).
- No dimension exponent range check: the public API builds any `int` exponent, so the proxy checks null and length 7
  only. The zero-factor check lives only in the unit proxy; `UomUnit.of` still accepts a zero-factor converter.
- A forged pi exponent or root index near `Integer.MAX_VALUE` costs what `Factor.of` plus `doubleValue()` already cost;
  only the huge-numerator case is under a time-bounded test.
- NaN payloads survive (raw bits); this relies on the JVM keeping quiet-NaN bits (HotSpot on x86-64 and aarch64; a test
  asserts it).
- The serialized form is pinned by golden files (12 units in `uom-core`, 8 quantities in `uom`) and is not promised stable
  before 1.0.

## Decimal precision (`uom-core`)

- Pre-existing: `Factor.decimalValue` is not correctly rounded at near-ties. It uses 5 fixed guard digits under
  `HALF_UP` and never extends them. Examples: pi at p=761 with `HALF_UP` or `HALF_EVEN` (the Feynman point); pi at
  p=762 and 763 with `DOWN` or `FLOOR`; the degree, grad and mu0 factors at 762 to 763; sqrt(2) at p=2706. With
  `UNNECESSARY` it returns a value there instead of throwing. Fix: Ziv-style evaluation, retrying with more guard
  digits until the rounding decision is unambiguous, then apply the mode (throw for `UNNECESSARY` on an irrational).
- Guard digits grow with the value's precision: multiplying a 3,000,000-digit value takes 28 s (through deserialization
  the worst case is 1.6 s, because the stream bounds the value at 64 KiB).
- `UomConverter.convert(BigDecimal, MathContext)` and `Factor.decimalValue` accept any precision (the caller chooses the
  cost); only quantity contexts are bounded at 100000.
- At precision 100000, inexact results below 1 throw `ArithmeticException("scale out of range: N")` (the D9 scale bound
  of milestone 4 is hit before the precision).
- Fixed in the final review: `FactorTest.piToTheMaximumContextPrecisionIsFast` and
  `QuantitySerializationTest.arithmeticAtTheMaximumPrecisionIsBounded`, both added on this branch, failed under load on
  JDK 21. The cause was the JDK: JDK 21's `BigDecimal.divide(x, mc)` strips the trailing zeros of an exact quotient
  padded to the context in quadratic time, and its `BigDecimal.sqrt` is slow. `Decimals.divide` (non-exported package
  `org.tinygears.uom.core.internal`) builds terminating quotients by multiplication, with results identical to
  `BigDecimal.divide` (5000 random cases in `DecimalsTest`; 30,000 conversion and arithmetic cases compared
  digit for digit against the previous build), and pi takes its root through `BigInteger.sqrt`. The helper counts
  the divisor's factors of 5 and strips trailing zeros by powers 5^(2^m) (a logarithmic number of divisions), so huge
  2^k or 5^k divisors stay fast (`1e100000 m / 2^65535` at `DECIMAL128`: 8 ms on JDK 21); `Factor.doubleValue` and the
  `UNLIMITED` exact quotient of the decimal quantities use it too. On JDK 21 at
  precision 100000: `to(km)` and `multiply(1.5)` went from 2.5 s to under 5 ms, rad to degree (warm) from 2.5 s to
  40 ms, pi at 200,100 digits from 13.7 s to 2.5 s (JDK 25: 0.6 s). The timing bounds are now 120 s. Remaining caveat:
  on JDK 21 the first conversion through a pi factor at precision 100000 costs about 1 to 3 s (computing pi).
- `UomQuantityFormat` keeps its own private `MAX_SCALE = 100_000`, a copy of the package-private
  `Precision.MAX_SCALE`; the two can drift.
- `AbstractDecimalQuantity.withMathContext` is new public API on the base class only (not on the typed interfaces).

## Formats and parsing (`uom`)

- Shift grammar (D2): a glued `-` followed by a plain integer (`s-1`, `kg m-3`, `m^2-1`) is an error with a hint
  (`write 's^-1' for a negative exponent or 's - 1' for a shift`), because UDUNITS and CF read it as a negative exponent;
  `K-273.15`, `K-1|3`, `K - 5` and every `+` shift are valid. Labels and data symbols containing `+` or `-` are now
  rejected (they could no longer parse back). `.units` bodies can define offset units as `K + 273.15` without `=`.
- After a shift, `m+1.` or `m+1x` reports `unexpected '<c>' after the shift`; `(m+1)/s` reports
  `shift is only allowed at the end of a formula`, not the offset-unit message.
- `LegacyFormulaEvaluator` (a 250-line frozen copy of the pre-milestone evaluator in `uom` test sources) is kept through
  milestone 6 as the regression baseline of `ShiftGrammarRegressionTest`: delete it, or replace it by a golden file of
  outcomes.
- Offset format: a non-terminating `o/f` with a huge denominator costs a large-precision division before it falls back to
  the fraction; bounded by the `Factor` size limit. The irrational-shift guard is defence in depth (equivalent mutant
  behind the verification parse).
- Locale format (D3): with `withGrouping(true)` in `de-DE`, `1.5 km` parses as 15 km (`.` is the grouping separator; group
  sizes are not checked; pinned and documented). `1 234 km` with ASCII spaces is 1 of the unit `234 km`, so
  space-grouping locales need their no-break grouping character. Locale and ASCII digits may be mixed in one number. Bidi
  marks (`Cf`) are skipped by the parser at the start and end of the number and after a sign, but never written.
  `withGrouping(true)` on ROOT drops hex floats and the `d`/`f` suffix. Exact expected strings for some locales depend on
  the JDK's CLDR data (the differing ones are derived at run time; the suite passes on JDK 21 and 25).
- Decimal parse (D4): the scale is pre-checked in linear time, but a huge integer (1,000,000 integer digits, scale 0)
  still costs a full `BigDecimal` parse, which is superlinear; there is no digit bound at parse time.
- `DecimalsTest.hugePowersOfTwoAndFiveEqualBigDecimalDivide` takes 34 s alone and up to 56 s under load on JDK 21 (1.4-2.4 s on
  JDK 25), almost all of it in the JDK 21 reference `BigDecimal.divide`; only the helper is timed, so it is not flaky, but
  shrink the cases on JDK 21 if the suite time matters. The helper is slower than the JDK when the exact quotient is much
  longer than `mc` (70,000-digit dividend / 5^70000 at DECIMAL128: 36-57 ms against 6-12 ms): it builds the full quotient
  before rounding. Growth measured at about n^1.3-1.6 (BigInteger multiplication), not quadratic.
- `withDecimals` parses every quantity with scale `ABSOLUTE`, so a `RELATIVE` quantity formats and parses back as
  `ABSOLUTE` (documented).
- `withDecimals` cannot be switched off (`withDecimals(null)` throws); keep the original format. It checks the precision by
  building a throwaway `UntypedDecimalQuantity`.
- The plain ROOT format still accepts `1f m`, `1d m` and hex floats (milestone 3 item, unchanged).
- The milestone 3 format items (labels per instance, `kiloohm`, `5.0 1` for dimensionless, the label that shadows a prefix
  reading) stay open.

## User units (`uom`, D5)

- `load(Path)` checks real-path containment, then reads the real path: a symbolic link swapped between the check and the
  read (TOCTOU) is not defended against; acceptable for local user files.
- Confusable Unicode symbols are distinct: Greek mu U+03BC differs from the micro sign U+00B5 (the metric prefix), ohm
  U+2126 from omega U+03A9 (the SI symbol), so one spelling is rejected as built-in and the other is accepted. Belongs in
  the custom-unit guide (M7).
- The parser has quadratic loops for very many `@prefixed` or `@member` lines (definitions scan in `prefixed`, members
  scan): 20,000 lines take about 5 s.
- The Reader variant `load(name, reader, resolver)` uses `name` verbatim as the system name (`user.units`); only
  `load(Path)` strips the extension. Document in the guide or align.
- Parent systems are parsed twice: inside the root parse and as systems of their own (`@extends`). Each `load` call opens
  each name once and caches parents, so the user resolver is called at most once per name.
- `with()` does not detect a prefixed reading of a new prefixable unit that equals an existing exact symbol; the exact
  symbol wins and formatting still round-trips (documented). `@prefixed u k` checks only `ku` against the built-in
  symbols, though every metric prefix on `u` is readable at run time.
- `~` lines of user files register their alternate symbols in the JVM-wide registry permanently: loading the same
  definition again works, a different one is an error until the JVM restarts (documented; a scoped registry for
  run-time files is the milestone 1 follow-up item).
- No built-in `.units` resources are shipped: `@include si.units` fails unless the user resolver provides it; built-in
  symbols are reached through `known` (`UomUnitFormat.resolve`).
- A system instance already in the format is skipped silently by `with()`.

## Generator and data (`uom-codegen`, `data/`)

- The 66 generated `*Double`/`*Decimal` classes declare `serialVersionUID = 1L` (template change, Task 2), because the
  codegen tests compile generated sources with `-Xlint:all`.
- Notes from the imports change merged before this milestone: long generated lines narrowed (the factory `SYSTEM_UNIT`
  line is not wrapped; catalog `BigInteger` transform lines and the catalog class Javadoc; header line 2 accepted); odd
  wraps in generated class headers (sized for the longest type name); template lines up to 151 columns; `Imports` helper
  limits (only `java.`, `javax.` and `org.` names shortened, `java.lang` names, an untested string-literal assumption,
  Javadoc resolved before expressions); an untested clash of a referenced catalog name with an import; `ImportCheck`
  flags only qualified leftovers of imported classes; the first commit used a `refactor:` prefix.
- Task 3 widened visibility for tests: `UomUnitFormat.lookup` and `SystemsCatalogTest.PARSED` are package-private.
- `CatalogEmitterTest.childCatalogCompilesWithItsParents` compiles the real `UomSystemOfUnits` with the generated
  `QuantityTypes` and a minimal `UomUnitFormat` stand-in, which can drift from the real class.

## Tests and process

- Test totals in the task reports were wrong twice: stale `target/` report directories of the removed modules
  `uom-systems` and `uom-quantities` were summed (1193 and 1217 instead of 1006 and 1030). Count only the five modules.
- Subprocess tests (fresh JVM, module path) fail rather than skip; the symbolic-link test skips when links cannot be
  created.
- A Task 2 session wrote mutation scripts under the shared `/tmp/claude-1000/mut/` and overwrote another session's
  `run.py`; use the session scratchpad.
- Equivalent mutants left alive: the irrational-shift guard (Task 4) and the post-creation scale catch in the decimal
  parse (Task 6); the flat-include rule has no clean mutant (its test pins it).
- `OffsetUnitFormatTest` and the locale round trip are brute force (20,000 random units; every available locale): a few
  seconds each.

- Carried from milestone 1, still open: the per-unit converter cache keeps its target units alive ("revisit before the
  milestone 6 unit parser"); with run-time user units, converting catalog units to many loaded units keeps those
  targets in the catalog units' caches.

## Build

- The TCK run still prints "SLF4J: No SLF4J providers were found" (milestone 3 item: add `slf4j-nop` at test scope).
- Milestone 7: release engineering (POM metadata, sources and javadoc jars, signing profile, CI on JDK 21 and the
  current JDK), the N7 custom-unit guide and unit table, the N8 versioning policy, a JPMS review and the repository
  rename; publishing stays the owner's action.
