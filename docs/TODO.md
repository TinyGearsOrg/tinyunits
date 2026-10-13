# TODO

Open items: decisions that need the project owner (1 to 4) and owner-requested follow-ups (5 to 8), then the steps before the first release ("Release readiness"). Each says under "Needed by" what, if anything, it blocks. The per-milestone follow-up lists in `docs/superpowers/plans/` hold the review findings that are not tracked here.

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

## 4. `to(null)` and the name of the system overload

**Status:** the typed interfaces have `to(Unit)` and `to(SystemOfUnits)` (D5 of milestone 5, F8), so a bare `null` literal in
`quantity.to(null)` is ambiguous and does not compile; a typed `null` variable is fine. Accepted in milestone 5 because the
requirement names the method `to(system)`. Options: keep, or rename the system overload (for example `toSystem`).

**Related, follows decision 1:** `to(system)` of a `Scale.RELATIVE` temperature applies the offset like any other conversion.

**Needed by:** nothing; decide before 1.0.

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

