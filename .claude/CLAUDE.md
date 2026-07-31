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

| Module | Role | Per-module docs |
|---|---|---|
| `:core` | Navigation layer on haldish — `HalNavigator`, `LinkSelector`, `DocLinkResolver`, platform facades. No app entry point. | [core/CLAUDE.md](../core/CLAUDE.md) |
| `:ui` | Compose Multiplatform desktop/browser/mobile GUI HAL navigator. | [ui/CLAUDE.md](../ui/CLAUDE.md) |
| `:plugins:*` | Independent example-plugin submodules (`api-key`, `chain`, `curie`, `logger`, `bearer-token`, `base-url-rewriter`). | [plugins/CLAUDE.md](../plugins/CLAUDE.md) |

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
- `:plugins:*` — `./gradlew :plugins:<name>:build` / `:jvmTest`.

## Publishing

Both primary modules publish to Maven Central via `com.vanniktech.maven.publish`. Coordinates:
- `com.helpchoice.nahal:nahal-core`
- `com.helpchoice.nahal:nahal-ui`

(`com.helpchoice.nahal:haldish` is published from `../HALDiSh_KMP`.)

Plugin modules publish as `haldish-plugin-<name>` (see [plugins/CLAUDE.md](../plugins/CLAUDE.md)).

Signing is required (`signAllPublications()`). Run `./gradlew publish` after configuring Sonatype credentials.
