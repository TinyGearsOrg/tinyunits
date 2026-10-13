# Public API and versioning

uom follows [semantic versioning](https://semver.org/) from 1.0.0. Before 1.0 (the `0.x` versions) any release may
change the API. From 1.0, a minor release adds API without breaking it and a major release may break it.

## What is API

The public API consists of the exported packages of the two published modules:

| Module | Exported packages |
|---|---|
| `org.tinygears.uom.core` (artifact `uom-core`) | `org.tinygears.uom.core`, `org.tinygears.uom.core.parse`, `org.tinygears.uom.core.quantity` |
| `org.tinygears.uom` (artifact `uom`) | `org.tinygears.uom.quantity`, `org.tinygears.uom.systems` |

Within them, these are stable:

- the public and protected members (constructors, methods, fields, nested types) of the public types in the exported
  packages, and the module directives (`requires transitive`, `exports`, `provides`);
- the names of the generated constants of the catalogs (`SI.METRE`, `USCustomary.FOOT`, and so on): a constant is
  not renamed or removed in a minor release;
- the syntax of `.units` files as described in [CUSTOM-UNITS.md](CUSTOM-UNITS.md): a file that loads in one minor
  release loads with the same meaning in the later ones of the same major version;
- the serialized form of units and quantities, from 1.0: a stream written by 1.x reads in every later 1.y. Before 1.0
  the serialized form may change in any release.

## What is not API

- `org.tinygears.uom.core.internal`: it is not exported; its public classes are public only for the other packages
  of `uom-core` and may change in any release.
- the generator internals (`uom-codegen`), and the unpublished modules `uom-tck` and `uom-examples`;
- test helpers;
- the symbols of units outside the catalogs, such as the formula `UomUnitFormat` writes for an unnamed unit
  (`1|1000·m/s`).

## Deprecation

An element to be removed is first deprecated (`@Deprecated` with a `@deprecated` tag naming the replacement) for at
least one minor release, and is removed only in the next major release.

## The API pin

`docs/api/public-api.txt` lists the API: one sorted line per module directive, public type and public or protected
member of the exported packages, with full generic signatures, plus `hidden` lines for the public types of the
non-exported packages. `ApiSurfaceTest` (in `uom`) reads the module descriptors and the class files of both modules
and fails when the API differs from the file, listing the added and removed lines; it also checks that
`org.tinygears.uom.core.internal` is not exported and is the only package with public types outside the exports.

To change the API deliberately, change the code, rewrite the file and review its diff with the change:

```
mvn -B verify -Duom.writeApi
git diff docs/api/public-api.txt
```

A removed or changed line is a breaking change: after 1.0 it needs a major release, or a deprecation first.

## Javadoc

The javadoc of both published modules builds with `doclint` on all groups and no warnings; the `release` profile fails
on a warning. `scripts/check-javadoc.sh` counts the warnings of both modules and fails on any.
