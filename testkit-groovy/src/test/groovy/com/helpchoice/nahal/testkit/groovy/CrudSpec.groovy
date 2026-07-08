package com.helpchoice.nahal.testkit.groovy

import com.helpchoice.nahal.testkit.MapCredentialsProvider
import com.helpchoice.nahal.testkit.MockApi
import com.helpchoice.nahal.testkit.NoSession
import com.helpchoice.nahal.testkit.SimpleCredentials
import com.helpchoice.nahal.testkit.TokenSession
import spock.lang.Specification

/**
 * The same scenarios as CrudTest, expressed in Groovy/Spock — the wrapper reads as a sequence of
 * HTTP calls with dynamic property/embedded/method access.
 */
class CrudSpec extends Specification {

    private Hal root(session = NoSession.INSTANCE) {
        Hal.from('http://api/', MockApi.INSTANCE.context(session)).GET('self').asHal()
    }

    def "reads properties with dot syntax"() {
        given:
        def root = root()

        expect:
        root.title == 'Root'
        root.count == 3
        root.active == true
        root.ratio == 1.5
        root.missing == null
        root.tags == ['a', 'b']
        root.meta == [k: 'v']
    }

    def "navigates embedded by subscript and by discriminator"() {
        given:
        def root = root()

        expect:
        root['orders'].total == 10
        root.embedded('orders', 1).total == 42
        root.embedded('orders', [id: 2]).total == 42
    }

    def "follows a templated link"() {
        given:
        def root = root()

        when:
        def orders = root.send('GET', 'orders', [vars: [page: 2]]).asHal()

        then:
        orders.page == 2
    }

    def "sends an arbitrary method name via methodMissing"() {
        given:
        def root = root()

        when:
        def report = root.REPORT('self')

        then:
        report.code == 200
        report.asText() == 'reported'
    }

    def "expands a CURIE href"() {
        given:
        def root = root()

        when:
        def widget = root.GET('widget').asHal()

        then:
        widget.kind == 'widget'
    }

    def "expands a SafeCURIE href"() {
        given:
        def root = root()

        when:
        def widget = root.GET('safe').asHal()

        then:
        widget.kind == 'widget'
    }

    def "expands a href without sending"() {
        given:
        def root = root()

        expect:
        root.expandedHref('safe') == 'http://api/orders/widget'
        root.expandedHref('widget') == 'http://api/orders/widget'
    }

    def "resolves documentation from curies for a rel"() {
        given:
        def root = root()

        expect:
        root.doc('orders').href == 'http://docs/orders'
        root.doc('doc:orders').href == 'http://docs/orders'
        root.doc('nope') == null
    }

    def "refreshes a token session on 401 and retries"() {
        given:
        def creds = new SimpleCredentials('stale', '', { it.setToken('good') })
        def provider = new MapCredentialsProvider([alice: creds])
        def session = new TokenSession('alice', provider, 'Authorization', 'Bearer', true)

        when:
        def secure = root(session).GET('secure')

        then:
        secure.code == 200
        secure.asHal().secret == 'ok'
    }

    def "returns the 401 as-is when refresh is disabled"() {
        given:
        def creds = new SimpleCredentials('stale', '', { it.setToken('good') })
        def provider = new MapCredentialsProvider([alice: creds])
        def session = new TokenSession('alice', provider, 'Authorization', 'Bearer', false)

        when:
        def secure = root(session).GET('secure')

        then:
        secure.code == 401
    }
}
