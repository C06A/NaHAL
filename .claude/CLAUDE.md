# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build all modules
./gradlew build

# Run all tests for the HALDiSh module
./gradlew :haldish:test

# Run tests for a specific platform
./gradlew :haldish:jvmTest
./gradlew :haldish:jsNodeTest
./gradlew :haldish:jsTest          # browser
./gradlew :haldish:wasmJsTest

# Run a single test class (JVM)
./gradlew :haldish:jvmTest --tests "com.helpchoice.nahal.haldish.parser.HalParserTest"

# Coverage report (JVM only, printed to console)
./gradlew :haldish:coverageReport

# Build native shared library (generates C header at haldish/build/bin/<target>/releaseShared/libhaldish_api.h)
./gradlew :haldish:linkReleaseSharedMacosArm64
./gradlew :haldish:linkReleaseSharedLinuxX64

# Build JS production library for Node.js (output: haldish/build/compileSync/js/main/productionLibrary/)
./gradlew :haldish:jsNodeProductionLibrary

# Run the JS example (requires a running HAL server on localhost:8080)
node examples/javascript/crud-example.mjs
# Or via Gradle (downloads Node automatically)
./gradlew :haldish:runJsSimpleExample

# Run core JVM tests (only platform with a standard test runner)
./gradlew :core:jvmTest

# Core platform verification tasks (custom, non-JVM)
./gradlew :core:runCoreJsTest          # runs src/test/js/simple-example.cjs via Node
./gradlew :core:compileCoreNativeTest  # compiles C++ test against macosX64 shared lib
./gradlew :core:runCoreNativeTest      # runs the compiled C++ binary

# Build core native shared library (base name: nahal-core)
./gradlew :core:linkReleaseSharedMacosArm64
./gradlew :core:linkReleaseSharedLinuxX64

# Run the desktop GUI (JVM — opens a 1280×820 window)
./gradlew :ui:jvmRun

# Build the web UI (JS — output: ui/build/dist/js/productionExecutable/)
./gradlew :ui:jsBrowserProductionWebpack

# Build the Wasm web UI
./gradlew :ui:wasmJsBrowserProductionWebpack
```

## Architecture

This is a Kotlin Multiplatform project with three modules:

- **`:haldish`** — The published library: a HAL (Hypertext Application Language) client supporting JSON, XML, and YAML HAL formats. Targets JVM, JS (IR), WasmJS, Linux (x64/arm64), macOS (x64/arm64), iOS, and Windows.
- **`:core`** — Higher-level navigation layer built on top of `:haldish`. Provides `HalNavigator`, `LinkSelector`, `NavigationPlugin`, and platform facades. Library module — no application entry point.
- **`:ui`** — Compose Multiplatform desktop/browser/mobile GUI client. Implements the HAL navigator UI (two-pane: traversal rail + resource viewer, accordion panels, request builder, template expander, breadcrumb navigation).

Dependency direction: `:ui` → `:core` → `:haldish` (each module exposes its dependency via `api()`).

### Key data flow in `:haldish`

```
HalHttpClient → HalHttpRequest → Ktor HttpClient (platform engine)
                                       ↓
                              HalHttpResponse (raw body + headers)
                                       ↓
                              HalParser.parse(body, contentType)
                              ├── JsonHalParser  (application/hal+json)
                              ├── XmlHalParser   (application/hal+xml)
                              └── YamlHalParser  (application/hal+yaml, JVM/JS only)
                                       ↓
                                 HalDocument { links, embedded, properties }
```

`HalLink.href` may be a URI template (RFC 6570). Expand it via `UriTemplate` / `expandHref()`.

`HalFormatDetector.detect(contentType, body)` — checks the content-type header first; falls back to body sniffing (`{` → JSON, `<` → XML) when content-type is absent or generic.

### Key data flow in `:core`

```
HalNavigator.navigate(resource, selector, method, ...)
       │
       ├── LinkSelector.select(resource)      → HalLink  (TopLevel / InEmbedded / InItems)
       │
       ├── NavigationPlugin.preRequest(link)  → HalLink  (fold over plugin list)
       │
       ├── HalHttpClient.resolveLink(...)     → HalLink  (HaldishPlugin.preLink hook)
       │
       ├── HalLink.expandHref(templateVars)   → URL
       │
       ├── HalHttpClient.execute(request)     → HalHttpResponse
       │
       ├── NavigationPlugin.postResponse(...) → HalHttpResponse (fold)
       │
       └── HalParser.parse(body, contentType) → HalDocument?
                                               (null if response is not HAL)
                    ↓
            NavigationResponse { raw, document, isHal, statusCode, isSuccess }
```

#### Core API types

| Type | Role |
|---|---|
| `HalNavigator` | Main entry point; wraps `HalHttpClient`, applies plugins, returns `NavigationResponse` |
| `LinkSelector` | Sealed class — `TopLevel(rel, index)`, `InEmbedded(embeddedRel, embeddedIndex, linkRel, linkIndex)`, `InItems(itemIndex, linkRel, linkIndex)` |
| `NavigationResponse` | Wraps `HalHttpResponse` + parsed `HalDocument?`; exposes `isHal`, `statusCode`, `isSuccess` |
| `NavigatorConfig` | `baseUrl`, `defaultHeaders`, `defaultCookies`, `properties` passed to plugins at init |
| `NavigationPlugin` | Interface: `initialize(config)`, `preRequest(link, resource) → HalLink`, `postResponse(response) → HalHttpResponse` |
| `CoreException` | Sealed base; `NoSuchLinkException(selector)` thrown when `LinkSelector.select` returns null |

#### `:core` platform-specific pieces

| Source set | What it provides |
|---|---|
| `jsMain` | `JsCoreNavigator` (`@JsExport` class) — `linkHref()` / `embeddedLinkHref()` Promise-friendly helpers |
| `wasmJsMain` | Top-level `@JsExport` functions `coreLinkHref` / `coreEmbeddedLinkHref` |
| `nativeMain` | `@CName("core_link_href")` / `@CName("core_embedded_link_href")` C-exported functions |

Native shared library base name is `nahal-core` (header: `core/build/bin/<target>/releaseShared/libnahal_core.h`).

### Key structure of `:ui`

```
NaHalNavigator                 — root composable (theme + state bootstrap)
  └── NaHalNavigatorContent
        ├── NaHalTopBar        — address bar, back/forward, loading indicator
        ├── Left rail (Column)
        │     ├── TraversalTree   — clickable history nodes
        │     └── RequestLog      — reverse-chronological request list
        └── Center pane (Box)
              ├── TemplateForm    — URI template variable editor (when pendingRequest.templated)
              ├── RequestBuilder  — method/headers/cookies/body editor (when pendingRequest ready)
              └── CenterPanel     — response/request viewer
                    ├── BreadcrumbTrail
                    ├── Accordion (response mode, pretty)
                    │     ├── LinksPanel    — follow / expand links
                    │     ├── EmbeddedPanel — open embedded sub-resources
                    │     ├── PropTree      — JSON property tree
                    │     ├── HeadersPanel / CookiesPanel
                    │     └── ArrayItemsPanel — for top-level JSON array responses
                    ├── RawJsonPanel (response mode, raw)
                    ├── Accordion (request mode, pretty)
                    └── CurlPanel (request mode, raw — shows equivalent curl command)
```

`NavigatorState` (in `state/`) owns all mutable state: `history`, `cursor`, `loading`, `pendingRequest`, `requestLog`. It wraps `HalHttpClient` directly (bypasses `:core`'s `HalNavigator` — simpler for the UI's event-driven model). Platform entries (`jvmMain/main.kt`, `jsMain/JsEntry.kt`, `wasmJsMain/WasmEntry.kt`, `macosMain/MacosEntry.kt`, `iosMain/IOSEntry.kt`) each call `NaHalNavigator()`.

### Platform-specific pieces (`:haldish`)

| Source set | Purpose |
|---|---|
| `jvmMain` | CIO HTTP engine + kaml for YAML |
| `jsMain` | ktor-client-js + kaml; exposes `JsHalClient` (Promise-based JS facade) |
| `wasmJsMain` | ktor-client-js (no kaml); exposes `WasmHalClient` (`@JsExport`) |
| `appleMain` | Darwin HTTP engine |
| `linuxMain` | cURL HTTP engine |
| `mingwMain` | WinHTTP engine |
| `nativeMain` | C API (`NativeCApi.kt`) — `@CName` functions become `extern` symbols in the generated `.h` |
| `nonJvmNonJsMain` | Intermediate set (between `commonMain` and both `nativeMain`/`wasmJsMain`) for stub YAML parser where kaml is unavailable |

### Exception hierarchy

```
sealed class HaldishException
  ├── HalParseException        — malformed HAL body
  ├── UriTemplateException     — invalid RFC 6570 template or variable
  └── HalHttpException         — HTTP-level error; exposes .response, .statusCode, .body
```

### Source set hierarchy note

`nonJvmNonJsMain` is a manually created intermediate source set (not part of Kotlin's default hierarchy). It sits between `commonMain` and `nativeMain`+`wasmJsMain`, used solely to provide `YamlHalParser` stubs on platforms that lack kaml.

### XmlHalParserTest exclusion

`XmlHalParserTest` is excluded from the JS Node test task because xmlutil's JS backend requires a browser `DOMParser`. The test runs under the browser JS task and JVM only.

### Publishing

All three modules publish to Maven Central via `com.vanniktech.maven.publish`. Coordinates:
- `com.helpchoice.nahal:haldish`
- `com.helpchoice.nahal:nahal-core`
- `com.helpchoice.nahal:nahal-ui`

Signing is required (`signAllPublications()`). Run `./gradlew publish` after configuring Sonatype credentials.

## Plugin modules

Ready-made example plugins live under `plugins/`. Each is an independent Gradle submodule.

```bash
# Build all plugin modules
./gradlew :plugins:api-key:build
./gradlew :plugins:chain:build
./gradlew :plugins:bearer-token:build
./gradlew :plugins:base-url-rewriter:build
./gradlew :plugins:logger:build

# Run JVM tests for each plugin
./gradlew :plugins:api-key:jvmTest
./gradlew :plugins:chain:jvmTest
./gradlew :plugins:bearer-token:jvmTest
./gradlew :plugins:base-url-rewriter:jvmTest
./gradlew :plugins:logger:jvmTest
```

### Plugin module overview

| Module | Pattern | Artifact |
|---|---|---|
| `:plugins:api-key` | KMP (commonMain) | `haldish-plugin-api-key` |
| `:plugins:chain` | KMP (commonMain) | `haldish-plugin-chain` |
| `:plugins:logger` | KMP + expect/actual file I/O | `haldish-plugin-logger` |
| `:plugins:bearer-token` | Per-platform (each `*Main` is standalone) | `haldish-plugin-bearer-token` |
| `:plugins:base-url-rewriter` | Per-platform (each `*Main` is standalone) | `haldish-plugin-base-url-rewriter` |

`bearer-token` and `base-url-rewriter` are **per-platform examples**: each platform's source
set carries its own complete implementation (no shared `commonMain` code) as a copy-paste
template for plugins that need genuinely platform-specific logic.
