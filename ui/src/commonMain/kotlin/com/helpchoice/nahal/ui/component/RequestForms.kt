package com.helpchoice.nahal.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helpchoice.nahal.ui.NaHalMonoFont
import com.helpchoice.nahal.ui.NaHalSansFont
import com.helpchoice.nahal.ui.LocalNaHalColors
import com.helpchoice.nahal.ui.model.BodyKind
import com.helpchoice.nahal.ui.model.BodyPart
import com.helpchoice.nahal.ui.model.PendingRequest
import com.helpchoice.nahal.ui.model.TemplateVarValue
import com.helpchoice.nahal.ui.model.VarRow
import com.helpchoice.nahal.ui.state.expandTemplate
import com.helpchoice.nahal.ui.state.extractTemplateVars

/**
 * A file chosen by the platform file picker. [content] is its text view; [bytes] the raw bytes
 * (used for binary/multipart bodies). Both default from each other so text-only providers still
 * compile, but a provider should populate [bytes] for true binary fidelity.
 */
data class PickedFile(
    val name: String,
    val content: String,
    val bytes: ByteArray = content.encodeToByteArray(),
    val contentType: String = guessContentType(name),
)

/** Best-effort media type from a file name's extension. */
fun guessContentType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "json"          -> "application/json"
    "hal"           -> "application/hal+json"
    "xml"           -> "application/xml"
    "yaml", "yml"   -> "application/x-yaml"
    "txt"           -> "text/plain"
    "html", "htm"   -> "text/html"
    "csv"           -> "text/csv"
    "pdf"           -> "application/pdf"
    "png"           -> "image/png"
    "jpg", "jpeg"   -> "image/jpeg"
    "gif"           -> "image/gif"
    "zip"           -> "application/zip"
    else            -> "application/octet-stream"
}

val LocalFilePicker = compositionLocalOf<(((PickedFile?) -> Unit) -> Unit)?> { null }

// ── Template form ─────────────────────────────────────────────────────────────

@Composable
fun TemplateForm(
    request: PendingRequest,
    onVarsChange: (Map<String, TemplateVarValue>) -> Unit,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val c = LocalNaHalColors.current
    val vars = extractTemplateVars(request.url)
    var localVars by remember(request.url) { mutableStateOf(request.vars) }
    val expanded = expandTemplate(request.url, localVars)

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.bg2)
            .border(1.dp, c.border2, RoundedCornerShape(6.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle("Expand template")
            Text(
                text = request.url,
                color = c.text,
                fontSize = 13.sp,
                fontFamily = NaHalMonoFont,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(c.bg3)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Variable inputs — one block each, since a value can grow to several rows and two columns.
        if (vars.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                vars.forEach { varName ->
                    TemplateVarEditor(
                        name = varName,
                        value = localVars[varName] ?: TemplateVarValue(),
                        onChange = { updated ->
                            localVars = localVars + (varName to updated)
                            onVarsChange(localVars)
                        },
                    )
                }
            }
        }

        // Preview
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(c.bg3)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionTitle("Result")
            Text(
                text = expanded,
                color = c.ok,
                fontSize = 12.sp,
                fontFamily = NaHalMonoFont,
            )
        }

        // Actions
        NaHalDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            NaHalButton(text = "Cancel", primary = false, onClick = onCancel)
            NaHalButton(text = "Use URL →", primary = true, onClick = { onSubmit(expanded) })
        }
    }
}

/**
 * Width of the control gutter left of the fields, holding the `×` that drops a field and the `+`s
 * that insert one. Column headers reserve it too, so they stay over their fields.
 */
private val GutterWidth = 22.dp

/**
 * Editor for one URI Template variable, covering all three RFC 6570 value kinds without a mode
 * selector — the shape of the grid *is* the kind:
 *
 * - one row, one column → string
 * - a second row → list; each row gets a `×` in the gutter, and deleting back down to one row
 *   returns it to a string
 * - `+name` / `+value`, over the ends of the first field → associative array. A lone column holds
 *   values, so `+name` opens a name column to its left and leaves the typing alone, while `+value`
 *   re-reads what is typed as the names and opens an empty value column beside it. Once both
 *   exist, the pair becomes `-name` / `-value`, centred over the column each one drops: `-name`
 *   keeps the values as a list, `-value` promotes the names — either way a mis-added column is
 *   recoverable without retyping.
 *
 * Every control lives in a gutter left of the fields: the `+`s in the gaps between them, the `×`
 * level with the field it drops. A `+` inserts directly **below its own field** rather than at the
 * end, and the first field carries one above it too, so every position including the front is
 * reachable. Order is meaningful — a list expands in row order, and `{/segments*}` is a path — so
 * building the sequence in place beats appending and reordering.
 */
@Composable
private fun TemplateVarEditor(
    name: String,
    value: TemplateVarValue,
    onChange: (TemplateVarValue) -> Unit,
) {
    val c = LocalNaHalColors.current
    // A value with no rows cannot be edited back into existence — always show one.
    val rows = value.rows.ifEmpty { listOf(VarRow()) }
    val kind = when {
        value.keyed   -> "assoc"
        rows.size > 1 -> "list"
        else          -> "string"
    }

    Column(verticalArrangement = Arrangement.spacedBy(HeaderGap)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, color = c.accent, fontSize = 11.sp, fontFamily = NaHalMonoFont)
            Text(kind, color = c.text3, fontSize = 10.sp, fontFamily = NaHalMonoFont)
        }

        // Column controls, sitting over the first field. One column shows what can be added and at
        // which end; two columns show what can be dropped, each centred over its own column.
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Clear the control gutter so these line up with the fields, not with the `+`/`×`.
            Spacer(Modifier.width(GutterWidth))
            if (value.keyed) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    MiniAction("-name") {
                        onChange(TemplateVarValue(rows.map { VarRow(value = it.value) }, keyed = false))
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    MiniAction("-value") {
                        onChange(TemplateVarValue(rows.map { VarRow(value = it.key) }, keyed = false))
                    }
                }
            } else {
                Box(Modifier.weight(1f)) {
                    // A single column holds values, so a name column can only appear to its left —
                    // hence `+name` at the left end. `+value` at the right end instead re-reads what
                    // is already typed as the names and opens an empty value column beside it.
                    MiniAction("+name", Modifier.align(Alignment.CenterStart)) {
                        onChange(TemplateVarValue(rows.map { VarRow(value = it.value) }, keyed = true))
                    }
                    MiniAction("+value", Modifier.align(Alignment.CenterEnd)) {
                        onChange(TemplateVarValue(rows.map { VarRow(key = it.value) }, keyed = true))
                    }
                }
            }
        }

        // Just enough room for the insert `+` to sit in the gap between two fields. The top
        // padding tops the header gap up to a full RowGap, so the leading `+` above the first
        // field gets the same slot every other one has.
        Column(
            modifier = Modifier.padding(top = RowGap - HeaderGap),
            verticalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            rows.forEachIndexed { i, row ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Control gutter, always reserved: the `×` sits level with the field, the
                        // insert `+`s in the gaps above and below it. Reserving it even for a lone
                        // field keeps the fields from shifting sideways when a second row appears.
                        Box(
                            modifier = Modifier.width(GutterWidth),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (rows.size > 1) {
                                Text(
                                    text = "×",
                                    color = c.text3,
                                    fontSize = 16.sp,
                                    modifier = Modifier.clickable {
                                        onChange(value.copy(rows = rows.filterIndexed { idx, _ -> idx != i }))
                                    },
                                )
                            }
                        }
                        if (value.keyed) {
                            NaHalTextField(
                                value = row.key,
                                onValueChange = { onChange(value.copy(rows = rows.replaceAt(i, row.copy(key = it)))) },
                                placeholder = "name",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        NaHalTextField(
                            value = row.value,
                            onValueChange = { onChange(value.copy(rows = rows.replaceAt(i, row.copy(value = it)))) },
                            placeholder = if (value.keyed) "value" else "{$name}",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // The first field also gets one *above* it, so a value can be put in front of
                    // what is already there — otherwise the first position is the one spot the
                    // insert affordance cannot reach.
                    if (i == 0) {
                        InsertFieldButton(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = GutterInset, y = -(RowGap / 2 + InsertButtonSize / 2)),
                            onClick = { onChange(value.copy(rows = rows.insertAt(0, VarRow()))) },
                        )
                    }

                    // Centred in the gap below this field, and in the gutter left of it. Aligning
                    // to the bottom puts the button's own bottom edge on the field's, so shifting
                    // it down by half the gap plus half its height lands its centre on the gap's
                    // centre line.
                    InsertFieldButton(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = GutterInset, y = RowGap / 2 + InsertButtonSize / 2),
                        onClick = { onChange(value.copy(rows = rows.insertAt(i + 1, VarRow()))) },
                    )
                }
            }
        }
    }
}

/** Vertical gap between two value fields — the insert `+` is centred in it. */
private val RowGap = 10.dp

/** Gap under the variable's name/kind line and its column headers. */
private val HeaderGap = 4.dp

/** Size of the per-field insert button. Slightly taller than [RowGap], so it grazes both fields. */
private val InsertButtonSize = 12.dp

/** Left inset that centres an insert button within [GutterWidth]. */
private val GutterInset = (GutterWidth - InsertButtonSize) / 2

/** The `+` sitting in the gap below a field's left edge: inserts a new field right below it. */
@Composable
private fun InsertFieldButton(modifier: Modifier, onClick: () -> Unit) {
    val c = LocalNaHalColors.current
    Box(
        modifier = modifier
            .size(InsertButtonSize)
            .clip(RoundedCornerShape(3.dp))
            // The form's own background, not the field's — the button has to punch through the
            // field border it sits on rather than look like a hole in the field.
            .background(c.bg2)
            .border(1.dp, c.border, RoundedCornerShape(3.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("+", color = c.text3, fontSize = 9.sp, fontFamily = NaHalMonoFont)
    }
}

/** Small bordered text button, matching the "+ Add header" affordance in the request builder. */
@Composable
private fun MiniAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalNaHalColors.current
    Text(
        text = label,
        color = c.text3,
        fontSize = 10.sp,
        fontFamily = NaHalMonoFont,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, c.border, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun <T> List<T>.replaceAt(index: Int, item: T): List<T> =
    toMutableList().also { it[index] = item }

private fun <T> List<T>.insertAt(index: Int, item: T): List<T> =
    toMutableList().also { it.add(index, item) }

// ── Request builder ───────────────────────────────────────────────────────────

@Composable
fun RequestBuilder(
    request: PendingRequest,
    onChange: (PendingRequest) -> Unit,
    onSend: (PendingRequest) -> Unit,
    onCancel: () -> Unit,
) {
    val c = LocalNaHalColors.current
    var activeTab by remember { mutableStateOf("headers") }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.bg2)
            .border(1.dp, c.border2, RoundedCornerShape(6.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle("Send request")
            Text("×", color = c.text3, fontSize = 18.sp, modifier = Modifier.clickable(onClick = onCancel))
        }

        // Method + URL
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Method selector — editable text + dropdown for quick picks
            var methodExpanded by remember { mutableStateOf(false) }
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(c.bg3)
                        .border(1.dp, c.border, RoundedCornerShape(3.dp))
                        .width(104.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = request.method,
                        onValueChange = { onChange(request.copy(method = it.uppercase())) },
                        singleLine = true,
                        cursorBrush = SolidColor(c.accent),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = c.text,
                            fontFamily = NaHalMonoFont,
                            fontSize = 12.sp,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
                    )
                    Text(
                        text = "▾",
                        color = c.text3,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clickable { methodExpanded = !methodExpanded }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                    )
                }
                DropdownMenu(
                    expanded = methodExpanded,
                    onDismissRequest = { methodExpanded = false },
                ) {
                    listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS").forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m, fontFamily = NaHalMonoFont, fontSize = 12.sp) },
                            onClick = { onChange(request.copy(method = m)); methodExpanded = false },
                        )
                    }
                }
            }

            NaHalTextField(
                value = request.url,
                onValueChange = { onChange(request.copy(url = it)) },
                modifier = Modifier.weight(1f),
            )
        }

        // Tabs
        Row(
            modifier = Modifier.fillMaxWidth().border(
                width = 1.dp,
                color = c.border,
                shape = RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp, topStart = 0.dp, topEnd = 0.dp),
            ),
        ) {
            listOf("headers" to "Headers (${request.headers.size})",
                   "cookies" to "Cookies (${request.cookies.size})",
                   "body"    to "Body").forEach { (key, label) ->
                val isActive = activeTab == key
                Box(
                    modifier = Modifier
                        .clickable { activeTab = key }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .then(
                            if (isActive) Modifier.border(
                                width = 2.dp,
                                color = c.accent,
                                shape = RoundedCornerShape(0.dp),
                            ) else Modifier
                        ),
                ) {
                    Text(
                        text = label,
                        color = if (isActive) c.text else c.text3,
                        fontSize = 11.sp,
                        fontFamily = NaHalMonoFont,
                    )
                }
            }
        }

        // Tab content
        when (activeTab) {
            "headers" -> HeadersEditor(
                headers = request.headers,
                onChange = { onChange(request.copy(headers = it)) },
            )
            "cookies" -> CookiesEditor(
                cookies = request.cookies,
                onChange = { onChange(request.copy(cookies = it)) },
            )
            "body" -> BodyEditor(request, onChange, LocalFilePicker.current)
        }

        // Footer
        NaHalDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The Accept line is elastic and the button is not: a followed link with no media type
            // falls back to the full HAL Accept (five types with q-values), which in the graph
            // layout's 640dp modal is wider than the row and would otherwise measure Send to zero
            // width — the button silently disappears. `fill = false` leaves a short Accept its own
            // width so SpaceBetween still pins the button to the trailing edge.
            Text(
                text = "Accept: ${request.headers["Accept"] ?: request.type ?: "application/hal+json"}",
                color = c.text3,
                fontSize = 11.sp,
                fontFamily = NaHalMonoFont,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
            )
            NaHalButton(text = "Send →", primary = true, onClick = { onSend(request) })
        }
    }
}

@Composable
private fun HeadersEditor(
    headers: Map<String, String>,
    onChange: (Map<String, String>) -> Unit,
) {
    val c = LocalNaHalColors.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        headers.entries.toList().forEachIndexed { _, (k, v) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NaHalTextField(
                    value = k,
                    onValueChange = { newKey ->
                        val m = headers.toMutableMap()
                        m.remove(k)
                        m[newKey] = v
                        onChange(m)
                    },
                    placeholder = "Header",
                    modifier = Modifier.weight(1f),
                )
                NaHalTextField(
                    value = v,
                    onValueChange = { newVal -> onChange(headers + (k to newVal)) },
                    placeholder = "Value",
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "×",
                    color = c.text3,
                    fontSize = 18.sp,
                    modifier = Modifier.clickable {
                        val m = headers.toMutableMap(); m.remove(k); onChange(m)
                    },
                )
            }
        }
        Text(
            text = "+ Add header",
            color = c.text3,
            fontSize = 11.sp,
            fontFamily = NaHalMonoFont,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, c.border, RoundedCornerShape(4.dp), )
                .clickable { onChange(headers + ("" to "")) }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun CookiesEditor(
    cookies: Map<String, String>,
    onChange: (Map<String, String>) -> Unit,
) {
    val c = LocalNaHalColors.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (cookies.isEmpty()) EmptyState("No cookies attached.")
        cookies.entries.toList().forEach { (k, v) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NaHalTextField(
                    value = k,
                    onValueChange = { newKey ->
                        val m = cookies.toMutableMap(); m.remove(k); m[newKey] = v; onChange(m)
                    },
                    modifier = Modifier.weight(1f),
                )
                NaHalTextField(
                    value = v,
                    onValueChange = { newVal -> onChange(cookies + (k to newVal)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = "+ Add cookie",
            color = c.text3,
            fontSize = 11.sp,
            fontFamily = NaHalMonoFont,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, c.border, RoundedCornerShape(4.dp))
                .clickable { onChange(cookies + ("" to "")) }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

// ── Body editor: text, binary file, or multipart ───────────────────────────────

@Composable
private fun BodyEditor(
    request: PendingRequest,
    onChange: (PendingRequest) -> Unit,
    pickFile: (((PickedFile?) -> Unit) -> Unit)?,
) {
    val c = LocalNaHalColors.current
    val isBodyMethod = request.method !in setOf("GET", "HEAD", "OPTIONS")
    // Choosing a body/file while on a bodyless method auto-switches to POST.
    val bodyMethod = if (isBodyMethod) request.method else "POST"

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BodyKind.entries.forEach { k ->
                val active = request.bodyKind == k
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (active) c.accent else c.bg2)
                        .clickable { onChange(request.copy(bodyKind = k, method = bodyMethod)) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = k.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = if (active) c.bg else c.text3,
                        fontSize = 11.sp, fontFamily = NaHalMonoFont,
                    )
                }
            }
        }

        when (request.bodyKind) {
            BodyKind.TEXT -> {
                if (pickFile != null) {
                    NaHalButton(text = "Load from file…", primary = false, onClick = {
                        pickFile { f -> if (f != null) onChange(request.copy(body = f.content, method = bodyMethod)) }
                    })
                }
                NaHalTextField(
                    value = request.body,
                    onValueChange = { onChange(request.copy(body = it)) },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    enabled = isBodyMethod,
                    placeholder = if (isBodyMethod) "{ \"key\": \"value\" }" else "No body for ${request.method}",
                    singleLine = false,
                )
            }

            BodyKind.BINARY -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = request.bodyFileName?.let { "$it  (${request.bodyBytes?.size ?: 0} B)" }
                            ?: "No file chosen",
                        color = c.text3, fontSize = 11.sp, fontFamily = NaHalMonoFont,
                    )
                    if (pickFile != null) {
                        NaHalButton(text = "Choose file…", primary = false, onClick = {
                            pickFile { f ->
                                if (f != null) onChange(request.copy(
                                    bodyBytes = f.bytes, bodyFileName = f.name,
                                    bodyContentType = f.contentType, method = bodyMethod,
                                ))
                            }
                        })
                    }
                }
                NaHalTextField(
                    value = request.bodyContentType,
                    onValueChange = { onChange(request.copy(bodyContentType = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "content-type",
                )
            }

            BodyKind.MULTIPART -> MultipartEditor(request, onChange, pickFile, bodyMethod)
        }
    }
}

@Composable
private fun MultipartEditor(
    request: PendingRequest,
    onChange: (PendingRequest) -> Unit,
    pickFile: (((PickedFile?) -> Unit) -> Unit)?,
    method: String,
) {
    val c = LocalNaHalColors.current
    fun updatePart(i: Int, f: (BodyPart) -> BodyPart) =
        onChange(request.copy(parts = request.parts.toMutableList().also { it[i] = f(it[i]) }, method = method))

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        request.parts.forEachIndexed { i, part ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NaHalTextField(
                    value = part.name,
                    onValueChange = { v -> updatePart(i) { it.copy(name = v) } },
                    modifier = Modifier.width(84.dp),
                    placeholder = "name",
                )
                if (part.isFile) {
                    Text(
                        text = part.fileName?.let { "$it (${part.bytes?.size ?: 0} B)" } ?: "no file",
                        color = c.text3, fontSize = 11.sp, fontFamily = NaHalMonoFont,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (pickFile != null) {
                        NaHalButton(text = "File…", primary = false, onClick = {
                            pickFile { f ->
                                if (f != null) updatePart(i) {
                                    it.copy(fileName = f.name, bytes = f.bytes, contentType = f.contentType)
                                }
                            }
                        })
                    }
                } else {
                    NaHalTextField(
                        value = part.value,
                        onValueChange = { v -> updatePart(i) { it.copy(value = v) } },
                        modifier = Modifier.weight(1f),
                        placeholder = "value",
                    )
                }
                Text(
                    text = "×", color = c.text3, fontSize = 16.sp,
                    modifier = Modifier.clickable {
                        onChange(request.copy(parts = request.parts.filterIndexed { j, _ -> j != i }))
                    },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NaHalButton(text = "+ field", primary = false, onClick = {
                onChange(request.copy(parts = request.parts + BodyPart(isFile = false), method = method))
            })
            NaHalButton(text = "+ file", primary = false, onClick = {
                onChange(request.copy(
                    parts = request.parts + BodyPart(isFile = true, contentType = "application/octet-stream"),
                    method = method,
                ))
            })
        }
    }
}

@Composable
fun NaHalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
) {
    val c = LocalNaHalColors.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        cursorBrush = SolidColor(c.accent),
        textStyle = androidx.compose.ui.text.TextStyle(
            color = c.text,
            fontFamily = NaHalMonoFont,
            fontSize = 12.sp,
        ),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(c.bg)
                    .border(1.dp, c.border, RoundedCornerShape(3.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, color = c.text3, fontSize = 12.sp, fontFamily = NaHalMonoFont)
                }
                inner()
            }
        },
        modifier = modifier,
    )
}

@Composable
fun NaHalButton(text: String, primary: Boolean, onClick: () -> Unit) {
    val c = LocalNaHalColors.current
    val bg = if (primary) c.accent else c.bg3
    val fg = if (primary) Color.White else c.text
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, if (primary) c.accent else c.border, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(text, color = fg, fontSize = 11.sp, fontFamily = NaHalMonoFont, fontWeight = FontWeight.Medium)
    }
}

