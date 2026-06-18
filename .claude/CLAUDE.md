# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Architecture

Kotlin Multiplatform project. Dependency direction: `:ui` → `:core` → `:haldish` (each module
exposes its dependency via `api()`).

| Module | Role | Per-module docs |
|---|---|---|
| `:haldish` | Published library: HAL (Hypertext Application Language) client for JSON/XML/YAML formats. Targets JVM, JS (IR), WasmJS, Linux (x64/arm64), macOS (x64/arm64), iOS, Windows. | [haldish/CLAUDE.md](../haldish/CLAUDE.md) |
| `:core` | Navigation layer on `:haldish` — `HalNavigator`, `LinkSelector`, `DocLinkResolver`, platform facades. No app entry point. | [core/CLAUDE.md](../core/CLAUDE.md) |
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

- `:haldish` — `./gradlew :haldish:test`, per-platform `:haldish:jvmTest` / `:jsNodeTest` / `:jsTest` / `:wasmJsTest`, single test via `--tests`, `:coverageReport`, native/JS library links.
- `:core` — `./gradlew :core:jvmTest` plus custom non-JVM verification tasks (`runCoreJsTest`, `runCoreNativeTest`).
- `:ui` — `./gradlew :ui:jvmRun` (desktop), `:jsBrowserProductionWebpack` / `:wasmJsBrowserProductionWebpack` (web).
- `:plugins:*` — `./gradlew :plugins:<name>:build` / `:jvmTest`.

## Publishing

All three primary modules publish to Maven Central via `com.vanniktech.maven.publish`. Coordinates:
- `com.helpchoice.nahal:haldish`
- `com.helpchoice.nahal:nahal-core`
- `com.helpchoice.nahal:nahal-ui`

Plugin modules publish as `haldish-plugin-<name>` (see [plugins/CLAUDE.md](../plugins/CLAUDE.md)).

Signing is required (`signAllPublications()`). Run `./gradlew publish` after configuring Sonatype credentials.
