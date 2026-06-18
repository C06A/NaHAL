# `:haldish` — HAL client library

The published library: a HAL (Hypertext Application Language) client supporting JSON, XML,
and YAML HAL formats. Targets JVM, JS (IR), WasmJS, Linux (x64/arm64), macOS (x64/arm64),
iOS, and Windows.

## Build & Test Commands

```bash
# Run all tests
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
```

## Key data flow

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

`HalFormatDetector.detect(contentType, body)` — checks the content-type header first; falls
back to body sniffing (`{` → JSON, `<` → XML) when content-type is absent or generic.

## Platform-specific pieces

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

## Exception hierarchy

```
sealed class HaldishException
  ├── HalParseException        — malformed HAL body
  ├── UriTemplateException     — invalid RFC 6570 template or variable
  └── HalHttpException         — HTTP-level error; exposes .response, .statusCode, .body
```

## Source set hierarchy note

`nonJvmNonJsMain` is a manually created intermediate source set (not part of Kotlin's default
hierarchy). It sits between `commonMain` and `nativeMain`+`wasmJsMain`, used solely to provide
`YamlHalParser` stubs on platforms that lack kaml.

## XmlHalParserTest exclusion

`XmlHalParserTest` is excluded from the JS Node test task because xmlutil's JS backend requires
a browser `DOMParser`. The test runs under the browser JS task and JVM only.
