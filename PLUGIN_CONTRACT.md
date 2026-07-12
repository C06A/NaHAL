# HALDiSh Plugin Contract

A HALDiSh plugin is a piece of code that is **discovered and loaded at runtime** —
no recompilation of the library is required.  The plugin can intercept three
lifecycle points:

| Hook | When | What it can do |
|------|------|----------------|
| `initialize` | Once, before the first HTTP call | Read configuration from `config.properties`, establish connections |
| `preLink` | Before following a link from a HalDocument | Create/modify the link using document context — the `ResourcePath` to the target and the root document (CURI lookup, rel name, ancestor walk) |
| `preRequest` | Before every HTTP request | Modify URL, method, headers, cookies, body |
| `postResponse` | After a HAL response is parsed (getHal / executeAndParse) | Add, remove or modify links, embedded docs, properties |

All hooks have **default no-op implementations** — implement only those you need.

---

## Plugin interface (Kotlin / JVM / iOS / macOS Swift)

```kotlin
interface HaldishPlugin {
    fun initialize(config: HaldishPluginConfig) {}
    fun preLink(link: HalLink, path: ResourcePath, rootDocument: HalDocument): HalLink = link
    fun preRequest(request: HalHttpRequest): HalHttpRequest = request
    fun postResponse(document: HalDocument, response: HalHttpResponse): HalDocument = document
}

/**
 * An addressable path from a root HalDocument to the link or property that produces a URL,
 * following the grammar `Item? Embedded* (Link | Property+)`:
 *   - an optional leading Item (a top-level array response),
 *   - a chain of Embedded descents,
 *   - a terminal — a single Link, or a chain of Property names (a nested property URL).
 * A path with no terminal resolves to the reached resource's `self` link.
 */
sealed interface PathStep {
    data class Item(val index: Int) : PathStep                         // items[index]
    data class Embedded(val rel: String, val index: Int = 0) : PathStep // _embedded[rel][index]
    data class Link(val rel: String, val index: Int = 0) : PathStep     // _links[rel][index]  (terminal)
    data class Property(val name: String) : PathStep                   // property name        (terminal chain)
}

data class ResourcePath(val steps: List<PathStep>) {
    val terminalRel: String                                    // link rel, last property name, or "self"
    fun documentsToContainer(root: HalDocument): List<HalDocument>  // root .. container of the terminal
    fun resolve(root: HalDocument): ResolvedTarget?            // link + container + document chain, or null
    companion object {
        fun link(rel: String, index: Int = 0): ResourcePath   // a top-level _links[rel][index]
        fun self(): ResourcePath                              // the reached resource's self link
    }
}

data class HaldishPluginConfig(
    val platform: String,                             // "jvm", "js", "wasmjs", "apple", "linux", "windows"
    val version: String,
    val properties: Map<String, Any?> = emptyMap(),   // plugin-specific config extracted from config file
)
```

`HalLink` carries: `href`, `templated`, `type`, `name`, `title`, `hreflang`, `profile`, `deprecation`.

`HalHttpRequest` carries: `url`, `method`, `headers`, `cookies`, `body`, `acceptHal` — all typed, no parsing needed.

`HalDocument` carries: `links` (Map of rel → List\<HalLink\>), `embedded` (Map of rel → List\<HalDocument\>), `properties` (Map of key → JsonElement).

`HalHttpResponse` (read-only context in `postResponse`): `statusCode`, `headers`, `cookies`, `body`, `contentType`.

### `preLink` — document-context hook

`preLink` fires when navigation originates from a HAL link or property (e.g. via `HalNavigator`), **not** for bare-URL calls. The `path` addresses the target from `rootDocument`; the relation name is `path.terminalRel` and the document directly holding the link is `path.documentsToContainer(rootDocument).last()`. Walk `path.documentsToContainer(rootDocument)` (root-first) to inspect ancestors. A plugin may **create or modify** the link and return it for the next plugin (or the client):

```kotlin
class CuriAwarePlugin : HaldishPlugin {
    override fun preLink(
        link: HalLink,
        path: ResourcePath,
        rootDocument: HalDocument,
    ): HalLink {
        // Ancestor documents, nearest (container) first
        val ancestorsNearestFirst = path.documentsToContainer(rootDocument).asReversed()

        // Resolve the CURI prefix on the relation name, if any
        val rel = path.terminalRel
        val colonIdx = rel.indexOf(':')
        if (colonIdx > 0) {
            val prefix = rel.substring(0, colonIdx)
            val suffix = rel.substring(colonIdx + 1)
            val curie = ancestorsNearestFirst.flatMap { it.links("curies") }.find { it.name == prefix }
            if (curie != null) {
                val docsUrl = curie.href.replace("{rel}", suffix)
                println("Following $rel  →  docs: $docsUrl")
            }
        }

        return link  // return unchanged; modify link.href etc. if needed
    }
}
```

`documentsToContainer` returns a **list** (root → container) so repeated relation names (e.g. "items" nested inside "items") are preserved correctly. The path's `Item`/`Embedded` steps carry the array `index` at each level.

For a link selected from a top-level array response, the path begins with a `PathStep.Item(index)`; use it to distinguish "came from items array" from "came from an embedded relation". A `Property` terminal produces a synthesized `HalLink` from the property's string value, which the plugin may then rewrite like any other link.

---

### `initialize` and `pluginOverride`

When `HalHttpClient` **discovers** a plugin via platform mechanisms (ServiceLoader, `window.__haldishPlugin`, etc.) it calls `initialize()` automatically on the first HTTP operation.

When a plugin is injected via `pluginOverride`, **the caller is responsible for calling `initialize()`** before passing the plugin.  `HalHttpClient` will not call it again.

```kotlin
val plugin = ApiKeyPlugin()
plugin.initialize(HaldishPluginConfig(platform = "jvm", version = "1.0",
    properties = mapOf("apiKey" to System.getenv("MY_KEY"))))
HalHttpClient(pluginOverride = plugin).get("https://api.example.com/")
```

The `:nahal-core` module handles this automatically when you use `HalNavigator` with a config file — see [Declarative configuration](#declarative-configuration-nahal-core) below.

---

## Platform registration

### JVM

No code changes in the library.  The plugin is discovered via **Java ServiceLoader**.

**Option A — classpath (e.g. fat JAR):**

1. Create a class that implements `HaldishPlugin`.
2. Add `META-INF/services/com.helpchoice.nahal.haldish.plugin.HaldishPlugin` to the JAR
   with the fully-qualified class name on the first line.
3. Ensure the JAR is on the classpath at runtime.

**Option B — external JAR via environment variable:**

Set `HALDISH_PLUGIN_PATH=/path/to/your-plugin.jar` before starting the JVM.
The library loads that JAR (in addition to the classpath) and uses the first
`HaldishPlugin` found in it.

If no plugin is found the library operates normally (no-op).

---

### iOS / macOS — Swift / Objective-C

Call `HaldishPluginRegistry.setPlugin(myPlugin)` **before** any HTTP call is made
(e.g. in `AppDelegate.application(_:didFinishLaunchingWithOptions:)`):

```swift
// Swift
import haldish

class MyAuthPlugin: HaldishPlugin {
    func preRequest(request: HalHttpRequest) -> HalHttpRequest {
        return request  // return modified copy
    }
}

// AppDelegate.swift
HaldishPluginRegistry.shared.setPlugin(MyAuthPlugin())
```

If nothing is registered the library continues with a no-op plugin.

**macOS note:** As an alternative to static registration, macOS applications can
also use the `HALDISH_PLUGIN_PATH` environment variable to point to a `.dylib` —
see the *Native C API* section below.

---

### JS / Node.js

> **Using `:nahal-core`?** Set `window.__nahalConfig` and use `CorePluginRegistry` instead — see [Declarative configuration](#declarative-configuration-nahal-core).

Set `window.__haldishPlugin` **before** the Kotlin module is initialised
(i.e. before the `<script>` tag that loads the compiled JS) when using `HalHttpClient` directly:

```html
<script>
window.__haldishPlugin = {
    initialize: function(platform, version) {
        console.log('HALDiSh plugin init on ' + platform + ' v' + version);
    },

    preRequest: function(req) {
        // req = { url, method, headers, cookies, body, acceptHal }
        req.headers['Authorization'] = 'Bearer ' + getToken();
        return req;   // return the (modified) object
    },

    postResponse: function(doc, resp) {
        // doc  = { links, embedded, properties }
        // resp = { statusCode, headers, cookies, body, contentType } (read-only)
        doc.links['audit'] = [{ href: '/audit', templated: false }];
        return doc;
    }
};
</script>
<script src="haldish.js"></script>
```

**Missing callbacks are silently ignored** — you do not need to implement all three.

#### JS object shapes

**`preRequest` argument:**
```js
{
  url:       "https://…",
  method:    "GET",
  headers:   { "key": "value", … },
  cookies:   { "key": "value", … },
  body:      null | { type, content, contentType },
  acceptHal: true
}
```

**`postResponse` `doc` argument:**
```js
{
  links: {
    "self": [{ href, templated, type, name, title, hreflang, profile, deprecation }, …],
    …
  },
  embedded: { "rel": [<doc>, …], … },
  properties: { "key": <json-value>, … }
}
```

**`postResponse` `resp` argument (read-only):**
```js
{
  statusCode:  200,
  headers:     { "Content-Type": ["application/hal+json"], … },
  cookies:     { "key": "value", … },
  body:        "…raw response body…",
  contentType: "application/hal+json"
}
```

---

### WasmJS

Identical contract to JS — set `window.__haldishPlugin` before the Wasm module loads.

> **Using `:nahal-core`?** Set `window.__nahalConfig` instead — see [Declarative configuration](#declarative-configuration-nahal-core).

---

### Native C API (Linux / Windows / macOS)

Two equivalent mechanisms are available:

#### Mechanism A — Dynamic library (recommended for packaged plugins)

1. Set `HALDISH_PLUGIN_PATH=/path/to/libmyplugin.so` (Linux) or `.dll` (Windows) or `.dylib` (macOS).
   If the env var is absent, the library looks for `libhaldish_plugin.so` /
   `haldish_plugin.dll` / `libhaldish_plugin.dylib` in the default library search path.
2. The plugin library must export the symbols described below.

#### Mechanism B — In-process function pointers (simpler for embedded use)

Call `haldish_plugin_register(init_fn, pre_request_fn, post_response_fn)`
**before the first HTTP call**.  Pass `NULL` for any hook you do not need.

```c
#include "libhaldish_api.h"

void my_init(const char* platform, const char* version) {
    printf("plugin init on %s v%s\n", platform, version);
}

/* Returns NULL to keep request unchanged, or a JSON string to override fields */
const char* my_pre_request(
    const char*  url,
    const char*  method,
    int          header_count,
    const char** header_keys,
    const char** header_vals,
    const char*  body,           /* may be NULL */
    const char*  content_type,   /* may be NULL */
    int          accept_hal      /* 1 = true */
) {
    /* Example: add an Authorization header by returning a JSON diff */
    static char buf[512];
    snprintf(buf, sizeof(buf),
        "{\"headers\": {\"Authorization\": \"Bearer mytoken\"}}");
    return buf;
}

int main(void) {
    haldish_plugin_register(my_init, my_pre_request, NULL);
    /* now use haldish_get(), etc. */
    haldish_get("https://api.example.com/");
    printf("status: %d\n", haldish_last_status());
}
```

---

## C callback reference

All three callbacks are **optional** — pass `NULL` (or omit from the dynamic library) for any hook you do not need.

### `haldish_plugin_init`

```c
void haldish_plugin_init(const char* platform, const char* version);
```

Called once on the first HTTP operation.  `platform` is one of `"linux"`, `"windows"`, `"apple"`, etc.

---

### `haldish_plugin_pre_request`

```c
const char* haldish_plugin_pre_request(
    const char*  url,
    const char*  method,
    int          header_count,
    const char** header_keys,    /* [header_count] entries */
    const char** header_vals,    /* [header_count] entries */
    const char*  body,           /* NULL if no body */
    const char*  content_type,   /* NULL if unknown */
    int          accept_hal      /* 1 or 0 */
);
```

Return `NULL` to send the request unchanged.

Return a pointer to a **JSON string** describing the fields to override:

```json
{
  "url":         "https://…",
  "method":      "POST",
  "headers":     { "Authorization": "Bearer …" },
  "body":        "…",
  "contentType": "application/json",
  "acceptHal":   true
}
```

Any key absent from the JSON keeps its original value.  If `"headers"` is present
it **replaces all headers** (merge manually if you want to extend them).

**Memory rule:** The returned pointer must remain valid until the **next** call to
this function.  Using a `static char buf[N]` is the simplest approach.

---

### `haldish_plugin_post_response`

```c
const char* haldish_plugin_post_response(
    int          link_count,
    const char** link_rels,          /* [link_count] */
    const char** link_hrefs,         /* [link_count] */
    const int*   link_templated,     /* [link_count], 1 = true */
    int          prop_count,
    const char** prop_keys,          /* [prop_count] */
    const char** prop_json_vals,     /* [prop_count], JSON-encoded */
    int          status_code,        /* HTTP status (read-only context) */
    const char*  resp_body,          /* raw response body (read-only) */
    const char*  resp_content_type   /* may be NULL */
);
```

The plugin receives the **parsed HAL elements** as flat arrays — no parsing required.

Return `NULL` to keep the document unchanged.

Return a **JSON string** describing the document modifications:

```json
{
  "links": {
    "self":      [{ "href": "/", "templated": false }],
    "canonical": [{ "href": "/v2/resource", "templated": false, "title": "Canonical" }]
  },
  "properties": {
    "injected": "value"
  }
}
```

Only keys present in the JSON are modified; the rest are preserved.

**Memory rule:** Same as `haldish_plugin_pre_request` — return value must be valid until the next call.

---

## Memory ownership summary

| Direction | Owner |
|-----------|-------|
| Input strings passed to C callbacks | Library (valid only during the callback) |
| String returned from C callbacks | Plugin (must remain valid until next call to same function) |
| Kotlin/JVM/JS objects | Managed by the GC, no ownership concerns |

---

## No-op fallback

If no plugin is discovered on any platform, the library operates normally using an
internal `NoOpPlugin` — all hooks are identity functions.  This means plugin support
adds **zero overhead** when no plugin is present (the lazy initialiser never invokes
discovery more than once).

---

## Declarative configuration (`:nahal-core`)

The `:nahal-core` module adds a **config-file-driven plugin loading mechanism** on top of `HaldishPlugin`.  When you use `HalNavigator` (instead of `HalHttpClient` directly), plugins are loaded, configured, and injected automatically at construction time.  Platform-level discovery (ServiceLoader, `window.__haldishPlugin`, etc.) is **bypassed entirely** — plugins are only active when a config source is present.

### Config file format

The config file is a nested JSON or YAML document.  Package segments of the plugin's fully-qualified class name become nested keys; the class name is the first node that resolves to a `HaldishPlugin`.  Its children are passed to `initialize()` as `config.properties`.

```yaml
# plugins.yaml
com:
  example:
    ApiKeyPlugin:
      apiKey: secret123
      header: X-Api-Key
    LoggerPlugin:
      outputDir: /tmp/hal-log
```

```json
// plugins.json
{ "com": { "example": {
    "ApiKeyPlugin": { "apiKey": "secret123", "header": "X-Api-Key" },
    "LoggerPlugin": { "outputDir": "/tmp/hal-log" }
}}}
```

YAML is supported on JVM and JS/Node.js (kaml).  On native targets use JSON.

### How it works per platform

| Platform | Config source | Plugin instantiation |
|----------|--------------|---------------------|
| JVM | `HALDISH_CONFIG` env var (file path) | `Class.forName(fqn)` — no-arg constructor required |
| JS / Node.js | `HALDISH_CONFIG` env var (file path) | `CorePluginRegistry` lookup by FQN |
| JS / Browser | `window.__nahalConfig` JS object | `CorePluginRegistry` lookup by FQN |
| WasmJS | `window.__nahalConfig` JS object | `CorePluginRegistry` lookup by FQN |
| Native (Linux / macOS / Windows) | `HALDISH_CONFIG` env var (file path, JSON only) | `CorePluginRegistry` lookup by FQN |

If neither `HALDISH_CONFIG` nor `window.__nahalConfig` is present, `HalNavigator` uses a no-op plugin — **no exceptions thrown**.

### JVM usage

Point `HALDISH_CONFIG` at a JSON or YAML file.  Any `HaldishPlugin` class on the classpath that has a no-arg constructor can be referenced by FQN:

```bash
HALDISH_CONFIG=/etc/myapp/plugins.yaml java -jar myapp.jar
```

### JS / Node.js usage

```bash
HALDISH_CONFIG=./plugins.json node myapp.js
```

### Browser JS / WasmJS usage

Set `window.__nahalConfig` before the Kotlin module initialises, **and** register plugin instances in `CorePluginRegistry`:

```html
<script>
// 1. Provide the config tree (same nested format as JSON/YAML)
window.__nahalConfig = {
  "com": { "example": {
    "ApiKeyPlugin": { "apiKey": "secret123" }
  }}
};
</script>
<script src="myapp.js"></script>
```

```kotlin
// 2. Register the plugin instance in Kotlin before HalNavigator is constructed
//    (e.g. in main() or app init)
CorePluginRegistry.register("com.example.ApiKeyPlugin", ApiKeyPlugin())
```

### Native (Linux / macOS / Windows) usage

```bash
HALDISH_CONFIG=/etc/myapp/plugins.json ./myapp
```

```kotlin
// Register before HalNavigator is constructed
CorePluginRegistry.register("com.example.ApiKeyPlugin", ApiKeyPlugin())
```

### Error behaviour

| Situation | Result |
|-----------|--------|
| FQN in config not on classpath (JVM) | `PluginConfigException` at startup |
| FQN in config not in `CorePluginRegistry` (non-JVM) | `PluginConfigException` at startup |
| JVM plugin class has no no-arg constructor | `PluginConfigException` at startup |
| FQN resolves to a class that is not `HaldishPlugin` (JVM) | `PluginConfigException` at startup |
| Multiple plugins | All are loaded; chained left-to-right automatically |

---

## Ready-made example plugins

The following plugins are available as separate Maven artifacts under
`com.helpchoice.nahal`. Each is an independent Kotlin Multiplatform module that can be
used as-is or as a starting point for your own plugin.

### `haldish-plugin-api-key` — API Key injection *(KMP)*

Adds a configurable header to every request.  Defaults to `X-Api-Key`; can be changed
to `Authorization` or any other header name.

```kotlin
HalHttpClient(pluginOverride = ApiKeyPlugin(apiKey = System.getenv("MY_KEY") ?: ""))
```

### `haldish-plugin-bearer-token` — Bearer token auth *(per-platform)*

Injects `Authorization: Bearer <token>` into every request.  This module is a
**per-platform authoring example**: each platform source set (`jvmMain`, `jsMain`,
`appleMain`, `linuxMain`, `mingwMain`, `wasmJsMain`) carries its own self-contained
class — copy the relevant file as a template when your plugin needs genuinely
platform-specific logic (e.g. iOS Keychain, Windows Credential Manager).

```kotlin
// JVM — programmatic
HalHttpClient(pluginOverride = BearerTokenPlugin(token = myToken))

// JVM — ServiceLoader: subclass and register in META-INF/services
class MyBearerPlugin : BearerTokenPlugin(token = System.getenv("TOKEN") ?: "")
```

### `haldish-plugin-base-url-rewriter` — Environment URL rewriting *(per-platform)*

Replaces the scheme + host of every outgoing URL with a configurable base, preserving
the path, query string, and fragment.  Useful for switching between production and
staging environments without changing the HAL links stored in responses.

```kotlin
// Redirect all HAL follow-up links to a local dev server
HalHttpClient(pluginOverride = BaseUrlRewriterPlugin("http://localhost:8080"))
```

Like `bearer-token`, this module is a per-platform authoring example.

### `haldish-plugin-chain` — Plugin combinator *(KMP)*

Chains multiple plugins into one.  Lifecycle hooks are applied in declaration order:
`initialize` → each plugin left-to-right; `preRequest` → threaded through all plugins;
`postResponse` → threaded through all plugins.

```kotlin
HalHttpClient(
    pluginOverride = ChainPlugin(
        BaseUrlRewriterPlugin("https://staging.example.com"),
        BearerTokenPlugin(token = myToken),
        LoggerPlugin(directory = "/tmp/hal-log"),   // logger last — sees final URL
    )
)
```

### `haldish-plugin-logger` — Request / response file logger *(KMP)*

Writes every HTTP exchange as a named set of files under a configurable directory.
The base name is `yyyyMMddTHHmmss_NNN` (local-time stamp + per-instance counter).

| File | Content |
|------|---------|
| `<base>.curl` | Equivalent `curl` command |
| `<base>.url` | Final URL sent (after any plugin rewrites) |
| `<base>.code` | HTTP status code number only |
| `<base>.status` | Status code + reason phrase, e.g. `200 (OK)` |
| `<base>.headers` | Response headers, one `Name: value` per line |
| `<base>.body` | Raw response body as received |
| `<base>.<fmt>` | Pretty-printed body (`.json`, `.xml`, `.yaml`, `.html`, `.txt`, `.dat`) |

```kotlin
HalHttpClient(pluginOverride = LoggerPlugin(directory = "./hal-log"))
```

**Platform note:** file output works on JVM, Node.js, and native (Linux/macOS/Windows).
On browser JS and WasmJS targets there is no writable sandbox filesystem; the logger
falls back to `console.log` output.
