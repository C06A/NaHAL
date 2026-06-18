# `:core` — navigation layer

Higher-level navigation layer built on top of `:haldish`. Provides `HalNavigator`,
`LinkSelector`, and platform facades. Library module — no application entry point.

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
                                               (null if response is not HAL)
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
| `DocLinkResolver` | Object mirroring HALDiSh's `haldoclink.sh`. Resolves a rel's documentation URL: matches the CURIE prefix against the lowercase HAL-spec `curies` relation, expanding its `{rel}` URI template; walks the holding document **outward through enclosing resources to the root**, first matching prefix wins. Accepts CURIE-prefixed (`doc:orders`) or bare (`orders`) rels. Distinct from `:plugins:curie` (uppercase `CURIE` URL-prefix rewrite). |

## Platform-specific pieces

| Source set | What it provides |
|---|---|
| `jsMain` | `JsCoreNavigator` (`@JsExport` class) — `linkHref()` / `embeddedLinkHref()` Promise-friendly helpers |
| `wasmJsMain` | Top-level `@JsExport` functions `coreLinkHref` / `coreEmbeddedLinkHref` |
| `nativeMain` | `@CName("core_link_href")` / `@CName("core_embedded_link_href")` C-exported functions |

Native shared library base name is `nahal-core` (header:
`core/build/bin/<target>/releaseShared/libnahal_core.h`).
