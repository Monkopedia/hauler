# Changelog

All notable changes to `hauler` are documented here. This project adheres to
[semantic versioning](https://semver.org/).

## 0.4.2

### Dependencies

- Upgraded ksrpc `1.1.1` → `1.1.4` (`ksrpc-core` / `ksrpc-flow` / `ksrpc-sockets` /
  `ksrpc-ktor-client` / `ksrpc-ktor-websocket-client` and the
  `com.monkopedia.ksrpc.plugin` Gradle plugin).
- Aligned to Kotlin `2.4.10` and kotlinx-coroutines `1.11.0` to match ksrpc 1.1.4
  and avoid klib skew.

### Changed

- On JVM, the suspend `Hauler.ship` path now falls back to the current thread name
  for a log's `threadName` when no `CallSign` is present, matching the existing
  `AsyncHauler.ship` behavior — the two entry points now attribute logs consistently
  (#13).

### Removed (may affect consumers)

- Four dependencies that `hauler` did not use were dropped from the published
  `hauler-jvm` POM (#6): `org.jetbrains.kotlin:kotlin-reflect`,
  `org.slf4j:slf4j-api`, `com.github.ajalt.clikt:clikt-jvm`, and
  `ch.qos.logback:logback-classic`.

  They were declared `implementation` in `jvmMain`, which publishes as **runtime**
  scope, so they were on the runtime classpath of every consumer. **If you were
  resolving any of them transitively through `hauler`, declare them directly.**
  `logback-classic` is the one most likely to bite: without it SLF4J has no
  binding, which surfaces as `SLF4J: No providers were found` and silently
  discarded log output rather than as a build failure.

  A library should not have been exporting a logging backend, so the removal is
  intentional and stays — but it is a consumer-visible change and the 0.4.2 notes
  originally filed it as internal.

### Internal (no public API change)

- Deduplicated `withPickup` / `dumpWithPickup` into a shared private polling helper (#10).
- `List<Box>.pack()` now delegates to the internal `LogPacker`, removing duplicated
  interning logic (#11).
- `Garage.route` defaults its formatter to the `DefaultFormat` constant, consistent
  with the other `route` overloads (#12).
- Removed vestigial `inline` from non-inlinable log helpers (#5); suppressed the
  expect/actual-classes Beta warning via `-Xexpect-actual-classes` (#7).
  (The unused-JVM-dependency half of #6 is listed under Removed above — it changes
  the published POM, so it is not internal.)

### Tooling

- Brought the codebase to ktlint-clean and added a pull-request / main CI workflow that
  builds, tests, and runs apiCheck + ktlint on every PR (#8, #9).
- Pinned the JS/Wasm toolchain's Node.js to 24.10.0 to sidestep a Kotlin 2.4.10 default
  (Node 25) that the transitive `nanoid` npm dependency rejects, so the Wasm npm install
  succeeds locally and in CI.

_No source-level API changes in this release, and the JVM ABI dump (`hauler/api/hauler.api`)
is unchanged from 0.4.1. Two caveats keep this short of a drop-in replacement._

_First, the published `hauler-jvm` POM dropped four runtime dependencies (see **Removed**
above). **Re-resolving your dependencies against 0.4.2 is what surfaces that loss** — if you
were relying on any of the four transitively, they leave your runtime classpath at the point
your build tool reads the new POM. A build that reuses an already-resolved classpath will not
notice._

_Second, the native/JS/Wasm ABI was **not** verified across the Kotlin 2.4.0 → 2.4.10 bump:
`klibApiCheck` is not currently an effective gate, so no klib baseline was compared. The JVM
surface is unchanged and checked; the other ten published artifacts are unverified rather
than known-good._
