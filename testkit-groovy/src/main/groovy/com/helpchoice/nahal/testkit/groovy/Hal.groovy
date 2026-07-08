package com.helpchoice.nahal.testkit.groovy

import com.helpchoice.nahal.haldish.http.HalRequestBody
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.testkit.HalContext
import com.helpchoice.nahal.testkit.HalResource
import com.helpchoice.nahal.testkit.Response
import com.helpchoice.nahal.testkit.SendOptions

/**
 * Groovy DSL facade over the Kotlin {@link HalResource}, giving Spock tests the intuitive syntax
 * the requirements ask for:
 *
 * <ul>
 *   <li><code>res.total</code> &mdash; property access (via {@code propertyMissing})</li>
 *   <li><code>res['orders']</code> / <code>res.embedded('orders', id: 5)</code> &mdash; embedded navigation</li>
 *   <li><code>res.GET('next')</code> / <code>res.send('LINK', 'related', vars: [id: 5])</code> &mdash;
 *       send with a standard or arbitrary method name (via {@code methodMissing})</li>
 * </ul>
 *
 * The wrapped {@link HalResource} is exposed by delegation, so its typed methods remain available.
 */
class Hal {

    @Delegate
    final HalResource resource

    Hal(HalResource resource) {
        this.resource = resource
    }

    // ── entry points ─────────────────────────────────────────────────────────────────────────

    static Hal from(String url, HalContext ctx)  { new Hal(HalResource.from(url, ctx)) }
    static Hal from(HalLink link, HalContext ctx) { new Hal(HalResource.from(link, ctx)) }
    static Hal from(Map map, HalContext ctx)     { new Hal(HalResource.from((Map<String, Object>) map, ctx)) }

    // ── property access: res.total ───────────────────────────────────────────────────────────

    def propertyMissing(String name) {
        resource.prop(name)
    }

    // ── embedded navigation: res['orders'], res.embedded('orders', id: 5) ─────────────────────

    Hal getAt(String rel) { wrap(resource.embedded(rel, 0)) }

    Hal embedded(String rel, int index = 0) { wrap(resource.embedded(rel, index)) }

    Hal embedded(String rel, Map discriminator) {
        wrap(resource.embedded(rel, (Map<String, Object>) discriminator))
    }

    // ── send: res.GET('orders'), res.send('POST', 'orders', body: ...) ────────────────────────

    Resp send(String method, String rel, Map opts = [:]) {
        new Resp(resource.send(method, rel, toOptions(opts)))
    }

    /** Any unknown method name is treated as an HTTP method: {@code res.GET('orders')}. */
    def methodMissing(String name, args) {
        Object[] a = (Object[]) args
        if (a.length == 0) {
            throw new MissingMethodException(name, Hal, a)
        }
        String rel = a[0] as String
        Map opts = a.length > 1 && a[1] instanceof Map ? (Map) a[1] : [:]
        new Resp(resource.send(name, rel, toOptions(opts)))
    }

    private static Hal wrap(HalResource r) { r == null ? null : new Hal(r) }

    private static SendOptions toOptions(Map m) {
        if (m == null || m.isEmpty()) return new SendOptions()
        new SendOptions(
            (m.index ?: 0) as int,
            m.name as String,
            (Map<String, Object>) (m.vars ?: [:]),
            (Map<String, String>) (m.headers ?: [:]),
            (Map<String, String>) (m.cookies ?: [:]),
            (HalRequestBody) (m.body ?: HalRequestBody.None.INSTANCE),
        )
    }
}
