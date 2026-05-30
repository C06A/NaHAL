# haldish-plugin-bearer-token

Injects an `Authorization: Bearer <token>` header into every outgoing HALDiSh request.

Supports static tokens (read once from a config file) and dynamic token providers
(called on every request — enables transparent re-authentication without restarting the app).

**Per-platform authoring example.** Each platform's source set is intentionally self-contained.
Copy any platform's file as a starting point for a plugin that needs genuinely
platform-specific token retrieval (iOS Keychain, Windows Credential Manager, Android Keystore,
Java KeyStore, Spring Environment, etc.).

---

## Add to your project

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.helpchoice.nahal:haldish-plugin-bearer-token:0.1.0")
}
```

### Maven

```xml
<dependency>
    <groupId>com.helpchoice.nahal</groupId>
    <artifactId>haldish-plugin-bearer-token</artifactId>
    <version>0.1.0</version>
</dependency>
```

---

## Config-file usage (recommended)

Use `BearerTokenPlugin` directly — no custom adapter class required.

### Static token

```yaml
# plugins.yaml
com:
  helpchoice:
    nahal:
      plugin:
        bearertoken:
          BearerTokenPlugin:
            token: eyJhbGci...
            headerName: Authorization   # optional — default: Authorization
```

Point `HALDISH_CONFIG` at the file and use `HalNavigator` (`:nahal-core`):

```bash
HALDISH_CONFIG=/etc/myapp/plugins.yaml ./gradlew :ui:jvmRun
```

### Dynamic provider (re-authenticates at runtime)

Implement `BearerTokenProvider` with a no-arg constructor (JVM) or register an instance
(non-JVM). Its `token()` method is called on **every** request, so it can silently refresh
an expired access token.

```yaml
# plugins.yaml
com:
  helpchoice:
    nahal:
      plugin:
        bearertoken:
          BearerTokenPlugin:
            providerClass: com.example.OAuthTokenProvider
```

#### JVM — implement and auto-discover via reflection

```kotlin
package com.example

import com.helpchoice.nahal.plugin.bearertoken.BearerTokenProvider
import com.helpchoice.nahal.haldish.plugin.HaldishPluginConfig

class OAuthTokenProvider : BearerTokenProvider {
    private lateinit var clientId: String
    private lateinit var clientSecret: String

    override fun initialize(config: HaldishPluginConfig) {
        clientId     = config.properties["clientId"]?.toString() ?: error("clientId required")
        clientSecret = config.properties["clientSecret"]?.toString() ?: error("clientSecret required")
    }

    override fun token(): String = fetchOrRefreshToken(clientId, clientSecret)
}
```

`OAuthTokenProvider` must be on the classpath (e.g. same fat JAR, or via `HALDISH_PLUGIN_PATH`).
No service-loader registration needed — `BearerTokenPlugin.initialize()` loads it by FQN.

Additional config properties (e.g. `clientId`, `clientSecret` above) are passed through
`HaldishPluginConfig.properties` to the provider's `initialize()`.

#### Non-JVM (JS, WasmJS, Apple, Linux, Windows) — register before first request

```kotlin
import com.helpchoice.nahal.plugin.bearertoken.BearerTokenProviderRegistry

BearerTokenProviderRegistry.register(
    "com.example.OAuthTokenProvider",
    OAuthTokenProvider(),
)
```

---

## Programmatic usage

Pass the plugin directly when constructing `HalHttpClient` or `HalNavigator`:

```kotlin
import com.helpchoice.nahal.haldish.http.HalHttpClient
import com.helpchoice.nahal.plugin.bearertoken.BearerTokenPlugin

val client = HalHttpClient(
    pluginOverride = BearerTokenPlugin(
        staticToken = System.getenv("API_TOKEN") ?: error("API_TOKEN not set"),
    )
)
```

### Subclass for platform-specific token retrieval (JVM example)

```kotlin
import com.helpchoice.nahal.plugin.bearertoken.BearerTokenPlugin
import java.security.KeyStore

/** Reads the bearer token from a Java KeyStore entry at startup. */
class KeyStoreBearerTokenPlugin : BearerTokenPlugin(
    staticToken = run {
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(ClassLoader.getSystemResourceAsStream("keys.p12"), "password".toCharArray())
        (ks.getKey("api-token", "password".toCharArray()) as javax.crypto.SecretKey)
            .encoded.decodeToString()
    }
)
```
