# TODO

Open items: decisions that need the project owner (1 to 4) and owner-requested follow-ups (5 to 8), gaps against the JSR-385 reference implementation Indriya (9 to 14), the base units of a system (15), then the steps before the first release ("Release readiness"). Each says under "Needed by" what, if anything, it blocks. The per-milestone follow-up lists in `docs/superpowers/plans/` hold the review findings that are not tracked here.

## 1. Offset-unit arithmetic (decision D5)

**Status:** undecided. The code follows the unit-api 2.2 `Quantity` Javadoc rule literally: every operation behaves as if
the operands were converted to the system unit first and the result converted back to the left operand's unit.

**Consequences today:** `2 degC + 1 degC` = 276.15 degC, `2 degC - 1 degC` = -272.15 degC, `20 degC - 0 degC` = -253.15 degC,
`2 degC * 2` = 277.15 degC. The Javadoc's own example "2 degC + 1 degC = 274.15 degC" contradicts its rule (the rule gives
276.15 degC). `negate` is a literal `-value` in the same unit.

**Options:**
- **A. Keep the literal rule** (current behavior). Follows the Javadoc text; surprising for users who think in temperature
  differences.
- **B. Use `Quantity.Scale` to separate points from intervals.** Adding or subtracting a RELATIVE quantity is a plain delta in
  the left unit; subtracting two ABSOLUTE quantities gives a RELATIVE one; adding two ABSOLUTE offset quantities is rejected.
  Closest to what users expect; deviates from the Javadoc rule and needs the scale to be set correctly by callers.
- **C. Hybrid.** Literal rule everywhere except `subtract(absolute, absolute)`, which returns a RELATIVE quantity.

**Before choosing:** check what the TCK asserts about offset-unit arithmetic (the milestone 1 spike found it mostly checks
that methods exist), and whether any reference implementation (for example Indriya) treats `Scale` this way.

**Code and docs touched by a change:** `AbstractDoubleQuantity` and `AbstractDecimalQuantity` (`combine`, `scalar`), their arithmetic tests, requirement F24,
DESIGN section 4.

**Where the code keeps today's behaviour:** `AbstractDoubleQuantity.combine` and `scalar`, and the same rule in
`AbstractDecimalQuantity`. `QuantityContractTest` and `QuantityContractDecimalTest` assert the Javadoc discrepancy (276.15 degC against the Javadoc's 274.15). The TCK passes (113 of 113) with the current rule, so
the decision no longer blocks milestone 3.

**Needed by:** nothing in milestone 3 any more; decide before 1.0.

## 2. `asType` and alternate units

**Status:** undecided. `asType` compares the quantity's system unit with the target type's system unit by structure
(atoms and exponents), so that Frequency (`Hz`) and Radioactivity (`Bq`) stay distinct (F21). That makes some
dimension-correct narrowings fail with `ClassCastException`:
- `Time.of(2, s).inverse().asType(Frequency.class)`: the source unit `1/s` has no alternate atom, the target's system unit is
  the alternate unit `Hz`.
- `Frequency.of(2, Hz).multiply(Time.of(3, s)).asType(Dimensionless.class)`: the source unit `Hz·s` contains the alternate
  atom `Hz`, the target's system unit is `ONE`.

The same applies to `rad` and `sr` (Angle, SolidAngle).

**Options for the acceptance rule** (dimensions must be equal in all of them):
- **A. Accept when the target's system unit has no alternate atom.** Fixes `Hz·s` to Dimensionless; does not fix `1/s` to
  Frequency.
- **B. Accept when the source unit has no alternate atoms.** Fixes `1/s` to Frequency (and to Radioactivity: the caller
  chooses); does not fix `Hz·s` to Dimensionless.
- **C. Accept when either side has no alternate atoms.** Fixes both examples. Still refuses `Hz` to Radioactivity and
  `rad` to `sr`, which is the intended distinction.
- **D. Keep the current structural rule** and document it.

**Where the code keeps today's behaviour:** `UomUnit.asType` (`uom-core`) compares the system units with `equals`. The TCK
passes (113 of 113) with it. The generated catalog already has `rad` and `sr` (`SI.RADIAN`, `SI.STERADIAN`) as alternate units.

**Needed by:** nothing in milestone 3 any more; decide before 1.0.

**Related risk, not a decision:** `asType` uses `ServiceProvider.current()`; our provider has priority 100, but a provider of
higher priority on the class path still breaks it. Other items: `docs/superpowers/plans/2026-10-12-uom-m3-follow-ups.md`.

## 3. Offset-unit products

**Status:** undecided. `UomUnit.multiply(Unit)` and `divide(Unit)` throw for a unit with an offset (DESIGN section 3), while the
`Unit` Javadoc says multiplication of offset units is supported. Kept in `UomUnit` and its tests in
`uom-core`. Options: keep and document, or apply the offset to the system unit first.

**Needed by:** nothing in milestone 3; decide before 1.0.

## 4. Name of the system overload

**Status:** decided. The typed interfaces had `to(Unit)` and `to(SystemOfUnits)`, so a bare `null` literal in `quantity.to(null)`
was ambiguous and did not compile. The system overload is renamed `toSystem(SystemOfUnits)`, which also avoids a clash with a
future `to(...)` overload in unit-api. Done.

**Related, follows decision 1:** `toSystem` of a `Scale.RELATIVE` temperature applies the offset like any other conversion.

## 5. Emit prefixed catalog units through `prefix(...)`

**Status:** owner-requested, not started. `@prefixed` units are generated as the decomposed
`BaseUnits.METRE.transform(Converters.affine(Factor.of(1L, 100L), Factor.ZERO)).named("cm", "centimetre")`. Emit them as
`<base constant>.prefix(MetricPrefix.CENTI).named("cm", "centimetre")` instead, using the shared base constant. The result
is the same unit; the generated code states its intent and is shorter. `Definitions` already records the `@prefixed` lines.
Needs a generator change, a test that every prefixed constant equals its prefixed base, and one regeneration.

**Needed by:** nothing; do together with item 6.

## 6. Typed `from(Quantity<?>)` on the quantity interfaces

**Status:** owner-requested, not started. `Speed v = Speed.from(length.divide(time));` instead of
`q.asType(javax.measure.quantity.Speed.class)`, which cannot take our interface (`T extends Quantity<T>` does not hold for it).
Generate `static X from(Quantity<?> quantity)` on each of the 33 typed interfaces: `asType` with the standard class, then a
cast to our interface; same exceptions as `asType`, plus `ClassCastException` for a quantity of a foreign provider. Tests for
33 types x {double, decimal} and the errors. README note: declare units with `Unit<?>` or `var`, and why. The typed interface
names stay as they are (owner decision, no `I` prefix).

**Needed by:** nothing; do together with item 5.

## 7. Binary prefixes and byte units

**Status:** not implemented. `BinaryPrefix` (`MiB`) is not resolved by the unit format or `@prefixed`, and no bit or byte
units exist; `UomUnit.prefix(BinaryPrefix.KIBI)` works on a unit. Needs bit and byte definitions (IEC 80000-13), a binary
prefix directive and run-time resolution, and a decision whether information units belong in 1.0.

**Needed by:** nothing; decide before 1.0.

## 8. Superscript exponents in formulas

**Status:** not implemented. The formula evaluator reads `^n` only, so the Acceleration system unit's symbol is `m/s^2` and
`m/s²` does not parse (as a symbol, formatting that unit would fail). Reading `²` and `³` as exponents would allow the nicer
symbol; the formatted output then changes from `m/s^2`.

**Needed by:** nothing.

## 9. Java baseline

**Status:** undecided. The baseline is Java 21 (REQUIREMENTS section 3); Indriya targets Java 8, and Java 11 for JPMS. Much of the
JSR-385 audience is on older JDKs, so this limits adoption more than any feature.

**Options:** keep 21, or lower to 17 or 11 (check which language features the core and the generated code use), or a
multi-release build.

**Needed by:** decide before 1.0.

## 10. Behaviour comparison with Indriya

**Status:** not started. Run the same probes against Indriya and record the differences: offset-unit arithmetic (item 1),
`asType` with alternate units such as `1/s` to `Frequency` (item 2), offset-unit products (item 3), quantity and unit
equality, `isEquivalentTo` tolerance, unit parsing and formatting of the same symbols. Users will compare against the
reference implementation first.

**Needed by:** input to items 1 to 3; do before deciding them.

## 11. Precision promotion rule

**Status:** undecided, revisit. F23 says the lower precision wins, so `double` mixed with `BigDecimal` gives `double`. This is
defensible but the opposite of promoting upward, and Indriya's number handling differs. Decide whether to keep it, document
the rationale prominently, or offer a strict no-mixing mode (not provided today).

**Needed by:** decide before 1.0.

## 12. Coexistence with other JSR-385 providers

**Status:** open risk. `UomUnit.asType` uses `ServiceProvider.current()`; a provider of higher priority on the class path
(Indriya, for example) breaks it (item 2, related risk). Decide whether `asType` and the formats should avoid the global
lookup, and test a class path that holds both implementations.

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
- Decide the CI items of the milestone 7 follow-ups (SHA pinning of actions, duplicate push and pull-request runs,
  `cancel-in-progress` on `main`, job-level permissions), push the branch and see the workflow green on JDK 21 and 25.
- Decide the repository rename; the POM's `url`, `scm` and `issueManagement` follow it.
- Items 1 to 4 above are due before 1.0, not before a 0.x release.

## Milestone follow-up lists

Review findings of each milestone that are not owner decisions: `docs/superpowers/plans/2026-10-12-uom-m1-follow-ups.md`
to `2026-10-12-uom-m6-follow-ups.md`, and [2026-10-13-uom-m7-follow-ups.md](superpowers/plans/2026-10-13-uom-m7-follow-ups.md)
(release engineering, including the CI owner decisions).

