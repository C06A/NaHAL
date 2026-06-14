# haldish-plugin-curie

Resolves a **CURIE-prefixed link href** into a full URL before the link is followed —
the NaHAL equivalent of HALDiSh's [`halcurie.sh`](https://github.com/C06A/HALDiSh) link plugin.

A href "looks like a CURIE" when it is `<prefix>:<reference>` with an NCName `<prefix>`
(e.g. `ord:widget`). The prefix is resolved against the first link in a `CURIE` collection
whose `name` equals the prefix, searched from the document directly holding the link
**upward through the embedding stack to the root**. The `CURIE` link's href is a plain URL
(no `{rel}` template); expansion replaces the `<prefix>:` of the original href with that URL.

```
ord:widget   +   _links.CURIE[name=ord].href = https://api.example.com/orders/
        →    https://api.example.com/orders/widget
```

Because a URL scheme also parses as `<prefix>:…` (e.g. `http://host/…`), an href whose
scheme has no matching `CURIE` definition is left unchanged — as are a prefix-less or
absent href, an unknown prefix, and a prefix that is not a valid NCName.

See also: [HALDiSh](https://github.com/C06A/HALDiSh) — a BASH script implementation of HAL
client functionality, and [MockingHAL](https://github.com/C06A/MockingHAL) — a mock HAL
server useful for testing plugins.

---

## CURIE definitions

Unlike the lowercase `curies` of the HAL spec, this plugin reads an **uppercase, singular**
`CURIE` relation whose entries are plain base URLs:

```json
{
  "_links": {
    "CURIE": [
      { "name": "ord",  "href": "https://api.example.com/orders/" },
      { "name": "prod", "href": "https://api.example.com/products/" }
    ],
    "product": { "href": "prod:widget" }
  }
}
```

Following `product` yields `https://api.example.com/products/widget`.

A `CURIE` collection may also be a single object; NaHAL's parser normalises it to a
one-element list, so the same `name` match applies. When the same prefix is defined at
multiple levels, the **nearest ancestor wins**; among equal-level duplicates, the **first**
match wins.

---

## Add to your project

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.helpchoice.nahal:haldish-plugin-curie:0.1.0")
}
```

### Maven

```xml
<dependency>
    <groupId>com.helpchoice.nahal</groupId>
    <artifactId>haldish-plugin-curie</artifactId>
    <version>0.1.0</version>
</dependency>
```

---

## Programmatic usage

Resolution happens in the `preLink` hook, so it applies to any link followed via a relation.
Pass the plugin directly when constructing `HalHttpClient` or `HalNavigator`:

```kotlin
import com.helpchoice.nahal.haldish.http.HalHttpClient
import com.helpchoice.nahal.plugin.curie.CuriePlugin

val client = HalHttpClient(pluginOverride = CuriePlugin())
```

Combine with other plugins via
[`ChainPlugin`](../chain):

```kotlin
import com.helpchoice.nahal.plugin.chain.ChainPlugin
import com.helpchoice.nahal.plugin.curie.CuriePlugin
import com.helpchoice.nahal.plugin.bearertoken.BearerTokenPlugin

val client = HalHttpClient(
    pluginOverride = ChainPlugin(
        CuriePlugin(),
        BearerTokenPlugin(staticToken = System.getenv("API_TOKEN") ?: ""),
    ),
)
```
