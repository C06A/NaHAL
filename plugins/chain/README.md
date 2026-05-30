# haldish-plugin-chain

Combines multiple HALDiSh plugins into a single plugin by threading each lifecycle hook
through all plugins in declaration order.

| Hook | Behaviour |
|---|---|
| `initialize` | Each plugin is initialised left-to-right |
| `preLink` | Link is threaded through all plugins; each receives the output of the previous one |
| `preRequest` | Request is threaded through all plugins left-to-right |
| `postResponse` | Document is threaded through all plugins left-to-right |

**Order matters.** Place URL-rewriting plugins before loggers so the logger records
the final, rewritten URL rather than the original one.

---

## Add to your project

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.helpchoice.nahal:haldish-plugin-chain:0.1.0")
    // plus the HALDiSh plugin dependencies you want to combine
}
```

### Maven

```xml
<dependency>
    <groupId>com.helpchoice.nahal</groupId>
    <artifactId>haldish-plugin-chain</artifactId>
    <version>0.1.0</version>
</dependency>
<!-- plus the HALDiSh plugin dependencies you want to combine -->
```

---

## Programmatic usage

```kotlin
import com.helpchoice.nahal.haldish.http.HalHttpClient
import com.helpchoice.nahal.plugin.chain.ChainPlugin

val client = HalHttpClient(
    pluginOverride = ChainPlugin(
        PluginA(),   // preLink/preRequest: runs first; postResponse: also runs first
        PluginB(),   // receives output of PluginA on every hook
        PluginC(),   // place loggers last — they see the URL and headers after all rewrites
    )
)
```

An empty chain is a no-op: `ChainPlugin()` behaves identically to the default no-plugin
configuration.

---

## Use with the NaHAL desktop app

The NaHAL app discovers plugins at startup via Java **ServiceLoader**.
Create an adapter that builds the desired `ChainPlugin` from environment variables and
provides the no-argument constructor ServiceLoader expects.

### Step 1 — Create an adapter project

**`build.gradle.kts`**

```kotlin
plugins {
    kotlin("jvm") version "2.1.0"
}

repositories { mavenCentral() }

dependencies {
    implementation("com.helpchoice.nahal:haldish-plugin-chain:0.1.0")
    // plus the HALDiSh plugin dependencies you want to combine
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
}
```

**`src/main/kotlin/com/example/MyChainPlugin.kt`**

```kotlin
package com.example

import com.helpchoice.nahal.plugin.chain.ChainPlugin

/** Chain plugin wired up from environment variables or fixed configuration. */
class MyChainPlugin : ChainPlugin(
    PluginA(config = System.getenv("PLUGIN_A_CONFIG") ?: ""),
    PluginB(),
)
```

**`src/main/resources/META-INF/services/com.helpchoice.nahal.haldish.plugin.HaldishPlugin`**

```
com.example.MyChainPlugin
```

### Step 2 — Build the adapter JAR

```bash
./gradlew jar
# → build/libs/my-chain-adapter.jar
```

### Step 3 — Run NaHAL with the plugin chain

**From the nahal source tree:**

```bash
HALDISH_PLUGIN_PATH=/path/to/my-chain-adapter.jar ./gradlew :ui:run
```

**From a packaged NaHAL distribution:**

```bash
# macOS
HALDISH_PLUGIN_PATH=/path/to/my-chain-adapter.jar open -a NaHAL

# Linux / Windows
HALDISH_PLUGIN_PATH=/path/to/my-chain-adapter.jar ./bin/NaHAL
```
