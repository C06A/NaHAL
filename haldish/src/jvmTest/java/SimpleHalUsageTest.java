/**
 * Happy-day usage of the haldish library from plain Java (no Kotlin, no HTTP).
 *
 * Demonstrates:
 *   - Parsing an inline HAL JSON string with HalParser
 *   - Extracting a link href from the parsed document
 *   - Expanding a URI template with UriTemplate and UriTemplateVars
 *
 * Run:
 *   ./gradlew :haldish:jvmTest
 */
import com.helpchoice.nahal.haldish.model.HalDocument;
import com.helpchoice.nahal.haldish.parser.HalParser;
import com.helpchoice.nahal.haldish.uritemplate.UriTemplate;
import com.helpchoice.nahal.haldish.uritemplate.UriTemplateVars;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SimpleHalUsageTest {

    private static final String HAL_JSON =
        "{\"_links\":{" +
        "\"self\":{\"href\":\"/orders\"}," +
        "\"search\":{\"href\":\"/orders{?page,size}\",\"templated\":true}" +
        "}}";

    @Test
    public void parsesLinkHref() {
        HalDocument doc = HalParser.INSTANCE.parse(HAL_JSON, null);
        assertEquals("/orders", doc.link("self").getHref());
    }

    @Test
    public void expandsUriTemplate() {
        HalDocument doc = HalParser.INSTANCE.parse(HAL_JSON, null);
        String tmpl = doc.link("search").getHref();
        UriTemplateVars vars = new UriTemplateVars()
            .set("page", "1")
            .set("size", "20");
        String expanded = new UriTemplate(tmpl).expand(vars);
        assertEquals("/orders?page=1&size=20", expanded);
    }
}
