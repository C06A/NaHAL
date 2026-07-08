package com.helpchoice.nahal.testkit.groovy

import com.helpchoice.nahal.testkit.IntegrationSupport
import com.helpchoice.nahal.testkit.ItConfig
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

/**
 * Groovy/Spock integration tests against a live MockingHAL server, seeded from the `haldish` demo
 * fixtures. The whole spec is skipped unless {@code haldish.it.url} is configured; the fixtures
 * folder defaults to the sibling MockingHAL checkout (override with {@code haldish.it.fixtures}).
 *
 * <pre>
 * ./gradlew :testkit-groovy:integrationTest -Dhaldish.it.url=http://localhost:8080/
 * </pre>
 *
 * Mirrors the Kotlin RootIntegrationTest and tracks the same demo-fixture contract.
 */
@Requires({ ItConfig.INSTANCE.isConfigured() })
class RootIntegrationSpec extends Specification {

    @Shared
    Hal root

    def setupSpec() {
        root = new Hal(IntegrationSupport.INSTANCE.seedRoot(IntegrationSupport.INSTANCE.context()))
    }

    def "reads simple JSON resources: empty, null, true, false, number, structure, array"() {
        given:
        def json = root.GET('json-samples').asHal()

        expect: 'empty → 204'
        json.GET('doc:empty').code == 204

        and: 'scalars selected by name'
        scalar(json, 'boolean-true') == 'true'
        scalar(json, 'boolean-false') == 'false'
        scalar(json, 'integer') == '42'
        scalar(json, 'number') == '3.14'
        scalar(json, 'string') == '"hello world"'

        and: 'JSON structure and a null within it'
        def structure = json.GET('doc:json').asHal()
        structure.null_value == null
        structure.integer == 42
        structure.nested instanceof Map

        and: 'array of JSONs'
        json.GET('doc:array').asText().trim().startsWith('[')
    }

    private static String scalar(Hal json, String name) {
        json.send('GET', 'doc:scalars', [name: name]).asText().trim()
    }

    def "follows link responses including SafeCURIE hrefs"() {
        given:
        def links = root.GET('links').asHal()

        expect: 'ordinary internal links resolve'
        links.GET('simple').isSuccess()
        links.GET('complete').isSuccess()
        links.GET('deprecated').isSuccess()
        links.GET('doc:array').isSuccess()

        and: 'SafeCURIE hrefs expand client-side (external targets — not sent)'
        def curies = links.GET('doc:curies').asHal()
        curies.expandedHref('doc:spec') == 'https://stateless.group/hal_specification.html'
        curies.expandedHref('doc:item') == 'https://api.example.com/v2/items/42'
        curies.expandedHref('doc:collection') == 'https://api.example.com/v2/items'
    }

    def "accesses embedded resources and their links"() {
        given:
        def complex = root.GET('links').asHal().GET('doc:complex').asHal()

        expect:
        complex.total == 2
        def item = complex.embedded('doc:items', 0)
        item.sku == 'SKU-1'
        item.inStock == true
        item.link('self').href == '/complex-hal/items/1'
        complex.embedded('doc:items', [sku: 'SKU-1']) != null
        complex.embedded('doc:author', 0) != null
    }

    def "resolves documentation from curies for a link's rel"() {
        given:
        def curies = root.GET('links').asHal().GET('doc:curies').asHal()

        expect:
        curies.doc('spec').href == '/docs/curies#spec'
        curies.doc('item').href == '/docs/curies#item'

        and:
        def doc = curies.openDoc('spec')
        doc.isSuccess()
        doc.contentType.contains('html')
    }
}
