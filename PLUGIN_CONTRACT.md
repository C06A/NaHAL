# HALDiSh Plugin Contract

A HALDiSh plugin is a piece of code **loaded at runtime** — neither the library nor the application
is recompiled to add one. The plugin implements `HaldishPlugin` (from
`com.helpchoice.nahal:haldish`) and intercepts four lifecycle points:

| Hook | When | What it can do |
|------|------|----------------|
| `initialize` | Once, before the first HTTP call | Read `config.properties`, establish connections, cache state |
| `preLink` | Before following a link (or a property URL) from a `HalDocument` | Create/modify the link using document context — the `ResourcePath` to the target and the root document (CURIE lookup, rel name, ancestor walk) |
| `preRequest` | Before every HTTP request | Modify URL, method, headers, cookies, body |
| `postResponse` | After a HAL response is parsed (`getHal` / `executeAndParse`) | Add, remove or modify links, embedded docs, properties |

All hooks have **default no-op implementations** — implement only those you need.

## Two ways a plugin gets activated

They are independent, and the second one wins wherever it applies.

| | **A — haldish platform discovery** | **B — NaHAL declarative config** |
|---|---|---|
| Who does it | `HalHttpClient` itself, on first use | `HalNavigator` (the navigation layer, shipped inside `com.helpchoice.nahal:nahal-ui`) |
| Trigger | JVM `ServiceLoader` / `HALDISH_PLUGIN_PATH` / `HaldishPluginRegistry` / `window.__haldishPlugin` / `dlopen` | `HALDISH_CONFIG` file, or `window.__nahalConfig` |
| How many plugins | Exactly one (the first found) | Any number, chained in config order |
| Per-plugin properties | No — `initialize` gets platform + version only | Yes — each entry's children become `config.properties` |
| Details | [Platform discovery](#platform-discovery-mechanism-a) | [Declarative configuration](#declarative-configuration-mechanism-b) |

`HalNavigator` **bypasses** platform discovery: it always passes `pluginOverride` to
`HalHttpClient`, so a plugin on the classpath or a jar in a drop-in directory stays inert until a
config names it. The one exception is `HALDISH_PLUGIN_PATH` set with no config — then the navigator
passes no override and haldish's own loader takes that single artifact.

> **Where the navigation layer lives.** The `:core` module of the NaHAL repository is **not
> published**; its sources are compiled into `com.helpchoice.nahal:nahal-ui`. There is no
> `nahal-core` artifact to depend on — `HalNavigator`, `CorePluginRegistry` and `chainedPlugin`
> all reach you inside `nahal-ui`. (Older revisions of this document called it `:nahal-core`.)

---

## Plugin interface (Kotlin — JVM, JS, WasmJS, Native, Apple)

```kotlin
package com.helpchoice.nahal.haldish.plugin

interface HaldishPlugin {
    fun initialize(config: HaldishPluginConfig) {}
    fun preLink(link: HalLink, path: ResourcePath, rootDocument: HalDocument): HalLink = link
    fun preRequest(request: HalHttpRequest): HalHttpRequest = request
    fun postResponse(document: HalDocument, response: HalHttpResponse): HalDocument = document
}

data class HaldishPluginConfig(
    val platform: String,                             // see the platform-string table below
    val version: String,                              // library version constant
    val properties: Map<String, Any?> = emptyMap(),   // only mechanism B fills this
)
```

`platform` values differ by which mechanism initialised the plugin:

| Mechanism | JVM / Android | JS | WasmJS | Linux | Windows | macOS / iOS |
|---|---|---|---|---|---|---|
| A — haldish discovery | `"jvm"` | `"js"` | `"wasmjs"` | `"linux"` | `"windows"` | `"apple"` |
| B — declarative config | `"jvm"` | `"js"` | `"wasmjs"` | `"native"` | `"native"` | `"native"` |

`version` is a compile-time constant inside the library, kept in sync by hand with the build — treat
it as diagnostic text, not as something to branch on.

### Model types the hooks receive

```kotlin
data class HalLink(
    val href: String,
    val templated: Boolean = false,
    val type: String? = null,
    val name: String? = null,
    val title: String? = null,
    val hreflang: String? = null,
    val profile: String? = null,
    val deprecation: String? = null,
)

data class HalDocument(
    val links: Map<String, List<HalLink>> = emptyMap(),
    val embedded: Map<String, List<HalDocument>> = emptyMap(),
    val properties: Map<String, JsonElement> = emptyMap(),
    val rawBody: String? = null,       // the body as received (JSON/XML/YAML)
    val items: List<HalDocument> = emptyList(),   // top-level array responses
    val sourceUrl: String? = null,     // set by the client; null for embedded sub-documents
)

data class HalHttpRequest(
    val url: String,
    val method: HttpMethod = HttpMethod.Get,       // ktor HttpMethod
    val headers: Map<String, String> = emptyMap(),
    val cookies: Map<String, String> = emptyMap(),
    val body: HalRequestBody = HalRequestBody.None,
    val acceptHal: Boolean = true,
)

// HalRequestBody: None | Text(content, contentType) | Json(content) | UrlEncoded(params)
//               | Binary(bytes, contentType) | FilePath(path, contentType) | Multipart(parts)

data class HalHttpResponse(                        // read-only context in postResponse
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val cookies: Map<String, String>,
    val body: String,
    val contentType: String?,
    val bytes: ByteArray = body.encodeToByteArray(),
)
```

`ResourcePath` — an addressable path from a root `HalDocument` to the link or property that produces
a URL, following the grammar `Item? Embedded* (Link | Property+)`: an optional leading `Item` (a
top-level array response), a chain of `Embedded` descents, then a terminal — a single `Link`, or a
chain of `Property` names (a nested property URL). A path with no terminal resolves to the reached
resource's `self` link.

```kotlin
sealed interface PathStep {
    data class Item(val index: Int) : PathStep                          // items[index]
    data class Embedded(val rel: String, val index: Int = 0) : PathStep // _embedded[rel][index]
    data class Link(val rel: String, val index: Int = 0) : PathStep     // _links[rel][index]  (terminal)
    data class Property(val name: String, val index: Int? = null) : PathStep // property name (terminal chain);
                                                                             // index selects inside a JSON array
}

data class ResolvedTarget(val link: HalLink, val container: HalDocument, val documents: List<HalDocument>)

data class ResourcePath(val steps: List<PathStep>) {
    val terminalRel: String                                         // link rel, last property name, or "self"
    fun documentsToContainer(root: HalDocument): List<HalDocument>  // root .. container of the terminal
    fun resolve(root: HalDocument): ResolvedTarget?                 // link + container + document chain, or null
    fun toJson(): String                                            // tagged-step array (the C ABI's path_json)
    companion object {
        const val SELF_REL = "self"
        fun link(rel: String, index: Int = 0): ResourcePath   // a top-level _links[rel][index]
        fun self(): ResourcePath                              // the reached resource's self link
        fun property(vararg names: String): ResourcePath      // a nested property URL
        fun fromJson(json: String): ResourcePath
    }
}
```

### `preLink` — document-context hook

`preLink` fires when navigation originates from a HAL link or property (via `HalNavigator`, or via
`HalHttpClient.resolveLink` directly), **not** for bare-URL calls such as `HalHttpClient.get`. The
`path` addresses the target from `rootDocument`; the relation name is `path.terminalRel` and the
document directly holding the link is `path.documentsToContainer(rootDocument).last()`. A plugin may
**create or modify** the link and return it for the next plugin (or the client):

```kotlin
class CuriAwarePlugin : HaldishPlugin {
    override fun preLink(
        link: HalLink,
        path: ResourcePath,
        rootDocument: HalDocument,
    ): HalLink {
        // Ancestor documents, nearest (container) first
        val ancestorsNearestFirst = path.documentsToContainer(rootDocument).asReversed()

        // Resolve the CURIE prefix on the relation name, if any
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

`documentsToContainer` returns a **list** (root → container) so repeated relation names (e.g.
"items" nested inside "items") are preserved correctly. The path's `Item`/`Embedded` steps carry the
array `index` at each level.

For a link selected from a top-level array response, the path begins with a `PathStep.Item(index)`;
use it to distinguish "came from items array" from "came from an embedded relation". A `Property`
terminal produces a synthesized `HalLink` from the property's string value, which the plugin may
then rewrite like any other link.

**`preLink` is Kotlin- and C-only.** The JS / WasmJS `window.__haldishPlugin` object supports
`initialize`, `preRequest` and `postResponse` — a `preLink` key on it is ignored.

### `initialize` and `pluginOverride`

When `HalHttpClient` **discovers** a plugin itself (mechanism A) it calls `initialize()`
automatically on the first HTTP operation, with `platformPluginConfig()` (platform + version, no
properties).

When a plugin is injected via `pluginOverride`, **the caller is responsible for calling
`initialize()`** — `HalHttpClient` will not call it:

```kotlin
val plugin = ApiKeyPlugin()
plugin.initialize(HaldishPluginConfig(platform = "jvm", version = "2.1.0",
    properties = mapOf("apiKey" to System.getenv("MY_KEY"))))
HalHttpClient(pluginOverride = plugin).get("https://api.example.com/")
```

`HalNavigator` does this for you when the config file names the plugin — see
[Declarative configuration](#declarative-configuration-mechanism-b).

---

## Developing a plugin

### 1. Gradle setup

A plugin is an ordinary Kotlin Multiplatform library depending on the published haldish artifact —
nothing from the NaHAL build is required to *write* one:

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()
    js(IR) { browser(); nodejs(); binaries.library() }
    @OptIn(ExperimentalWasmDsl::class) wasmJs { browser() }

    // Shared libraries let haldish dlopen the plugin on native targets.
    // The base name must be haldish_plugin -> libhaldish_plugin.{so,dylib} / haldish_plugin.dll
    linuxX64   { binaries { sharedLib { baseName = "haldish_plugin" } } }
    macosArm64 { binaries { sharedLib { baseName = "haldish_plugin" } } }
    mingwX64   { binaries { sharedLib { baseName = "haldish_plugin" } } }
    iosArm64(); iosSimulatorArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api("com.helpchoice.nahal:haldish:<version>")   // libs.haldish in a version catalog
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
```

Add `com.helpchoice.nahal:nahal-ui` **only** if the plugin itself calls into the navigation layer
(e.g. registering with `CorePluginRegistry` from a non-JVM app entry point). Published plugin
artifacts normally do not depend on it.

### 2. Write the class

Give it a **no-arg constructor** — the JVM config loader instantiates it reflectively — and read
everything else from `initialize`:

```kotlin
package com.example

class ApiKeyPlugin : HaldishPlugin {
    private var key: String? = null
    private var header: String = "X-Api-Key"

    override fun initialize(config: HaldishPluginConfig) {
        key = config.properties["apiKey"] as? String
        header = config.properties["header"] as? String ?: header
    }

    override fun preRequest(request: HalHttpRequest): HalHttpRequest =
        key?.let { request.copy(headers = request.headers + (header to it)) } ?: request
}
```

Property values arrive as plain Kotlin types — `String`, `Long`, `Double`, `Boolean`, `null`,
`List<Any?>`, `Map<String, Any?>` — from both JSON and YAML. Cast defensively; a typo in a config
file must not crash the app.

Hooks are called **serially** and must not block: they run on the request path. When you change
nothing, return the value you were given — the native bridge compares the result with the input
(data-class equality) and skips emitting a diff when they match.

### 3. Package it per platform

| Target | What to ship | Contract |
|---|---|---|
| JVM | A jar carrying `META-INF/services/com.helpchoice.nahal.haldish.plugin.HaldishPlugin` with the implementing class's FQN on the first non-blank line | Needed by `ServiceLoader` for mechanism A. Mechanism B needs only the class on the classpath, but shipping the services file costs nothing and enables both |
| Native (Linux/Windows/macOS) | `libhaldish_plugin.{so,dylib}` / `haldish_plugin.dll` exporting the `haldish_plugin_*` C symbols | See the `@CName` bridge below |
| JS / WasmJS browser | Either a `window.__haldishPlugin` object (mechanism A) or a Kotlin instance registered in `CorePluginRegistry` (mechanism B) | No dynamic library loading in a browser |
| iOS | A Kotlin/Swift instance registered at app startup | `HaldishPluginRegistry.setPlugin(…)` or `CorePluginRegistry.register(…)` |

For the native shared library, write a thin `@CName` bridge that delegates to `NativePluginBridge`
(haldish's author-side helper — it rebuilds the arguments, calls your plugin and emits the JSON
diff, including the per-hook C-string lifetime the ABI requires):

```kotlin
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.example

import com.helpchoice.nahal.haldish.plugin.NativePluginBridge
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer

private val plugin = ApiKeyPlugin()

@CName("haldish_plugin_init")
fun haldishPluginInit(platform: CPointer<ByteVar>?, version: CPointer<ByteVar>?) =
    NativePluginBridge.init(plugin, platform, version)

@CName("haldish_plugin_pre_request")
fun haldishPluginPreRequest(
    url: CPointer<ByteVar>?, method: CPointer<ByteVar>?,
    headerCount: Int,
    headerKeys: CPointer<CPointerVar<ByteVar>>?, headerVals: CPointer<CPointerVar<ByteVar>>?,
    body: CPointer<ByteVar>?, contentType: CPointer<ByteVar>?, acceptHal: Int,
): CPointer<ByteVar>? =
    NativePluginBridge.preRequest(plugin, url, method, headerCount, headerKeys, headerVals,
        body, contentType, acceptHal)

// …and haldish_plugin_pre_link / haldish_plugin_post_response the same way, for the hooks you implement.
```

Export only the hooks you implement — missing symbols are treated as no-ops. **The C `init` carries
no properties**, so a native plugin cannot be configured from the config file: read environment
variables (or bake the settings in) instead.

### 4. Test it

Hooks are plain functions on a plain object — construct the plugin, call `initialize` with a
hand-built `HaldishPluginConfig`, and assert on what `preRequest` / `preLink` / `postResponse`
return. No HTTP, no classloader, no config file:

```kotlin
@Test
fun addsTheHeader() {
    val plugin = ApiKeyPlugin()
    plugin.initialize(HaldishPluginConfig("jvm", "test", mapOf("apiKey" to "secret")))
    val out = plugin.preRequest(HalHttpRequest(url = "https://api.example.com/"))
    assertEquals("secret", out.headers["X-Api-Key"])
}
```

---

## Platform discovery (mechanism A)

Used by `HalHttpClient` when no `pluginOverride` is passed. Exactly one plugin is found; if none is,
the library runs an internal `NoOpPlugin` whose hooks are identity functions — **zero overhead**, and
discovery runs at most once.

### JVM

1. If `HALDISH_PLUGIN_PATH` (a real environment variable) points at an existing file, that jar is
   opened on a child `URLClassLoader` and searched via `ServiceLoader`; the first `HaldishPlugin`
   wins.
2. Otherwise `ServiceLoader` scans the **thread context classloader**.
3. Otherwise `NoOpPlugin`.

Either way the jar needs
`META-INF/services/com.helpchoice.nahal.haldish.plugin.HaldishPlugin`.

### iOS / macOS — Swift / Objective-C

Call `HaldishPluginRegistry.setPlugin(…)` **before** any HTTP call, e.g. in
`AppDelegate.application(_:didFinishLaunchingWithOptions:)`:

```swift
import haldish

class MyAuthPlugin: HaldishPlugin {
    func preRequest(request: HalHttpRequest) -> HalHttpRequest {
        return request  // return modified copy
    }
}

HaldishPluginRegistry.shared.setPlugin(MyAuthPlugin())
```

Only one plugin at a time; a later call replaces the earlier one. `clearPlugin()` reverts to no-op.

Discovery order on Apple targets is: the registry, then `HALDISH_PLUGIN_PATH` via `dlopen`, then
`dlopen("libhaldish_plugin.dylib")` from the default search path. **On iOS the `dlopen` steps
normally fail** (the OS restricts loading unsigned libraries) and the loader falls through
silently — treat static registration as the only iOS mechanism.

### JS / Node.js and WasmJS

Set `window.__haldishPlugin` **before** the Kotlin module is initialised (i.e. before the `<script>`
tag that loads the compiled JS/Wasm). Both targets read the same global.

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

**Missing callbacks are silently ignored** — implement only what you need. There is no `preLink` on
this object.

> Using `HalNavigator` in the browser? Set `window.__nahalConfig` and use `CorePluginRegistry`
> instead — see [Declarative configuration](#declarative-configuration-mechanism-b).

#### JS object shapes

**`preRequest` argument:**
```js
{
  url:       "https://…",
  method:    "GET",
  headers:   { "key": "value", … },
  cookies:   { "key": "value", … },
  body:      null | { type, content, contentType }  // type: text|json|urlEncoded|binary|filePath|multipart
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

### Native (Linux / Windows / macOS)

Two equivalent mechanisms.

**Mechanism A1 — dynamic library (for packaged plugins).** Set `HALDISH_PLUGIN_PATH` to the
library's path; with the variable absent, the loader tries the default name in the platform's
library search path:

| Platform | Default name | Loader |
|---|---|---|
| Linux | `libhaldish_plugin.so` | `dlopen`, `LD_LIBRARY_PATH` |
| macOS / iOS | `libhaldish_plugin.dylib` | `dlopen`, `DYLD_LIBRARY_PATH` / `@rpath` |
| Windows | `haldish_plugin.dll` | `LoadLibraryA`, default DLL search path |

If none of the four `haldish_plugin_*` symbols is present the handle is closed and discovery falls
through to `NoOpPlugin`.

**Mechanism A2 — in-process function pointers (for embedded use).** Call
`haldish_plugin_register(init_fn, pre_request_fn, post_response_fn)` **before the first HTTP call**.
Pass `NULL` for any hook you do not need. Registration takes priority over `dlopen` on every native
platform.

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
    static char buf[512];
    snprintf(buf, sizeof(buf), "{\"headers\": {\"Authorization\": \"Bearer mytoken\"}}");
    return buf;
}

int main(void) {
    haldish_plugin_register(my_init, my_pre_request, NULL);
    haldish_get("https://api.example.com/");
    printf("status: %d\n", haldish_last_status());
}
```

`haldish_plugin_register` takes **three** callbacks — there is no `pre_link` slot. A plugin that
needs `preLink` must ship as a dynamic library exporting `haldish_plugin_pre_link`.

---

## C callback reference

A plugin library **may** export any subset of these four functions; missing symbols are no-ops.

### `haldish_plugin_init`

```c
void haldish_plugin_init(const char* platform, const char* version);
```

Called once on the first HTTP operation. `platform` is `"linux"`, `"windows"` or `"apple"`. No
properties are passed — configure a native plugin through environment variables or compile-time
constants.

### `haldish_plugin_pre_link`

```c
const char* haldish_plugin_pre_link(
    const char* rel,        /* the relation name being followed (path.terminalRel) */
    const char* href,       /* the link's current href (may be a URI template) */
    int         templated,  /* 1 if href is a URI template, else 0 */
    const char* type,       /* link "type", NULL if absent */
    const char* name,       /* link "name", NULL if absent */
    const char* title,      /* link "title", NULL if absent */
    const char* root_body,  /* the root document's raw body (JSON/XML/YAML), NULL if unavailable */
    const char* path_json   /* the ResourcePath from the root to this link, as JSON */
);
```

Fires before a link (or property) is followed, giving the plugin the target link plus full document
context — re-parse `root_body` to inspect CURIE collections and ancestors, and use `path_json`
(`ResourcePath.toJson()` format) to locate the link within it. Not called for bare-URL requests.

Return `NULL` to follow the link unchanged, or a **JSON string** overriding the link fields:

```json
{ "href": "https://…", "templated": false, "type": "…", "name": "…", "title": "…" }
```

Any key absent from the JSON keeps its original value. Note that `hreflang`, `profile` and
`deprecation` cannot be changed across the C ABI. Same memory rule as below.

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

Return `NULL` to send the request unchanged, or a **JSON string** describing the fields to override:

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

Any key absent from the JSON keeps its original value. If `"headers"` is present it **replaces all
headers** (merge manually to extend them). Cookies are not part of this ABI.

**Memory rule:** the returned pointer must remain valid until the **next** call to this function. A
`static char buf[N]` is the simplest approach.

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

The plugin receives the **parsed HAL elements** as flat arrays — no parsing required. Return `NULL`
to keep the document unchanged, or a **JSON string** describing the modifications:

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

Only keys present in the JSON are modified; the rest are preserved. A `"links"` object **replaces**
the whole link map, so re-emit the links you want to keep. Embedded documents cannot be changed
through this ABI.

**Memory rule:** same as `haldish_plugin_pre_request`.

### Memory ownership summary

| Direction | Owner |
|-----------|-------|
| Input strings passed to C callbacks | Library (valid only during the callback) |
| String returned from C callbacks | Plugin (must remain valid until next call to the same function) |
| Kotlin / JVM / JS objects | Managed by the GC, no ownership concerns |

`NativePluginBridge` already satisfies the return-value rule for Kotlin/Native plugins: it keeps one
heap C string per hook and frees it on that hook's next call.

---

## Declarative configuration (mechanism B)

The navigation layer (`HalNavigator`, shipped in `nahal-ui`) loads plugins from a config source,
initialises each with its own properties, chains them, and injects the chain as `pluginOverride` at
construction time.

### Config file format

A nested JSON or YAML document. Package segments of the plugin's fully-qualified class name become
nested keys; **the class name is the first node that resolves to a `HaldishPlugin`**, and its
children are passed to `initialize()` as `config.properties`.

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

Plugins run in **document order** (depth-first): above, `ApiKeyPlugin` sees each request first,
`LoggerPlugin` sees whatever it produced. Ordering deliberately lives here rather than in file names
or classpath order.

YAML is parsed with kaml on JVM/Android and JS/Node.js. **Native and WasmJS are JSON-only.**

### Config source per platform

| Platform | Config source | Plugin instantiation |
|----------|--------------|---------------------|
| JVM / Android | `HALDISH_CONFIG` — **system property first, environment variable as fallback** (file path) | `Class.forName(fqn)` through the thread context classloader; no-arg constructor required |
| JS / Node.js | `HALDISH_CONFIG` env var (file path; read with `fs`) | `CorePluginRegistry` lookup by FQN |
| JS / browser | `window.__nahalConfig` object | `CorePluginRegistry` lookup by FQN |
| WasmJS | `window.__nahalConfig` object | `CorePluginRegistry` lookup by FQN |
| Native (Linux / macOS / Windows) | `HALDISH_CONFIG` env var (file path, JSON only) | `CorePluginRegistry` lookup by FQN |

`window.__nahalConfig` takes precedence over `HALDISH_CONFIG` where both could exist.

The system-property fallback exists because a JVM process cannot set its own environment: an
embedding app that discovers a config at startup can `System.setProperty("HALDISH_CONFIG", …)`
before constructing `HalNavigator`. Note this covers only the navigator's own lookups —
`HALDISH_PLUGIN_PATH` handed on to haldish's loader must be a **real environment variable**, since
that loader reads `System.getenv` only.

### No config

With no config source, `HalNavigator` takes one of two paths — **no exceptions thrown** either way:

| Situation | Result |
|---|---|
| No config, no `HALDISH_PLUGIN_PATH` | No-op plugin. Nothing runs — not a plugin on the classpath, not a jar in a drop-in directory. Activation requires a config that names it. |
| No config, `HALDISH_PLUGIN_PATH` set | `HalNavigator` passes no override, so haldish's own loader takes the **single** artifact that variable points at: a JAR on the JVM, a `.dylib` / `.so` / `.dll` on native. Chain several plugins by pointing it at an artifact that combines them. The C ABI carries only `platform` and `version` to `initialize()`, so a native artifact cannot be configured from a config file — bake its settings in when you build it. |

### JVM usage

```bash
HALDISH_CONFIG=/etc/myapp/plugins.yaml java -jar myapp.jar
```

Any `HaldishPlugin` class reachable from the **thread context classloader**, with a no-arg
constructor, can be named by FQN. That is how an application offers a drop-in plugins directory
without rebuilding: put the directory's jars on a child `URLClassLoader`, install it as the context
classloader before constructing `HalNavigator`, and let the config name them.

The NaHAL desktop UI does exactly this (`ui/src/jvmMain/kotlin/com/helpchoice/nahal/ui/main.kt`):
every `*.jar` in `$NAHAL_PLUGINS_DIR` (default: `plugins/` in the working directory) goes on the
child loader, sorted by name — and that is *all* it does. Which of them run, in what order, with
what properties is the config file's decision alone.

### JS / Node.js usage

```bash
HALDISH_CONFIG=./plugins.json node myapp.js
```

### Browser JS / WasmJS usage

Set `window.__nahalConfig` before the Kotlin module initialises, **and** register plugin instances
in `CorePluginRegistry` (no reflection in the browser):

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
// 2. Register the instance in Kotlin before HalNavigator is constructed (e.g. in main())
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

A native app therefore has plugins **compiled in**; the config decides which of the registered ones
run and in what order. One caveat for bundled macOS apps: LaunchServices does not pass the shell
environment, so `HALDISH_CONFIG` / `HALDISH_PLUGIN_PATH` exported in a terminal never reach an app
started with `open` — run the executable directly when you need them.

### Assembling the chain yourself

An embedder that builds its plugins in code can skip config files entirely:

```kotlin
import com.helpchoice.nahal.core.plugin.chainedPlugin

val plugin = chainedPlugin(listOf(CuriePlugin(), ApiKeyPlugin().apply {
    initialize(HaldishPluginConfig("jvm", "2.1.0", mapOf("apiKey" to key)))
}))
val navigator = HalNavigator(plugin)     // secondary constructor taking an explicit plugin
```

`chainedPlugin` folds each hook through the list in order; an empty list gives a no-op plugin, a
single-element list gives that plugin unchanged. You own `initialize()` on this path.

### Error behaviour

| Situation | Result |
|-----------|--------|
| FQN in config not on the classpath (JVM) / not in `CorePluginRegistry` (other platforms) | The name is treated as package segments and the walk descends into its children; the first scalar child raises `PluginConfigException("Unexpected scalar at '<path>' — check for a typo in a plugin FQN")` |
| A misspelled plugin FQN whose value is an **empty** map | No exception and no plugin — the walk finds nothing to do. Check the plugin actually ran if a config entry seems ignored |
| JVM class has no no-arg constructor | `PluginConfigException` at construction |
| FQN resolves to a class that is not a `HaldishPlugin` (JVM) | `PluginConfigException` at construction |
| Config file unreadable / invalid JSON / invalid YAML / root not an object | `PluginConfigException` |
| YAML on native or WasmJS | `PluginConfigException` — use JSON |
| Multiple plugins | All loaded, chained in document order |

`PluginConfigException` is `com.helpchoice.nahal.core.PluginConfigException`, thrown while
`HalNavigator` is being constructed — i.e. at app startup, not mid-navigation.

---

## Example plugins

Ready-made plugins — usable as-is, or as templates for your own — live in their own repository,
[C06A/HALDiSh_Plugins](https://github.com/C06A/HALDiSh_Plugins). Nothing in this build depends on
them; see that repository for what it offers, how each one is configured, and how to run NaHAL with
them active.
