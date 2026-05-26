/**
 * Happy-day usage of the core library from plain JavaScript (no HTTP).
 *
 * Demonstrates:
 *   - Selecting a top-level link with JsCoreNavigator.linkHref()
 *   - Selecting a link inside an embedded resource with JsCoreNavigator.embeddedLinkHref()
 *
 * Build:
 *   ./gradlew :core:jsProductionLibraryCompileSync
 *
 * Run (once built):
 *   node core/src/test/js/simple-example.cjs
 *
 * Or via Gradle (builds first automatically):
 *   ./gradlew :core:runCoreJsTest
 */

const path = require('path');
const libDir = path.resolve(__dirname,
    '../../../build/compileSync/js/main/productionLibrary/kotlin');
const lib = require(path.join(libDir, 'nahal-core.js'));
const JsCoreNavigator = lib.com.helpchoice.nahal.core.JsCoreNavigator;

const HAL_JSON =
    '{"_links":{' +
    '"self":{"href":"/orders"},' +
    '"search":{"href":"/orders{?page,size}","templated":true}' +
    '},"_embedded":{"orders":[{"_links":{"self":{"href":"/orders/1"}}}]}}';

function assert(condition, message) {
    if (!condition) throw new Error('FAIL: ' + message);
}

const nav = new JsCoreNavigator();

// ── Test 1: select top-level link ─────────────────────────────────────────────
const selfHref = nav.linkHref(HAL_JSON, 'self');
assert(selfHref === '/orders', `Expected /orders, got ${selfHref}`);
console.log('✓  top-level href:', selfHref);

// ── Test 2: select embedded link ──────────────────────────────────────────────
const embeddedHref = nav.embeddedLinkHref(HAL_JSON, 'orders', 'self');
assert(embeddedHref === '/orders/1', `Expected /orders/1, got ${embeddedHref}`);
console.log('✓  embedded href: ', embeddedHref);

console.log('\nAll core JS tests passed');
