# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Architecture

Kotlin Multiplatform project. Dependency direction: `:ui` → `:core` → `haldish` (each module
exposes its dependency via `api()`).

**haldish is no longer a module of this build.** It lives in its own repository,
`../HALDiSh_KMP`, and is consumed here as the published artifact
`com.helpchoice.nahal:haldish` (version catalog entry `libs.haldish`). Change the HAL client
there, not here. Because Maven Central currently serves only 1.0.1, `settings.gradle.kts` adds
`mavenLocal()` — run `./gradlew publishToMavenLocal -PRELEASE_SIGNING_ENABLED=false` in
`../HALDiSh_KMP` to make the current 2.0.0 resolvable.

**The example plugins are no longer modules of this build either.** They live in
`../HALDiSh_Plugins` and publish as `com.helpchoice.nahal:haldish-plugin-<name>`. `:testkit` is
the only consumer here (`libs.haldish.plugin.curie`, for CURIE expansion), also via `mavenLocal()`.
Note the bootstrap order across the three repositories: `../HALDiSh_KMP` → `:core`/`:ui` here →
`../HALDiSh_Plugins` → `:testkit` here.

| Module | Role | Per-module docs |
|---|---|---|
| `:core` | Navigation layer on haldish — `HalNavigator`, `LinkSelector`, `DocLinkResolver`, platform facades. No app entry point. | [core/CLAUDE.md](../core/CLAUDE.md) |
| `:ui` | Compose Multiplatform desktop/browser/mobile GUI HAL navigator. | [ui/CLAUDE.md](../ui/CLAUDE.md) |

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
- `:ui` — `./gradlew :ui:jvmRun` (desktop), `:jsBrowserProductionWebpack` / `:wasmJsBrowserProductionWebpack` (web).

**This build produces a plugin-free app.** Plugins activate only when a config source names them:
`HALDISH_CONFIG` (order + per-plugin properties) over classes the runtime can already resolve —
the JVM drop-in directory `$NAHAL_PLUGINS_DIR`, or `CorePluginRegistry` registrations compiled into
a native binary. Sole exception: `HALDISH_PLUGIN_PATH` with no config loads that one artifact via
haldish's own loader. To run the UI with the example plugins chained:
`(cd ../HALDiSh_Plugins && ./gradlew :chain:jvmRun)` — see "Plugins in the app" in
[ui/CLAUDE.md](../ui/CLAUDE.md) and `PLUGIN_CONTRACT.md`.

## Publishing

Both primary modules publish to Maven Central via `com.vanniktech.maven.publish`. Coordinates:
- `com.helpchoice.nahal:nahal-core`
- `com.helpchoice.nahal:nahal-ui`

plus `:testkit` / `:testkit-groovy` as `haldish-testkit` / `haldish-testkit-groovy`.

(`com.helpchoice.nahal:haldish` is published from `../HALDiSh_KMP`, and the
`haldish-plugin-<name>` artifacts from `../HALDiSh_Plugins`.)

Signing is required (`signAllPublications()`). Run `./gradlew publish` after configuring Sonatype credentials.
