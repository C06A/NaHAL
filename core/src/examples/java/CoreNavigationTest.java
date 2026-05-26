/**
 * Happy-day usage of the core library from plain Java (no Kotlin, no HTTP).
 *
 * Demonstrates:
 *   - Parsing an inline HAL JSON string with HalParser
 *   - Selecting a top-level link with LinkSelector.TopLevel
 *   - Selecting a link inside an embedded resource with LinkSelector.InEmbedded
 *
 * Run:
 *   ./gradlew :core:jvmTest
 */
import com.helpchoice.nahal.haldish.model.HalDocument;
import com.helpchoice.nahal.haldish.model.HalLink;
import com.helpchoice.nahal.haldish.parser.HalParser;
import com.helpchoice.nahal.core.LinkSelector;
import org.junit.Test;

import static org.junit.Assert.*;

public class CoreNavigationTest {

    private static final String HAL_JSON =
        "{\"_links\":{" +
            "\"self\":{\"href\":\"/orders\"}," +
            "\"search\":{\"href\":\"/orders{?page,size}\",\"templated\":true}" +
        "},\"_embedded\":{\"orders\":[" +
            "{\"_links\":{\"self\":{\"href\":\"/orders/1\"}}}" +
        "]}}";

    @Test
    public void selectsTopLevelLink() {
        HalDocument resource = HalParser.INSTANCE.parse(HAL_JSON, "application/hal+json");
        HalLink link = new LinkSelector.TopLevel("self", 0).select(resource);
        assertNotNull(link);
        assertEquals("/orders", link.getHref());
    }

    @Test
    public void selectsEmbeddedLink() {
        HalDocument resource = HalParser.INSTANCE.parse(HAL_JSON, "application/hal+json");
        HalLink link = new LinkSelector.InEmbedded("orders", 0, "self", 0).select(resource);
        assertNotNull(link);
        assertEquals("/orders/1", link.getHref());
    }
}
