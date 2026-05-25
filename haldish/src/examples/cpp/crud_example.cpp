/**
 * HAL CRUD walkthrough — native target, C++.
 *
 * Scenario:
 *   1. GET http://localhost:8080 → parse as HAL, extract templated "projects" link
 *   2. POST to that template expanded with no vars (creates a new project)
 *   3. PATCH the "self" link of the created resource (updates description)
 *   4. GET the same resource with custom Accept and Authorization headers
 *   5. POST a single file to the project's "files" link
 *   6. POST a multipart request (file + JSON metadata part) to the same link
 *   7. GET the uploaded file back (file download)
 *   8. GET with template expanded for two-name list + name_op="in"
 *   9. Extract first embedded "projects" resource; DELETE its self link
 *
 * Each step prints the equivalent curl command, response status, and body.
 *
 * The C API comes from NativeCApi.kt (@CName-annotated blocking wrappers).
 * Each HTTP call stores its status and content-type in globals accessible via
 * haldish_last_status() / haldish_last_content_type().
 * Headers set via haldish_headers_set() are consumed (and cleared) on the
 * next HTTP call.  Multipart parts accumulate via haldish_part_add() and are
 * sent + cleared by haldish_post_multipart().
 *
 * The header and library are build outputs — they don't exist until the
 * corresponding Gradle link task runs.  Pattern:
 *
 *   haldish/build/bin/<target>/<variant>Shared/
 *     libhaldish_api.h   ← generated C header  (pass with -I)
 *     libhaldish.dylib   ← macOS shared library (pass with -L -lhaldish)
 *     libhaldish.so      ← Linux shared library
 *     libhaldish.dll     ← Windows shared library
 *
 * ── macOS (x64 or arm64) ──────────────────────────────────────────────────────
 *   ./gradlew :haldish:linkReleaseSharedMacosX64
 *   OUTDIR=haldish/build/bin/macosX64/releaseShared
 *   g++ -std=c++17 -o crud_example examples/cpp/crud_example.cpp \
 *       -I$OUTDIR -L$OUTDIR -lhaldish -Wl,-rpath,$OUTDIR
 *   ./crud_example
 *
 * ── Linux x64 ────────────────────────────────────────────────────────────────
 *   ./gradlew :haldish:linkReleaseSharedLinuxX64
 *   OUTDIR=haldish/build/bin/linuxX64/releaseShared
 *   g++ -std=c++17 -o crud_example examples/cpp/crud_example.cpp \
 *       -I$OUTDIR -L$OUTDIR -lhaldish -Wl,-rpath,$OUTDIR
 *   ./crud_example
 *
 * ── Windows (mingwX64) ────────────────────────────────────────────────────────
 *   ./gradlew :haldish:linkReleaseSharedMingwX64
 *   OUTDIR=haldish/build/bin/mingwX64/releaseShared
 *   g++ -std=c++17 -o crud_example examples/cpp/crud_example.cpp \
 *       -I$OUTDIR -L$OUTDIR -lhaldish
 */

#include "libhaldish_api.h"

#include <cstdio>
#include <cstring>
#include <initializer_list>
#include <string>
#include <utility>
#include <vector>

// ── Constants ─────────────────────────────────────────────────────────────────

static const char* HAL_ACCEPT =
    "application/hal+json, application/hal+xml;q=0.9, "
    "application/hal+yaml;q=0.8, application/json;q=0.7, "
    "application/xml;q=0.6";

// ── Helpers ───────────────────────────────────────────────────────────────────

static const char* statusText(int code) {
    switch (code) {
        case 200: return "OK";            case 201: return "Created";
        case 204: return "No Content";    case 400: return "Bad Request";
        case 401: return "Unauthorized";  case 403: return "Forbidden";
        case 404: return "Not Found";     case 409: return "Conflict";
        case 500: return "Internal Server Error";
        default:  return "";
    }
}

static inline std::string str(const char* s) { return s ? s : ""; }

static void printStep(const char* label) {
    printf("\n══════ %s ══════\n", label);
}

/**
 * Print a curl equivalent for GET / JSON-body requests.
 * extra: zero or more {"Header-Name", "value"} pairs shown as -H flags.
 * accept: when non-null overrides HAL_ACCEPT (e.g. for file download).
 * saveAs: when non-null appends -o <saveAs>.
 */
static void printCurl(
        const char* method, const char* url,
        const char* jsonBody = nullptr,
        const char* accept   = nullptr,
        std::initializer_list<std::pair<const char*, const char*>> extra = {},
        const char* saveAs   = nullptr) {
    printf("curl -X %s \\\n  -H 'Accept: %s'", method, accept ? accept : HAL_ACCEPT);
    for (auto& [k, v] : extra)
        printf(" \\\n  -H '%s: %s'", k, v);
    if (jsonBody)
        printf(" \\\n  -H 'Content-Type: application/json' \\\n  -d '%s'", jsonBody);
    if (saveAs)
        printf(" \\\n  -o %s", saveAs);
    printf(" \\\n  %s\n", url);
}

/** Print a curl equivalent for a single-file binary upload. */
static void printCurlBinary(const char* url, const char* filename, const char* contentType) {
    printf("curl -X POST \\\n"
           "  -H 'Accept: %s' \\\n"
           "  -H 'Content-Type: %s' \\\n"
           "  --data-binary @%s \\\n"
           "  %s\n",
           HAL_ACCEPT, contentType, filename, url);
}

/** One part descriptor for printCurlMultipart. */
struct CurlPart {
    std::string name;
    std::string fileOrContent;
    std::string contentType;
    bool isFile;
};

/** Print a curl equivalent for a multipart/form-data upload. */
static void printCurlMultipart(const char* url,
                                std::initializer_list<CurlPart> parts) {
    printf("curl -X POST \\\n  -H 'Accept: %s'", HAL_ACCEPT);
    for (auto& p : parts) {
        if (p.isFile)
            printf(" \\\n  -F '%s=@%s;type=%s'",
                   p.name.c_str(), p.fileOrContent.c_str(), p.contentType.c_str());
        else
            printf(" \\\n  -F '%s=%s;type=%s'",
                   p.name.c_str(), p.fileOrContent.c_str(), p.contentType.c_str());
    }
    printf(" \\\n  %s\n", url);
}

static void printResp(int status, const char* body) {
    printf("→ %d %s\n", status, statusText(status));
    if (body && *body) {
        std::string pretty = str(haldish_pretty_json(body));
        printf("%s\n", pretty.c_str());
    }
}

/** Read an entire file into a byte vector; returns empty vector on error. */
static std::vector<uint8_t> readFile(const char* path) {
    FILE* f = fopen(path, "rb");
    if (!f) return {};
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);
    std::vector<uint8_t> buf(static_cast<size_t>(size));
    fread(buf.data(), 1, static_cast<size_t>(size), f);
    fclose(f);
    return buf;
}

// ── Main ──────────────────────────────────────────────────────────────────────

int main() {
    const char* ROOT = "http://localhost:8080";

    // ── Step 1: GET root, extract the templated "projects" link ─────────────
    printStep("GET root");
    printCurl("GET", ROOT);
    std::string rootBody = str(haldish_get(ROOT));
    std::string rootCt   = str(haldish_last_content_type());
    printResp(haldish_last_status(), rootBody.c_str());

    std::string tmpl = str(haldish_link_href(rootBody.c_str(), rootCt.c_str(), "projects"));
    printf("  → projects template: %s\n", tmpl.c_str());

    // ── Step 2: POST to template with no vars → create a project ────────────
    printStep("POST new project");
    std::string postUrl    = str(haldish_expand(tmpl.c_str()));
    const char* createJson = "{\"name\":\"My Project\",\"description\":\"A sample project\"}";
    printCurl("POST", postUrl.c_str(), createJson);
    std::string postBody = str(haldish_post_json(postUrl.c_str(), createJson));
    std::string postCt   = str(haldish_last_content_type());
    printResp(haldish_last_status(), postBody.c_str());

    std::string selfUrl  = str(haldish_link_href(postBody.c_str(), postCt.c_str(), "self"));
    std::string filesUrl = str(haldish_link_href(postBody.c_str(), postCt.c_str(), "files"));

    // ── Step 3: PATCH self link → update description ─────────────────────────
    printStep("PATCH update description");
    const char* patchJson = "{\"description\":\"Updated description\"}";
    printCurl("PATCH", selfUrl.c_str(), patchJson);
    std::string patchBody = str(haldish_patch_json(selfUrl.c_str(), patchJson));
    printResp(haldish_last_status(), patchBody.c_str());

    // ── Step 4: GET with custom Accept and Authorization headers ─────────────
    printStep("GET with custom headers");
    printCurl("GET", selfUrl.c_str(),
              /*json*/ nullptr,
              /*accept*/ "application/hal+xml",
              {{"Authorization", "Bearer example-token"}});
    haldish_headers_set("Accept", "application/hal+xml");
    haldish_headers_set("Authorization", "Bearer example-token");
    std::string customBody = str(haldish_get(selfUrl.c_str()));
    printResp(haldish_last_status(), customBody.c_str());

    // ── Step 5: POST single file upload ──────────────────────────────────────
    printStep("POST single file upload");
    auto photoData = readFile("examples/cpp/sample.jpg");
    printCurlBinary(filesUrl.c_str(), "sample.jpg", "image/jpeg");
    std::string uploadBody = str(haldish_post_file(
        filesUrl.c_str(),
        reinterpret_cast<int8_t*>(photoData.data()),
        static_cast<int>(photoData.size()),
        "image/jpeg"
    ));
    std::string uploadCt = str(haldish_last_content_type());
    printResp(haldish_last_status(), uploadBody.c_str());

    std::string fileUrl = str(haldish_link_href(uploadBody.c_str(), uploadCt.c_str(), "self"));

    // ── Step 6: POST multipart (file + JSON metadata part) ───────────────────
    printStep("POST multipart upload");
    const char* metaJson = "{\"title\":\"Project photo\",\"alt\":\"Office\"}";
    printCurlMultipart(filesUrl.c_str(), {
        {"photo",    "sample.jpg", "image/jpeg",        true},
        {"metadata", metaJson,     "application/json",  false},
    });
    haldish_part_add("photo",
        reinterpret_cast<int8_t*>(photoData.data()),
        static_cast<int>(photoData.size()),
        "image/jpeg", "sample.jpg");
    haldish_part_add("metadata",
        reinterpret_cast<int8_t*>(const_cast<char*>(metaJson)),
        static_cast<int>(strlen(metaJson)),
        "application/json", nullptr);
    std::string multipartBody = str(haldish_post_multipart(filesUrl.c_str()));
    printResp(haldish_last_status(), multipartBody.c_str());

    // ── Step 7: GET file download ─────────────────────────────────────────────
    printStep("GET file download");
    printCurl("GET", fileUrl.c_str(),
              /*json*/ nullptr, /*accept*/ "image/jpeg",
              {}, /*saveAs*/ "downloaded.jpg");
    haldish_headers_set("Accept", "image/jpeg");
    const char* dlBody = haldish_get(fileUrl.c_str());
    printf("→ %d %s\n", haldish_last_status(), statusText(haldish_last_status()));
    if (dlBody) {
        // strlen stops at the first null byte; binary files may be truncated.
        // For true binary-safe downloads extend HalHttpResponse with a byte-array body.
        FILE* f = fopen("downloaded.jpg", "wb");
        if (f) { fwrite(dlBody, 1, strlen(dlBody), f); fclose(f); }
        printf("  → saved to downloaded.jpg\n");
    }

    // ── Step 8: Expand template with list + scalar, then GET ─────────────────
    printStep("GET filtered list");
    haldish_vars_add("name", "My Project");
    haldish_vars_add("name", "Other Project");
    haldish_vars_set("name_op", "in");
    std::string filterUrl = str(haldish_expand_vars(tmpl.c_str()));
    printCurl("GET", filterUrl.c_str());
    std::string listBody = str(haldish_get(filterUrl.c_str()));
    std::string listCt   = str(haldish_last_content_type());
    printResp(haldish_last_status(), listBody.c_str());

    // ── Step 9: First embedded project → DELETE its self link ────────────────
    printStep("DELETE first project in result");
    std::string firstSelf = str(haldish_first_embedded_self(
        listBody.c_str(), listCt.c_str(), "projects"
    ));
    printCurl("DELETE", firstSelf.c_str());
    haldish_delete(firstSelf.c_str());
    printResp(haldish_last_status(), nullptr);

    return 0;
}
