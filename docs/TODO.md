# TODO

Open items that need a decision from the project owner. Each says under "Needed by" what, if anything, it blocks; none blocks milestone 3 any more.

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

**Code and docs touched by a change:** `AbstractDoubleQuantity` (`combine`, `scalar`), its arithmetic tests, requirement F24,
DESIGN section 4.

**Where the code keeps today's behaviour:** `AbstractDoubleQuantity.combine` and `scalar`. `QuantityContractTest` asserts
the Javadoc discrepancy (276.15 degC against the Javadoc's 274.15). The TCK passes (113 of 113) with the current rule, so
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
