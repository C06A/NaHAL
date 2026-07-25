# `:plugins:*` — example plugins

Ready-made example plugins. Each is an independent Gradle submodule.

## Build & Test Commands

```bash
# Build all plugin modules
./gradlew :plugins:api-key:build
./gradlew :plugins:chain:build
./gradlew :plugins:curie:build
./gradlew :plugins:bearer-token:build
./gradlew :plugins:base-url-rewriter:build
./gradlew :plugins:logger:build

# Run JVM tests for each plugin
./gradlew :plugins:api-key:jvmTest
./gradlew :plugins:chain:jvmTest
./gradlew :plugins:curie:jvmTest
./gradlew :plugins:bearer-token:jvmTest
./gradlew :plugins:base-url-rewriter:jvmTest
./gradlew :plugins:logger:jvmTest
```

## Run the NaHAL desktop UI with a plugin

Every plugin module overrides the KMP `jvmRun` task: it launches `:ui`'s JVM main with that
plugin's jvm artifact on the classpath and `HALDISH_CONFIG` pointing at a config file it
generates into the plugin's `build/` dir, so `:core` discovers and activates the plugin.

```bash
./gradlew :plugins:curie:jvmRun              # CURIE-prefixed hrefs expanded
./gradlew :plugins:base-url-rewriter:jvmRun  # relative link hrefs resolved
./gradlew :plugins:logger:jvmRun             # exchanges written to ./haldish-log
./gradlew :plugins:chain:jvmRun              # curie + base-url-rewriter + logger together
./gradlew :plugins:api-key:jvmRun -PapiKey=secret
./gradlew :plugins:bearer-token:jvmRun -Ptoken=eyJhbGci...
```

`:plugins:chain:jvmRun` puts `curie`, `base-url-rewriter` and `logger` on the classpath and
lists all three in its config (curie first — its expansion runs before base-URL resolution;
logger last — it sees the final request).

## Module overview

| Module | Pattern | Artifact |
|---|---|---|
| `:plugins:api-key` | KMP (commonMain) | `haldish-plugin-api-key` |
| `:plugins:chain` | KMP (commonMain) | `haldish-plugin-chain` |
| `:plugins:curie` | KMP (commonMain) | `haldish-plugin-curie` |
| `:plugins:logger` | KMP + expect/actual file I/O | `haldish-plugin-logger` |
| `:plugins:bearer-token` | Per-platform (each `*Main` is standalone) | `haldish-plugin-bearer-token` |
| `:plugins:base-url-rewriter` | Per-platform (each `*Main` is standalone) | `haldish-plugin-base-url-rewriter` |

`bearer-token` and `base-url-rewriter` are **per-platform examples**: each platform's source
set carries its own complete implementation (no shared `commonMain` code) as a copy-paste
template for plugins that need genuinely platform-specific logic.

`curie` is the only example using the **`preLink`** hook — it reads the uppercase `CURIE`
collection from the document and rewrites a link href by plain-URL prefix substitution
(distinct from `:core`'s `DocLinkResolver`, which expands the lowercase HAL-spec `curies`
template into a *new* documentation link).

## Plugin contract

`PLUGIN_CONTRACT.md` (repo root) is the authoritative spec for `HaldishPlugin`. Plugins are
**discovered and loaded at runtime** (no library recompile) and may intercept four lifecycle
hooks, all with default no-op impls:

| Hook | When |
|---|---|
| `initialize(config)` | Once, before first HTTP call — read `config.properties` |
| `preLink(link, path, rootDocument)` | Before following a link/property; `path` (a `ResourcePath`) addresses the target from `rootDocument`, `path.terminalRel` is the rel, `path.documentsToContainer(rootDocument)` walks the ancestors |
| `preRequest(request)` | Before every HTTP request — modify URL/method/headers/cookies/body |
| `postResponse(document, response)` | After a HAL response is parsed — add/remove/modify links, embedded, properties |

## Release artifacts (runtime-dependency libraries)

Each plugin ships as a library the NaHAL app loads at runtime, one asset per plugin per platform
(root `stagePluginArtifacts` task → `build/release/`; also pulled in by `stageReleaseArtifacts`):

- **JVM** — `haldish-plugin-<name>-<version>.jar` (thin jar). Drop it into the desktop app's
  plugins directory (`$NAHAL_PLUGINS_DIR`, else `./plugins`); the app discovers it via
  `ServiceLoader` (`jvmMain/resources/META-INF/services/…HaldishPlugin`) and injects it into the
  navigator. `curie`/`logger` register an active service; `api-key`/`chain`/`bearer-token`/
  `base-url-rewriter` ship a commented template (they need config, so register a configured
  subclass). Or point `HALDISH_PLUGIN_PATH` at the jar (single-plugin, ServiceLoader).

- **Native** — `haldish-plugin-<name>-<platform>-<version>.zip` containing `libhaldish_plugin.*`
  (+ header). Point `HALDISH_PLUGIN_PATH` at the lib; haldish's `NativeDylibPluginAdapter`
  `dlopen`s it and resolves the `haldish_plugin_*` C symbols. The `@CName` C bridge lives in
  `src/nativeMain/.../CApi.kt` (or per-platform `src/{apple,linux,mingw}Main` for the per-platform
  modules) and delegates to `haldish`'s `NativePluginBridge`. The C `init` contract carries no
  config, so native builds read theirs from `HALDISH_PLUGIN_*` env vars:

  | Plugin | Hooks exported | Env config |
  |---|---|---|
  | `api-key` | init, pre_request | `HALDISH_PLUGIN_API_KEY`, `HALDISH_PLUGIN_API_KEY_HEADER` |
  | `bearer-token` | init, pre_request | `HALDISH_PLUGIN_API_TOKEN`, `HALDISH_PLUGIN_API_TOKEN_HEADER` |
  | `logger` | init, pre_request, post_response | `HALDISH_PLUGIN_LOG_DIR` |
  | `curie` | init, pre_link | — |
  | `base-url-rewriter` | init, pre_link | `HALDISH_PLUGIN_BASE_URL` |
  | `chain` | init, pre_link, pre_request, post_response | (curie→base-url-rewriter→logger, batteries-included) |

Browser JS is excluded (no runtime dynamic-library loading). `chain`'s shared lib is built from a
separate `pluginLib` compilation so the published `haldish-plugin-chain` klib stays a generic
combinator with no dependency on the other plugin modules.
