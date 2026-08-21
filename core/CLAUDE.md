# `:core` — navigation layer

Higher-level navigation layer built on top of haldish (`libs.haldish`, developed in the separate
`../HALDiSh_KMP` repository — it is no longer a module of this build). Provides `HalNavigator`,
`LinkSelector`, and platform facades. Library module — no application entry point.

**Not published, and not a dependency of the published artifacts.** `:ui` and `:testkit` add this
module's source directories to their own source sets rather than depending on `project(":core")`,
because a project dependency publishes as an unresolvable coordinate once `nahal-core` is gone
from Maven Central. So **editing anything here recompiles `:ui` and `:testkit`** — run their
builds, not just `:core:jvmTest`. Two consequences for code in this module:

- Anything added to `commonMain` (or a platform source set) lands inside `nahal-ui`. New
  dependencies must be re-declared in `ui/build.gradle.kts`, which is where core's own
  `haldish`/coroutines/serialization/kaml declarations now live in duplicate.
- The `@JsExport` / `@CName` facades (`JsCoreNavigator`, `WasmCoreClient`, `NativeCoreApi`) are
  **excluded** from that absorption by name. They exist for non-Kotlin callers of the `nahal-core`
  shared library — the artifact this module still builds for the GitHub release. Renaming one
  means updating the exclude in `ui/build.gradle.kts`.

## Build & Test Commands

```bash
# Run core JVM tests (only platform with a standard test runner)
./gradlew :core:jvmTest

# Core platform verification tasks (custom, non-JVM)
./gradlew :core:runCoreJsTest          # runs src/test/js/simple-example.cjs via Node
./gradlew :core:compileCoreNativeTest  # compiles C++ test against macosX64 shared lib
./gradlew :core:runCoreNativeTest      # runs the compiled C++ binary

# Build core native shared library (base name: nahal-core)
./gradlew :core:linkReleaseSharedMacosArm64
./gradlew :core:linkReleaseSharedLinuxX64
```

## Key data flow

```
HalNavigator.navigate(resource, selector, method, ...)
       │
       ├── LinkSelector.select(resource)      → HalLink  (TopLevel / InEmbedded / InItems)
       │
       ├── HalHttpClient.resolveLink(...)     → HalLink  (HaldishPlugin.preLink hook)
       │
       ├── HalLink.expandHref(templateVars)   → URL
       │
       ├── HalHttpClient.execute(request)     → HalHttpResponse
       │
       └── HalParser.parse(body, contentType) → HalDocument?
                                               (parsed whenever the body is structured
                                                JSON/XML/YAML, HAL or not; null otherwise)
                    ↓
            NavigationResponse { raw, document, isHal, statusCode, isSuccess }
```

## API types

| Type | Role |
|---|---|
| `HalNavigator` | Main entry point; wraps `HalHttpClient`, returns `NavigationResponse` |
| `LinkSelector` | Sealed class — `TopLevel(rel, index)`, `InEmbedded(embeddedRel, embeddedIndex, linkRel, linkIndex)`, `InItems(itemIndex, linkRel, linkIndex)` |
| `NavigationResponse` | Wraps `HalHttpResponse` + parsed `HalDocument?`; exposes `isHal`, `statusCode`, `isSuccess` |
| `NavigatorConfig` | `defaultHeaders`, `defaultCookies` merged into each request |
| `CoreException` | Sealed base; `NoSuchLinkException(selector)` thrown when `LinkSelector.select` returns null |
| `DocLinkResolver` | Object mirroring HALDiSh's `haldoclink.sh`. Resolves a rel's documentation URL: matches the CURIE prefix against the lowercase HAL-spec `curies` relation, expanding its `{rel}` URI template; walks the holding document **outward through enclosing resources to the root**, first matching prefix wins. Accepts CURIE-prefixed (`doc:orders`) or bare (`orders`) rels. This is the lowercase HAL `curies` mechanism — unrelated to any uppercase-`CURIE` URL-prefix rewriting a plugin might do. |

## Platform-specific pieces

| Source set | What it provides |
|---|---|
| `jsMain` | `JsCoreNavigator` (`@JsExport` class) — `linkHref()` / `embeddedLinkHref()` Promise-friendly helpers |
| `wasmJsMain` | Top-level `@JsExport` functions `coreLinkHref` / `coreEmbeddedLinkHref` |
| `nativeMain` | `@CName("core_link_href")` / `@CName("core_embedded_link_href")` C-exported functions |

Native shared library base name is `nahal-core` (header:
`core/build/bin/<target>/releaseShared/libnahal_core.h`).
