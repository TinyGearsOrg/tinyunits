# Milestone 2 follow-ups

Findings from the per-task reviews of milestone 2 that were triaged as follow-up work, not merge blockers, plus design
points that were parked. Items marked "M3" belong to milestone 3 (catalogs, provider), "M4" to milestone 4 (`BigDecimal`);
unmarked items are later or opportunistic.

## Decisions needed before milestone 3

- `asType` refuses quantity types whose system unit is an alternate unit: `Time.of(2, s).inverse().asType(Frequency.class)`
  and `Frequency.of(2, Hz).multiply(Time.of(3, s)).asType(Dimensionless.class)` throw `ClassCastException` although the
  dimensions match (milestone 1 compares atom-structured system units so that Frequency and Radioactivity stay distinct,
  F21). Milestone 3 hits it with `rad`/`sr` (Angle, SolidAngle). Needs an OWNER ruling before the provider and TCK work.
  Directions: accept when dimensions are equal and the target's system unit has no alternate atom; or accept when the
  source unit has no alternate atoms, so a plain `s^-1` may become Frequency or Radioactivity.
- `asType` and the factory lookup assume `ServiceProvider.current()` is ours: with another provider on the class path (for
  example Indriya) every narrowing throws `ClassCastException`. Resolve our provider explicitly in milestone 3 (filter
  `ServiceProvider.available()` or a registration hook in `uom-core`).
- One conflicting alternate-unit registration breaks the registry for the life of the class loader:
  `BaseUnits.ONE.alternate("Hz")` before first use makes `QuantityTypes` fail with `ExceptionInInitializerError` and then
  `NoClassDefFoundError` (the root cause only appears in the first error's cause). Build the registry lazily per type or
  register catalog alternates eagerly in core; at minimum document the failure.
- Offset-unit arithmetic (decision D5): it follows the Javadoc rule literally (`2 degC - 1 degC` = -272.15 degC,
  `20 degC - 0 degC` = -253.15 degC). The owner should confirm decision D5 before the TCK work.

## Design points and notes

- Tolerance of `isEquivalentTo` is relative only: asymmetric near zero for offset units (`0.001 degC` vs `273.151 K`) and
  against infinity (`inf m` vs `1e308 km`); the `isEquivalentTo` Javadoc states the offset case only. Decide in M4 whether an absolute floor is wanted.
- The generated factory's foreign-unit message uses the unit's `toString`; `uom-core` uses the class name. Align them (the
  test Proxy units must otherwise answer `toString`).
- An unlabelled shifted or scaled unit (`KELVIN.shift(273.15)`, `METRE.multiply(1000)`) has a null symbol but prints with
  the system label: `100 degC` prints "100.0 K", and `2 km` built as `METRE.multiply(1000)` prints `2.0 m`. Fix with the M3 SI catalog or a label rule in the milestone 1 unit code.
  `AbstractDoubleQuantityTest.toStringFormats` only checks `startsWith("1.5 ")`, so it passes with the wrong label; assert
  the full text once the label is fixed.
- TCK: the `Quantity` Javadoc example "2 degC + 1 degC = 274.15 degC" contradicts its own rule (276.15 degC); we follow the
  rule (F24). Record it when the TCK runs for real (M3).
- M3: `-pl uom-codegen` without `-am` fails because `~/.m2` holds a stale `uom-core` snapshot; always build with `-am`.
- M3: the plan's coverage gaps for typed quantities: `to(foreignUnit)` through the typed interface, `compareTo` across
  types, `isEquivalentTo` through the typed interface, `Scale.RELATIVE` through `of`, `create(Number, Unit, Scale)` with
  `BigDecimal` and `Integer`, a typed result of quantity times quantity, `Dimensionless` from `m/m`, `equals` for 1 km vs
  1000 m.

## Core (`uom-core`)

- `AbstractDoubleQuantity.doubleValue()` is public but no interface exposes it: drop it or wait for `toDouble()` in
  milestone 4.
- `-0.0` ordering differs by unit: `0 m` compareTo `-0 m` is 1, `0 degC` compareTo `-0 degC` is 0 (`isEquivalentTo` is
  true for both). Add a one-line note to the `compareTo` Javadoc.
- Two `toSystem` overloads with different reach (rename the static helper `systemValue`); `fromSystem` allocates an
  inverse converter per call.
- Javadoc: `add`, `multiply` and `divide` do not mention the `NullPointerException` on null; null operands give JDK helpful messages
  (use `requireNonNull(that, "addend")`).
- `Factor.of` Javadoc says "normalized parts" but the factory normalizes any parts and the sign of `n` applies outside the
  root.
- `Factor` tests: the normalization contract of the factory (`of(4,1,0,2)` equals `ofLong(2)`, `of(1,1,2,2)` equals `PI`);
  `assertTrue(!equals)` should be `assertNotEquals`; the "only the new factory can build" case is also buildable via
  `PI.pow(1000).multiply(PI.pow(500))`; the message-less lookup test does not check column or cause.
- `LicenseHeaderTest`: no non-vacuity guard (assert the candidate list is non-empty and contains
  `uom-core/src/main/java/module-info.java`); the first-line comparison uses `stripTrailing` (tolerate only `\r`); the skip
  rule drops any path component named `target` or `.git`; fully qualified `java.io.UncheckedIOException`.
- Tests: the property test never compares the fast path with the system-unit rule (add one assert in the loop at 1e-12)
  and uses only positive values; the `asType` test is narrow (factory-received unit and scale, a km quantity to `Length`
  through the provider, a non-`Untyped` quantity with `asType(Untyped.class)`); the no-provider `IllegalStateException`
  path is untested; `UomUnitAsTypeTest` still leaks `SYSTEM_UNITS` entries.

## Generator (`uom-codegen`)

- `QuantityTypesParser`: `Class.forName(name)` initializes the class (use `Class.forName(fqn, false, loader)`); identical
  `FormulaException` and `RuntimeException` catches and a possible "null" message; literal invisible BOM characters in the
  parser and its test (use the escape); the parser closes the caller's `Reader` (document it).
- Parser test gaps: `Length m#nospace`, names with digits, duplicate versus unknown ordering, cause set, list immutability.
- Generated header lines are duplicated in `Generate` and its test (make `SourceEmitter.HEADER` package-private and reuse).
- The `generate` profile paths depend on the invocation directory (use `${maven.multiModuleProjectDirectory}`).
- `Generate`: I/O error messages lose the reason (include the exception class); `System.exit` inside `exec:java` prints no
  BUILD FAILURE summary; `--check` reports "stale" only for files with the generated header.
- 20 generated lines exceed 120 characters (`XDouble` extends line, compound `SYSTEM_UNIT` expressions).
- `SourceEmitter`: `literal` escapes only backslash and quote; `golden()` throws an unclear NPE on a missing resource;
  `registry(List.of())` compiles to an empty registry (`Generate` rejects an empty file).
- The emitter round-trip test lacks `J` (covered by `namedJoule` and the exact-text test); the generated behaviour smoke test
  is shallow (add `QuantityTypes.factory(Length.class) == LengthFactory.INSTANCE`, add and `to`, the foreign-unit and null
  paths).
- The `.gitattributes` guard test uses `contains("eol=lf")`, which a commented-out line would satisfy, and assumes
  surefire's working directory.
- `uom-codegen/pom.xml` adds `--add-modules` and `--add-reads java.compiler` for the test compile (`javax.tools`); revisit
  if the module descriptor changes.

## Generated quantities tests (`uom-quantities`)

- `everySystemUnitIsIdentityAndTypeable` cannot fail (`getConverterTo(self).isIdentity()` is a tautology): assert `asType`
  against a different type fails and go through the test provider.
- `threadSafety` uses non-daemon pool threads; the `hashCode` stability test compares the same input twice;
  `serviceProviderRoundTrip` relies on exactly one provider on the test class path (comment it); unnecessary
  `throws Exception`.
- `staticInitOrder`: the registry-first loader's `touch` runs after `QuantityTypes` already initialized the factories, and
  the test asserts that nothing throws, not a causal order.
