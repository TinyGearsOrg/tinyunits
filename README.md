# uom

A Java library for typed quantities and units of measurement, implementing JSR-385 (`javax.measure`, unit-api 2.2). It is in development.

Status: milestone 6 done (units core, typed `double` and `BigDecimal` quantities for all 33 quantity types, seven catalogs with prefixes, `quantity.to(system)`, unit and quantity formats with locale-aware numbers, a decimal-preserving parse and a faithful form for offset units, Java serialization of units and quantities, user `.units` files loaded at run time, the JSR-385 service provider). The systems are `SI` (128 distinct units), `USCustomary` (35), `Imperial` (42), `CGS` (16), `ESU` (22), `EMU` (29) and `Gaussian` (26); `quantity.to(system)` converts to the system's preferred unit for the quantity type. The JSR-385 TCK (`unit-tck` 2.2) passes: 113 of 113 tests; `mvn -q -B clean verify` runs 1068 tests (core 504, codegen 162, uom 288, tck 113, examples 1). Release engineering is milestone 7. Not released.

## Modules

- `uom-core`: units, dimensions, converters, the `.units` parser, the shared `double` and `BigDecimal` quantity base classes.
- `uom`: the library to depend on (JPMS module `org.tinygears.uom`, requires `uom-core` transitively). Package `org.tinygears.uom.quantity`: generated typed quantity interfaces, `double` and `BigDecimal` implementations and factories (33 types, 133 generated files). Package `org.tinygears.uom.systems`: the generated catalogs (`SI`, `USCustomary`, `Imperial`, `CGS`, `ESU`, `EMU`, `Gaussian`), `UomSystemOfUnits`, unit and quantity formats, the `ServiceProvider`.
- `uom-codegen`: the generator (internal, not published).
- `uom-tck`: runs the JSR-385 TCK against `uom` (test only, not published).
- `uom-examples`: runnable usage examples (not published); see below.

Build and test everything: `mvn -q -B clean verify`.

Dependency (not published yet):

```xml
<dependency>
  <groupId>org.tinygears</groupId>
  <artifactId>uom</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

See [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) and [docs/DESIGN.md](docs/DESIGN.md).

## Examples

[`Examples.java`](uom-examples/src/main/java/org/tinygears/uom/examples/Examples.java) shows typed quantities and conversion, dividing quantities and `asType`, offset units, `equals` versus `isEquivalentTo`, unit and quantity parsing and formatting, and use through the JSR-385 `ServiceProvider`. `decimals()` shows `BigDecimal` quantities: `0.1 m + 0.2 m` is exactly `0.3 m`, unlike with `double`. `mixedPrecision()`, `contexts()`, `decimalEquality()` and `decimalFormat()` show the precision rule (a `double` operand makes the result a `double`), `MathContext` and `toDecimal`/`toDouble`, `equals` versus `isEquivalentTo` across precisions, and decimal formatting. `systems()` shows `quantity.to(system)`: 1 ft in CGS is `30.48 cm`, 100 °C in US customary is `212.0 °F` (exactly `212 °F` for a `BigDecimal`), the US gallon (3.785411784 L) against the imperial gallon (4.54609 L), and 1 C in Gaussian units. `serialization()` round-trips a unit and a quantity through a byte array, `locales()` prints and parses `1,5 km` in German, `offsetUnits()` formats an unnamed shifted kelvin as `K + 5` and parses it back, `decimalParse()` keeps the scale of `1.50 km`, and `userUnits()` loads a small `.units` text, parses `2 league` and converts it with `to(system)`. `ExamplesTest` checks the printed output. Run it after a build with:

```
mvn -q -B -pl uom-examples -am install -DskipTests
mvn -q -B -pl uom-examples exec:java -Dexec.mainClass=org.tinygears.uom.examples.Examples
```

## Typed quantities and unit declarations

The typed interfaces have the simple names of the standard ones (`org.tinygears.uom.quantity.Length` extends `javax.measure.quantity.Length`), so a file that imports ours cannot also import the standard type. Declare units as `Unit<?>` or with `var` (or name the standard type fully qualified), and quantities with our types:

```java
var km = SI.KILOMETRE;                       // UomUnit<javax.measure.quantity.Length>
Length run = Length.of(10, km);
```

## Formats

`UomUnitFormat.standard()` parses and formats unit symbols and formulas (`kg·m/s^2`) over all seven systems. `new UomQuantityFormat(unitFormat)` formats a quantity as value, space, unit, in `Locale.ROOT`; it is immutable:

- `withLocale(Locale.GERMANY)` writes and reads the number in that locale (`1,5 km`); unit symbols are never localized. Grouping separators are never written and are a parse error unless `withGrouping(true)`.
- `withDecimals(MathContext.DECIMAL64)` parses to `BigDecimal` quantities that keep every digit and the scale of the text (`1.50 km` has scale 2); the default parses to `double`.
- An offset unit without a symbol formats as a shifted formula (`SI.KELVIN.shift(5)` is `K + 5`, `5|9·K + 459.67` is the degree Fahrenheit) and parses back to an equal unit. Units with a negative or root factor, an irrational shift or a formula over 10,000 characters have no faithful form: `format` throws `IllegalArgumentException`. A glued minus before an integer (`s-1`) is an error: write `s^-1` or `s - 1`.

## Serialization

Units and quantities are `Serializable` through serialization proxies, which rebuild every object through the public factories, so the reader re-checks the invariants and rejects a forged stream that violates them with `InvalidObjectException` (one known gap: the steps of a multi-step converter that do not fold exactly and have a part over 4096 bits are not checked against its stored factor and offset). A quantity reads back through the factory of the current `ServiceProvider`. The serialized form may change before 1.0. Systems and formats are not serializable.

Never read untrusted streams without an `ObjectInputFilter`: the JDK allocates the arrays and strings of a stream before our checks run. For example, as `-Djdk.serialFilter=<filter>` or per stream with `in.setObjectInputFilter(ObjectInputFilter.Config.createFilter(filter))`:

```
maxarray=65536;maxbytes=1048576;maxdepth=20;maxrefs=10000;org.tinygears.uom.core.**;org.tinygears.uom.quantity.*;javax.measure.**;java.lang.*;java.math.*;!*
```

The allow-list needs `org.tinygears.uom.quantity` too, because the filter also checks the classes the proxies resolve to; `serialization()` in the examples reads through this filter.

## Your own units

A `.units` file defines units, one per line as `symbol:name formula` (`_` in a name is a space). They load as `Unit<?>`; narrow them to a built-in quantity type with `asType`:

```
@system Medieval                # system name (else the file name)
league:league   3 mi            # formulas use the built-in symbols
furlong:furlong 220 yd
rod:rod         1|40 furlong    # exact fraction
@prefixed league k              # adds kleague
@preferred Length furlong       # q.to(system) gives furlongs
```

```java
UomSystemOfUnits medieval = UomSystemOfUnits.load(Path.of("medieval.units"));
UomQuantityFormat format = new UomQuantityFormat(UomUnitFormat.standard().with(medieval));
Length d = (Length) format.parse("2 league").asType(javax.measure.quantity.Length.class);
d.to(medieval);                                  // 48.0 furlong
```

`load(String name, Reader, Resolver)` reads from any `Reader`. Formulas may use the built-in symbols (exact catalog symbols and metric prefixes, not labels), and a file may not redefine one. `@preferred Type symbol` names a standard type by simple name; a type without a line takes the preferred unit of the first `@extends`-ed system, else `SI`'s. `@include other.units` makes another user file's units usable in formulas (`@extends` also makes them members); `load(Path)` resolves such names relative to the root file, inside its directory, and the `Reader` variant through the given `Resolver`. User files cannot include the built-in data files. Nothing is registered globally: `with` returns a new format, and a symbol of the new system that means a different unit elsewhere is an `IllegalArgumentException`. The exception is `~` (alternate unit) lines, which register their symbol in the JVM-wide registry for the life of the JVM.

## Generated sources

The sources in `uom/src/main/java/org/tinygears/uom/quantity` and the catalogs in `uom/src/main/java/org/tinygears/uom/systems/` (`SI.java`, `USCustomary.java`, `Imperial.java`, `CGS.java`, `ESU.java`, `EMU.java`, `Gaussian.java`) are generated by `uom-codegen` from `data/quantity-types.txt` and the `.units` files in `data/`, and are committed. After changing `data/`, regenerate from the repository root (the `generate` profile passes `<dataDir> <quantitiesOut> <systemsOut>`):

    mvn -q -B -Pgenerate -pl uom-codegen -am process-classes

`GeneratedSourcesUpToDateTest` fails when `data/` and the committed sources disagree.
