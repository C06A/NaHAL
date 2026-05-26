/**
 * Happy-day usage of the core library from C++ (no HTTP).
 *
 * Demonstrates:
 *   - Selecting a top-level link with core_link_href()
 *   - Selecting a link inside an embedded resource with core_embedded_link_href()
 *
 * The C API header and shared library are build outputs — they don't exist until
 * the corresponding Gradle link task runs.
 *
 * ── macOS arm64 ──────────────────────────────────────────────────────────────
 *   ./gradlew :core:linkReleaseSharedMacosArm64
 *   OUTDIR=core/build/bin/macosArm64/releaseShared
 *   g++ -std=c++17 -o /tmp/core_simple_example core/src/test/cpp/simple_example.cpp \
 *       -I$OUTDIR -L$OUTDIR -lnahal_core -Wl,-rpath,$OUTDIR
 *   /tmp/core_simple_example
 *
 * Or via Gradle (builds and runs automatically):
 *   ./gradlew :core:runCoreNativeTest
 *
 * ── macOS x64 ────────────────────────────────────────────────────────────────
 *   ./gradlew :core:linkReleaseSharedMacosX64
 *   OUTDIR=core/build/bin/macosX64/releaseShared
 *   g++ -std=c++17 -o /tmp/core_simple_example core/src/test/cpp/simple_example.cpp \
 *       -I$OUTDIR -L$OUTDIR -lnahal_core -Wl,-rpath,$OUTDIR
 *   /tmp/core_simple_example
 *
 * ── Linux x64 ────────────────────────────────────────────────────────────────
 *   ./gradlew :core:linkReleaseSharedLinuxX64
 *   OUTDIR=core/build/bin/linuxX64/releaseShared
 *   g++ -std=c++17 -o /tmp/core_simple_example core/src/test/cpp/simple_example.cpp \
 *       -I$OUTDIR -L$OUTDIR -lnahal_core -Wl,-rpath,$OUTDIR
 *   /tmp/core_simple_example
 */

#include "libnahal_core_api.h"

#include <cassert>
#include <cstdio>
#include <string>

static std::string str(const char* s) { return s ? s : ""; }

int main() {
    const char* HAL_JSON =
        "{\"_links\":{"
        "\"self\":{\"href\":\"/orders\"},"
        "\"search\":{\"href\":\"/orders{?page,size}\",\"templated\":true}"
        "},\"_embedded\":{\"orders\":["
        "{\"_links\":{\"self\":{\"href\":\"/orders/1\"}}}"
        "]}}";

    // ── Test 1: select top-level link ─────────────────────────────────────────
    std::string selfHref = str(core_link_href(HAL_JSON, "self"));
    assert(selfHref == "/orders");
    printf("✓  top-level href: %s\n", selfHref.c_str());

    // ── Test 2: select embedded link ──────────────────────────────────────────
    std::string embeddedHref = str(core_embedded_link_href(HAL_JSON, "orders", "self"));
    assert(embeddedHref == "/orders/1");
    printf("✓  embedded href:  %s\n", embeddedHref.c_str());

    printf("\nAll C++ core tests passed\n");
    return 0;
}
