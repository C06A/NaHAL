/**
 * HAL CRUD walkthrough — JS target, Node.js (ESM).
 *
 * Scenario:
 *   1. GET http://localhost:8080 → parse as HAL, extract templated "projects" link
 *   2. POST to that template expanded with no vars (creates a new project)
 *   3. PATCH the "self" link of the created resource (updates description)
 *   4. GET the same resource with custom Accept and Authorization headers
 *   5. POST a single file to the project's "files" link
 *   6. POST a multipart request to the same "files" link (file + JSON metadata part)
 *   7. GET the uploaded file back (file download)
 *   8. GET with template expanded for two-name list + name_op="in"
 *   9. Extract first embedded "projects" resource; DELETE its self link
 *
 * Each step prints the equivalent curl command, response status, and body.
 *
 * Kotlin suspend functions are compiled to Promise-returning async methods.
 * HAL navigation uses plain JSON.parse() on the response body.
 * expandTemplate() is synchronous (pure computation exposed by the Kotlin facade).
 *
 * Build the library:
 *   ./gradlew :haldish:jsNodeProductionLibrary
 *
 * Run:
 *   node examples/javascript/crud-example.mjs
 *
 * Adjust the import path to wherever Gradle places the compiled output:
 *   haldish/build/compileSync/js/main/productionLibrary/kotlin/haldish.mjs
 */

import fs from 'fs';
import { JsHalClient, JsHeaders, JsMultipartPart } from
  '../../haldish/build/compileSync/js/main/productionLibrary/kotlin/haldish.mjs';

const ROOT = 'http://localhost:8080';

const HAL_ACCEPT =
  'application/hal+json, application/hal+xml;q=0.9, ' +
  'application/hal+yaml;q=0.8, application/json;q=0.7, ' +
  'application/xml;q=0.6';

const STATUS_TEXT = {
  200: 'OK', 201: 'Created', 204: 'No Content',
  400: 'Bad Request', 401: 'Unauthorized', 403: 'Forbidden',
  404: 'Not Found', 409: 'Conflict', 500: 'Internal Server Error',
};

// ── Output helpers ────────────────────────────────────────────────────────────

function step(label) { console.log(`\n══════ ${label} ══════`); }

/**
 * body:
 *   null                                              → no body
 *   { type: 'json', content }                         → -d '...'
 *   { type: 'binary', contentType, filename }         → --data-binary @file
 *   { type: 'multipart', parts: [{name, filename?,
 *       content?, contentType}] }                     → -F ...
 *
 * saveAs: when set, appends -o <saveAs> (file download).
 */
function printCurl(method, url, body = null, headers = {}, saveAs = null) {
  const allHeaders = { Accept: HAL_ACCEPT, ...headers };
  let bodyFlags = '';

  if (body?.type === 'json') {
    allHeaders['Content-Type'] = 'application/json';
    bodyFlags = ` \\\n  -d '${body.content}'`;
  } else if (body?.type === 'binary') {
    allHeaders['Content-Type'] = body.contentType;
    bodyFlags = ` \\\n  --data-binary @${body.filename}`;
  } else if (body?.type === 'multipart') {
    bodyFlags = body.parts.map(p =>
      p.filename
        ? ` \\\n  -F '${p.name}=@${p.filename};type=${p.contentType}'`
        : ` \\\n  -F '${p.name}=${p.content};type=${p.contentType}'`
    ).join('');
  }

  const headerFlags = Object.entries(allHeaders)
    .map(([k, v]) => `  -H '${k}: ${v}'`)
    .join(' \\\n');
  const outputFlag = saveAs ? ` \\\n  -o ${saveAs}` : '';
  console.log(`curl -X ${method} \\\n${headerFlags}${bodyFlags}${outputFlag} \\\n  ${url}`);
}

function printResp(resp) {
  console.log(`→ ${resp.statusCode} ${STATUS_TEXT[resp.statusCode] ?? ''}`);
  if (resp.body) {
    try { console.log(JSON.stringify(JSON.parse(resp.body), null, 2)); }
    catch { console.log(resp.body); }
  }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Convert a Node.js Buffer to the Int8Array that Kotlin's ByteArray expects. */
function toInt8Array(buf) {
  return new Int8Array(buf.buffer, buf.byteOffset, buf.byteLength);
}

// ── Scenario ──────────────────────────────────────────────────────────────────

async function main() {
  const client = new JsHalClient();
  try {

    // ── Step 1: GET root, extract the templated "projects" link ─────────────
    step('GET root');
    printCurl('GET', ROOT);
    const rootResp = await client.get(ROOT);
    printResp(rootResp);
    const rootHal = JSON.parse(rootResp.body);
    const tmpl    = rootHal._links.projects.href;
    console.log(`  → projects template: ${tmpl}`);

    // ── Step 2: POST to template with no vars → create a project ────────────
    step('POST new project');
    const postUrl    = client.expandVars(tmpl);
    const createBody = JSON.stringify({ name: 'My Project', description: 'A sample project' });
    printCurl('POST', postUrl, { type: 'json', content: createBody });
    const postResp = await client.post(postUrl, createBody);
    printResp(postResp);
    const created  = JSON.parse(postResp.body);
    const selfUrl  = created._links.self.href;
    const filesUrl = created._links.files.href;

    // ── Step 3: PATCH self link → update description ─────────────────────────
    step('PATCH update description');
    const patchBody = JSON.stringify({ description: 'Updated description' });
    printCurl('PATCH', selfUrl, { type: 'json', content: patchBody });
    const patchResp = await client.patch(selfUrl, patchBody);
    printResp(patchResp);

    // ── Step 4: GET with custom Accept and Authorization headers ─────────────
    step('GET with custom headers');
    const getHeaders = new JsHeaders()
      .set('Accept', 'application/hal+xml')
      .set('Authorization', 'Bearer example-token');
    printCurl('GET', selfUrl, null, {
      Accept: 'application/hal+xml',
      Authorization: 'Bearer example-token',
    });
    const headersResp = await client.get(selfUrl, getHeaders);
    printResp(headersResp);

    // ── Step 5: POST single file upload ──────────────────────────────────────
    step('POST single file upload');
    const photoBytes = toInt8Array(fs.readFileSync('examples/javascript/sample.jpg'));
    printCurl('POST', filesUrl, { type: 'binary', contentType: 'image/jpeg', filename: 'sample.jpg' });
    const uploadResp = await client.postFile(filesUrl, photoBytes, 'image/jpeg');
    printResp(uploadResp);
    const uploadedFile = JSON.parse(uploadResp.body);
    const fileUrl      = uploadedFile._links.self.href;

    // ── Step 6: POST multipart (file + JSON metadata part) ───────────────────
    step('POST multipart upload');
    const metaJson  = JSON.stringify({ title: 'Project photo', alt: 'Office' });
    const metaBytes = toInt8Array(Buffer.from(metaJson, 'utf8'));
    const parts = [
      new JsMultipartPart('photo', photoBytes, 'image/jpeg', 'sample.jpg'),
      new JsMultipartPart('metadata', metaBytes, 'application/json'),
    ];
    printCurl('POST', filesUrl, {
      type: 'multipart',
      parts: [
        { name: 'photo',    filename: 'sample.jpg', contentType: 'image/jpeg' },
        { name: 'metadata', content: metaJson,      contentType: 'application/json' },
      ],
    });
    const multipartResp = await client.postMultipart(filesUrl, parts);
    printResp(multipartResp);

    // ── Step 7: GET file download ─────────────────────────────────────────────
    step('GET file download');
    const downloadHeaders = new JsHeaders().set('Accept', 'image/jpeg');
    printCurl('GET', fileUrl, null, { Accept: 'image/jpeg' }, 'downloaded.jpg');
    const downloadResp = await client.get(fileUrl, downloadHeaders);
    console.log(`→ ${downloadResp.statusCode} ${STATUS_TEXT[downloadResp.statusCode] ?? ''}`);
    fs.writeFileSync('downloaded.jpg', Buffer.from(downloadResp.body, 'binary'));
    console.log('  → saved to downloaded.jpg');

    // ── Step 8: Expand template with list + scalar, then GET ─────────────────
    step('GET filtered list');
    client.varsAdd('name', 'My Project');
    client.varsAdd('name', 'Other Project');
    client.varsSet('name_op', 'in');
    const filterUrl = client.expandVars(tmpl);
    printCurl('GET', filterUrl);
    const listResp = await client.get(filterUrl);
    printResp(listResp);
    const listHal = JSON.parse(listResp.body);

    // ── Step 9: First embedded project → DELETE its self link ────────────────
    step('DELETE first project in result');
    const first     = listHal._embedded.projects[0];
    const firstSelf = first._links.self.href;
    printCurl('DELETE', firstSelf);
    const delResp = await client.delete(firstSelf);
    printResp(delResp);

  } finally {
    client.close();
  }
}

main().catch(console.error);
