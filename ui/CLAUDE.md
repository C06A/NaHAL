# `:ui` — Compose Multiplatform GUI

Compose Multiplatform desktop/browser/mobile GUI client. Implements the HAL navigator UI in two
switchable layouts (top-bar toggle `▤ panes` / `◈ graph`, `AppLayout` in `NaHalNavigator.kt`):

- **panes** (design variant A) — traversal rail + resource viewer, accordion panels, request
  builder, template expander, breadcrumb navigation.
- **graph** (design variant C) — node-link graph of the traversal as the main canvas, 420dp detail
  drawer on the right, request forms in a modal.

The design source for variant C is vendored at [design/atlas/](design/atlas/) — open
`design/atlas/preview.html` in a browser to compare against the reference, and see its `README.md`
for tokens, copy and interaction specs.

## Build & Run Commands

```bash
# Run the desktop GUI (JVM — opens a 1280×820 window)
./gradlew :ui:jvmRun

# Running the desktop GUI with plugins active is not a task of this build. The plugin repository
# (../HALDiSh_Plugins) overrides `jvmRun` per plugin module — it puts that plugin on the UI
# classpath, generates its HALDISH_CONFIG, and resolves :ui as the published nahal-ui artifact.
# See that repository for the commands.
#
# Those tasks need nahal-ui in the local Maven repository first (Central has no 2.0.0 yet):
#   (cd ../HALDiSh_KMP && ./gradlew publishToMavenLocal -PRELEASE_SIGNING_ENABLED=false)
#   ./gradlew publishToMavenLocal -PRELEASE_SIGNING_ENABLED=false


# Build the web UI. Two independent bundles from the same sources — the JS one runs in any
# browser, the wasm one needs WasmGC (Chrome/Edge 119+, Firefox 120+, Safari 18.2+).
# Use the *Distribution tasks: the *Webpack ones emit only the compiled script, while these
# assemble what a static host actually serves (index.html, runtime, composeResources).
./gradlew :ui:jsBrowserDistribution        # -> ui/build/dist/js/productionExecutable/
./gradlew :ui:wasmJsBrowserDistribution    # -> ui/build/dist/wasmJs/productionExecutable/

# Their index.html files differ on purpose: the JS one waits on skiko.js's onWasmReady before
# loading ui.js, the wasm one just loads ui.js — Compose's wasm runtime initialises Skia itself.

# Android (the installable app lives in :androidApp; :ui only carries the library variant)
./gradlew :androidApp:assembleRelease     # APK
./gradlew :androidApp:bundleRelease       # AAB

# Native macOS desktop app — no JVM. Slow: linking a Compose app with Kotlin/Native
# takes hours, not minutes.
./gradlew :ui:runMacosArm64App

# iOS. Gradle only links NahalUI.framework; Xcode turns it into an .app.
# Needs the iOS 17 SDK (Xcode 15+) — Compose 1.8's UIKit bindings reference iOS 17 symbols.
./gradlew :ui:copyIosFrameworkForXcode -Pios.target=iosSimulatorArm64 -Pios.config=Debug
xcodebuild -project iosApp/iosApp.xcodeproj -target iosApp \
    -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO
```

## Plugins in the app

The app ships **without** plugins. Nothing activates unless a config source names it — a plugin on
the classpath or a jar in the drop-in directory stays inert on its own.

| Runtime | How plugins get in | Ordering & properties |
|---|---|---|
| JVM (macOS / Linux / Windows) | Drop `*.jar` into `$NAHAL_PLUGINS_DIR` (default: `plugins/` in the working dir). `main()` puts them on a child `URLClassLoader` and installs it as the thread context classloader — that is *all* it does. | `HALDISH_CONFIG` (JSON or YAML, or the same-named system property) lists FQNs; file order = chain order; each entry's children reach `initialize()` as `config.properties`. |
| Native macOS | No reflection → plugins must be compiled in and registered by FQN in `CorePluginRegistry` before the UI starts. That app is built in the plugin repository, not here. | `HALDISH_CONFIG` (JSON only) picks which registered plugins run, in what order. |
| Any platform, single artifact | `HALDISH_PLUGIN_PATH` with **no** `HALDISH_CONFIG` → `:core` passes no override and haldish's own loader takes that one artifact (JAR on JVM, `.dylib` on native). Naming the artifact is the configuration. | Chain several by pointing at an artifact that combines them. Note the C ABI passes no properties. |
| JS / Wasm / iOS | Registered in app code; config comes from `window.__nahalConfig`. | Same order-and-properties rules. |

Ordering deliberately lives in the config, never in file names — `main.kt` used to chain every jar
in the directory in file-name order via `ServiceLoader`, which nobody can control meaningfully and
which dropped per-plugin properties on the floor.

Caveat for bundled macOS apps: `runMacos*App` launches via `open`, and LaunchServices does not pass
the shell environment, so `HALDISH_CONFIG` / `HALDISH_PLUGIN_PATH` set in a terminal do not reach
the app. Run the `.kexe` directly when you need env vars.

## `:core` is compiled in, not depended on

Every `com.helpchoice.nahal.core.*` type you see here comes from `:core`'s own source files, added
to this module's source sets — `nahal-ui` is self-contained and names no `nahal-core` coordinate
(the reasoning is in [core/CLAUDE.md](../core/CLAUDE.md) and the repo-root `CLAUDE.md`).

The plumbing sits at the top of `build.gradle.kts`: a `coreSourcesDeps`/`coreSources` configuration
pair resolves `project(":core")`'s `coreSourcesElements` variant to core's `src` directory, and the
helper `coreSrcDir("<source set>/kotlin")` indexes into it. Working rules:

- Editing `core/src` recompiles this module. There is no artifact boundary.
- A new dependency in `:core` must be re-declared in the matching source set here, or the compile
  breaks with unresolved references.
- A new platform source set in `:core` needs its own `kotlin.srcDir(coreSrcDir(...))` call here, or
  its files are silently absent.
- `JsCoreNavigator`, `WasmCoreClient` and `NativeCoreApi` are excluded by name in `jsMain`,
  `wasmJsMain` and `nativeMain`. Rename one and the exclude must follow it — `@CName` would
  otherwise export C symbols from `NahalUI.framework`.

## Structure

```
NaHalNavigator                 — root composable (theme + state bootstrap)
  └── NaHalNavigatorContent
        ├── NaHalTopBar        — address bar, back/forward, loading indicator, layout toggle
        ├── TraversalRail      — left rail, two independently scrolling sections (hidden while
        │     │                  both are empty; an empty section drops out on its own)
        │     ├── TraversalTree   — clickable history nodes
        │     ├── HorizontalSplitter — drag to re-share the rail's height
        │     └── RequestLog      — reverse-chronological request list
        ├── VerticalSplitter   — drag to resize the rail
        └── Center pane (Box)
              ├── TemplateForm    — URI template variable editor (when pendingRequest.templated)
              ├── RequestBuilder  — method/headers/cookies/body editor (when pendingRequest ready)
              └── CenterPanel     — response/request viewer
                    ├── BreadcrumbTrail
                    ├── Accordion (response mode, pretty)
                    │     ├── LinksPanel    — follow / expand links; a link's `profile`
                    │     │                   (RFC 6906) follows in-app as a bare URL, verbatim
                    │     ├── EmbeddedPanel — open embedded sub-resources
                    │     ├── PropTree      — JSON property tree
                    │     ├── HeadersPanel / CookiesPanel
                    │     └── ArrayItemsPanel — for top-level JSON array responses
                    ├── RawJsonPanel (response mode, raw)
                    ├── Accordion (request mode, pretty)
                    └── CurlPanel (request mode, raw — shows equivalent curl command)
```

Graph layout (`AppLayout.Graph`) replaces the rail + center pane with:

```
GraphLayout
  ├── GraphCanvas        — dotted grid, elbow parent→child edges with arrowheads and rel labels,
  │     │                  clickable curl boxes, legend
  │     └── RequestModal — scrim + card wrapping TemplateForm / RequestBuilder
  ├── VerticalSplitter   — drag to resize the drawer (both it and the drawer are omitted
  │                        while no node is selected)
  └── DetailDrawer (420dp default) — overview · links · embedded · props · response
        └── reuses LinksPanel / EmbeddedPanel / PropTree / HeadersPanel / CookiesPanel /
            RawJsonPanel / ArrayItemsPanel
```

## Resizable panes

`component/Splitter.kt` provides `VerticalSplitter` / `HorizontalSplitter`: a
`NaHalDimens.splitterHit`-wide grab area around the usual hairline, accent-coloured while hovered
or dragged, double-click to restore the default size. They are stateless — `onDelta` hands the
caller a dp delta and the caller stores the clamped pane size (`railWidth` / `traversalH` /
`drawerW` in `NaHalNavigatorContent`, all session-only, not persisted).

Every pane is measured inside a `BoxWithConstraints` and re-clamped on each layout between its own
`NaHalDimens.min*` floor and `container − neighbour's floor`, so shrinking the window squeezes the
flexible pane first and can never collapse either side. Pass the clamped value (not the raw state)
back into `onDelta`, otherwise a drag past the edge accumulates invisible slack.

## Empty panes are not rendered

A pane with nothing in it is left out of the composition, along with the splitter that would size
it — so a cold start shows only the main pane of the layout, carrying the "Enter a URL in the
address bar to start." instruction (`CenterMessage` in panes, `GraphMessage` inside `GraphCanvas`
in graph), and panes appear as the session produces content for them:

| Pane | Rendered while |
|---|---|
| Left rail | `state.railHasContent` — history or request log non-empty |
| Traversal section | `state.history` non-empty |
| Request Log section | `state.requestLog` non-empty |
| Rail splitter | both rail sections present |
| `DetailDrawer` + its splitter | a node is selected (`selectedNode != null`) |

The accordion sections inside the center pane and the drawer's tabs are *not* gated this way — a
zero-count section still renders its own empty state.

The canvas mirrors what `grapher.sh --format svg` draws for a HAL session: every node is a box
titled with its method + path and filled with its equivalent curl command, connected by orthogonal
elbows captioned with the link rel. `graphSpecs(history)` + `layoutGraph(specs)` in
`component/GraphCanvas.kt` are pure and unit-tested in `commonTest` — a tidy-tree pass in character
cells (column = depth, each column as wide as its widest box and its gutter as wide as its widest
edge label; children stack down, parents centre between their first and last child), scaled to
pixels by `NaHalDimens.graphCharW` × `graphRowH`. `graphSpecs` prepends one synthetic empty
`START_NODE_ID` box and parents every root on it (a root being an address-bar send, or a node whose
parent is not in the history), so a session with several entry points still draws as one
tree; the start box stands for no request, so it carries no status and is not clickable.
Picking a node calls `NavigatorState.jumpTo`, so
the cursor follows selection and a request sent from the drawer branches off that node. The canvas
scrolls in both axes and draws natively — it is not an SVG; `grapher.sh` only supplies the geometry
this mirrors.

`history` is **append-only** — it models a traversal tree (`parentId`), not a browser back-stack.
Sending from a node selected earlier adds a branch and leaves that node's siblings on the canvas;
back/forward walk the list in arrival order.

`NavigatorState` (in `state/`) owns all mutable state: `history`, `cursor`, `loading`,
`pendingRequest`, `requestLog`. It performs **no URL manipulation** — it assembles a
`core.RequestSpec` (a bare `url` for the address bar, or a `ResourcePath` + `rootDocument`
for a followed link/property) and calls `core.HalNavigator.send`, which resolves the link via
`preLink` plugins, expands the template, sends, and parses. The final sent URL comes back as
`NavigationResponse.url`. Platform entries
(`jvmMain/main.kt`, `jsMain/JsEntry.kt`, `wasmJsMain/WasmEntry.kt`, `macosMain/MacosEntry.kt`,
`iosMain/IOSEntry.kt`) each call `NaHalNavigator()`.
