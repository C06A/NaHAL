# HALDiSh TestKit (`:testkit` + `:testkit-groovy`)

Write HAL API tests as a **readable sequence of HTTP calls**, from both **Kotlin** and
**Groovy/Spock**. Two artifacts so a Kotlin-only consumer never pulls Groovy:

| Module | Artifact | Contents |
|---|---|---|
| `:testkit` | `com.helpchoice.nahal:haldish-testkit` | Kotlin core (Groovy-free) |
| `:testkit-groovy` | `com.helpchoice.nahal:haldish-testkit-groovy` | `Hal`/`Resp` DSL facade for Spock (depends on the core) |

It wraps `:haldish` (the HAL client) and adds:

- **`HalResource`** — the central type. Create it from a URL, a `HalLink`, a `Map`, or a parsed
  `HalDocument`. Extract properties intuitively (excluding `_links`/`_embedded`), navigate
  embedded resources **by index or key=value discriminator**, and follow links with any HTTP
  method (standard or arbitrary-named).
- **Sessions** — a request modifier carrying auth. `NoSession`, `TokenSession` (auth header),
  `CookieSession` (auth cookie). Token/cookie sessions look up credentials **by user id** and, on
  a `401`, either **refresh and retry once** or return the `401` **as-is** (`refreshOn401`).
- **Credentials** — `CredentialsProvider.forUser(userId)`. `MapCredentialsProvider` (inline, for
  tests) and `FileCredentialsProvider` (reads the `HALDISH_CONFIG` JSON/YAML file, keyed by user).
- **Modifiers** — `CurieModifier` (expands both a bare CURIE `prefix:ref` **and a SafeCURIE**
  `[prefix:ref]` against the `CURIE` collection) and `ContentTypeModifier` (sets `Content-Type`
  from the link's declared type).
- **Documentation** — `HalResource.doc(rel)` / `openDoc(rel)` resolve a rel's documentation link
  from the HAL-spec `curies` (reusing `:core`'s `DocLinkResolver`).
- **`Response`** — `code`, `headers`, `cookies`, and the body as `asText()`, `asBytes()`,
  `asFile(file)`, or `asHal()` (a navigable `HalResource`).

## Kotlin

```kotlin
val root = HalResource.from("http://api/", client).send("GET", "self").asHal()
root.prop("title")                                   // property
root.embedded("orders", mapOf("id" to 2))!!["total"] // embedded by discriminator
val orders = root.send("GET", "orders", SendOptions(vars = mapOf("page" to 2))).asHal()
root.send("REPORT", "self").asText()                 // arbitrary method
root.doc("orders")?.href                             // documentation link from curies
```

## Groovy / Spock

The `Hal` facade adds dynamic sugar: `res.total` (property), `res['orders']` (embedded),
`res.GET('next')` (send with any method name).

```groovy
def root = Hal.from('http://api/', context).GET('self').asHal()
root.title == 'Root'
root['orders'].total == 10
root.embedded('orders', [id: 2]).total == 42
root.send('GET', 'orders', [vars: [page: 2]]).asHal().page == 2
```

## Unit tests

```bash
./gradlew :testkit:test :testkit-groovy:test
```

Driven by a scripted API via Ktor's `MockEngine` (a `testFixtures` helper, `MockApi.kt`, shared
by both modules), so no live server is needed.

## Integration tests

Separate `integrationTest` source sets/tasks (kept out of `test`) exercise the wrapper against a
**live HAL server** (e.g. [MockingHAL](https://github.com/C06A/MockingHAL)). They are **skipped**
unless both the server URL and a **fixtures folder** are configured — no fixtures are bundled.

| Setting      | System property        | Environment variable   |
|--------------|------------------------|------------------------|
| server URL   | `haldish.it.url`       | `HALDISH_IT_URL`       |
| fixtures dir | `haldish.it.fixtures`  | `HALDISH_IT_FIXTURES`  |

The fixtures folder must contain `hal_root.yaml`, `hal_templated.yml`, `hal-json.json`, and
`hal_embedded.json`. A `@BeforeAll`/`setupSpec` seeds the server: GET root → DELETE → POST
`hal_root.yaml` → PATCH a multipart of `hal_templated.yml` + `hal-json.json` → PATCH
`hal_embedded.json`. The follow tests then check simple JSON value types, links (including
SafeCURIE hrefs), embedded resources and their links, and curies-based documentation.

```bash
./gradlew :testkit:integrationTest :testkit-groovy:integrationTest \
  -Dhaldish.it.url=http://localhost:8080/ \
  -Dhaldish.it.fixtures=/path/to/fixtures
```
