package com.helpchoice.nahal.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.haldish.model.PathStep
import com.helpchoice.nahal.ui.NaHalMonoFont
import com.helpchoice.nahal.ui.NaHalSansFont
import com.helpchoice.nahal.ui.JsonColors
import com.helpchoice.nahal.ui.LocalCurrentUrl
import com.helpchoice.nahal.ui.LocalNaHalColors
import kotlinx.serialization.json.*

// ── Headers panel ─────────────────────────────────────────────────────────────

@Composable
fun HeadersPanel(headers: Map<String, String>) {
    if (headers.isEmpty()) { EmptyState("none"); return }
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        headers.entries.forEachIndexed { i, (k, v) ->
            val c = LocalNaHalColors.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (i < headers.size - 1) Modifier.padding(bottom = 2.dp) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = k,
                    color = c.text3,
                    fontSize = 11.sp,
                    fontFamily = NaHalMonoFont,
                    modifier = Modifier.width(180.dp),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
                SelectionContainer {
                    Text(
                        text = v,
                        color = c.text,
                        fontSize = 11.sp,
                        fontFamily = NaHalMonoFont,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ── Cookies panel ─────────────────────────────────────────────────────────────

@Composable
fun CookiesPanel(cookies: Map<String, String>) {
    if (cookies.isEmpty()) { EmptyState("No cookies."); return }
    val c = LocalNaHalColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cookies.forEach { (name, value) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp)
                    .border(
                        width = 1.dp,
                        color = c.border,
                        shape = RoundedCornerShape(3.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name, color = c.text3, fontSize = 11.sp, fontFamily = NaHalMonoFont, fontWeight = FontWeight.SemiBold)
                    SelectionContainer {
                        Text(value, color = c.text, fontSize = 11.sp, fontFamily = NaHalMonoFont)
                    }
                }
            }
        }
    }
}

// ── Response cookies panel (with attributes) ──────────────────────────────────

data class CookieInfo(
    val name: String,
    val value: String,
    val domain: String? = null,
    val path: String? = null,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val sameSite: String? = null,
)

@Composable
fun ResponseCookiesPanel(cookies: List<CookieInfo>) {
    val c = LocalNaHalColors.current
    if (cookies.isEmpty()) { EmptyState("No Set-Cookie headers."); return }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cookies.forEach { cookie ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, c.border, RoundedCornerShape(3.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(cookie.name, color = c.text3, fontSize = 11.sp, fontFamily = NaHalMonoFont, fontWeight = FontWeight.SemiBold)
                    Text(cookie.value, color = c.text, fontSize = 11.sp, fontFamily = NaHalMonoFont)
                }
                val attrs = buildList {
                    cookie.domain?.let { add("domain=$it") }
                    cookie.path?.let { add("path=$it") }
                    if (cookie.secure) add("Secure")
                    if (cookie.httpOnly) add("HttpOnly")
                    cookie.sameSite?.let { add("SameSite=$it") }
                }
                if (attrs.isNotEmpty()) {
                    Text(
                        text = attrs.joinToString("  "),
                        color = c.text3,
                        fontSize = 10.sp,
                        fontFamily = NaHalSansFont,
                    )
                }
            }
        }
    }
}

// ── Links panel ───────────────────────────────────────────────────────────────

@Composable
fun LinksPanel(
    document: HalDocument,
    onFollow: (rel: String, index: Int, link: HalLink) -> Unit,
) {
    val c = LocalNaHalColors.current
    val uriHandler = LocalUriHandler.current
    val curies = document.links["curies"] ?: emptyList()
    val links = document.links.entries.filter { it.key != "curies" }

    if (links.isEmpty()) { EmptyState("No links."); return }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        links.forEach { (rel, linkList) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // Rel chip column (fixed width)
                Column(
                    modifier = Modifier.width(110.dp).padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RelChip(rel = rel, curies = curies, templated = linkList.firstOrNull()?.templated == true)
                    if (linkList.size > 1) {
                        CountBadge(linkList.size)
                    }
                }

                // Links list
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    linkList.forEachIndexed { index, link ->
                        val isDeprecated = link.deprecation != null
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onFollow(rel, index, link) }
                                .background(c.bg3.copy(alpha = 0f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            // href
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = link.href,
                                    color = c.accent,
                                    fontSize = 12.sp,
                                    fontFamily = NaHalMonoFont,
                                    textDecoration = if (isDeprecated) TextDecoration.LineThrough else TextDecoration.Underline,
                                    modifier = Modifier.weight(1f, fill = false),
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1,
                                )
                                if (link.templated) {
                                    Text(
                                        "· fill",
                                        color = c.text3,
                                        fontSize = 10.sp,
                                        fontFamily = NaHalMonoFont,
                                        fontStyle = FontStyle.Italic,
                                    )
                                }
                            }

                            // title
                            link.title?.let {
                                Text(it, color = c.text3, fontSize = 11.sp, fontFamily = NaHalSansFont)
                            }

                            // meta fields
                            val hasMeta = link.name != null || link.hreflang != null ||
                                link.type != null || link.profile != null || link.deprecation != null
                            if (hasMeta) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    link.name?.let { MetaTag("name", it) }
                                    link.hreflang?.let { MetaTag("lang", it) }
                                    link.type?.let { MetaTag("type", it) }
                                    val currentUrl = LocalCurrentUrl.current
                                    link.profile?.let { v ->
                                        MetaTagLink("profile", v) { uriHandler.openUri(resolveUri(currentUrl, v)) }
                                    }
                                    link.deprecation?.let { v ->
                                        MetaTagLink("deprecated", v, warn = true) { uriHandler.openUri(resolveUri(currentUrl, v)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaTag(key: String, value: String) {
    val c = LocalNaHalColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(key.uppercase(), color = c.text3.copy(alpha = 0.7f), fontSize = 9.sp, fontFamily = NaHalMonoFont, letterSpacing = 0.06.sp)
        Text(value, color = c.text3, fontSize = 10.5.sp, fontFamily = NaHalSansFont)
    }
}

@Composable
private fun MetaTagLink(key: String, value: String, warn: Boolean = false, onClick: () -> Unit) {
    val c = LocalNaHalColors.current
    val color = if (warn) c.warn else c.accent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(key.uppercase(), color = if (warn) c.warn.copy(alpha = 0.7f) else c.text3.copy(alpha = 0.7f), fontSize = 9.sp, fontFamily = NaHalMonoFont)
        Text(value, color = color, fontSize = 10.5.sp, fontFamily = NaHalSansFont, textDecoration = TextDecoration.Underline)
    }
}

// ── Embedded panel ────────────────────────────────────────────────────────────

@Composable
fun EmbeddedPanel(
    document: HalDocument,
    onOpen: (rel: String, index: Int) -> Unit,
) {
    val c = LocalNaHalColors.current
    val curies = document.links["curies"] ?: emptyList()
    val embedded = document.embedded

    if (embedded.isEmpty()) { EmptyState("No embedded resources."); return }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        embedded.forEach { (rel, items) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RelChip(rel = rel, curies = curies)
                    CountBadge(items.size)
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    items.forEachIndexed { idx, item ->
                        val summary = summaryOf(item)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(3.dp))
                                .background(c.bg3)
                                .border(
                                    width = 1.dp,
                                    color = c.border,
                                    shape = RoundedCornerShape(3.dp),
                                )
                                .border(
                                    width = 2.dp,
                                    color = c.accent,
                                    shape = RoundedCornerShape(
                                        topStart = 3.dp, bottomStart = 3.dp,
                                        topEnd = 0.dp, bottomEnd = 0.dp,
                                    ),
                                )
                                .clickable { onOpen(rel, idx) }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = summary,
                                color = c.text,
                                fontSize = 11.5.sp,
                                fontFamily = NaHalMonoFont,
                                modifier = Modifier.weight(1f),
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                            )
                            Text("↳ open", color = c.text3, fontSize = 10.sp, fontFamily = NaHalMonoFont)
                        }
                    }
                }
            }
        }
    }
}

private fun summaryOf(doc: HalDocument): String {
    val p = doc.properties
    fun str(key: String) = (p[key] as? JsonPrimitive)?.contentOrNull
    return str("full_name")
        ?: str("name")
        ?: str("title")
        ?: str("login")
        ?: str("number")?.let { "#$it" }
        ?: doc.links["self"]?.firstOrNull()?.href
        ?: "(item)"
}

// ── Array items panel ─────────────────────────────────────────────────────────

@Composable
fun ArrayItemsPanel(items: List<HalDocument>, onOpen: (Int) -> Unit) {
    val c = LocalNaHalColors.current
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items.forEachIndexed { idx, item ->
            val summary = summaryOf(item)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(c.bg3)
                    .border(width = 1.dp, color = c.border, shape = RoundedCornerShape(3.dp))
                    .border(
                        width = 2.dp, color = c.accent,
                        shape = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 0.dp, bottomEnd = 0.dp),
                    )
                    .clickable { onOpen(idx) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = summary,
                    color = c.text,
                    fontSize = 11.5.sp,
                    fontFamily = NaHalMonoFont,
                    modifier = Modifier.weight(1f),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
                Text("↳ open", color = c.text3, fontSize = 10.sp, fontFamily = NaHalMonoFont)
            }
        }
    }
}

// ── Property tree ─────────────────────────────────────────────────────────────

/**
 * Renders [document]'s JSON properties. A string property holding an absolute URL is clickable and
 * reports its [PathStep.Property] chain — the terminal only, relative to [document] — to [onFollow].
 * Rooting that terminal against the fetched ancestor is the caller's job.
 */
@Composable
fun PropTree(
    document: HalDocument,
    onFollow: ((List<PathStep.Property>, String) -> Unit)? = null,
) {
    val props = document.properties
    if (props.isEmpty()) { EmptyState("No properties."); return }
    Column(modifier = Modifier.fillMaxWidth()) {
        props.forEach { (key, value) ->
            JsonNode(
                key = key, value = value, depth = 0,
                path = listOf(PathStep.Property(key)), onFollow = onFollow,
            )
        }
    }
}

/** `<scheme>://<non-empty>` — the only property values offered as followable. */
private val ABSOLUTE_URL = Regex("^[A-Za-z][A-Za-z0-9+.-]*://.+$")

/**
 * One node of the property tree.
 *
 * [path] is the property chain addressing this node, or null when the node sits in a subtree the
 * path grammar cannot express — an array nested directly in an array, whose elements have no name
 * to hang an index on. Such values still render; they are simply never clickable.
 */
@Composable
private fun JsonNode(
    key: String,
    value: JsonElement,
    depth: Int,
    path: List<PathStep.Property>?,
    onFollow: ((List<PathStep.Property>, String) -> Unit)?,
) {
    val c = LocalNaHalColors.current
    val indent = (depth * 14).dp

    when (value) {
        is JsonObject -> {
            var open by remember(key) { mutableStateOf(depth < 2) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { open = !open }
                    .padding(start = indent, top = 1.dp, bottom = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(if (open) "▾" else "▸", color = c.text3, fontSize = 9.sp, modifier = Modifier.width(10.dp))
                Text(key, color = c.accent, fontSize = 11.5.sp, fontFamily = NaHalMonoFont)
                Text("Object{${value.size}}", color = c.text3, fontSize = 10.5.sp, fontFamily = NaHalSansFont)
            }
            if (open) {
                value.entries.forEach { (k, v) ->
                    JsonNode(
                        key = k, value = v, depth = depth + 1,
                        path = path?.plus(PathStep.Property(k)), onFollow = onFollow,
                    )
                }
            }
        }
        is JsonArray -> {
            var open by remember(key) { mutableStateOf(depth < 2) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { open = !open }
                    .padding(start = indent, top = 1.dp, bottom = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(if (open) "▾" else "▸", color = c.text3, fontSize = 9.sp, modifier = Modifier.width(10.dp))
                Text(key, color = c.accent, fontSize = 11.5.sp, fontFamily = NaHalMonoFont)
                Text("Array[${value.size}]", color = c.text3, fontSize = 10.5.sp, fontFamily = NaHalSansFont)
            }
            if (open) {
                // The index folds into the step naming this array. An array directly inside an
                // array has no name of its own to carry a second index, so its elements are
                // unaddressable (path = null) and render as plain values.
                val last = path?.lastOrNull()
                val addressable = last != null && last.index == null
                value.forEachIndexed { i, item ->
                    JsonNode(
                        key = "[$i]", value = item, depth = depth + 1,
                        path = if (addressable) path.dropLast(1) + last!!.copy(index = i) else null,
                        onFollow = onFollow,
                    )
                }
            }
        }
        is JsonPrimitive -> {
            val (displayText, textColor) = when {
                value.isString -> "\"${value.content}\"" to JsonColors.string
                value.booleanOrNull != null -> value.content to JsonColors.bool
                value.content == "null" -> "null" to JsonColors.null_
                else -> value.content to JsonColors.number
            }
            val followable = onFollow != null && path != null &&
                value.isString && ABSOLUTE_URL.matches(value.content)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = indent + 18.dp, top = 1.dp, bottom = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(key, color = c.accent, fontSize = 11.5.sp, fontFamily = NaHalMonoFont)
                SelectionContainer {
                    Text(displayText, color = textColor, fontSize = 11.5.sp, fontFamily = NaHalMonoFont)
                }
                if (followable) {
                    Text(
                        "follow ↗",
                        color = c.accent,
                        fontSize = 10.5.sp,
                        fontFamily = NaHalSansFont,
                        modifier = Modifier.clickable { onFollow!!(path!!, value.content) },
                    )
                }
            }
        }
    }
}

// ── Raw JSON panel ────────────────────────────────────────────────────────────

@Composable
fun RawJsonPanel(
    body: String,
    preamble: String? = null,
    onFollowUrl: ((String) -> Unit)? = null,
) {
    val c = LocalNaHalColors.current

    val annotated = remember(preamble, body) {
        buildAnnotatedString {
            if (preamble != null) {
                withStyle(SpanStyle(color = JsonColors.key)) { append(preamble) }
                append("\n")
            }
            try {
                val element = Json.parseToJsonElement(body)
                appendJsonElement(element, 0)
            } catch (_: Exception) {
                append(body)
            }
        }
    }

    SelectionContainer {
        Text(
            text = annotated,
            color = c.text,
            fontFamily = NaHalMonoFont,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(c.bg2)
                .border(1.dp, c.border, RoundedCornerShape(5.dp))
                .padding(16.dp),
        )
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendJsonElement(
    element: JsonElement,
    indent: Int,
) {
    val spaces = "  ".repeat(indent)
    when (element) {
        is JsonObject -> {
            append("{\n")
            val entries = element.entries.toList()
            entries.forEachIndexed { i, (key, value) ->
                append("$spaces  ")
                withStyle(SpanStyle(color = JsonColors.key)) { append("\"$key\"") }
                append(": ")
                appendJsonElement(value, indent + 1)
                if (i < entries.size - 1) append(",")
                append("\n")
            }
            append("$spaces}")
        }
        is JsonArray -> {
            append("[\n")
            element.forEachIndexed { i, item ->
                append("$spaces  ")
                appendJsonElement(item, indent + 1)
                if (i < element.size - 1) append(",")
                append("\n")
            }
            append("$spaces]")
        }
        is JsonPrimitive -> when {
            element.isString -> withStyle(SpanStyle(color = JsonColors.string)) {
                append("\"${element.content}\"")
            }
            element.booleanOrNull != null -> withStyle(SpanStyle(color = JsonColors.bool)) {
                append(element.content)
            }
            element.content == "null" -> withStyle(SpanStyle(color = JsonColors.null_)) {
                append("null")
            }
            else -> withStyle(SpanStyle(color = JsonColors.number)) {
                append(element.content)
            }
        }
    }
}
