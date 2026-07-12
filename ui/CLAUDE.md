# `:ui` — Compose Multiplatform GUI

Compose Multiplatform desktop/browser/mobile GUI client. Implements the HAL navigator UI
(two-pane: traversal rail + resource viewer, accordion panels, request builder, template
expander, breadcrumb navigation).

## Build & Run Commands

```bash
# Run the desktop GUI (JVM — opens a 1280×820 window)
./gradlew :ui:jvmRun

# Run the desktop GUI with a plugin active — each plugin module overrides its own `jvmRun`
# (puts that plugin's jvm artifact on the UI classpath + generates its HALDISH_CONFIG).
# E.g. base-url-rewriter, so relative link hrefs resolve (the default NoOp plugin sends
# relative URLs as-is). See plugins/CLAUDE.md for the full list.
./gradlew :plugins:base-url-rewriter:jvmRun

# Build the web UI (JS — output: ui/build/dist/js/productionExecutable/)
./gradlew :ui:jsBrowserProductionWebpack

# Build the Wasm web UI
./gradlew :ui:wasmJsBrowserProductionWebpack
```

## Structure

```
NaHalNavigator                 — root composable (theme + state bootstrap)
  └── NaHalNavigatorContent
        ├── NaHalTopBar        — address bar, back/forward, loading indicator
        ├── Left rail (Column)
        │     ├── TraversalTree   — clickable history nodes
        │     └── RequestLog      — reverse-chronological request list
        └── Center pane (Box)
              ├── TemplateForm    — URI template variable editor (when pendingRequest.templated)
              ├── RequestBuilder  — method/headers/cookies/body editor (when pendingRequest ready)
              └── CenterPanel     — response/request viewer
                    ├── BreadcrumbTrail
                    ├── Accordion (response mode, pretty)
                    │     ├── LinksPanel    — follow / expand links
                    │     ├── EmbeddedPanel — open embedded sub-resources
                    │     ├── PropTree      — JSON property tree
                    │     ├── HeadersPanel / CookiesPanel
                    │     └── ArrayItemsPanel — for top-level JSON array responses
                    ├── RawJsonPanel (response mode, raw)
                    ├── Accordion (request mode, pretty)
                    └── CurlPanel (request mode, raw — shows equivalent curl command)
```

`NavigatorState` (in `state/`) owns all mutable state: `history`, `cursor`, `loading`,
`pendingRequest`, `requestLog`. It performs **no URL manipulation** — it assembles a
`core.RequestSpec` (a bare `url` for the address bar, or a `ResourcePath` + `rootDocument`
for a followed link/property) and calls `core.HalNavigator.send`, which resolves the link via
`preLink` plugins, expands the template, sends, and parses. The final sent URL comes back as
`NavigationResponse.url`. Platform entries
(`jvmMain/main.kt`, `jsMain/JsEntry.kt`, `wasmJsMain/WasmEntry.kt`, `macosMain/MacosEntry.kt`,
`iosMain/IOSEntry.kt`) each call `NaHalNavigator()`.
