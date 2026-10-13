# Changelog

All notable changes are recorded here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
the project follows [semantic versioning](https://semver.org/) from 1.0.0 (see [docs/API.md](docs/API.md)).

## [Unreleased]

Nothing is released yet; the development version is `0.1.0-SNAPSHOT`.

### Added

- Units core (`uom-core`): units as exact products of atoms with a rational factor, `pi` and roots; dimensions;
  linear, offset and affine converters with exact and `BigDecimal` conversion; metric prefixes; the `.units`
  definition parser and formula evaluator with file and line in every error.
- Typed `double` quantities for all 33 JSR-385 quantity types (`Length`, `Speed`, ...), generated with factories, the
  strict arithmetic of the typed interfaces, `asType`, and `equals` versus `isEquivalentTo`.
- The `SI` catalog (128 units, from the SI Brochure, 9th edition), metric prefixes, the JSR-385 `ServiceProvider`,
  unit and quantity formats; the JSR-385 TCK (`unit-tck` 2.2) passes 113 of 113 tests.
- `BigDecimal` quantities for all 33 types with a `MathContext` (`DECIMAL128` by default), single rounding, and the
  precision rule (a `double` operand makes the result a `double`).
- The catalogs `USCustomary`, `Imperial`, `CGS`, `ESU`, `EMU` and `Gaussian`, the `@system`, `@extends` and `@member`
  directives, and `quantity.toSystem(system)`, which converts to the preferred unit of a system.
- Java serialization of units and quantities through serialization proxies that re-check every invariant.
- Locale-aware quantity formats (`withLocale`, `withGrouping`), a decimal-preserving parse (`withDecimals`), the
  shift grammar for offset units in formulas (`K + 273.15`) and a faithful text form for unnamed offset units.
- User `.units` files loaded at run time (`UomSystemOfUnits.load`) and added to a format with `UomUnitFormat.with`
  ([docs/CUSTOM-UNITS.md](docs/CUSTOM-UNITS.md)).
- Release preparation: POM metadata, an inactive `release` profile (sources, javadoc, signing, Central upload),
  reproducible jars, the public API definition and its pin ([docs/API.md](docs/API.md)), the generated unit table
  ([docs/UNITS.md](docs/UNITS.md)), the user documentation, the release verification script and a CI workflow
  (JDK 21 and 25).

[Unreleased]: https://github.com/TinyGearsOrg/tinyunits/commits/main
