/**
 * Happy-day usage of the haldish library from C++ (no HTTP).
 *
 * Demonstrates:
 *   - Extracting a link href from an inline HAL JSON string with haldish_link_href()
 *   - Expanding a URI template with haldish_vars_set() + haldish_expand_vars()
 *
 * The C API header and shared library are build outputs — they don't exist until
 * the corresponding Gradle link task runs.
 *
 * ── macOS arm64 ──────────────────────────────────────────────────────────────
 *   ./gradlew :haldish:linkReleaseSharedMacosArm64
 *   OUTDIR=haldish/build/bin/macosArm64/releaseShared
 *   g++ -std=c++17 -o /tmp/simple_example examples/cpp/simple_example.cpp \
 *       -I$OUTDIR -L$OUTDIR -lhaldish -Wl,-rpath,$OUTDIR
 *   /tmp/simple_example
 *
 * ── macOS x64 ────────────────────────────────────────────────────────────────
 *   ./gradlew :haldish:linkReleaseSharedMacosX64
 *   OUTDIR=haldish/build/bin/macosX64/releaseShared
 *   g++ -std=c++17 -o /tmp/simple_example examples/cpp/simple_example.cpp \
 *       -I$OUTDIR -L$OUTDIR -lhaldish -Wl,-rpath,$OUTDIR
 *   /tmp/simple_example
 *
 * ── Linux x64 ────────────────────────────────────────────────────────────────
 *   ./gradlew :haldish:linkReleaseSharedLinuxX64
 *   OUTDIR=haldish/build/bin/linuxX64/releaseShared
 *   g++ -std=c++17 -o /tmp/simple_example examples/cpp/simple_example.cpp \
 *       -I$OUTDIR -L$OUTDIR -lhaldish -Wl,-rpath,$OUTDIR
 *   /tmp/simple_example
 *
 * ── Windows (mingwX64) ───────────────────────────────────────────────────────
 *   ./gradlew :haldish:linkReleaseSharedMingwX64
 *   OUTDIR=haldish/build/bin/mingwX64/releaseShared
 *   g++ -std=c++17 -o simple_example.exe examples/cpp/simple_example.cpp \
 *       -I$OUTDIR -L$OUTDIR -lhaldish
 *   simple_example.exe
 */

#include "libhaldish_api.h"

#include <cassert>
#include <cstdio>
#include <string>

static std::string str(const char* s) { return s ? s : ""; }

int main() {
    const char* HAL_JSON =
        "{\"_links\":{"
        "\"self\":{\"href\":\"/orders\"},"
        "\"search\":{\"href\":\"/orders{?page,size}\",\"templated\":true}"
        "}}";
    const char* CONTENT_TYPE = "application/hal+json";

    // ── Test 1: extract link href ─────────────────────────────────────────────
    std::string self_href = str(haldish_link_href(HAL_JSON, CONTENT_TYPE, "self"));
    assert(self_href == "/orders");
    printf("✓  link href: %s\n", self_href.c_str());

    // ── Test 2: expand URI template ───────────────────────────────────────────
    std::string tmpl = str(haldish_link_href(HAL_JSON, CONTENT_TYPE, "search"));
    haldish_vars_set("page", "1");
    haldish_vars_set("size", "20");
    std::string expanded = str(haldish_expand_vars(tmpl.c_str()));
    assert(expanded == "/orders?page=1&size=20");
    printf("✓  expanded:  %s\n", expanded.c_str());

    printf("\nAll C++ tests passed\n");
    return 0;
}
