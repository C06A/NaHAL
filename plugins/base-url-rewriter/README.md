# HALDiSh plugin base-url-rewriter

Resolves relative link hrefs against the base URL of the HAL resource they were found in,
preserving any path prefix added by a reverse proxy or API gateway.

When a HAL server sits behind a gateway that adds a prefix (`/v2`, `/api/internal`, …), the
`self` link typically reflects the upstream server's path while `HalDocument.sourceUrl` carries
the full gateway URL. This plugin detects the overlap and strips only the resource-specific
suffix, keeping the prefix intact:

```
Resource loaded from: https://gw.example.com/v2/orders/123
  self link:          /orders/123
  derived base:       https://gw.example.com/v2

  link href: /customers/456   →  https://gw.example.com/v2/customers/456
  link href: ?page=2          →  https://gw.example.com/v2?page=2
```

If there is no `self` link, or the `self` path is not a suffix of `sourceUrl`, the plugin
uses its cached base — the value derived from the most recent successful match, or the
`configuredBase` constructor argument if provided — and falls back to `scheme://host[:port]`
only when the cache is empty:

```
// First GET: self matches → base cached as https://gw.example.com/v2
// POST to /v2/orders/123/item → returns self: /items/321 (unrelated path)
// cached base used:  https://gw.example.com/v2

  link href: /orders   →  https://gw.example.com/v2/orders
```

Embedded sub-documents have no `sourceUrl`; their links are left unchanged. Absolute hrefs
(containing `://`) are always passed through unchanged.

**Per-platform authoring example.** This plugin has no shared `commonMain` — each
platform's source set is intentionally self-contained. Copy any platform's file as a
starting point for a plugin that needs platform-specific environment routing
(Spring `Environment`, Android `BuildConfig`, iOS `Bundle.main`, etc.).

---

## Add to your project

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.helpchoice.nahal:haldish-plugin-base-url-rewriter:0.1.0")
}
```

### Maven

```xml
<dependency>
    <groupId>com.helpchoice.nahal</groupId>
    <artifactId>haldish-plugin-base-url-rewriter</artifactId>
    <version>0.1.0</version>
</dependency>
```

---

## Programmatic usage

Optional `configuredBase` seeds the cache before any document is seen — useful when the
heuristic can't fire on the first request (e.g. the first call is a POST):

```kotlin
val client = HalHttpClient(
    pluginOverride = BaseUrlRewriterPlugin(configuredBase = "https://gw.example.com/v2")
)
```

Without a configured base the plugin starts empty and derives the base from the first
document whose `self` link matches the request URL:

```kotlin
import com.helpchoice.nahal.haldish.http.HalHttpClient
import com.helpchoice.nahal.plugin.baseurlrewriter.BaseUrlRewriterPlugin

val client = HalHttpClient(pluginOverride = BaseUrlRewriterPlugin())
```

---

## Use with the NaHAL desktop app

The NaHAL app discovers plugins via Java **ServiceLoader**. Because `BaseUrlRewriterPlugin`
has a no-argument constructor, it can be registered directly — no adapter class needed.

### Step 1 — Create a plugin JAR

**`build.gradle.kts`**

```kotlin
plugins {
    kotlin("jvm") version "2.1.0"
}

repositories { mavenCentral() }

dependencies {
    implementation("com.helpchoice.nahal:haldish-plugin-base-url-rewriter:0.1.0")
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
}
```

**`src/main/resources/META-INF/services/com.helpchoice.nahal.haldish.plugin.HaldishPlugin`**

```
com.helpchoice.nahal.plugin.baseurlrewriter.BaseUrlRewriterPlugin
```

### Step 2 — Build the JAR

```bash
./gradlew jar
# → build/libs/my-base-url-rewriter.jar
```

### Step 3 — Run NaHAL with the plugin

**From the nahal source tree:**

```bash
HALDISH_PLUGIN_PATH=/path/to/my-base-url-rewriter.jar ./gradlew :ui:run
```

**From a packaged NaHAL distribution:**

```bash
# macOS
HALDISH_PLUGIN_PATH=/path/to/my-base-url-rewriter.jar open -a NaHAL

# Linux / Windows
HALDISH_PLUGIN_PATH=/path/to/my-base-url-rewriter.jar ./bin/NaHAL
```
