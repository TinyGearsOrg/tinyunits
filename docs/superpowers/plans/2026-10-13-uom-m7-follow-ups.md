# Milestone 7 follow-ups

Findings from the per-task reviews of milestone 7 (release engineering and documentation) that were triaged as
follow-up work or owner decisions, not merge blockers. Milestone 7 state: nothing published, tagged or signed; version
`0.1.0-SNAPSHOT`; TCK 113 of 113 tests; 1112 tests in `mvn -q -B clean verify` (core 506, codegen 169, uom 322,
tck 113, examples 2). "Task n" refers to `2026-10-13-uom-m7-release-engineering.md`. What the owner must do before the
first release is in `docs/TODO.md`, "Release readiness".

## Done by milestone 7 (from earlier follow-ups)

- M6, "Milestone 7: release engineering": POM metadata, sources and javadoc jars, the signing profile, CI on JDK 21 and
  25, the custom-unit guide and unit table (N7), the versioning policy (N8) and the JPMS review are done. The
  repository rename and the publication are not (owner's action).
- M6, confusable Unicode symbols (micro sign and Greek mu, ohm sign and omega) belong in the custom-unit guide: done
  (`docs/CUSTOM-UNITS.md`, caveats).
- M6, `load(name, reader, resolver)` keeps the extension in the system name: documented in the guide.
- M6, alternate units of user files are permanent: documented in the guide.

## Owner decisions

- Rulings of milestone 7: `inceptionYear` 2026; the unpublished modules (`uom-codegen`, `uom-tck`, `uom-examples`)
  build no sources or javadoc jar under `-Prelease` and set `skipPublishing` literally (not overridable by
  `-Dcentral.skipPublishing=false`); reproducibility holds per JDK (`Build-Jdk-Spec`), releases are built with JDK 21;
  `javadoc.failOnWarnings=true` for the published modules only.
- Actions are pinned to major tags (`actions/checkout@v7`, `actions/setup-java@v6`), as the plan says; full-SHA pins
  with a version comment are stricter and need `ACTION` in `check-workflows.sh` to accept `@[0-9a-f]{40}`.
- `on: push` (no branch filter) plus `pull_request` runs the workflow twice for a PR from a branch of this repository;
  `push: branches: [main]` would halve it.
- `cancel-in-progress: true` also cancels superseded runs on `main`;
  `cancel-in-progress: ${{ github.event_name == 'pull_request' }}` would keep every `main` result.
- Job-level `permissions` are absent and `check-workflows.sh` forbids them (jobs inherit the workflow's
  `contents: read`); per-job `permissions: {contents: read}` needs the check changed to require it.
- `SECURITY.md` names GitHub private vulnerability reporting and the maintainer's email, and says reports are answered
  on a best-effort basis: confirm the contact and the wording.
- `docs/RELEASING.md` assumes control of the `tinygears.org` domain for the Central namespace `org.tinygears`; without
  it the group id changes (an API-visible change of the coordinates).
- The repository rename (`tinyunits` to `uom`?): the POM's `url`, `scm` and `issueManagement` and `SECURITY.md` follow
  it.

## Build and release profile (Task 1)

- Plugin updates: compiler 3.13.0, surefire 3.2.5 and exec 3.2.0 were kept for stability; 3.16.0, 3.6.0 and 3.6.4
  exist (dependabot will propose them once pushed).
- `waitForPublishCompletion=false` is pinned in the release profile (so `-DwaitForPublishCompletion=true` cannot
  change it); the parameter is deprecated in central-publishing-maven-plugin 0.11.0, so `-Prelease` prints a WARNING
  per module.
- In central-publishing-maven-plugin 0.11.0, `skipPublishing` only filters a module's artifacts; the upload mojo still
  runs on the last reactor module (`uom-examples`). Re-check on a plugin update.
- A non-quiet build warns `JAR will be empty` for `uom-tck` (no main sources); `skipIfEmpty` breaks its install.
- On JDK 21 the TCK also prints "No output directory specified, using <root>/target instead" and leaves an empty
  root `target/` (ignored by `.gitignore`; final review L6, deferred).
- On JDK 21 the TCK prints four "No audit file specified" lines; the SLF4J "No providers" lines remain (milestone 3
  item: `slf4j-nop` at test scope).
- The Central bundle is never built locally (that needs `-Prelease deploy`, which can upload); the release-version
  dry run of Task 1 (proxies to `127.0.0.1:9`) staged exactly the three published artifacts.

## Javadoc and API pin (Task 2)

- The API pin strings (`toGenericString`, `ModuleDescriptor.toString`) were generated on JDK 25; `mvn -B verify` on
  Temurin 21.0.12 passed locally (Task 6), the CI matrix confirms it on every push.
- `mvn test -pl uom` without `-am` reads `uom-core` from `~/.m2`, so `ApiSurfaceTest` can compare a stale jar; the
  reactor build is authoritative.
- maven-javadoc-plugin 3.12.0 misses the locale-formatted warning count when `-Xmaxwarns` is raised, so the POM keeps
  javadoc's limit of 100 and only `check-javadoc.sh` raises it.
- `check-javadoc.sh` counts only `file:line: warning:` lines; a tool-level warning is caught by the release build's
  `failOnWarnings` only.
- Nits: the `Factor.root` Javadoc sentence on its exceptions is ambiguous (`Factor.java`, "a zero or `|n|` over
  1000"); a few new lines exceed 120 columns (`Factor`, `UomSystemOfUnits`, `ApiSurfaceTest`, `CatalogEmitter`).

## Unit table (Task 3)

- Gaussian has 27 rows for 26 distinct units (`Fr` equals `statC`); the plan's 26 counts distinct units, the table one
  row per symbol. The test asserts both.
- The header says `from data/*.units` (as specified) although quantity types come from `data/quantity-types.txt`.

## Documentation (Task 4)

- `DocsSnippetsTest`'s directive coverage is substring-based (`pi`, ` ~` also match comments or symbols such as
  `pint`): a guard, not a proof.
- `DocsSnippetsTest` compiles each `java` block separately (about 1.5 s for the class).

## Release verification (Task 5)

- `verify-release.sh` installs the unpublished modules into its temporary repository (pom and jar, no sources or
  javadoc); "never deployed" rests on `check-pom.sh` on the installed POMs.
- Self-test gaps: no mutation for the consumer's resolved dependency set or for the `jlink` stage (verified positively
  on Temurin 21).
- `xargs -n1 basename` in the dependency-set check splits on whitespace: a local repository path with spaces breaks it.
- `scripts/consumer/expected-output.txt` duplicates the values `ExamplesTest` pins; a drift fails the script, not
  silently.
- Each run builds twice into fresh temporary repositories; on a CI runner without `~/.m2/repository` both download
  the dependencies.
- A `jlink` image needs `jdk.localedata` for locale formatting (`1.5 km` instead of `1,5 km` in German); the README
  says so and the script adds it.

## CI (Task 6)

- The workflow has never run on GitHub (nothing pushed); the first push of `m7-release` starts it.
- `check-workflows.sh` is not run by CI (adding it changes the CONTRIBUTING block the check compares) and needs PyYAML.

## Final review

- M1, nothing stopped `-Prelease deploy` on a SNAPSHOT: done. The release profile has an enforcer execution
  (`requireReleaseVersion`, `requireReleaseDeps`, literal `skip=false`) bound to `deploy` and declared before the
  Central plugin; a dead-port dry run fails at it on `0.1.0-SNAPSHOT` (even with `-Denforcer.skip=true`) and passes it
  on a `0.1.0` copy. `check-pom.sh` guards it (seven self-test mutations).
- L1, the POM comment claimed `-DexcludeArtifacts` replaces the configured list: corrected.
- L2, Gaussian 26 (README) against 27 (UNITS.md): the README explains it; labelling the generated column as symbols
  (a generator change) is open.
- L3, `scripts/consumer` pins plugin versions outside the reactor: dependabot's Maven entry covers both directories,
  `check-workflows.sh` requires it. Dependabot cannot resolve the consumer's `org.tinygears:uom` (not on Central).
- L4 and L5, RELEASING wording (parent is pom-only) and the README status lines at release time: done.
- L6, the empty root `target/` on JDK 21: deferred (see above).
- `check-pom.sh` and `check-javadoc.sh` are not CI steps; `verify-release.sh` runs `check-pom.sh` on the installed
  POMs and the `-Prelease` build fails on javadoc warnings.
