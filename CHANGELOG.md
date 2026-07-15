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

### Internal (no public API change)

- Deduplicated `withPickup` / `dumpWithPickup` into a shared private polling helper (#10).
- `List<Box>.pack()` now delegates to the internal `LogPacker`, removing duplicated
  interning logic (#11).
- `Garage.route` defaults its formatter to the `DefaultFormat` constant, consistent
  with the other `route` overloads (#12).
- Removed vestigial `inline` from non-inlinable log helpers (#5) and unused JVM runtime
  dependencies (#6); suppressed the expect/actual-classes Beta warning via
  `-Xexpect-actual-classes` (#7).

### Tooling

- Brought the codebase to ktlint-clean and added a pull-request / main CI workflow that
  builds, tests, and runs apiCheck + ktlint on every PR (#8, #9).
- Pinned the JS/Wasm toolchain's Node.js to 24.10.0 to sidestep a Kotlin 2.4.10 default
  (Node 25) that the transitive `nanoid` npm dependency rejects, so the Wasm npm install
  succeeds locally and in CI.

_No public API/ABI changes in this release; `hauler` 0.4.2 is a drop-in replacement for
0.4.1 for consumers._
