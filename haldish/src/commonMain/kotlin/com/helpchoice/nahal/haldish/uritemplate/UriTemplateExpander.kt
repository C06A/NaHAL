package com.helpchoice.nahal.haldish.uritemplate

internal object UriTemplateExpander {

    private data class OpSpec(
        val prefix: String,
        val sep: String,
        val allowReserved: Boolean,
        val named: Boolean,
        val ifEmpty: String,
    )

    private val OPS: Map<Char, OpSpec> = mapOf(
        ' ' to OpSpec("",  ",", false, false, ""),
        '+' to OpSpec("",  ",", true,  false, ""),
        '#' to OpSpec("#", ",", true,  false, ""),
        '.' to OpSpec(".", ".", false, false, ""),
        '/' to OpSpec("/", "/", false, false, ""),
        ';' to OpSpec(";", ";", false, true,  ""),
        '?' to OpSpec("?", "&", false, true,  "="),
        '&' to OpSpec("&", "&", false, true,  "="),
    )

    fun expand(template: String, vars: UriTemplateVars): String = buildString {
        var i = 0
        while (i < template.length) {
            when (val ch = template[i]) {
                '{' -> {
                    val end = template.indexOf('}', i)
                    if (end < 0) { append(template.substring(i)); break }
                    append(expandExpression(template.substring(i + 1, end), vars))
                    i = end + 1
                }
                else -> { append(ch); i++ }
            }
        }
    }

    private fun expandExpression(expr: String, vars: UriTemplateVars): String {
        val opChar = if (expr.isNotEmpty() && expr[0] in OPS) expr[0] else ' '
        val spec   = OPS[opChar]!!
        val varList = if (opChar == ' ') expr else expr.substring(1)

        val parts = mutableListOf<String>()
        for (varSpec in varList.split(',')) {
            val (name, modifier) = parseVarSpec(varSpec.trim())
            val value = vars.vars[name] ?: continue

            val expanded = when (value) {
                is UriTemplateVars.Value.Str -> expandString(name, value.v, modifier, spec)
                is UriTemplateVars.Value.Lst -> expandList(name, value.v, modifier, spec)
                is UriTemplateVars.Value.Mp  -> expandMap(name, value.v, modifier, spec)
            }
            if (expanded != null) parts.add(expanded)
        }

        if (parts.isEmpty()) return ""
        return spec.prefix + parts.joinToString(spec.sep)
    }

    private data class VarSpec(val name: String, val modifier: Modifier)
    private sealed interface Modifier {
        object None : Modifier
        object Explode : Modifier
        data class Prefix(val len: Int) : Modifier
    }

    private fun parseVarSpec(spec: String): VarSpec {
        return when {
            spec.endsWith('*') -> VarSpec(spec.dropLast(1), Modifier.Explode)
            ':' in spec -> {
                val idx = spec.lastIndexOf(':')
                val len = spec.substring(idx + 1).toIntOrNull() ?: 0
                VarSpec(spec.substring(0, idx), Modifier.Prefix(len))
            }
            else -> VarSpec(spec, Modifier.None)
        }
    }

    private fun expandString(name: String, value: String, modifier: Modifier, spec: OpSpec): String? {
        val v = when (modifier) {
            is Modifier.Prefix -> value.take(modifier.len)
            else               -> value
        }
        val encoded = UriTemplateEncoder.encode(v, spec.allowReserved)
        return if (spec.named) {
            if (encoded.isEmpty()) "$name${spec.ifEmpty}" else "$name=$encoded"
        } else {
            encoded
        }
    }

    private fun expandList(name: String, list: List<String>, modifier: Modifier, spec: OpSpec): String? {
        if (list.isEmpty()) return null
        return when (modifier) {
            is Modifier.Explode -> {
                val items = list.map { v ->
                    val enc = UriTemplateEncoder.encode(v, spec.allowReserved)
                    if (spec.named) "$name=$enc" else enc
                }
                items.joinToString(spec.sep)
            }
            else -> {
                val encoded = list.joinToString(",") { UriTemplateEncoder.encode(it, spec.allowReserved) }
                if (spec.named) {
                    if (encoded.isEmpty()) "$name${spec.ifEmpty}" else "$name=$encoded"
                } else encoded
            }
        }
    }

    private fun expandMap(name: String, map: Map<String, String>, modifier: Modifier, spec: OpSpec): String? {
        if (map.isEmpty()) return null
        return when (modifier) {
            is Modifier.Explode -> {
                map.entries.joinToString(spec.sep) { (k, v) ->
                    "${UriTemplateEncoder.encode(k, spec.allowReserved)}=${UriTemplateEncoder.encode(v, spec.allowReserved)}"
                }
            }
            else -> {
                val pairs = map.entries.flatMap { (k, v) ->
                    listOf(
                        UriTemplateEncoder.encode(k, spec.allowReserved),
                        UriTemplateEncoder.encode(v, spec.allowReserved),
                    )
                }.joinToString(",")
                if (spec.named) {
                    if (pairs.isEmpty()) "$name${spec.ifEmpty}" else "$name=$pairs"
                } else pairs
            }
        }
    }
}
