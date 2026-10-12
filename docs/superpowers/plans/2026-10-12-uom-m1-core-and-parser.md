# uom Milestone 1: Core and Parser Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A tested `uom-core` module with exact factors, converters, dimensions, a JSR-385 `Unit` implementation, the `.units` definition parser, and a spike report on running `unit-tck` against it.

**Architecture:** Plain Java 21 library over `javax.measure:unit-api` 2.2. A unit is a map of atoms to integer exponents, plus an affine converter to the system unit, plus labels; equality ignores labels. Factors are exact rationals with optional power of pi and an optional root index, so every converter is affine and closed under concatenation.

**Tech Stack:** Java 21, Maven, JUnit 5.10.2, `javax.measure:unit-api:2.2` (the only runtime dependency).

**Spec:** `docs/DESIGN.md` (sections 3 and 5, milestone 1) and `docs/REQUIREMENTS.md`. This is the first of six milestone plans; later milestones are planned after this one lands.

## Global Constraints

- Java baseline 21; Maven multi-module; Apache-2.0 (existing `LICENSE`).
- Group id `org.tinygears`; artifacts `uom-*`; package prefix `org.tinygears.uom`; JPMS module `org.tinygears.uom.core` requiring `java.measure`.
- Runtime dependencies: only `javax.measure:unit-api` 2.2. No proxies, no runtime reflection (N3).
- Unit conversions use exact definitions (inch = 0.0254 m); factors are exact rationals, optionally times a power of pi, rounded once for `double` (N2).
- `Unit.equals` is structural over the system-unit decomposition and the converter, ignoring symbol and name; `isEquivalentTo` means the converter between the units is the identity (F21).
- Units with an offset can not be multiplied or divided (they throw).
- Errors in definition files carry file and line (F19); one pass, definitions before use.
- Immutable, thread-safe types (F12; `Serializable` is milestone 6).
- Commit subjects use a conventional prefix (`feat:`, `test:`, `build:`, `docs:`, ...), no emojis.

## Review Focus

1. **Definition-file robustness.** UTF-8 BOM, CRLF line endings, tabs as separators, empty file, whitespace-only lines, non-ASCII symbols (`°C`, `Ω`, `µ`): parse the same as their ASCII equivalents (Task 10).
2. **Alternate-unit registry.** Registering the same symbol concurrently with the same definition yields equal units; a conflicting definition throws `MeasurementException`, never silently rebinds (Task 8).
3. **Exponent edge cases.** `pow(0)` is `ONE`, `root(0)` throws `ArithmeticException`, a huge exponent throws `ArithmeticException` instead of hanging, negative powers work (Tasks 2, 7).
4. **Offset units in algebra.** `multiply`, `divide`, `pow(n != 1)` and `root` on an offset unit throw `UnconvertibleException`; shifting a shifted unit accumulates the offsets (Task 7).
5. **Thread safety.** The converter cache is safe under concurrent first use and returns one instance per target (Task 6).

---

### Task 1: Project skeleton

**Files:**
- Delete: `tinyunits-core/`, `tinyunits-processor/`, `tinyunits-quantities/` (the throwaway sketch)
- Modify: `pom.xml` (becomes `uom-parent`)
- Create: `uom-core/pom.xml`, `uom-core/src/main/java/module-info.java`
- Test: `uom-core/src/test/java/org/tinygears/uom/core/ModuleSmokeTest.java`

**Interfaces:**
- Produces: Maven coordinates `org.tinygears:uom-parent:0.1.0-SNAPSHOT` and `org.tinygears:uom-core:0.1.0-SNAPSHOT`; module `org.tinygears.uom.core` exporting `org.tinygears.uom.core` and `org.tinygears.uom.core.parse`.

- [ ] **Step 1: Branch.** `git checkout -b m1-core` from `docs/requirements` (it carries the docs). `git rm -r tinyunits-core tinyunits-processor tinyunits-quantities`.
- [ ] **Step 2: Write the failing test** `ModuleSmokeTest.unitApiIsAvailable`: `assertEquals("javax.measure.Unit", javax.measure.Unit.class.getName())` and `assertNotNull(ServiceLoader.load(javax.measure.spi.ServiceProvider.class))`.
- [ ] **Step 3: Run `mvn -q -B verify`.** Expected: FAIL (no module `uom-core`).
- [ ] **Step 4: Write the poms and `module-info.java`.** Parent: packaging `pom`, module `uom-core`, properties `maven.compiler.release=21` and UTF-8, `maven-compiler-plugin` 3.13.0, `maven-surefire-plugin` 3.2.5 with `<useModulePath>false</useModulePath>` (tests run on the class path so test-only service providers work), JUnit `junit-jupiter` 5.10.2 test scope for all modules. Core: depends on `javax.measure:unit-api:2.2`. `module-info.java`: `module org.tinygears.uom.core { requires transitive java.measure; exports org.tinygears.uom.core; exports org.tinygears.uom.core.parse; }` (create both packages with a `package-info.java` so the exports compile).
- [ ] **Step 5: Run `mvn -q -B verify`.** Expected: BUILD SUCCESS, 1 test passed.
- [ ] **Step 6: Commit** `build: replace the sketch with the uom-core skeleton`.

### Task 2: Exact factors

**Files:**
- Create: `uom-core/src/main/java/org/tinygears/uom/core/Factor.java`
- Test: `uom-core/src/test/java/org/tinygears/uom/core/FactorTest.java`

**Interfaces:**
- Produces: `public final class Factor` with value = `(n/d * pi^k)^(1/r)`, immutable, normalized (gcd-reduced, `d > 0`, exact roots extracted so `r == 1` whenever the radicand has an exact root, pi exponent folded), equals/hashCode on the normal form:
  - constants `ZERO`, `ONE`, `PI`
  - `static Factor of(long n, long d)`, `of(BigInteger n, BigInteger d)`, `ofLong(long)`, `decimal(String)` (plain decimals with optional `e` exponent, exact)
  - `Factor multiply(Factor)`, `divide(Factor)`, `add(Factor)`, `negate()`, `pow(int)`, `root(int)`
  - `boolean isExact()`, `isZero()`, `isOne()`, `int signum()`
  - `double doubleValue()`, `BigDecimal decimalValue(MathContext)`

- [ ] **Step 1: Write the failing tests** in `FactorTest` (all assertions use `Factor.equals` unless stated):
  - `normalizes`: `of(2,4)` equals `of(1,2)`; `of(1,-2)` equals `of(-1,2)`.
  - `decimalLiteralIsExact`: `decimal("0.0254")` equals `of(254,10000)`; `decimal("1e3")` equals `ofLong(1000)`; `decimal("-1.5")` equals `of(-3,2)`.
  - `arithmetic`: `of(1,3).multiply(of(3,4))` equals `of(1,4)`; `of(1,3).add(of(1,6))` equals `of(1,2)`; `ofLong(1).divide(ofLong(3)).multiply(ofLong(3))` equals `ONE`.
  - `piIsTracked`: `PI.multiply(PI.pow(-1))` equals `ONE`; `PI.divide(ofLong(180)).doubleValue()` within `1e-15` of `Math.PI/180`; `PI.add(ONE)` throws `ArithmeticException`.
  - `piToFiftyDigits`: `PI.decimalValue(new MathContext(50))` equals `new BigDecimal("3.1415926535897932384626433832795028841971693993751")`.
  - `rootsNormalize`: `of(1,4).root(2)` equals `of(1,2)`; `ofLong(2).root(2).isExact()` is false; `ofLong(2).root(2).pow(2)` equals `ofLong(2)`; `ofLong(2).root(2).multiply(ofLong(2).root(2))` equals `ofLong(2)`; `ofLong(2).root(2).doubleValue()` equals `1.4142135623730951`; `ofLong(2).root(2).decimalValue(new MathContext(30))` equals `new BigDecimal("1.41421356237309504880168872421")`; `ofLong(2).root(2).add(ONE)` throws `ArithmeticException`; `ofLong(-4).root(2)` throws `ArithmeticException`.
  - `zeroAndLimits`: `ONE.divide(ZERO)` and `ZERO.pow(-1)` throw `ArithmeticException`; `ofLong(7).pow(0)` equals `ONE`; `ofLong(10).pow(100_000)` and `ofLong(2).root(100_000)` throw `ArithmeticException` (limit: `|n| > 1000`).
- [ ] **Step 2: Run** `mvn -q -B -pl uom-core test -Dtest=FactorTest`. Expected: FAIL (class missing).
- [ ] **Step 3: Implement `Factor`.** Store radicand as `BigInteger num, den`, `int piExp`, plus `int index`. `multiply`/`divide` of mixed indexes go to the lcm index (`a^(1/2) * b^(1/3) = (a^3 b^2)^(1/6)`). Normalization extracts perfect r-th powers and reduces the index by common divisors of index and exponents. `add` requires equal index (after normalization) and equal pi exponent, otherwise `ArithmeticException`. Compute pi with Machin's formula to `precision + 10` digits and cache it per precision; compute decimal roots by Newton iteration at `precision + 5` digits. Document the ceiling in a `ponytail:` comment: equality of irrational factors is structural, so `8^(1/2)` and `2 * 2^(1/2)` compare unequal.
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** `feat: add exact factors`.

### Task 3: Converters

**Files:**
- Create: `uom-core/src/main/java/org/tinygears/uom/core/UomConverter.java`, `Converters.java`
- Test: `uom-core/src/test/java/org/tinygears/uom/core/ConvertersTest.java`

**Interfaces:**
- Consumes: `Factor` (Task 2).
- Produces:
  - `public interface UomConverter extends javax.measure.UnitConverter` with `BigDecimal convert(BigDecimal value, MathContext mc)`, `UomConverter inverse()`, `UomConverter concatenate(UnitConverter other)` (covariant; throws `IllegalArgumentException` if `other` is not a `UomConverter`), `Factor factor()`, `Factor offset()`.
  - `public final class Converters` with `static final UomConverter IDENTITY`, `static UomConverter affine(Factor factor, Factor offset)` (`x -> factor*x + offset`), `scale(Factor)`, `shift(Factor)`.
- Semantics: `a.concatenate(b)` converts by `b` first, then `a` (the standard's order). The result is one affine converter (factor `a.f*b.f`, offset `a.f*b.o + a.o`) that also remembers its steps for `getConversionSteps()`. Equality is on `(factor, offset)` only. `convert(Number)` returns a `Double` for `Float`/`Double`/integral input and a `BigDecimal` (DECIMAL128) for `BigDecimal` input.

- [ ] **Step 1: Write the failing tests** in `ConvertersTest`:
  - `affineConvertsDouble`: `affine(ONE, Factor.decimal("273.15")).convert(100.0)` within `1e-12` of `373.15`; its `inverse().convert(373.15)` within `1e-12` of `100.0`.
  - `affineConvertsDecimalExactly`: fahrenheit to celsius `affine(of(5,9), of(-160,9)).convert(new BigDecimal("212"), MathContext.DECIMAL128).compareTo(new BigDecimal("100")) == 0`.
  - `numberKeepsType`: `convert(Double.valueOf(1))` is a `Double`; `convert(new BigDecimal("1"))` is a `BigDecimal`.
  - `concatenateOrder`: `scale(ofLong(2)).concatenate(shift(ofLong(1))).convert(3.0)` equals `8.0`.
  - `concatenationEqualsFusedForm`: `scale(ofLong(2)).concatenate(scale(ofLong(3)))` equals `scale(ofLong(6))`; its `getConversionSteps()` has size 2 and `scale(ofLong(6)).getConversionSteps()` has size 1 and contains the converter itself.
  - `flags`: `IDENTITY.isIdentity()`, `scale(ofLong(2)).isLinear()`, `!shift(ofLong(1)).isLinear()`, `affine(ONE, ZERO)` equals `IDENTITY`.
  - `irrationalFactor`: `scale(ofLong(2).root(2)).convert(1.0)` equals `1.4142135623730951`.
- [ ] **Step 2: Run** `-Dtest=ConvertersTest`. Expected: FAIL.
- [ ] **Step 3: Implement** `Converters.affine` returning an internal `AffineConverter` (package-private record-like class). `convert(double)` uses the cached `double` factor and offset; the `BigDecimal` path rounds once with the given `MathContext`.
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** `feat: add affine converters`.

### Task 4: Dimension

**Files:**
- Create: `uom-core/src/main/java/org/tinygears/uom/core/UomDimension.java`
- Test: `uom-core/src/test/java/org/tinygears/uom/core/UomDimensionTest.java`

**Interfaces:**
- Produces: `public record UomDimension(int length, int mass, int time, int current, int temperature, int amount, int luminosity) implements javax.measure.Dimension`, constants `NONE`, `LENGTH`, `MASS`, `TIME`, `CURRENT`, `TEMPERATURE`, `AMOUNT`, `LUMINOSITY`; `multiply`, `divide`, `pow(int)`, `root(int)` return `UomDimension` (covariant); `getBaseDimensions()` returns `null` for a base dimension, otherwise a map from base constants to exponents (empty for `NONE`). `root(n)` throws `ArithmeticException` when `n == 0` or an exponent is not divisible by `n`. `toString()` lists nonzero exponents with symbols `L M T I Θ N J` joined by `·`, exponent shown as `^n` when `n != 1` (e.g. `L·T^-1`).

- [ ] **Step 1: Write the failing tests:** `LENGTH.getBaseDimensions()` is null; `LENGTH.divide(TIME).getBaseDimensions()` equals `Map.of(LENGTH, 1, TIME, -1)`; `LENGTH.pow(2).root(2)` equals `LENGTH`; `LENGTH.root(2)` and `LENGTH.root(0)` throw `ArithmeticException`; `NONE.multiply(LENGTH)` equals `LENGTH`; `LENGTH.divide(TIME).toString()` equals `"L·T^-1"`; `NONE.getBaseDimensions()` is empty.
- [ ] **Step 2: Run** `-Dtest=UomDimensionTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** Pure integer arithmetic on the seven fields.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat: add dimensions`.

### Task 5: Atoms, `UomUnit` basics and base units

**Files:**
- Create: `UnitAtom.java`, `UomUnit.java`, `BaseUnits.java` in `uom-core/src/main/java/org/tinygears/uom/core/`
- Test: `UomUnitTest.java`

**Interfaces:**
- Consumes: `UomDimension` (Task 4), `UomConverter`/`Converters` (Task 3).
- Produces:
  - `public record UnitAtom(String symbol, UomDimension dimension)`.
  - `public final class UomUnit<Q extends Quantity<Q>> implements javax.measure.Unit<Q>` (algebra methods are stubs throwing `UnsupportedOperationException` until Tasks 7 and 8). `public static <Q extends Quantity<Q>> UomUnit<Q> of(Map<UnitAtom,Integer> atoms, UomConverter toSystem, String symbol, String name)` (atoms normalized: zero exponents dropped, immutable, ordered by symbol); `UomUnit<Q> named(String symbol, String name)` (same atoms and converter, new labels); `Map<UnitAtom,Integer> atoms()`; `UomConverter toSystemConverter()`.
  - Implemented here: `getSymbol()`, `getName()`, `getDimension()` (sum of atom dimension times exponent), `getSystemUnit()` (same atoms, identity converter, labels derived from atoms like `m·kg/s^2`, name null), `getBaseUnits()` (`null` when the unit is a single atom with exponent 1, else a map of `UomUnit` atoms-as-units to exponents), `isCompatible(Unit<?>)` (dimension equality), `equals`/`hashCode` (atoms and converter), `toString()` (symbol if present, never null).
  - `public final class BaseUnits` with `UomUnit<Length> METRE`, `UomUnit<Mass> KILOGRAM`, `UomUnit<Time> SECOND`, `UomUnit<ElectricCurrent> AMPERE`, `UomUnit<Temperature> KELVIN`, `UomUnit<AmountOfSubstance> MOLE`, `UomUnit<LuminousIntensity> CANDELA` (atom symbols `m kg s A K mol cd`) and `UomUnit<Dimensionless> ONE` (no atoms, dimension `NONE`).

- [ ] **Step 1: Write the failing tests** in `UomUnitTest`: `METRE.getDimension()` equals `UomDimension.LENGTH`; `METRE.getBaseUnits()` is null and `ONE.getBaseUnits()` is null; a unit built with `UomUnit.of(atoms(m:1), Converters.scale(Factor.ofLong(1000)), "km", "kilometre")` has `getSystemUnit()` equal to `METRE`; `equalsIgnoresLabels` (two units with equal atoms and converter but different symbol and name are equal and have equal hash codes); `UomUnit.of(atoms(m:1), scale(1000), ...)` is not equal to `METRE`; `isCompatible` is true between metre and kilometre and false between metre and second; `getSystemUnit().getSymbol()` of `{m:1, s:-1}` equals `"m/s"`; `toString()` of an unlabelled unit is not null.
- [ ] **Step 2: Run** `-Dtest=UomUnitTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** Compute the dimension by folding `atom.dimension().pow(exp)` with `multiply`.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat: add unit atoms, UomUnit and base units`.

### Task 6: Converters between units

**Files:**
- Modify: `UomUnit.java`
- Test: `UomUnitConversionTest.java`

**Interfaces:**
- Consumes: Task 5.
- Produces on `UomUnit<Q>`: `UomConverter getConverterTo(Unit<Q>)` (throws `UnconvertibleException` for a foreign `Unit` implementation or incompatible dimension), `UomConverter getConverterToAny(Unit<?>)` (throws `IncommensurableException` when not compatible), `boolean isEquivalentTo(Unit<Q>)` (the converter between them is the identity). Converter = `that.toSystem.inverse().concatenate(this.toSystem)`, cached per target unit in a `ConcurrentHashMap` created with the unit, filled with `computeIfAbsent`.

- [ ] **Step 1: Write the failing tests:** km to m converter converts `1.5` to `1500.0`; `getConverterToAny` between metre and second throws `IncommensurableException`; `getConverterTo` with a mismatched dimension (via an unchecked cast in the test) throws `UnconvertibleException`; `isEquivalentTo` is true for two metres with different labels and false for metre and kilometre; `getConverterTo` called twice on the same target returns the same instance (`assertSame`); concurrency test `cacheIsSafeUnderContention`: 16 threads each call `getConverterTo(target)` 10_000 times on one unit, all results `assertSame`.
- [ ] **Step 2: Run** `-Dtest=UomUnitConversionTest`. Expected: FAIL.
- [ ] **Step 3: Implement** the three methods as described.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat: add unit conversion lookup`.

### Task 7: Unit algebra

**Files:**
- Modify: `UomUnit.java`
- Test: `UomUnitAlgebraTest.java`

**Interfaces:**
- Consumes: Tasks 2, 3, 5, 6.
- Produces on `UomUnit<Q>`, all returning `UomUnit` (covariant): `multiply(Unit<?>)`, `divide(Unit<?>)`, `inverse()`, `pow(int)`, `root(int)`, `multiply(Number)`, `multiply(double)`, `divide(Number)`, `divide(double)`, `shift(Number)`, `shift(double)`, `transform(UnitConverter)`, `prefix(Prefix)`, plus `UomUnit<?> scaledBy(Factor)`.
- Rules: product and quotient add or subtract atom exponents and multiply or divide the converters' factors; if either operand has a nonzero offset, throw `UnconvertibleException`. `pow(0)` returns `BaseUnits.ONE`; `pow(n != 1)` on an offset unit throws `UnconvertibleException`; `root(0)` throws `ArithmeticException`; a root whose atom exponent is not divisible throws `ArithmeticException`; exponent beyond `Factor`'s limit throws `ArithmeticException`. `transform(c)` yields a unit whose converter is `this.toSystem.concatenate(c)`. `shift(o)` is `transform(shift(o))`; `multiply(m)` is `transform(scale(m))`. `Number` to `Factor`: integral types exact; `BigInteger` exact; `BigDecimal` via `Factor.decimal(toPlainString())`; `Double`/`Float` via `BigDecimal.valueOf(d)` (shortest decimal); NaN or infinite throws `IllegalArgumentException`; dividing by zero throws `ArithmeticException`. `prefix(p)` multiplies by `p.getValue()` raised to `p.getExponent()` and prepends `p.getSymbol()` to the symbol. Derived symbols: product `a·b`, quotient `a/b`, power `a^n` when the operands have symbols, else null.

- [ ] **Step 1: Write the failing tests** in `UomUnitAlgebraTest`:
  - `footIsExact`: `METRE.multiply(3048).divide(10000).getConverterTo(METRE).convert(1.0)` within `1e-15` of `0.3048`; `isEquivalentTo(METRE.multiply(0.3048))` is true.
  - `composition`: `KILOGRAM.multiply(METRE).divide(SECOND.pow(2))` equals `KILOGRAM.multiply(METRE).divide(SECOND).divide(SECOND)`; `METRE.pow(2).root(2)` equals `METRE`; `METRE.inverse()` equals `BaseUnits.ONE.divide(METRE)`; `METRE.divide(SECOND).getSystemUnit()` equals `METRE.divide(SECOND)`.
  - `exponentEdges`: `METRE.pow(0)` equals `BaseUnits.ONE`; `METRE.pow(-2)` equals `BaseUnits.ONE.divide(METRE.pow(2))`; `METRE.root(0)`, `METRE.root(2)`, `METRE.pow(100_000)` throw `ArithmeticException`.
  - `irrationalRoot`: `METRE.pow(2).multiply(2).root(2).getConverterTo` to `METRE` converts `1.0` to `1.4142135623730951`, and squaring it (`pow(2)`) equals `METRE.pow(2).multiply(2)`.
  - `offsetUnits`: `KELVIN.shift(273.15).getConverterTo(KELVIN).convert(100.0)` within `1e-12` of `373.15`; shifting by `1` then `2` equals shifting by `3`; `multiply(METRE)`, `divide(SECOND)`, `pow(2)`, `root(2)` on it each throw `UnconvertibleException`; `pow(1)` returns an equal unit.
  - `numbers`: `METRE.multiply(Double.NaN)` throws `IllegalArgumentException`; `METRE.divide(0)` throws `ArithmeticException`; `METRE.multiply(new BigDecimal("0.0254"))` is equivalent to `METRE.multiply(254).divide(10000)`.
  - `prefix`: `METRE.prefix(MetricPrefix.KILO)` is equivalent to `METRE.multiply(1000)` and has symbol `"km"` when `METRE` is labelled `"m"`.
- [ ] **Step 2: Run** `-Dtest=UomUnitAlgebraTest`. Expected: FAIL.
- [ ] **Step 3: Implement** each method on top of `UomUnit.of` and `Converters`; use one private helper that rejects offset operands.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat: add unit algebra`.

### Task 8: Alternate units and `asType`

**Files:**
- Create: `uom-core/src/main/java/org/tinygears/uom/core/AlternateUnits.java`
- Modify: `UomUnit.java`
- Test: `AlternateUnitsTest.java`, `UomUnitAsTypeTest.java`; test provider `uom-core/src/test/java/org/tinygears/uom/core/TestServiceProvider.java` and `uom-core/src/test/resources/META-INF/services/javax.measure.spi.ServiceProvider`

**Interfaces:**
- Produces: `UomUnit<Q> alternate(String symbol)` and `<T extends Quantity<T>> Unit<T> asType(Class<T> type)`. `AlternateUnits` is package-private: `static UnitAtom register(String symbol, UomUnit<?> definition)` backed by one `ConcurrentHashMap<String, Registered>`, where `Registered` holds the atom and the defining decomposition. `alternate(symbol)` requires an unscaled system unit (identity converter), else `IllegalArgumentException`; the same symbol with the same definition returns the existing atom, a different definition throws `MeasurementException`. The result has the single atom, an identity converter, and the symbol as label. `asType(type)` compares `getSystemUnit()` with `ServiceProvider.current().getQuantityFactory(type).getSystemUnit()` and throws `ClassCastException` on mismatch; if no provider is available it throws `IllegalStateException`.

- [ ] **Step 1: Write the failing tests:**
  - `AlternateUnitsTest.radianIsDistinct`: `ONE.alternate("rad")` is not equal to `ONE`, `isCompatible(ONE)` is true, `getConverterToAny(ONE).isIdentity()` is true.
  - `sameDefinitionIsIdempotent`: calling `ONE.alternate("rad")` twice yields equal units.
  - `conflictingDefinition`: `METRE.alternate("rad")` throws `MeasurementException`.
  - `scaledUnitRejected`: `METRE.multiply(1000).alternate("km2")` throws `IllegalArgumentException`.
  - `hertzIsNotBecquerel`: `ONE.divide(SECOND).alternate("Hz")` is not equal to `ONE.divide(SECOND).alternate("Bq")`; both are compatible.
  - `registrationRace`: 16 threads registering `"race-same"` with one definition all get equal units; 16 threads where half register `"race-x"` as `METRE` and half as `SECOND` end with exactly one winner (the others throw `MeasurementException`).
  - `UomUnitAsTypeTest`: with `TestServiceProvider` returning a `QuantityFactory` whose `getSystemUnit()` is `METRE` for `Length` and `METRE.divide(SECOND)` for `Speed`: `METRE.multiply(1000).asType(Length.class)` succeeds; `METRE.asType(Speed.class)` throws `ClassCastException`.
- [ ] **Step 2: Run** `-Dtest='AlternateUnitsTest,UomUnitAsTypeTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.** `TestServiceProvider` implements the abstract methods of `ServiceProvider` (the system-of-units and format services return `null`) and `getQuantityFactory` from a `Map<Class<?>, Unit<?>>`; register it in the services file.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat: add alternate units and asType`.

### Task 9: Formula evaluator

**Files:**
- Create: `uom-core/src/main/java/org/tinygears/uom/core/parse/FormulaException.java`, `FormulaEvaluator.java`
- Test: `uom-core/src/test/java/org/tinygears/uom/core/parse/FormulaEvaluatorTest.java`

**Interfaces:**
- Consumes: `UomUnit`, `Factor`, `BaseUnits`.
- Produces: `public final class FormulaException extends Exception` with `int column()` (1-based) and message; `public final class FormulaEvaluator` with `static UomUnit<?> evaluate(String formula, Function<String, UomUnit<?>> lookup) throws FormulaException`. Grammar: `expr := term (('*' | '/' | juxtaposition) term)*` left-associative; `term := atom ('^' ['-'] int)?`; `atom := number | symbol | '(' expr ')'`; `number := decimal [e[+-]int] | int '|' int` (exact fraction, no spaces); the reserved word `pi` is the constant `Factor.PI`; a symbol is a maximal run of characters other than whitespace and `* / ( ) ^ | = # [ ] ! ~ @ ,` that does not start with a digit or `.`. A symbol the lookup does not know is a `FormulaException` ("unknown unit 'foo'").

- [ ] **Step 1: Write the failing tests** (lookup knows `m`, `kg`, `s`, `cm` as `METRE.multiply(0.01)`, `N` as `kg m / s^2`):
  - `kg m / s^2` equals `KILOGRAM.multiply(METRE).divide(SECOND.pow(2))`.
  - `0.0254 m` converts one to `0.0254` metres exactly: `getConverterToAny(METRE).convert(BigDecimal.ONE, MathContext.DECIMAL128)` compares equal to `0.0254`.
  - `1|1000 m` is equivalent to `METRE.divide(1000)`; `pi|180` is equivalent to `BaseUnits.ONE.scaledBy(Factor.PI.divide(Factor.ofLong(180)))`; `(m / s)^2` equals `METRE.divide(SECOND).pow(2)`; `s^-1` equals `SECOND.inverse()`; `2 N m` equals `N.multiply(METRE).multiply(2)`.
  - Errors, asserting message and column: `foo` ("unknown unit 'foo'", column 1), `m /` (unexpected end of formula), `m ^ x` (exponent must be an integer), `(m` (missing `)`), `1|0` (division by zero), empty string (empty formula).
- [ ] **Step 2: Run** `-Dtest=FormulaEvaluatorTest`. Expected: FAIL.
- [ ] **Step 3: Implement** a hand-written recursive-descent parser over a char index (no regex, no parser library).
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat: add the unit formula evaluator`.

### Task 10: Definition files

**Files:**
- Create in `uom-core/src/main/java/org/tinygears/uom/core/parse/`: `DefinitionException.java`, `Definition.java`, `Preferred.java`, `Definitions.java`, `AffineFunction.java`, `DefinitionParser.java`
- Test: `DefinitionParserTest.java`

**Interfaces:**
- Consumes: `FormulaEvaluator` (Task 9), `UomUnit`, `BaseUnits`, `Converters`.
- Produces:
  - `public final class DefinitionException extends RuntimeException` with `String source()`, `int line()`; message format `source:line: text`.
  - `public record Definition(String symbol, String name, UomUnit<?> unit, String explicitType, String source, int line)` (`explicitType` null when absent), `public record Preferred(String quantityType, String symbol, String source, int line)`, `public record Definitions(List<Definition> definitions, List<Preferred> preferred)` (file order, includes first).
  - `public final class DefinitionParser` with `interface Resolver { Reader open(String name) throws IOException; }` and `static Definitions parse(String source, Reader reader, Resolver resolver)`.
- Syntax (one definition per line): `symbol[:name] body [Type] [# comment]`. Underscores in the name become spaces; symbols keep them. `body` is one of: `!X` base unit where `X` is `L M T I Θ N J` (`K` accepted as an alias for `Θ`) and the line's symbol must equal the base atom's symbol; `~ [formula]` alternate unit (formula evaluates to an unscaled unit; bare `~` means dimensionless); `ref = function` affine definition (`ref` a known symbol, `function` an expression in `x` with numbers, `+ - * /`, `( )`, fractions `a|b`, which must reduce to `a*x + b`, else error "function is not affine", evaluated by `AffineFunction`; the unit is `ref.transform(affine(a, b))`); otherwise a formula. `[Type]` must match `[A-Z][A-Za-z0-9]*`. `@include name` reads another file through the resolver once (a diamond is processed once; a cycle is an error). `@preferred Type symbol` needs an already defined symbol. Blank lines and `#` lines are ignored. A leading UTF-8 BOM is ignored; CRLF and tabs are accepted.

- [ ] **Step 1: Write the failing tests** in `DefinitionParserTest`. Happy path: parse a file named `test.units`:
  ```
  # comment
  m:metre            !L
  kg:kilogram        !M
  s:second           !T
  K:kelvin           !Θ
  rad:radian         ~
  Hz:hertz           ~ 1/s   [Frequency]
  N:newton           kg m / s^2 [Force]
  in:inch            0.0254 m
  °C:degree_celsius  K = x + 273.15
  @preferred Length m
  ```
  Assertions: symbols in order are `m kg s K rad Hz N in °C`; `Hz.explicitType()` is `"Frequency"`, `N.explicitType()` is `"Force"`, `in.explicitType()` is null; `°C.name()` equals `"degree celsius"`; `°C` converts `100` to `373.15` kelvin within `1e-12`; `Hz.unit()` is not equal to `ONE.divide(SECOND)` but is compatible with it; `in.unit()` is equivalent to `METRE.multiply(254).divide(10000)`; `preferred()` has one entry (`Length`, `m`); every `Definition` carries `source` and `line`.
  - Errors (assert `getMessage()` equals the quoted text): line 3 `x:foo bar` gives `test.units:3: unknown unit 'bar'`; a duplicate `m` on line 5 gives `test.units:5: duplicate symbol 'm'`; `m:metre !Q` gives `... unknown base dimension 'Q'`; `kg:kilogram !L` gives `... base unit symbol must be 'm'`; `@include missing.units` with a resolver that throws `FileNotFoundException` gives `... cannot include 'missing.units'`; two files including each other give `... include cycle: a.units -> b.units -> a.units`; `x:y m [lower]` gives `... malformed quantity type 'lower'`; `x:y m = x * x` gives `... function is not affine`; `x:y ~ 1000 m` gives `... alternate unit must be an unscaled unit`; `@preferred Length cm` before `cm` exists gives `... unknown unit 'cm'`.
  - Robustness (Review Focus 1): the happy-path text with a leading BOM, with CRLF line endings, and with tabs instead of spaces each produces `Definitions` equal in symbols and units to the plain version; an empty reader yields empty lists; whitespace-only lines are ignored; `Ω:ohm m` and `µ:micro 1|1000000` define symbols `"Ω"` and `"µ"` and look up correctly.
  - Includes: a diamond (`a` includes `b` and `c`, both include `d`) yields `d`'s definitions once; an included file's definitions appear before the including file's own.
- [ ] **Step 2: Run** `-Dtest=DefinitionParserTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** Read lines with `BufferedReader` and strip the BOM from the first line. Split each line at the first whitespace after the symbol, strip the `#` comment (not inside `[ ]`), then dispatch on the body form. Translate `FormulaException` into `DefinitionException` with the line number. Keep a symbol table (`Map<String, UomUnit<?>>`) used as the evaluator's lookup. `AffineFunction`: evaluate the expression with `x` as the affine form `(1, 0)`; a product of two forms with nonzero `x` coefficients is an error.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat: add the unit definition parser`.

### Task 11: Module wrap-up

**Files:**
- Modify: `uom-core/src/main/java/module-info.java` (if needed), `README.md` (create, short: purpose, status, link to `docs/`)
- Test: the whole module

- [ ] **Step 1: Run** `mvn -q -B clean verify` on JDK 21 and on the newest installed JDK (`JAVA_HOME=... mvn ...`). Expected: BUILD SUCCESS on both, all tests pass.
- [ ] **Step 2: Add `ModulePathTest`** (`uom-core/src/test/java/org/tinygears/uom/core/ModulePathTest.java`, `describesModuleOnModulePath`): run `java --module-path uom-core/target/classes:<unit-api jar> --describe-module org.tinygears.uom.core` through `ProcessBuilder` and assert exit code 0 and that the output contains `exports org.tinygears.uom.core.parse`. Skip with `Assumptions.assumeTrue` when `uom-core/target/classes` does not exist.
- [ ] **Step 3: Commit** `test: verify the module descriptor` and `docs: add the README`.

### Task 12: TCK spike

**Files:**
- Create: `docs/spikes/2026-tck-m1.md` (the only artifact kept; spike code stays on a throwaway branch `spike/tck`, not merged)

**Interfaces:**
- Consumes: the finished `uom-core`.
- Produces: a findings document with sections Setup, What ran, What failed and why, Recommendation.

- [ ] **Step 1: Branch `spike/tck`** from `m1-core`. Add `tech.units:unit-tck:2.2` and `org.testng:testng` as test dependencies in a scratch module `uom-tck-spike`, plus a stub `ServiceProvider` (returning `null` services except the parts the unit tests need).
- [ ] **Step 2: Try to run** the TCK's unit-level tests (e.g. `UnitTest`, `UnitConverterTest`, `DimensionTest`) against `UomUnit`. Record: does the suite start without Indriya on the class path; which test classes execute; each failure with the root cause (our bug, a missing quantity implementation, or an Indriya assumption).
- [ ] **Step 3: Write `docs/spikes/2026-tck-m1.md`** answering: can the TCK run against a non-Indriya implementation (yes, no, partly), what must be in place for milestone 3, and a recommendation (use the TCK as is, use it with a thin adapter, or write our own contract tests from the Javadoc, as the risk section of the requirements anticipates). Fix real bugs in `uom-core` found on the way on `m1-core` with a regression test each.
- [ ] **Step 4: Commit** the findings document on `m1-core`: `docs: record the TCK spike results`.

---

## Self-review notes

- **Spec coverage (milestone 1):** units core and equality (design section 3): Tasks 2 to 8; exact factors and pi (N2): Task 2; alternate registry and `asType`: Task 8; definition syntax, errors with file and line, includes, `[Type]`, `@preferred` (F15, F16 parsing side, F19): Tasks 9 and 10; TCK spike (N1, risk): Task 12. Deliberately not in this milestone: prefix parsing of symbols such as `km` (milestone 3), formatters and `UnitFormat` (milestone 6), quantities (milestones 2 and 4), the generator (milestone 2), serialization (milestone 6).
- **Spec deviation to confirm:** `Factor` carries a root index so `root(n)` never fails on a non-perfect power while converters stay affine. The design said "numeric converter" for this case; the factor-level representation keeps `equals` structural.
