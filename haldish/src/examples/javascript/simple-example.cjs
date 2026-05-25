/**
 * Happy-day usage of the haldish library from plain JavaScript (no HTTP).
 *
 * Demonstrates:
 *   - Extracting a link href from an inline HAL JSON string (plain JSON.parse)
 *   - Expanding a URI template with JsHalClient.expandVars() (synchronous)
 *
 * Build:
 *   ./gradlew :haldish:jsProductionLibraryCompileSync
 *
 * Run (once built):
 *   node examples/javascript/simple-example.cjs
 *
 * Or via Gradle (builds first automatically):
 *   ./gradlew :haldish:runJsSimpleExample
 */

const path = require('path');
const libDir = path.resolve(__dirname, '../../haldish/build/compileSync/js/main/productionLibrary/kotlin');
const lib = require(path.join(libDir, 'nahal-haldish.js'));
const JsHalClient = lib.com.helpchoice.nahal.haldish.JsHalClient;

const HAL_JSON =
  '{"_links":{' +
  '"self":{"href":"/orders"},' +
  '"search":{"href":"/orders{?page,size}","templated":true}' +
  '}}';

function assert(condition, message) {
  if (!condition) throw new Error('FAIL: ' + message);
}

// ── Test 1: extract link href from parsed HAL JSON ────────────────────────────

const hal      = JSON.parse(HAL_JSON);
const selfHref = hal._links.self.href;
assert(selfHref === '/orders', `Expected /orders, got ${selfHref}`);

// ── Test 2: expand URI template ───────────────────────────────────────────────

const client   = new JsHalClient();
const tmpl     = hal._links.search.href;
client.varsSet('page', '1');
client.varsSet('size', '20');
const expanded = client.expandVars(tmpl);
assert(expanded === '/orders?page=1&size=20',
  `Expected /orders?page=1&size=20, got ${expanded}`);
client.close();

console.log('All JS tests passed');
