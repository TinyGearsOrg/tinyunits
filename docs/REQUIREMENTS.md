# tinyunits: Requirements

Status: draft for review. Nothing here is implemented; the code currently in the repository is a
throwaway sketch of the annotation-processor idea and will be replaced.

## 1. Purpose

A general-purpose, published open-source Java library for quantities and units of measurement,
restarted from the unfinished `netomi/uom`. It targets the `javax.measure` (JSR-385) API and
differs from existing implementations (e.g. Indriya) in two ways:

1. **Fully typed quantities.** `Length`, `Mass`, `Speed`, ... are real classes that implement the
   standard interfaces, not just `Quantity<Length>` instances of one generic class.
2. **Multiple unit systems** with conversion between them, as a first-class feature.

Success means a developer can use tinyunits wherever JSR-385 code is expected, gets typed values
and catalogs for SI, Imperial/US customary and CGS units, and has no runtime reflection or
dynamic proxies in the picture.

## 2. Scope

### In scope for 1.0

| ID | Requirement |
|----|-------------|
| F1 | Implement the JSR-385 API (`javax.measure`): `Quantity`, `Unit`, `UnitConverter`, `Dimension`, `SystemOfUnits`, service provider. Target version: `javax.measure:unit-api` 2.2 (latest, May 2023; 15 methods on `Quantity`, 25 on `Unit`). Older versions are not supported. |
| F2 | Own implementation of the units side: no dependency on Indriya or any other JSR-385 implementation at runtime. |
| F3 | Typed quantity classes generated at compile time by an annotation processor from annotated interfaces. No proxies, no runtime reflection for quantity creation. |
| F4 | Generated quantities implement the standard quantity interfaces (`javax.measure.quantity.Length`, ...) where one exists, and the user's own interface otherwise. A typed quantity is accepted anywhere `Quantity<Q>` is. |
| F5 | Two numeric precisions from the first release: `double` and `BigDecimal`. Both are generated for every quantity type. Mixing precisions in one operation has a single, documented promotion rule. |
| F6 | Unit catalogs: SI (all prefixes, plus accepted non-SI units such as minute, hour, litre, tonne, electronvolt), Imperial / US customary (US and imperial gallons distinguished, international vs US survey foot distinguished), CGS mechanical, and CGS electromagnetic (ESU, EMU, Gaussian). |
| F7 | CGS electromagnetic units are conversion conventions: a statcoulomb is a unit of `ElectricCharge` with a numeric factor to the coulomb. They are not modelled as separate dimension sets (see the open questions). |
| F8 | Typed system conversion: `quantity.to(system)` converts to the system's preferred unit for that quantity type (e.g. CGS gives centimetre for a length). A unit system maps quantity types to their preferred unit. |
| F9 | Unit parsing and formatting (e.g. `"km/h"`, `"m/s^2"`), and quantity formatting (value plus unit symbol). |
| F10 | Unit algebra per JSR-385: multiply, divide, pow, root, prefixes, shift (offset units such as degree Celsius and Fahrenheit), transform, alternate units, and base-unit decomposition. |
| F11 | Compatibility checking: combining incompatible units fails with a clear unchecked exception naming both units. |
| F12 | Quantities and units are immutable, thread-safe and `Serializable`. |
| F13 | A `module-info.java` (JPMS) for every published module. |
| F14 | Users can define their own quantity types and units with the same annotation and generator as the built-in ones. |

### Explicitly out of scope for 1.0

- Typed quantity products and quotients (`Length / Time` returning `Speed`, `Length * Length`
  returning `Area`). JSR-385 `multiply` and `divide` return `Quantity<?>`; narrowing is by
  `asType(Class)`. Declared relations can be added in a later release.
- Modelling ESU/EMU/Gaussian as distinct dimension systems (option B in the design notes).
- Arbitrary-precision support for transcendental conversions beyond what `BigDecimal` and
  `MathContext` give.
- Database, JSON and framework integrations.
- Android or Java versions below the baseline.

## 3. Constraints

- Build with Maven. Multi-module.
- Java baseline 17 (assumed; confirm against the downstream audience).
- License Apache-2.0 (already in the repository).
- No dynamic proxies. Annotation processing must work on JDK 17 through the current JDK,
  including JDK 23+ where classpath processors are not run unless enabled; the documentation
  and build setup must cover this.
- The generator emits plain Java source via the standard `Filer`; a code-generation library
  dependency is allowed only if it earns its weight.
- Runtime dependencies: only the JSR-385 API artifact. Annotation processor and catalog
  generators are build-time only.

## 4. Non-functional requirements

| ID | Requirement |
|----|-------------|
| N1 | **Conformance:** pass the JSR-385 compatibility tests (`tech.units:unit-tck` 2.2) for the implemented API. If the TCK cannot be run against a non-Indriya implementation, write an equivalent contract test suite. |
| N2 | **Correctness:** unit conversions use exact definitions where the definition is exact (inch = 0.0254 m, c = 299 792 458 m/s). Factors are expressed as exact rationals or `BigDecimal` in the catalog source and rounded once for `double`. |
| N3 | **Performance:** `double` quantity operations allocate at most the result object; no reflection, boxing or map lookups on the hot path. Converters between two units are cached or precomputed. |
| N4 | **Compile-time safety:** adding quantities of different types, or passing a unit of the wrong quantity to a typed factory, is a compile error wherever the standard API allows it. Where the API forces a looser parameter type (`Quantity<Length>` rather than `Length`), return types stay typed. |
| N5 | **Diagnostics:** the processor reports misuse as compile errors attached to the offending element (wrong shape of annotated type, duplicate names, unknown dimension). |
| N6 | **Testing:** unit tests per module, a processor test that compiles sample inputs and asserts on diagnostics and generated behaviour, and a property-style round-trip test over every catalog unit (convert to the system unit and back within tolerance). |
| N7 | **Documentation:** README with a five-minute example, a guide for defining custom quantities, and a table of catalog units with their systems. |
| N8 | **Release:** versioned releases on Maven Central under a stable group id, semantic versioning, a public API definition (what is stable) in the docs. |

## 5. Proposed module layout (for discussion, to be settled in the design)

| Module | Contents |
|--------|----------|
| `tinyunits-api` | Our own annotations and shared types not covered by JSR-385 |
| `tinyunits-core` | Units, converters, dimensions, prefixes, parser, formatter, unit-system service |
| `tinyunits-processor` | The annotation processor (build time) |
| `tinyunits-quantities` | Standard typed quantity types with generated implementations (double and BigDecimal) |
| `tinyunits-systems` | Unit catalogs: SI, Imperial/US customary, CGS, CGS electromagnetic |

## 6. Open questions

1. ~~**JSR-385 version and TCK.**~~ Resolved: unit-api 2.2, TCK `unit-tck` 2.2 exists. Still to
   verify in a spike: whether the TCK runs against an implementation other than Indriya.
2. **Custom quantity dimensions.** For a user-defined quantity type, how does the dimension get
   declared and checked (annotation attributes, a registered `Dimension` constant, or derived
   from the system unit)?
3. **Precision promotion.** When a `double` quantity is combined with a `BigDecimal` one, which
   precision wins, and is mixing allowed at all?
4. ~~**Equality.**~~ Resolved: `equals` is value and unit; `isEquivalentTo` (in the API since
   2.1) means the same physical quantity.
5. **Catalog authoring.** Hand-written Java constants, or a data file (in the style of GNU
   units) from which the catalogs are generated at build time? Hundreds of units favour a data
   file.
6. **Java baseline.** Is 17 right, or should a lower baseline be supported for the API artifact?
7. **Group id and naming.** Final Maven group id, package prefix and artifact names for the
   Central release.

## 7. Risks

- **Scope.** Own implementation of the whole unit API plus two precisions plus four unit
  systems is a large project. Mitigation: staged milestones (core and double first, then
  BigDecimal, then catalogs) with each stage releasable.
- **TCK portability.** The TCK's POM references Indriya and TestNG; it may assume the reference
  implementation. Mitigation: spike early; fall back to our own contract tests (N1).
- **API friction.** The standard fixes parameter types such as `Quantity<Length>`, so
  generated types cannot be stricter than the standard on inputs.
- **Generated code volume.** Two precisions per quantity type multiplies generated classes.
  Mitigation: keep generated classes thin and push shared logic into non-generated base
  classes.
- **Electromagnetic CGS semantics.** Treating ESU/EMU/Gaussian as conversion factors converts
  values but not formulas; the documentation must say so.

## 8. Acceptance criteria for 1.0

1. A user can write `@QuantityType`-style annotated interfaces, build with Maven, and get typed
   `double` and `BigDecimal` quantities with no manual implementation.
2. Typed quantities from tinyunits are accepted by generic JSR-385 code, and the conformance
   tests (N1) pass.
3. All four unit systems in F6 are available as catalogs, with the round-trip test (N6)
   passing for every unit.
4. Units can be parsed from and formatted to their symbols, and quantities are formatted.
5. Quantities and units survive Java serialization round trips.
6. The library works on the module path and on the class path, on JDK 17 and the current JDK.
7. Artifacts are published to Maven Central with the documentation from N7.
