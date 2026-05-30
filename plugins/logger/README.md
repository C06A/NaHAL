# haldish-plugin-logger

Records every HALDiSh HTTP exchange as a set of named files.

For each request/response pair the plugin writes to a configurable directory:

| File | Content |
|---|---|
| `<timestamp>_NNN.curl` | Equivalent `curl` command for the request |
| `<timestamp>_NNN.url` | Final URL the request was sent to |
| `<timestamp>_NNN.code` | HTTP status code (e.g. `200`) |
| `<timestamp>_NNN.status` | Status code + reason phrase (e.g. `200 (OK)`) |
| `<timestamp>_NNN.headers` | Response headers, one `Name: value` per line |
| `<timestamp>_NNN.body` | Raw response body as received |
| `<timestamp>_NNN.json` / `.xml` / `.yaml` / `.html` / `.txt` / `.dat` | Pretty-printed body |

The base name has the form `yyyyMMddTHHmmss_NNN`, e.g. `20260523T143012_001`.

> **Note:** `LoggerPlugin` stores per-request state in instance fields and is **not thread-safe**.
> For concurrent use, create one `LoggerPlugin` per `HalHttpClient` or add external synchronisation.

---

## Add to your project

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.helpchoice.nahal:haldish-plugin-logger:0.1.0")
}
```

### Maven

```xml
<dependency>
    <groupId>com.helpchoice.nahal</groupId>
    <artifactId>haldish-plugin-logger</artifactId>
    <version>0.1.0</version>
</dependency>
```

---

## Programmatic usage

```kotlin
import com.helpchoice.nahal.haldish.http.HalHttpClient
import com.helpchoice.nahal.plugin.logger.LoggerPlugin

// Standalone — log every exchange to ./haldish-log
val client = HalHttpClient(
    pluginOverride = LoggerPlugin(directory = "./haldish-log")
)
```

---

## Use with the NaHAL desktop app

The NaHAL app discovers plugins at startup via Java **ServiceLoader**.
You need a thin adapter class that provides the no-argument constructor ServiceLoader
expects (and optionally reads the log directory from an environment variable).

### Step 1 — Create an adapter project

**`build.gradle.kts`**

```kotlin
plugins {
    kotlin("jvm") version "2.1.0"
}

repositories { mavenCentral() }

dependencies {
    implementation("com.helpchoice.nahal:haldish-plugin-logger:0.1.0")
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
}
```

**`src/main/kotlin/com/example/MyLoggerPlugin.kt`**

```kotlin
package com.example

import com.helpchoice.nahal.plugin.logger.LoggerPlugin

/**
 * Reads the log directory from the LOG_DIR environment variable.
 * Falls back to ./haldish-log when LOG_DIR is not set.
 */
class MyLoggerPlugin : LoggerPlugin(
    directory = System.getenv("LOG_DIR") ?: "./haldish-log",
)
```

**`src/main/resources/META-INF/services/com.helpchoice.nahal.haldish.plugin.HaldishPlugin`**

```
com.example.MyLoggerPlugin
```

### Step 2 — Build the adapter JAR

```bash
./gradlew jar
# → build/libs/my-logger-adapter.jar
```

### Step 3 — Run NaHAL with the plugin

**From the nahal source tree:**

```bash
LOG_DIR=/tmp/nahal-log \
HALDISH_PLUGIN_PATH=/path/to/my-logger-adapter.jar \
./gradlew :ui:run
```

**From a packaged NaHAL distribution:**

```bash
# macOS
LOG_DIR=/tmp/nahal-log \
HALDISH_PLUGIN_PATH=/path/to/my-logger-adapter.jar \
open -a NaHAL

# Linux / Windows
LOG_DIR=/tmp/nahal-log \
HALDISH_PLUGIN_PATH=/path/to/my-logger-adapter.jar \
./bin/NaHAL
```

Logged files appear in `/tmp/nahal-log/` after the first request is made in the app.
Open the `.curl` file to reproduce any request at the command line.
