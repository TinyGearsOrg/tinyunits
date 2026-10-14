# uom

A Java library for typed quantities and units of measurement, implementing JSR-385 (`javax.measure`, unit-api 2.2). It is in development.

Status: milestone 6 done (units core, typed `double` and `BigDecimal` quantities for all 33 quantity types, seven catalogs with prefixes, `quantity.toSystem(system)`, unit and quantity formats with locale-aware numbers, a decimal-preserving parse and a faithful form for offset units, Java serialization of units and quantities, user `.units` files loaded at run time, the JSR-385 service provider). The systems are `SI` (128 distinct units), `USCustomary` (35), `Imperial` (42), `CGS` (16), `ESU` (22), `EMU` (29) and `Gaussian` (26, listed as 27 symbols in [docs/UNITS.md](docs/UNITS.md) because `Fr` and `statC` are the same unit); `quantity.toSystem(system)` converts to the system's preferred unit for the quantity type. The JSR-385 TCK (`unit-tck` 2.2) passes: 113 of 113 tests; `mvn -q -B clean verify` runs 1150 tests (core 512, codegen 171, uom 352, tck 113, examples 2). Milestone 7 prepared the release (POM metadata, an inactive `release` profile, the public API pin, the generated unit table, the documentation set, `scripts/verify-release.sh` and a CI workflow on JDK 21 and 25); nothing has been published. Not released.

## Five minutes

```java
Length marathon = Length.of(42.195, SI.KILOMETRE);
System.out.println(marathon.to(USCustomary.MILE));                 // prints: 26.218757456454306 mi
Speed pace = (Speed) marathon.divide(Time.of(3, SI.HOUR)).asType(javax.measure.quantity.Speed.class);
System.out.println(pace.to(USCustomary.MILE_PER_HOUR));             // prints: 8.739585818818103 mph
Temperature boiling = Temperature.of(100, SI.DEGREE_CELSIUS);
System.out.println(boiling.toSystem(USCustomary.SYSTEM));                 // prints: 212.0 °F
Length exact = Length.of(new BigDecimal("0.1"), SI.METRE).add(Length.of(new BigDecimal("0.2"), SI.METRE));
System.out.println(exact);                                          // prints: 0.3 m
UomQuantityFormat format = new UomQuantityFormat(UomUnitFormat.standard());
System.out.println(format.parse("20 km").asType(javax.measure.quantity.Length.class).to(SI.METRE));
// prints: 20000.0 m
```

Quantities are typed (`Length`, `Speed`, ... for all 33 JSR-385 quantity types), in `double` or exact `BigDecimal`
precision; the catalogs `SI`, `USCustomary`, `Imperial`, `CGS`, `ESU`, `EMU` and `Gaussian` hold the units
([docs/UNITS.md](docs/UNITS.md) lists them all), and `quantity.toSystem(system)` converts to the preferred unit of a system.

## Install

Java 21 or later. Not published yet; after `mvn -q -B install -DskipTests` in this repository:

```xml
<dependency>
  <groupId>org.tinygears</groupId>
  <artifactId>uom</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

`uom` depends on `uom-core` and the JSR-385 API; the only third-party runtime dependency is
`javax.measure:unit-api` 2.2. On the module path, `requires org.tinygears.uom;`
(module `org.tinygears.uom` requires `org.tinygears.uom.core` and `java.measure` transitively).

## Documentation

- [docs/CUSTOM-UNITS.md](docs/CUSTOM-UNITS.md): define your own units in `.units` files and load them at run time.
- [docs/PROVIDERS.md](docs/PROVIDERS.md): uom next to Indriya or another JSR-385 implementation: which provider is
  current and why uom works with either.
- [docs/OFFSET-UNITS.md](docs/OFFSET-UNITS.md): units with an offset (°C, °F): points and differences, `Quantity.Scale`,
  and what conversion, sums, scaling and comparison do for each.
- [docs/UNITS.md](docs/UNITS.md): every unit of the built-in catalogs (generated).
- [docs/API.md](docs/API.md): what the public API is, semantic versioning, deprecation.
- [SECURITY.md](SECURITY.md): serialization filter, reporting a vulnerability.
- [CHANGELOG.md](CHANGELOG.md): changes per release.
- [CONTRIBUTING.md](CONTRIBUTING.md): build, regeneration, tests, conventions.
- [docs/RELEASING.md](docs/RELEASING.md): the release procedure (for the maintainer).
- [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md), [docs/DESIGN.md](docs/DESIGN.md) and [docs/TODO.md](docs/TODO.md):
  requirements, design and open decisions.
- [LICENSE](LICENSE): Apache License 2.0.

## Modules

- `uom-core`: units, dimensions, converters, the `.units` parser, the shared `double` and `BigDecimal` quantity base classes.
- `uom`: the library to depend on (JPMS module `org.tinygears.uom`, requires `uom-core` transitively). Package `org.tinygears.uom.quantity`: generated typed quantity interfaces, `double` and `BigDecimal` implementations and factories (33 types, 133 generated files). Package `org.tinygears.uom.systems`: the generated catalogs (`SI`, `USCustomary`, `Imperial`, `CGS`, `ESU`, `EMU`, `Gaussian`), `UomSystemOfUnits`, unit and quantity formats, the `ServiceProvider`.
- `uom-codegen`: the generator (internal, not published).
- `uom-tck`: runs the JSR-385 TCK against `uom` (test only, not published).
- `uom-examples`: runnable usage examples (not published); see below.

Build and test everything: `mvn -q -B clean verify` (see [CONTRIBUTING.md](CONTRIBUTING.md)).

## Examples

[`Examples.java`](uom-examples/src/main/java/org/tinygears/uom/examples/Examples.java) shows typed quantities and conversion, dividing quantities and `asType`, offset units, `equals` versus `isEquivalentTo`, unit and quantity parsing and formatting, and use through the JSR-385 `ServiceProvider`. `decimals()` shows `BigDecimal` quantities: `0.1 m + 0.2 m` is exactly `0.3 m`, unlike with `double`. `mixedPrecision()`, `contexts()`, `decimalEquality()` and `decimalFormat()` show the precision rule (a `double` operand makes the result a `double`), `MathContext` and `toDecimal`/`toDouble`, `equals` versus `isEquivalentTo` across precisions, and decimal formatting. `systems()` shows `quantity.toSystem(system)`: 1 ft in CGS is `30.48 cm`, 100 °C in US customary is `212.0 °F` (exactly `212 °F` for a `BigDecimal`), the US gallon (3.785411784 L) against the imperial gallon (4.54609 L), and 1 C in Gaussian units. `serialization()` round-trips a unit and a quantity through a byte array, `locales()` prints and parses `1,5 km` in German, `offsetUnits()` formats an unnamed shifted kelvin as `K + 5` and parses it back, `decimalParse()` keeps the scale of `1.50 km`, and `userUnits()` loads a small `.units` text, parses `2 league` and converts it with `toSystem(system)`. `ExamplesTest` checks the printed output. Run it after a build with:

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
- A `jlink` image of an application that formats in a locale other than English needs the module
  `jdk.localedata` (`--add-modules jdk.localedata`); without it, `Locale.GERMANY` formats `1.5 km`.
- An offset unit without a symbol formats as a shifted formula (`SI.KELVIN.shift(5)` is `K + 5`, `5|9·K + 459.67` is the degree Fahrenheit) and parses back to an equal unit. Units with a negative or root factor, an irrational shift or a formula over 10,000 characters have no faithful form: `format` throws `IllegalArgumentException`. A glued minus before an integer (`s-1`) is an error: write `s^-1` or `s - 1`.

```java
UomQuantityFormat german = new UomQuantityFormat(UomUnitFormat.standard()).withLocale(Locale.GERMANY);
System.out.println(german.format(Length.of(1.5, SI.KILOMETRE)));          // prints: 1,5 km
UomQuantityFormat decimals = new UomQuantityFormat(UomUnitFormat.standard()).withDecimals(MathContext.DECIMAL64);
Quantity<?> parsed = decimals.parse("1.50 km");
System.out.println(parsed + " " + ((BigDecimal) parsed.getValue()).scale()); // prints: 1.50 km 2
System.out.println(UomUnitFormat.standard().format(SI.KELVIN.shift(5)));     // prints: K + 5
```

## Serialization

Units and quantities are `Serializable` through serialization proxies that re-check every invariant on reading. Never read untrusted streams without an `ObjectInputFilter`: [SECURITY.md](SECURITY.md) has the recommended filter and the details. The serialized form may change before 1.0. Systems and formats are not serializable.

## Your own units

A `.units` file defines units, one per line as `symbol:name formula` (`_` in a name is a space); `UomSystemOfUnits.load` reads it at run time. The units load as `Unit<?>`; narrow them to a built-in quantity type with `asType`. [docs/CUSTOM-UNITS.md](docs/CUSTOM-UNITS.md) is the full guide.

```units medieval.units
@system Medieval                # system name (else the file name)
league:league   3 mi            # formulas use the built-in symbols
furlong:furlong 220 yd
rod:rod         1|40 furlong    # exact fraction
@prefixed league k              # adds kleague
@preferred Length furlong       # q.toSystem(system) gives furlongs
```

```java
UomSystemOfUnits medieval = UomSystemOfUnits.load(Path.of("medieval.units"));
UomQuantityFormat format = new UomQuantityFormat(UomUnitFormat.standard().with(medieval));
Length d = (Length) format.parse("2 league").asType(javax.measure.quantity.Length.class);
d.toSystem(medieval);                                  // 48.0 furlong
```

## Generated sources

The quantity sources in `uom/src/main/java/org/tinygears/uom/quantity`, the catalogs in `uom/src/main/java/org/tinygears/uom/systems/` and [docs/UNITS.md](docs/UNITS.md) are generated by `uom-codegen` from `data/` and committed; [CONTRIBUTING.md](CONTRIBUTING.md) has the regeneration command.
