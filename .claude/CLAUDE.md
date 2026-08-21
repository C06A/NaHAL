# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Architecture

Kotlin Multiplatform project. Dependency direction: `:ui` → `:core` → `haldish`.

**`:core` is not published, and is not a dependency of the published artifacts — its sources are
compiled into them.** A `project(":core")` dependency survives publication as a *coordinate* in the
POM and Gradle module metadata, not as inlined content (application packaging is the opposite: the
jpackage image, the APK and the static `NahalUI.framework` all link core in). With `nahal-core`
gone from Maven Central, that coordinate would be unresolvable, so `:ui` adds core's source
directories to its own source sets (`ui/build.gradle.kts`, minus the `@JsExport`/`@CName` facades)
and `:testkit` compiles in the single class it uses, `DocLinkResolver`. Core's dependencies are
re-declared in both. The `:core` module itself stays — it builds the `nahal-core` native shared
library and header shipped with the GitHub release, and owns the tests for that code.

Practical consequence: **editing `core/src` changes `:ui` and `:testkit`.** They compile those
files; there is no artifact boundary to shield them.

**haldish is no longer a module of this build.** It lives in its own repository,
`../HALDiSh_KMP`, and is consumed here as the published artifact
`com.helpchoice.nahal:haldish` (version catalog entry `libs.haldish`). Change the HAL client
there, not here. Because Maven Central currently serves only 1.0.1, `settings.gradle.kts` adds
`mavenLocal()` — run `./gradlew publishToMavenLocal -PRELEASE_SIGNING_ENABLED=false` in
`../HALDiSh_KMP` to make the current 2.0.0 resolvable.

**The example plugins are no longer modules of this build either.** They live in
`../HALDiSh_Plugins`, which this build neither consumes nor knows the contents of — what plugins
exist, what they are called and how they are published are that repository's business. Bootstrap
order is therefore just `../HALDiSh_KMP` → this build; the plugin repository builds after both.

| Module | Role | Per-module docs |
|---|---|---|
| `:core` | Navigation layer on haldish — `HalNavigator`, `LinkSelector`, `DocLinkResolver`, platform facades. No app entry point, not published; its sources compile into `:ui` and `:testkit`. | [core/CLAUDE.md](../core/CLAUDE.md) |
| `:ui` | Compose Multiplatform desktop/browser/mobile GUI HAL navigator. Library only — including its `androidTarget()` variant. | [ui/CLAUDE.md](../ui/CLAUDE.md) |
| `:androidApp` | The installable Android app: an Activity hosting `NaHalNavigator()`. Not published to Maven Central. | — |
| `iosApp/` | Xcode host for the iOS app. Links `NahalUI.framework`, which `:ui` produces. Not a Gradle module. | — |

**Which platforms get a native app.** macOS does (Kotlin/Native, `NaHAL.app`, no JVM), as do
Android and iOS. Linux and Windows do **not** — Compose Multiplatform ships no Kotlin/Native
renderer for either, so `ui/src/linuxMain` and `ui/src/mingwMain` are empty and those two ship as
jpackage `.deb`/`.msi` with an embedded Java runtime. Wasm has no app at all: the `wasmJs` target
declares `browser()` but no `binaries.executable()`.

> Read the relevant per-module `CLAUDE.md` (it auto-loads when you open files in that module)
> before working in a module — it carries the build/test commands, data flow, platform-specific
> source sets, and gotchas for that module. The plugin contract spec is `PLUGIN_CONTRACT.md` (repo root).

## Build & Test Commands

```bash
# Build all modules
./gradlew build
```

Per-module build/test/run commands live in each module's `CLAUDE.md` (linked above). Quick index:

- `:core` — `./gradlew :core:jvmTest` plus custom non-JVM verification tasks (`runCoreJsTest`, `runCoreNativeTest`).
- `:ui` — `./gradlew :ui:jvmRun` (desktop), `:jsBrowserProductionWebpack` (web — there is no wasm
  executable, so no wasm webpack task exists).
- `:androidApp` — `./gradlew :androidApp:assembleRelease` (APK) / `:bundleRelease` (AAB).
- Release assets — `./gradlew stageReleaseArtifacts` stages everything the host can build into
  `build/release/`; the full set spans several hosts, which is what `.github/workflows/release.yml`
  is for.

**This build produces a plugin-free app.** Plugins activate only when a config source names them:
`HALDISH_CONFIG` (order + per-plugin properties) over classes the runtime can already resolve —
the JVM drop-in directory `$NAHAL_PLUGINS_DIR`, or `CorePluginRegistry` registrations compiled into
a native binary. Sole exception: `HALDISH_PLUGIN_PATH` with no config loads that one artifact via
haldish's own loader. Running the UI with plugins active is driven from `../HALDiSh_Plugins`, which
owns those tasks — see "Plugins in the app" in [ui/CLAUDE.md](../ui/CLAUDE.md) and
`PLUGIN_CONTRACT.md`.

## Publishing

Published to Maven Central via `com.vanniktech.maven.publish`:
- `com.helpchoice.nahal:nahal-ui` (`:ui`)
- `com.helpchoice.nahal:haldish-testkit` / `haldish-testkit-groovy` (`:testkit` / `:testkit-groovy`)

`:core` **does not publish** — see Architecture above. `nahal-core` 1.0.1 and 2.0.0 remain on
Maven Central from before that change; nothing new is released under that coordinate.

(`com.helpchoice.nahal:haldish` is published from `../HALDiSh_KMP`. The plugin repository publishes
its own artifacts; nothing here references them.)

Signing is required (`signAllPublications()`). Run `./gradlew publish` after configuring Sonatype credentials.
