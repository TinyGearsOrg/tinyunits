# Milestone 4 follow-ups

Findings from the per-task reviews of milestone 4 (`BigDecimal` quantities) that were triaged as follow-up work, not merge
blockers. Milestone 4 state: 33 quantity types with a `double` and a `BigDecimal` implementation each (133 generated quantity
files), 128 SI catalog entries, TCK 113 of 113 tests passing, 701 tests in `mvn -q -B clean verify` (core 343, codegen 97,
quantities 28, systems 119, tck 113, examples 1). "D1" to "D12" refer to the decisions of
`2026-10-12-uom-m4-decimal.md`; "M5" and later refer to the milestones in `docs/DESIGN.md` section 7.

## Done by milestone 4 (from earlier follow-ups)

- M1, `AffineConverter` exact `BigDecimal` path (Task 1): the add is skipped for a zero offset, and terms whose exponents
  differ by more than the precision plus a sticky bound are no longer aligned. `1e-20000000` and `1e+20000000` convert in well
  under 2 s for a pure scale and for F-to-C (`ConvertersDecimalTest`); quantities reject `|scale| > 100000` at construction
  (D9, `Precision.MAX_SCALE`).
- M2, `AbstractDoubleQuantity.doubleValue()` is removed; `toDouble()` and `toDecimal(MathContext)` are on the typed interface
  (D10).
- M2, the `isEquivalentTo` tolerance of `BigDecimal` and the absolute floor: decided in D5, relative only with no floor
  (asymmetric near zero for offset units, as for `double`).
- M2, `create(Number, Unit, Scale)` with `BigDecimal` and `Integer` through the factory: covered by
  `GeneratedDecimalQuantitiesTest` (`createDispatchesOnNumberKind`, `scaleRoundTrips`).
- M3, decimal `equals` scale rule and the `BigDecimal` `isEquivalentTo` tolerance (DESIGN section 8): D4 and D5.

## Open owner decisions

Unchanged, tracked with options in `docs/TODO.md`; decimal quantities follow the same literal rules as `double` ones.

- Offset-unit arithmetic (D5 of milestone 2): `AbstractDecimalQuantity` keeps the literal Javadoc rule, asserted in
  `QuantityContractDecimalTest` (276.15 degC against the Javadoc's 274.15).
- `asType` and alternate units: `AbstractDecimalQuantity.asType` has the structural rule of the `double` class.
- Offset-unit products.

## Decimal arithmetic and core (`uom-core`)

- Irrational-factor decimal arithmetic (degrees to radians, anything with a pi or a root in the factor) is best effort: the
  result carries about 25 guard digits beyond the context, so it is correct to the context in practice but not proven for every
  operand; the rational path is brute-force checked (300k to 2.2M cases without a difference).
- Extreme-scale products and quotients throw `ArithmeticException` once the result scale passes 100000 (D9), zeros included
  (`0E+60000 * 0E+60000`). Intended.
- D5 is degenerate at precision 1: the tolerance `10^(1-p)` is 1, so any two same-sign values within a factor of two are
  equivalent. Document on `isEquivalentTo` or require a minimum precision.
- `isEquivalentTo` of a decimal against a `double` quantity throws `ArithmeticException` when the decimal is outside the
  `double` range (F23 (3)), where `false` would be the expected answer of an equivalence test.
- The double-operand path in `AbstractDecimalQuantity` casts the `createDouble` result to the typed interface without a check
  (unchecked cast); it relies on the generated `XDecimal.createDouble` returning the matching `XDouble`.
- Cost of decimal arithmetic is linear in the digit count of the operands (exact single-rounding formulation); a value with
  100000 digits is slow but bounded.
- `asType` applies the receiver's `MathContext` only when the factory returns a decimal quantity (re-created through `create`);
  any other factory result is returned as is (D10).
- `Precision.toDouble(BigInteger)` does not range-check (`10^400` gives `Infinity`); EXACT `BigInteger` operands of a `double`
  operation must not rely on it silently.
- `toDecimal(MathContext)` on a `double` quantity rounds to the given context (same as on the decimal side); `toDecimal` rounding
  near the scale limit can raise `ArithmeticException` when the rounded value leaves the D9 bound.
- Result scale and representation change with zero offsets: the zero-offset add is skipped, so `1 m + 0.000 m` has the scale of
  `1`. Affects `toString` and `scale()` only, never `equals` or `compareTo`.
- The helper `uomUnit` is copied in `AbstractDecimalQuantity` and `AbstractDoubleQuantity`; consider one shared static helper.
- `AbstractDecimalQuantity.asType` still resolves `ServiceProvider.current()`, like the `double` class: the provider-priority
  item of the milestone 3 file applies to both.

## Formats (`uom-systems`)

- `UomQuantityFormat.parse` always returns a `double` quantity; a decimal-preserving parse (`1.10 m` as `BigDecimal`) is
  milestone 6 (D12). `format` prints `BigDecimal.toString()`.

## Generator (`uom-codegen`)

- `of(null, foreignUnit)` on the `BigDecimal` factories gives `NullPointerException` first (null value before the foreign-unit
  check, ruled in Task 7). Revisit with the factory messages.

## Tests and process

- Task 1: one test line is 121 characters (limit 120).
- Task 3: the `getClass()` check in `AbstractDecimalQuantity.equals` has a surviving mutant (`instanceof`), because the core
  tests have one decimal subclass only; the generated `LengthDecimal` and `LengthDouble` are tested in `uom-quantities`.
- Task 8: 8 of 28 mutants survived (beyond the brief); the context propagation and cross-unit cases were then added.
- Task 9: the `RELATIVE` `asType` test in `QuantityContractDecimalTest` uses a stand-in unit; the `0.3 - 0.1` subtract case was
  not individually mutant-checked.
- The red run was skipped for Task 2.
- The full build is red between Tasks 6 and 7 by design (new abstract hook before the generated classes); the milestone
  commits are bisectable only from Task 7 on.

## Build

- The TCK run still prints "SLF4J: No SLF4J providers were found" (milestone 3 item: add `slf4j-nop` at test scope).
