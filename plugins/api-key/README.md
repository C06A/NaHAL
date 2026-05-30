# haldish-plugin-api-key

Adds a configurable API-key header to every outgoing HALDiSh request.

By default the header is `X-Api-Key`, but any header name can be used.

---

## Add to your project

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.helpchoice.nahal:haldish-plugin-api-key:0.1.0")
}
```

### Maven

```xml
<dependency>
    <groupId>com.helpchoice.nahal</groupId>
    <artifactId>haldish-plugin-api-key</artifactId>
    <version>0.1.0</version>
</dependency>
```

---

## Programmatic usage

Pass the plugin directly when constructing `HalHttpClient` or `HalNavigator`:

```kotlin
import com.helpchoice.nahal.haldish.http.HalHttpClient
import com.helpchoice.nahal.plugin.apikey.ApiKeyPlugin

val client = HalHttpClient(
    pluginOverride = ApiKeyPlugin(
        apiKey     = System.getenv("API_KEY") ?: error("API_KEY not set"),
        headerName = "X-Api-Key",   // default; omit to keep this value
    )
)
```

---

## Use with the NaHAL desktop app

The NaHAL app discovers plugins at startup via Java **ServiceLoader**.
`ApiKeyPlugin` requires a key value, so you need a thin adapter class that reads it
from an environment variable and provides the no-argument constructor ServiceLoader expects.

### Step 1 — Create an adapter project

Create a small Gradle project with these three files:

**`build.gradle.kts`**

```kotlin
plugins {
    kotlin("jvm") version "2.1.0"
}

repositories { mavenCentral() }

dependencies {
    implementation("com.helpchoice.nahal:haldish-plugin-api-key:0.1.0")
}

// Produce a fat JAR so HALDISH_PLUGIN_PATH needs only one file
tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
}
```

**`src/main/kotlin/com/example/MyApiKeyPlugin.kt`**

```kotlin
package com.example

import com.helpchoice.nahal.plugin.apikey.ApiKeyPlugin

/** Reads the key from the API_KEY environment variable at startup. */
class MyApiKeyPlugin : ApiKeyPlugin(
    apiKey     = System.getenv("API_KEY") ?: error("API_KEY environment variable not set"),
    headerName = System.getenv("API_KEY_HEADER") ?: "X-Api-Key",
)
```

**`src/main/resources/META-INF/services/com.helpchoice.nahal.haldish.plugin.HaldishPlugin`**

```
com.example.MyApiKeyPlugin
```

### Step 2 — Build the adapter JAR

```bash
./gradlew jar
# → build/libs/my-api-key-adapter.jar
```

### Step 3 — Run NaHAL with the plugin

**From the nahal source tree:**

```bash
API_KEY=my-secret-key \
HALDISH_PLUGIN_PATH=/path/to/my-api-key-adapter.jar \
./gradlew :ui:run
```

**From a packaged NaHAL distribution:**

```bash
# macOS
API_KEY=my-secret-key \
HALDISH_PLUGIN_PATH=/path/to/my-api-key-adapter.jar \
open -a NaHAL

# Linux / Windows (from the distribution directory)
API_KEY=my-secret-key \
HALDISH_PLUGIN_PATH=/path/to/my-api-key-adapter.jar \
./bin/NaHAL
```
