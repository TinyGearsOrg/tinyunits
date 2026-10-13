# Milestone 1 follow-ups

Findings from the per-task and final reviews that were triaged as follow-up work, not merge blockers.
The items that milestone 2 resolved are listed at the end ("Done in milestone 2"); milestone 3 and 4 planning should
pick up the items marked "M3+" or "M4".

## Parked residual (final review, no second fix wave)

- **M4, done by milestone 4 (Task 1, `ConvertersDecimalTest`; scale bound D9)** `AffineConverter` exact `BigDecimal` path: `BigDecimal.add` aligns scales exactly, so values with absurd scale
  (|scale| around 2e7) take seconds (F-to-C `convert(1e-20000000, DECIMAL64)` 11 s) or overflow. Fix: skip the add when the
  offset is zero; pre-align or reduce the small term to a sticky bit when the exponents differ by more than
  `precision + digits + 2`; validate or bound the value scale at BigDecimal quantity construction. Add timeout tests for
  `1e+-20000000` on a pure scale and on F-to-C.

## For milestone 3 or later

- **M3+** `Factor.make` checks the size cap before root normalization, so some products whose normal form fits are rejected
  (documented ceiling); move the check after the normalization loop.

## Tests and warnings

- Fix `-Xlint:all` warnings: raw types/unchecked in tests (DefinitionParserTest, FormulaEvaluatorTest), two serial warnings
  in main.
- Remove or make observable `failuresAreNotCached` (cannot fail); anchor the `exports org.tinygears.uom.core` assertion in
  ModulePathTest to a line; `FactorTest.piCacheIsBounded` never reaches the clear-at-16 branch.
- Missing tests: compound-unit dimension fold and `getBaseUnits` on multi-atom units; null rejection and `atoms()`
  immutability; `convert(Number)` types (null, Integer, Long, BigInteger, Float); three-way concatenation order; `km.shift(1)`;
  an offset-converter conversion test; the no-provider `IllegalStateException` path of `asType`; DefinitionParser: reader
  closing on failure, `UncheckedIOException`, directive argument-count messages, zero scale factor; FormulaEvaluator:
  non-ASCII symbols inside compounds, `number out of range`, a throwing lookup.
- `TestServiceProvider.SYSTEM_UNITS` is a static mutable map filled by one test class; centralize before a second consumer.

## Behavior and messages

- `rad.alternate("rad")` throws while `METRE.alternate("m")` returns an equal unit (alternate atoms store the decomposition
  they were built from); accept `{existingAtom:1}` as the same definition or document it. `asType` NPEs when a provider
  returns a null factory.
- A failed `parse` leaves its alternates registered for the life of the JVM (documented on `parse`); consider a scoped
  registry mode for runtime-loaded files (F18).
- `DefinitionParser`: empty name `x: m` yields an empty name; symbol rule duplicated from `FormulaEvaluator`; unclear
  messages for `x#y m` and `x[Foo] m`; `m:metre ! L` accepted.
- `UomConverter` equality with third-party implementations is
  asymmetric (consider sealing); the per-unit converter cache keeps targets alive (revisit before the milestone 6 unit
  parser); `getDimension` and `UomDimension.getBaseDimensions()` return mutable copies.
- `Factor.toString` prints `(-2)^(1/2)` for a negative irrational; prefix parsing reads `Prefix.getValue().toString()`;
  `UomUnit.of` accepts a zero-factor converter that `transform` rejects; `getConverterTo` between offset units with an
  irrational scale throws `ArithmeticException` instead of `UnconvertibleException`; a huge `BigDecimal` `toPlainString()`
  is quadratic.

## Done in milestone 2

- `Factor.of(BigInteger n, BigInteger d, int piExponent, int rootIndex)` parts factory, routed through `make`.
- `UomUnit.getDimension()` is a cached final field.
- License-header decision: the line `// SPDX-License-Identifier: Apache-2.0` on every source file, checked by `LicenseHeaderTest`.
- A throwing lookup passed to `FormulaEvaluator.evaluate` is wrapped by `guard`.
- `AffineConverter.convert(double)` keeps `-0.0`.
