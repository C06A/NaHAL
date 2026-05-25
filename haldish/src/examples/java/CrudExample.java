/**
 * HAL CRUD walkthrough — JVM target, plain Java.
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
 * Build the library:
 *   ./gradlew :haldish:jvmJar
 *
 * Dependencies on classpath:
 *   com.helpchoice.nahal:haldish-jvm:0.1.0-SNAPSHOT
 *   org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.1
 *   org.jetbrains.kotlin:kotlin-stdlib
 *   io.ktor:ktor-client-cio-jvm:3.1.3  (plus transitive Ktor/serialization jars)
 */

import com.helpchoice.nahal.haldish.http.HalHttpClient;
import com.helpchoice.nahal.haldish.http.HalHttpResponse;
import com.helpchoice.nahal.haldish.http.HalRequestBody;
import com.helpchoice.nahal.haldish.http.MultipartPart;
import com.helpchoice.nahal.haldish.model.HalDocument;
import com.helpchoice.nahal.haldish.parser.HalParser;
import com.helpchoice.nahal.haldish.uritemplate.UriTemplate;
import com.helpchoice.nahal.haldish.uritemplate.UriTemplateVars;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CrudExample {

    static final String ROOT = "http://localhost:8080";

    /** Describes one part in a multipart curl command for display purposes. */
    record CurlPart(String name, String fileOrContent, String contentType, boolean isFile) {}

    public static void main(String[] args) throws Exception {
        try (HalHttpClient client = new HalHttpClient()) {

            // ── Step 1: GET root, extract the templated "projects" link ──────────
            step("GET root");
            HalDocument root = halGet(client, ROOT, Map.of());
            String tmpl = root.link("projects").getHref();
            System.out.println("  → projects template: " + tmpl);

            // ── Step 2: POST to template with no vars → create a project ─────────
            step("POST new project");
            String postUrl    = new UriTemplate(tmpl).expand(new UriTemplateVars());
            String createBody = "{\"name\":\"My Project\",\"description\":\"A sample project\"}";
            HalHttpResponse postResp = halPost(client, postUrl, createBody, Map.of());
            HalDocument created = HalParser.INSTANCE.parse(postResp.getBody(), postResp.getContentType());
            String selfUrl  = created.link("self").getHref();
            String filesUrl = created.link("files").getHref();

            // ── Step 3: PATCH self link → update description ──────────────────────
            step("PATCH update description");
            halPatch(client, selfUrl, "{\"description\":\"Updated description\"}", Map.of());

            // ── Step 4: GET with custom Accept and Authorization headers ──────────
            step("GET with custom headers");
            Map<String, String> authHeaders = new LinkedHashMap<>();
            authHeaders.put("Accept", "application/hal+xml");
            authHeaders.put("Authorization", "Bearer example-token");
            halGet(client, selfUrl, authHeaders);

            // ── Step 5: POST single file upload ───────────────────────────────────
            step("POST single file upload");
            byte[] photoBytes = Files.readAllBytes(Path.of("examples/java/sample.jpg"));
            HalHttpResponse uploadResp = halPostFile(
                client, filesUrl, photoBytes, "image/jpeg", "sample.jpg", Map.of());
            HalDocument uploaded = HalParser.INSTANCE.parse(
                uploadResp.getBody(), uploadResp.getContentType());
            String fileUrl = uploaded.link("self").getHref();

            // ── Step 6: POST multipart (file + JSON metadata) ─────────────────────
            step("POST multipart upload");
            String metaJson = "{\"title\":\"Project photo\",\"alt\":\"Office\"}";
            List<MultipartPart> parts = List.of(
                new MultipartPart("photo",    photoBytes,
                                              "sample.jpg",  "image/jpeg"),
                new MultipartPart("metadata", metaJson.getBytes(StandardCharsets.UTF_8),
                                              null,          "application/json")
            );
            List<CurlPart> curlParts = List.of(
                new CurlPart("photo",    "sample.jpg", "image/jpeg",       true),
                new CurlPart("metadata", metaJson,     "application/json", false)
            );
            halPostMultipart(client, filesUrl, parts, curlParts, Map.of());

            // ── Step 7: GET file download ─────────────────────────────────────────
            step("GET file download");
            halGetRaw(client, fileUrl, Map.of("Accept", "image/jpeg"), "downloaded.jpg");

            // ── Step 8: Expand template with list var + scalar var, then GET ──────
            step("GET filtered list");
            UriTemplateVars filterVars = new UriTemplateVars()
                .set("name",    Arrays.asList("My Project", "Other Project"))
                .set("name_op", "in");
            String filterUrl = new UriTemplate(tmpl).expand(filterVars);
            HalDocument list = halGet(client, filterUrl, Map.of());

            // ── Step 9: Take first embedded project, DELETE its self link ─────────
            step("DELETE first project in result");
            HalDocument first    = list.embedded("projects").get(0);
            String      firstSelf = first.link("self").getHref();
            halDelete(client, firstSelf, Map.of());
        }
    }

    // ── HAL HTTP helpers ──────────────────────────────────────────────────────

    static HalDocument halGet(HalHttpClient client, String url,
                               Map<String, String> headers) throws Exception {
        HalHttpResponse r = halGetRaw(client, url, headers, null);
        return HalParser.INSTANCE.parse(r.getBody(), r.getContentType());
    }

    /**
     * GET with optional file save.
     * When saveAs is non-null the response body is written to that path and the
     * curl command includes -o saveAs.
     * ISO_8859_1 preserves byte values 0–255; note that binary content may already
     * be corrupted by Ktor's UTF-8 bodyAsText() decoding upstream.
     */
    static HalHttpResponse halGetRaw(HalHttpClient client, String url,
                                      Map<String, String> headers,
                                      String saveAs) throws Exception {
        printCurlGet(url, headers, saveAs);
        HalHttpResponse r = (HalHttpResponse) BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE,
            (scope, cont) -> client.get(url, headers, Collections.emptyMap(), cont));
        System.out.printf("→ %d %s%n", r.getStatusCode(), statusText(r.getStatusCode()));
        if (saveAs != null && r.getBody() != null) {
            Files.write(Path.of(saveAs), r.getBody().getBytes(StandardCharsets.ISO_8859_1));
            System.out.println("  → saved to " + saveAs);
        } else if (r.getBody() != null && !r.getBody().isBlank()) {
            System.out.println(prettyJson(r.getBody()));
        }
        return r;
    }

    static HalHttpResponse halPost(HalHttpClient client, String url, String json,
                                    Map<String, String> headers) throws Exception {
        printCurlJson("POST", url, json, headers);
        HalHttpResponse r = (HalHttpResponse) BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE,
            (scope, cont) -> client.post(url, new HalRequestBody.Json(json), headers, cont));
        printResp(r);
        return r;
    }

    static void halPatch(HalHttpClient client, String url, String json,
                          Map<String, String> headers) throws Exception {
        printCurlJson("PATCH", url, json, headers);
        HalHttpResponse r = (HalHttpResponse) BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE,
            (scope, cont) -> client.patch(url, new HalRequestBody.Json(json), headers, cont));
        printResp(r);
    }

    static void halDelete(HalHttpClient client, String url,
                           Map<String, String> headers) throws Exception {
        printCurl("DELETE", url, headers);
        HalHttpResponse r = (HalHttpResponse) BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE,
            (scope, cont) -> client.delete(url, headers, cont));
        printResp(r);
    }

    static HalHttpResponse halPostFile(HalHttpClient client, String url,
                                        byte[] bytes, String contentType, String displayFilename,
                                        Map<String, String> headers) throws Exception {
        printCurlBinary(url, displayFilename, contentType, headers);
        HalHttpResponse r = (HalHttpResponse) BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE,
            (scope, cont) -> client.post(url, new HalRequestBody.Binary(bytes, contentType), headers, cont));
        printResp(r);
        return r;
    }

    static HalHttpResponse halPostMultipart(HalHttpClient client, String url,
                                             List<MultipartPart> parts, List<CurlPart> curlParts,
                                             Map<String, String> headers) throws Exception {
        printCurlMultipart(url, curlParts, headers);
        HalHttpResponse r = (HalHttpResponse) BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE,
            (scope, cont) -> client.post(url, new HalRequestBody.Multipart(parts), headers, cont));
        printResp(r);
        return r;
    }

    // ── Output helpers ────────────────────────────────────────────────────────

    static void step(String label) {
        System.out.println("\n══════ " + label + " ══════");
    }

    /** GET with optional -o saveAs; Accept may be overridden via extraHeaders. */
    static void printCurlGet(String url, Map<String, String> extraHeaders, String saveAs) {
        Map<String, String> all = buildHeaders(extraHeaders);
        System.out.print("curl -X GET");
        all.forEach((k, v) -> System.out.printf(" \\\n  -H '%s: %s'", k, v));
        if (saveAs != null) System.out.printf(" \\\n  -o %s", saveAs);
        System.out.printf(" \\\n  %s%n", url);
    }

    /** No-body verbs other than GET (e.g. DELETE). */
    static void printCurl(String method, String url, Map<String, String> extraHeaders) {
        Map<String, String> all = buildHeaders(extraHeaders);
        System.out.printf("curl -X %s", method);
        all.forEach((k, v) -> System.out.printf(" \\\n  -H '%s: %s'", k, v));
        System.out.printf(" \\\n  %s%n", url);
    }

    /** POST / PATCH with a JSON body. */
    static void printCurlJson(String method, String url, String json,
                               Map<String, String> extraHeaders) {
        Map<String, String> all = buildHeaders(extraHeaders);
        all.put("Content-Type", "application/json");
        System.out.printf("curl -X %s", method);
        all.forEach((k, v) -> System.out.printf(" \\\n  -H '%s: %s'", k, v));
        System.out.printf(" \\\n  -d '%s' \\\n  %s%n", json, url);
    }

    /** POST with a single binary file body. */
    static void printCurlBinary(String url, String filename, String contentType,
                                  Map<String, String> extraHeaders) {
        Map<String, String> all = buildHeaders(extraHeaders);
        all.put("Content-Type", contentType);
        System.out.print("curl -X POST");
        all.forEach((k, v) -> System.out.printf(" \\\n  -H '%s: %s'", k, v));
        System.out.printf(" \\\n  --data-binary @%s \\\n  %s%n", filename, url);
    }

    /** POST multipart/form-data. */
    static void printCurlMultipart(String url, List<CurlPart> parts,
                                    Map<String, String> extraHeaders) {
        Map<String, String> all = buildHeaders(extraHeaders);
        System.out.print("curl -X POST");
        all.forEach((k, v) -> System.out.printf(" \\\n  -H '%s: %s'", k, v));
        for (CurlPart p : parts) {
            if (p.isFile())
                System.out.printf(" \\\n  -F '%s=@%s;type=%s'",
                    p.name(), p.fileOrContent(), p.contentType());
            else
                System.out.printf(" \\\n  -F '%s=%s;type=%s'",
                    p.name(), p.fileOrContent(), p.contentType());
        }
        System.out.printf(" \\\n  %s%n", url);
    }

    /** Starts with Accept: HAL_ACCEPT, then overlays caller-supplied headers. */
    private static Map<String, String> buildHeaders(Map<String, String> extraHeaders) {
        Map<String, String> all = new LinkedHashMap<>();
        all.put("Accept", HalHttpClient.HAL_ACCEPT);
        all.putAll(extraHeaders);
        return all;
    }

    static void printResp(HalHttpResponse r) {
        System.out.printf("→ %d %s%n", r.getStatusCode(), statusText(r.getStatusCode()));
        String body = r.getBody();
        if (body != null && !body.isBlank()) System.out.println(prettyJson(body));
    }

    static String statusText(int code) {
        return switch (code) {
            case 200 -> "OK";           case 201 -> "Created";
            case 204 -> "No Content";   case 400 -> "Bad Request";
            case 401 -> "Unauthorized"; case 403 -> "Forbidden";
            case 404 -> "Not Found";    case 409 -> "Conflict";
            case 500 -> "Internal Server Error";
            default  -> "";
        };
    }

    static String prettyJson(String json) {
        var sb = new StringBuilder();
        int indent = 0; boolean inStr = false, escape = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape)             { sb.append(c); escape = false; continue; }
            if (c == '\\' && inStr) { sb.append(c); escape = true;  continue; }
            if (c == '"') inStr = !inStr;
            if (inStr) { sb.append(c); continue; }
            switch (c) {
                case '{', '[' -> { sb.append(c).append('\n'); indent++; sb.append("  ".repeat(indent)); }
                case '}', ']' -> { sb.append('\n'); indent--; sb.append("  ".repeat(indent)).append(c); }
                case ','      -> sb.append(c).append('\n').append("  ".repeat(indent));
                case ':'      -> sb.append(": ");
                case ' ', '\t', '\n', '\r' -> {}
                default       -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
