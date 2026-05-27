package com.helpchoice.nahal.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helpchoice.nahal.haldish.model.HalLink
import com.helpchoice.nahal.ui.NaHalMonoFont
import com.helpchoice.nahal.ui.NaHalSansFont
import com.helpchoice.nahal.ui.LocalCurrentUrl
import com.helpchoice.nahal.ui.LocalNaHalColors
import com.helpchoice.nahal.ui.model.StatusClass
import com.helpchoice.nahal.ui.model.statusClass

// ── Method badge ─────────────────────────────────────────────────────────────

@Composable
fun MethodBadge(method: String) {
    val c = LocalNaHalColors.current
    val (bg, fg) = when (method.uppercase()) {
        "GET"      -> Color(0x1A6DD497) to c.ok
        "POST"     -> Color(0x1A6CB6E2) to c.redir
        "PUT"      -> Color(0x1AE2B057) to c.warn
        "PATCH"    -> Color(0x1AE2B057) to c.warn
        "DELETE"   -> Color(0x1AE26C6C) to c.err
        "HEAD"     -> Color(0x1A9AA0AD) to c.info
        "OPTIONS"  -> Color(0x1A9AA0AD) to c.info
        "EMBEDDED" -> Color(0x1A8B7CFF) to c.accent
        "ARRAY"    -> Color(0x1A6DD497) to c.ok
        else       -> c.bg3 to c.text2
    }
    val label = when (method) {
        "EMBEDDED" -> "EMB"
        "ARRAY"    -> "ARR"
        else       -> method
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            color = fg,
            fontFamily = NaHalMonoFont,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
        )
    }
}

// ── Status pill ───────────────────────────────────────────────────────────────

@Composable
fun StatusPill(code: Int, statusText: String = "") {
    val c = LocalNaHalColors.current
    val (bg, fg) = when (statusClass(code)) {
        StatusClass.OK    -> Color(0x1A6DD497) to c.ok
        StatusClass.WARN  -> Color(0x1AE2B057) to c.warn
        StatusClass.ERR   -> Color(0x1AE26C6C) to c.err
        StatusClass.REDIR -> Color(0x1A6CB6E2) to c.redir
        StatusClass.INFO  -> Color(0x1A9AA0AD) to c.info
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(fg))
        Text(
            text = if (statusText.isNotBlank()) "$code $statusText" else "$code",
            color = fg,
            fontSize = 11.sp,
            fontFamily = NaHalMonoFont,
        )
    }
}

// ── Rel chip ─────────────────────────────────────────────────────────────────

fun resolveUri(base: String, href: String): String {
    if (href.contains("://") || href.startsWith("//")) return href
    val schemeEnd = base.indexOf("://")
    if (schemeEnd < 0) return href
    val afterScheme = base.substring(schemeEnd + 3)
    val slashIdx = afterScheme.indexOf('/')
    val origin = base.substring(0, schemeEnd + 3) + if (slashIdx >= 0) afterScheme.substring(0, slashIdx) else afterScheme
    return if (href.startsWith('/')) "$origin$href" else "${base.substringBeforeLast('/')}/$href"
}

fun resolveCurieDocs(rel: String, curies: List<HalLink>): String? {
    val colonIdx = rel.indexOf(':')
    if (colonIdx < 0) return null
    val prefix = rel.substring(0, colonIdx)
    val suffix = rel.substring(colonIdx + 1)
    val curie = curies.find { it.name == prefix } ?: return null
    return curie.href.replace("{rel}", suffix)
}

@Composable
fun RelChip(rel: String, curies: List<HalLink>, templated: Boolean = false) {
    val c = LocalNaHalColors.current
    val docsUrl = resolveCurieDocs(rel, curies)
    val uriHandler = LocalUriHandler.current

    val isCuried = docsUrl != null
    val bg = if (isCuried) c.accentSoft else c.bg3
    val fg = if (isCuried) c.accent else c.text2
    val borderColor = if (isCuried) c.accent.copy(alpha = 0.3f) else c.border
    val currentUrl = LocalCurrentUrl.current

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(3.dp))
            .then(if (docsUrl != null) Modifier.clickable { uriHandler.openUri(resolveUri(currentUrl, docsUrl)) } else Modifier)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = rel,
            color = fg,
            fontSize = 11.sp,
            fontFamily = NaHalMonoFont,
            textDecoration = if (isCuried) TextDecoration.None else TextDecoration.None,
        )
        if (isCuried) {
            Text("↗", color = fg.copy(alpha = 0.5f), fontSize = 9.sp)
        }
        if (templated) {
            Text("·tpl", color = fg.copy(alpha = 0.6f), fontSize = 9.sp)
        }
    }
}

// ── Count badge ───────────────────────────────────────────────────────────────

@Composable
fun CountBadge(count: Int) {
    val c = LocalNaHalColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(c.bg3)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(text = "$count", color = c.text3, fontSize = 10.sp, fontFamily = NaHalMonoFont)
    }
}

// ── Section title ─────────────────────────────────────────────────────────────

@Composable
fun SectionTitle(text: String) {
    val c = LocalNaHalColors.current
    Text(
        text = text.uppercase(),
        color = c.text3,
        fontSize = 10.sp,
        fontFamily = NaHalMonoFont,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
    )
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
fun EmptyState(text: String) {
    val c = LocalNaHalColors.current
    Text(
        text = text,
        color = c.text3,
        fontSize = 11.5.sp,
        fontFamily = NaHalSansFont,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
    )
}

// ── Key-value row ─────────────────────────────────────────────────────────────

@Composable
fun KvRow(key: String, value: String) {
    val c = LocalNaHalColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = key,
            color = c.text3,
            fontSize = 11.sp,
            fontFamily = NaHalMonoFont,
            modifier = Modifier.width(180.dp),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
        Text(
            text = value,
            color = c.text,
            fontSize = 11.sp,
            fontFamily = NaHalMonoFont,
            modifier = Modifier.weight(1f),
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
        )
    }
}

// ── Divider ───────────────────────────────────────────────────────────────────

@Composable
fun NaHalDivider(modifier: Modifier = Modifier) {
    val c = LocalNaHalColors.current
    Box(modifier = modifier.background(c.border))
}
