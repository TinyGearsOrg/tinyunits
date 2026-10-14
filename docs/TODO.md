# TODO

Open work only. The item numbers are stable IDs: a gap is a finished item; the decisions, with their rationale and when to revisit
them, are in [DESIGN.md](DESIGN.md) section 1 (DR-01 to DR-08); the last version of this file with the full discussion is
`git show 5d734d3:docs/TODO.md`. The groups: owner-requested follow-ups (6 and 7), gaps against the JSR-385 reference
implementation Indriya (9 to 11, 13, 14), the base units of a system (15), an AGENTS.md (16), pre-commit and formatting (17 and
18), open defects and hardening carried over from the milestone reviews (21 to 32), then the steps before the first release
("Release readiness"). Each item says under "Needed by" what, if anything, it blocks.

## 6. Typed `from(Quantity<?>)` on the quantity interfaces

**Status:** owner-requested, not started. `Speed v = Speed.from(length.divide(time));` instead of
`q.asType(javax.measure.quantity.Speed.class)`, which cannot take our interface (`T extends Quantity<T>` does not hold for it).
Generate `static X from(Quantity<?> quantity)` on each of the 33 typed interfaces: `asType` with the standard class, then a
cast to our interface; same exceptions as `asType`, plus `ClassCastException` for a quantity of a foreign provider. Tests for
33 types x {double, decimal} and the errors. README note: declare units with `Unit<?>` or `var`, and why. The typed interface
names stay as they are (owner decision, no `I` prefix).

**Needed by:** nothing.

## 7. Binary prefixes and byte units

**Status:** not implemented. `BinaryPrefix` (`MiB`) is not resolved by the unit format or `@prefixed`, and no bit or byte
units exist; `UomUnit.prefix(BinaryPrefix.KIBI)` works on a unit. Needs bit and byte definitions (IEC 80000-13), a binary
prefix directive and run-time resolution, and a decision whether information units belong in 1.0.

**Needed by:** nothing; decide before 1.0.

## 9. Java baseline

**Status:** undecided; the baseline stays Java 21 for now (REQUIREMENTS section 3). Indriya targets Java 8, and Java 11 for
JPMS. Much of the JSR-385 audience is on older JDKs, so this limits adoption more than any feature.

**Measured cost** (a scratch copy built with `maven.compiler.release=N` on JDK 25; `--release` fixes the bytecode level and
the visible API, whatever JDK builds):
- **Java 17:** cheap. The only failures are three APIs: `Math.divideExact` (18; use `Math.toIntExact((long) a / n)`, which
  keeps the `MIN_VALUE / -1` overflow that `UomDimensionTest.rootEdgeCases` pins), `BigDecimal.TWO` (19) and
  `InvalidObjectException(String, Throwable)` (19; use `initCause`). That is 8 call sites in 6 main files (`UomUnit`,
  `UomDimension`, `Factor`, `UnitAtom`, `AffineConverter`, `QuantityProxy`) and 3 test lines (`BigDecimal.TWO` twice,
  `List.reversed()` in `CgsDataTest`). With these the full build passes: 1115 tests, TCK 113 of 113. Also change the
  `maven.compiler.release` property, the enforcer `requireJavaVersion`, the CI matrix, the README and REQUIREMENTS.
  Not verified: runtime on a real JDK 17 (none was available); add a JDK 17 job to CI.
- **Java 11:** mechanical but large. About 70 syntax sites in main (16 records, 6 of them public API types in
  `core.parse`, so the API pin changes; about 43 `instanceof` patterns; 9 text blocks; 7 switch rules; 1 `sealed`) plus 9
  `Stream.toList()`, and in tests about 10 records, 30 text blocks, 57 switch-rule lines and 17 patterns. No Java 12 to 17
  API was found by grep; not machine-verified.
- **Java 8:** not recommended. Adds a multi-release jar for `module-info`, about 70 uses of Java 9 to 11 APIs in hand-written
  code (`List.of`, `Map.of`, `strip`, `repeat`), the same Java 9 factories in the generated catalogs and quantity classes
  (generator output changes), and an own `BigInteger.sqrt` for pi.

**Options:** keep 21; lower to 17 (recommended if lowered, with a JDK 17 CI job, releases still built with JDK 21); 11 or 8
only if users ask.

**Needed by:** decide before 1.0.

## 10. Behaviour comparison with Indriya

**Status:** partly done. Probes against Indriya 2.2 decided offset arithmetic (DR-01), `asType` with alternate units
(DR-02), offset-unit products (DR-03) and the provider coexistence (DR-05); the behaviour of both libraries on the same
classpath is a test (`ProviderCoexistenceTest`). Still to probe and record: quantity and unit `equals`, the `isEquivalentTo`
tolerance, and unit parsing and formatting of the same symbols. Users will compare against the reference implementation first.

**Needed by:** before 1.0.

## 11. Precision promotion rule

**Status:** undecided, revisit. F23 says the lower precision wins, so `double` mixed with `BigDecimal` gives `double`. This is
defensible but the opposite of promoting upward, and Indriya's number handling differs. Decide whether to keep it, document
the rationale prominently, or offer a strict no-mixing mode (not provided today).

**Needed by:** decide before 1.0.

## 13. Scope gaps against Indriya

**Status:** known, deferred or unchecked. Items marked "unchecked" were read from Indriya's README only.
- User-defined quantity types: out of scope for 1.0 (REQUIREMENTS F14); Indriya supports them.
- Typed quantity products and quotients (`Length` / `Time` returning `Speed`): out of scope; `multiply` and `divide` return
  untyped results and need `asType` or `from` (item 6).
- Closed set of 33 generated quantity types.
- Unit sets and formats beyond the seven catalogs and the standard format (for example UCUM, and unit sets of the separate
  unitsofmeasurement projects): unchecked.
- Byte and bit units and binary prefixes: item 7.
- Poise, stokes and oersted are untyped `UomUnit<?>` constants; extra quantity types (`DynamicViscosity`,
  `KinematicViscosity`, `MagneticFieldStrength`) would type them and extend `data/quantity-types.txt`.

**Needed by:** decide which gaps matter for 1.0 and say so in the README.

## 14. Positioning and community

**Status:** open. Position uom as a typed, multi-system JSR-385 implementation, not as the reference implementation. The
reference status is the expert group's, so engage the unitsofmeasurement maintainers early and share the TCK result as the
first second implementation. Re-run the TCK in CI and publish the result rather than relying on the README claim.

**Needed by:** before the first release announcement.

## 15. Base units of a system (CGS and others)

**Status:** undecided. Every system is built on the seven SI base units: `cgs.units` is `@include si.units` with `cm g s` as
members, and its definitions are exact factors to SI units (F7). `getSystemUnit()` and `Dimension` are SI-based, so `cm` and
`0.01 m` are the same unit, and untyped results of `multiply`/`divide` come back in SI system units. A system only adds
its catalogue and its preferred units (`@preferred Length cm`). A user may expect CGS to have `cm`, `g`, `s` as base units.

**Limit:** for ESU, EMU and Gaussian, values convert correctly but formulas do not (in Gaussian, charge has dimension
`g^1/2 cm^3/2 s^-1`); the documentation must say so (REQUIREMENTS section 7) and does not yet, prominently.

**Options:**
- **A. Document the current model** in the README: systems are catalogues plus a preferred-unit mapping over the SI
  dimension model.
- **B. Per-system base-unit view.** Each system names its base units (CGS: `cm`, `g`, `s`), used to decompose and format
  units and untyped results in that system (`cm/s` instead of `m/s`). Presentation only; `getSystemUnit()` stays SI-based.
- **C. Distinct dimension systems** for ESU, EMU and Gaussian (option B of the design notes, out of scope for 1.0). The
  only option that makes the electromagnetic CGS formulas correct; touches the core, `asType` and equality.

**Needed by:** A before 1.0; decide B and C before 1.0 or record them as out of scope.

## 16. Add an AGENTS.md

**Status:** not started. The repository has no `AGENTS.md`, so AI coding agents work without the project's rules. Add one at
the root (and a `CLAUDE.md` that points to it, if wanted) as a short must-follow contract that links to `CONTRIBUTING.md`
instead of repeating it. Candidate content, all from `CONTRIBUTING.md` and the docs:
- Build and test: `mvn -q -B clean verify`; Java 21.
- Generated files (`data/` is the source; never edit the generated catalogs and quantity types by hand; regenerate after
  changing `data/` or the generator).
- Tests: every change has tests, a bug fix a test that fails without the fix; exact assertions.
- `ApiSurfaceTest` pins the public API (`mvn -B verify -Duom.writeApi` rewrites `docs/api/public-api.txt`);
  `DocsSnippetsTest` runs the code blocks of the docs, so keep them true.
- Commit prefixes (`feat:`, `fix:`, `chore:`, `docs:`, `test:`, `style:`, `build:`, `ci:`), short bodies, no emojis.
- Git hygiene: commit only the files you changed, stage explicit paths, never push or open PRs unless asked.
- Source files carry the `SPDX-License-Identifier: Apache-2.0` header; comments short.

**Needed by:** nothing; do before accepting outside or agent contributions.

## 17. Pre-commit configuration

**Status:** not started. There is no `.pre-commit-config.yaml`; the checks run only in CI and by hand (`scripts/check-pom.sh`,
`scripts/check-javadoc.sh`, `scripts/check-workflows.sh`). Add a config whose hooks are cheap and local: the formatting
check (item 18), whitespace and end-of-file fixers, a check that generated files are not edited by hand (regenerate and
diff, or a header check), the `scripts/check-*.sh` scripts that need no network, and a commit-message hook for the
conventional prefixes of `CONTRIBUTING.md`. Keep `mvn clean verify` out of the hooks (too slow); CI stays the gate.
Document the setup in `CONTRIBUTING.md` and `AGENTS.md` (item 16).

**Needed by:** nothing; do together with item 18.

## 18. Code formatting

**Status:** not started. No formatter or style check is configured (no Spotless, Checkstyle, formatter plugin or
`.editorconfig`); the code is formatted by convention only (4-space indent, no tabs, lines up to about 126 characters in
the files sampled). Decide the style, apply it once in a single `style:` commit, and enforce it.

**Options:**
- **A. Spotless** with google-java-format (AOSP style for 4 spaces) or a custom Eclipse profile, run as `spotless:check` in
  `verify` and `spotless:apply` locally.
- **B. Checkstyle** for rules only (indent, line length, imports, no tabs); no auto-format.
- **C. `.editorconfig` only** (indent, line length, final newline), no enforcement.

**Points to settle:** the generated sources are committed and must stay byte-identical to the generator output, so either
exclude them from the formatter or make the generator emit formatted code (the golden tests in `uom-codegen` pin its
output); the line-length limit; whether the check fails the build or only warns. A formatting-only commit also moves
`git blame`, so list it in `.git-blame-ignore-revs`.

**Needed by:** nothing; do before accepting outside contributions.

## 21. Formats and parsing

**Status:** open.
- The number grammar of `UomQuantityFormat.parse` is loose: `1f m`, `1d m` and hex floats are accepted (`1f m` parses as
  `1.0 m`). Restrict to a decimal pattern and keep `NaN` and `Infinity`.
- Labels are per `UomUnitFormat` instance, while the `UnitFormat.label` Javadoc says system-wide: document or make static.
  The label and alias maps are updated non-atomically.
- `UomUnitFormat.format` refuses units of other JSR-385 implementations, so `UomQuantityFormat.format` throws for foreign
  quantities. Decide whether a best-effort symbol is wanted; the refusal message gives no reason and no test covers it.
- `UomQuantityFormat` swallows a failing factory initialisation and parses the quantity as untyped: log it through
  `System.Logger`.
- `format` does not memoise the verification parse (about 5 µs per call); `withDecimals` cannot be switched off and builds
  a throwaway quantity to check the precision; `UomQuantityFormat.MAX_SCALE` is a private copy of `Precision.MAX_SCALE`.
- `LegacyFormulaEvaluator` (a frozen copy of the old evaluator in `uom` test sources) is the regression baseline of
  `ShiftGrammarRegressionTest`: delete it or replace it by a golden file of outcomes.

**Needed by:** nothing; decide before 1.0 where it changes behaviour.

## 22. Decimal arithmetic residuals

**Status:** open.
- `UNLIMITED`: `to()` and `add()` can disagree. The converter falls back to `DECIMAL128` whenever its factor is not exact
  in its own path, even where the exact result terminates (confirmed: `254.000000000000000000000000000000000000254 m` to
  inches gives `10000.00000000000000000000000000000`; about 45 in 600k random checks). Fix the converter, or state it in
  F23. Also `BTU_th` (denominator 9) loses digits under `UNLIMITED`.
- `Factor.decimalValue` is not correctly rounded at near-ties: 5 fixed guard digits under `HALF_UP`, never extended
  (pi at p=761 with `HALF_UP`, the degree factor at 762 to 763, sqrt(2) at p=2706); with `UNNECESSARY` it returns a value
  instead of throwing. Fix by Ziv-style evaluation: retry with more guard digits until the rounding decision is
  unambiguous.
- `isEquivalentTo` and `compareTo` of a decimal outside the `double` range against a `double` quantity throw
  `ArithmeticException` (confirmed for `1e400 m`); `false` and the obvious sign are the expected answers.
- The `isEquivalentTo` tolerance `10^(1-p)` is 1 at precision 1, so any two same-sign values within a factor of two are
  equivalent: document it or require a minimum precision.
- Cost: guard digits grow with the operand's precision (multiplying a 3,000,000-digit value takes 28 s) and a huge
  integer parses with a full superlinear `BigDecimal` parse (no digit bound at parse time). Only the scale and quantity
  contexts are bounded. At precision 100000 inexact results below 1 throw `ArithmeticException("scale out of range")`.
- Irrational-factor arithmetic (pi or a root in the factor) is best effort: about 25 guard digits beyond the context, correct
  in practice but not proven.

**Needed by:** decide before 1.0 (the first item changes documented behaviour).

## 23. Alternate-unit registry (global state)

**Status:** open, decision needed. Alternate units (`~` lines, `UomUnit.alternate`) register in one JVM-wide registry.
- Alternates of user `.units` files and of a failed `parse` stay registered for the life of the JVM; loading the same
  definition again works, a different one is an error until restart. A scoped registry for run-time files is the proposed
  fix.
- `rad.alternate("rad")` throws while `METRE.alternate("m")` returns an equal unit: accept `{existingAtom:1}` as the same
  definition or document it.
- One conflicting registration breaks initialisation: `SI` loads all 33 factories through `PREFERRED`, so a conflicting
  alternate breaks `SI` (the factories are built lazily per type in `QuantityTypes`).

**Needed by:** decide before 1.0 (user-visible behaviour of `load`).

## 24. Per-unit converter cache keeps targets alive

**Status:** open, noted since milestone 1 ("revisit before the unit parser"). Each unit caches its converter to a target
unit in a small map, so converting catalog units to many run-time loaded units keeps those units alive in the catalog
units' caches. Bound the cache or use weak keys; measure first.

**Needed by:** nothing; before heavy use of `UomSystemOfUnits.load`.

## 25. Core hardening

**Status:** open, small items in `uom-core`.
- `Factor.make` checks the size cap before root normalization, so some products whose normal form fits are rejected
  (a documented ceiling); move the check after normalization.
- `UomUnit.of` accepts a zero-factor converter that `transform` rejects (check the current behaviour first).
- `getConverterTo` between offset units with an irrational scale throws `ArithmeticException` instead of
  `UnconvertibleException`.
- `UomConverter` equality with third-party implementations is asymmetric (consider sealing); `getDimension` and
  `UomDimension.getBaseDimensions` return mutable copies; `Factor.toString` prints `(-2)^(1/2)` for a negative irrational;
  `toPlainString()` of a huge `BigDecimal` is quadratic; `DefinitionParser` accepts an empty name (`x: m`) and
  `m:metre ! L`, and has unclear messages for `x#y m` and `x[Foo] m`.
- `asType` with a provider that returns a null factory throws `NullPointerException`; `of(null, foreignUnit)` on the
  decimal factories gives `NullPointerException` before the foreign-unit check.

**Needed by:** nothing.

## 26. Serialization hardening residuals

**Status:** open, low risk (untrusted streams need the documented `ObjectInputFilter`; the serialized form is not promised
stable before 1.0).
- A multi-step converter whose inexact fold has a part over 4096 bits skips the decimal fold check, so a forged identity
  converter with huge irrational parts is accepted and its `getConversionSteps()` contradicts `convert()`.
- No dimension exponent range check (the proxy checks null and length 7); the zero-factor check lives only in the unit
  proxy.
- Untyped quantity reads depend on `SI` being loaded: a forged `Hz` atom inside an untyped `Hz·m` is accepted in a fresh
  JVM that has not touched `SI`. Re-serializing an unregistered atom drops its definition, and definition-less atoms match
  on symbol and dimension only.
- `QuantityProxy` repeats a typed `read` and a bounded byte read of `SerialForm` because `SerialForm` is package-private
  in an exported package: move both into the non-exported `org.tinygears.uom.core.internal`.
- Strings in a stream (symbols, names) are unbounded except by the filter.

**Needed by:** before 1.0, when the serialized form is promised stable.

## 27. User-unit loader

**Status:** open, low priority.
- `load(Path)` checks real-path containment, then reads the real path: a symbolic link swapped in between (TOCTOU) is not
  defended against; acceptable for local files, say so in the guide.
- The parser has quadratic loops for very many `@prefixed` or `@member` lines (20,000 lines take about 5 s).
- A nested file with two `@system` lines is not rejected when included (only as the root); parent systems are parsed
  twice (inside the root parse and as systems of their own).
- Fallback preferred units are not members of the system (`USCustomary.SYSTEM.getUnit(Time.class)` is `s`, but `getUnit("s")`
  is null): decide whether to list them.

**Needed by:** nothing.

## 28. Generator and data hygiene

**Status:** open, small items in `uom-codegen` and `data/`.
- The `to(system)` Javadoc of the typed interfaces (now `toSystem`) uses plain text for the exceptions and omits the
  `NullPointerException`: fix in `SourceEmitter` and regenerate.
- `QuantityTypesParser` uses `Class.forName(name)`, which initializes the class; use `Class.forName(fqn, false, loader)`.
- The `generate` profile paths depend on the invocation directory (use `${maven.multiModuleProjectDirectory}`);
  `Generate` loses the reason in I/O error messages and `System.exit` under `exec:java` prints no BUILD FAILURE summary.
- Constant names replace non-ASCII letters with `_` (`électron` becomes `_LECTRON`): deliberate, but neither commented nor
  pinned by a test. `SourceEmitter.literal` escapes only backslash and quote.
- `QuantityTypes.factory` is a linear if-chain (fine for 33 types); `DefinitionParser.prefixed` scans the definitions list
  although a symbol map exists.
- The `--add-modules`/`--add-reads java.compiler` test-compile arguments of the `uom-codegen` pom are likely redundant now
  that main code requires `java.compiler`.
- `docs/UNITS.md` lists Gaussian as 27 rows for 26 distinct units (`Fr` and `statC`): label the generated column as
  symbols. Generated lines over 120 columns: item 18.

**Needed by:** nothing.

## 30. Test gaps

**Status:** open. The review nits that are not listed here were not carried over.
- Fix the `-Xlint:all` warnings: raw types and unchecked in `DefinitionParserTest` and `FormulaEvaluatorTest`, two serial
  warnings in main.
- Missing cases: `to(foreignUnit)` through the typed interface, `compareTo` across different quantity types, `equals` of
  1 km against 1000 m, `pow(0) == ONE`, the no-provider `IllegalStateException` path of `asType`, `convert(Number)` types
  (null, Integer, Long, BigInteger, Float), `km.shift(1)`, an offset-converter conversion, and the `getBaseUnits` fold
  of multi-atom units.
- Tests that cannot fail or are weak: `everySystemUnitIsIdentityAndTypeable` (tautology), `failuresAreNotCached`,
  `FactorTest.piCacheIsBounded` never reaches the clear-at-16 branch, `LicenseHeaderTest` has no non-vacuity guard,
  `SystemConversionTest` pins only some `PREFERRED` entries (`SystemsCatalogTest` and `ToSystemTest` catch the rest).
- `assertEquals(128, SI.UNITS.size())` and other pinned counts are brittle and need an update with every added unit.
- `DecimalsTest.hugePowersOfTwoAndFiveEqualBigDecimalDivide` takes 34 s alone and up to 56 s under load on JDK 21; shrink
  the cases there if the suite time matters.
- `TestServiceProvider.SYSTEM_UNITS` is a static mutable map filled by one test class and leaked by `UomUnitAsTypeTest`;
  centralize before a second consumer.

**Needed by:** nothing.

## 31. Build and CI tooling

**Status:** open.
- Add `slf4j-nop` at test scope in `uom-tck`: the TCK run prints "SLF4J: No SLF4J providers were found" (no slf4j entry in
  the POM today). On JDK 21 the TCK also prints "No output directory specified" and leaves an empty root `target/`.
- CI owner decisions: pin actions to full SHAs (needs `ACTION` in `check-workflows.sh` to accept `@[0-9a-f]{40}`; they
  are pinned to major tags today); `on: push` plus `pull_request` runs a PR from a branch of this repository twice (a
  `main`-only push filter halves it); `cancel-in-progress: true` also cancels superseded runs on `main` (use
  `${{ github.event_name == 'pull_request' }}`); job-level `permissions: {contents: read}` needs `check-workflows.sh`
  changed, which now forbids it.
- `check-pom.sh`, `check-javadoc.sh` and `check-workflows.sh` are not CI steps (`check-workflows.sh` needs PyYAML, and
  adding it changes the `CONTRIBUTING.md` block it compares). `verify-release.sh` runs `check-pom.sh` on the installed
  POMs.
- Plugin updates were deferred for stability (compiler 3.13.0, surefire 3.2.5, exec 3.2.0; dependabot proposes newer ones).
  `waitForPublishCompletion` is deprecated in central-publishing-maven-plugin 0.11.0 and prints a warning per module;
  `skipPublishing` only filters artifacts and the upload mojo still runs on the last module; re-check on a plugin update.
  A non-quiet build warns "JAR will be empty" for `uom-tck`.
- `verify-release.sh`: no self-test mutation for the consumer's resolved dependency set or the `jlink` stage;
  `xargs -n1 basename` splits on whitespace (a repository path with spaces breaks it); it builds twice and downloads the
  dependencies twice on a runner without `~/.m2`; `scripts/consumer/expected-output.txt` duplicates what `ExamplesTest` pins.
- `mvn test -pl uom` without `-am` reads `uom-core` from `~/.m2`, so `ApiSurfaceTest` can compare a stale jar (same for
  `-pl uom-codegen`): say so in `CONTRIBUTING.md`. The API pin strings were generated on JDK 25 and pass on Temurin 21.
- `maven-javadoc-plugin` 3.12.0 misses the warning count when `-Xmaxwarns` is raised, so only `check-javadoc.sh` raises it;
  it counts only `file:line: warning:` lines.

**Needed by:** the CI decisions before the first push of the workflow; the rest nothing.

## 32. Explicit type assignments and the dimension rule

**Status:** open, decision needed. `asType` goes by dimension (DR-02), but the checks that give an explicit type to a unit
stay structural: `UomSystemOfUnits.load` (the `@preferred Type unit` line of a user file) and the code generator (`[Type]` and
`@preferred`) require the unit's system unit to equal the type's. So `@preferred Frequency Bq` is an error although
`asType` would accept it. Inferring a unit's type from its atoms must stay structural (it is how `Hz` and `Bq` are told
apart); only the explicit assignments could follow the dimension rule.

**Options:** keep the explicit assignments structural and document it; or accept any unit of the dimension.

**Needed by:** decide before 1.0.

## Release readiness

No version has been released, signed, tagged or uploaded. The release is prepared and is the owner's action
([RELEASING.md](RELEASING.md)). Before the first release:

- Enable GitHub private vulnerability reporting for the repository (`SECURITY.md` points to it), and confirm the
  contact and the "best-effort" response wording of `SECURITY.md`.
- Verify the Central Portal namespace `org.tinygears` (DNS TXT record of `tinygears.org`; without the domain the group
  id changes).
- Create the OpenPGP signing key, publish it to the key servers, and keep a backup and a revocation certificate.
- Generate a Central Portal user token and put it into `~/.m2/settings.xml` under the server id `central` (never into
  the repository).
- Replace `0.1.0-SNAPSHOT` with the release version and set `project.build.outputTimestamp` (RELEASING, step 1).
- Decide the CI items of item 31 (SHA pinning of actions, duplicate push and pull-request runs, `cancel-in-progress` on
  `main`, job-level permissions), push the branch and see the workflow green on JDK 21 and 25.
- Decide the repository rename; the POM's `url`, `scm` and `issueManagement` follow it.
