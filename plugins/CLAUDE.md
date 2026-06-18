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
| `preLink(link, rel, linkIndex, inDocument, embeddingPath)` | Before following a link; has full document + embedding context |
| `preRequest(request)` | Before every HTTP request — modify URL/method/headers/cookies/body |
| `postResponse(document, response)` | After a HAL response is parsed — add/remove/modify links, embedded, properties |
