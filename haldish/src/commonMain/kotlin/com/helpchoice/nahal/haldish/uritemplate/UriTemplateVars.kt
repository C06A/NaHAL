package com.helpchoice.nahal.haldish.uritemplate

class UriTemplateVars {

    internal sealed interface Value {
        data class Str(val v: String) : Value
        data class Lst(val v: MutableList<String> = mutableListOf()) : Value
        data class Mp(val v: MutableMap<String, String> = mutableMapOf()) : Value
    }

    internal val vars: MutableMap<String, Value> = mutableMapOf()

    private fun Any?.toStr(): String = this?.toString() ?: ""

    fun set(name: String, value: Any): UriTemplateVars {
        vars[name] = Value.Str(value.toStr())
        return this
    }

    fun set(name: String, value: List<*>): UriTemplateVars {
        vars[name] = Value.Lst(value.map { it.toStr() }.toMutableList())
        return this
    }

    fun set(name: String, value: Map<String, *>): UriTemplateVars {
        vars[name] = Value.Mp(value.mapValues { (_, v) -> v.toStr() }.toMutableMap())
        return this
    }

    fun add(name: String, value: Any): UriTemplateVars {
        val existing = vars[name]
        val list = when (existing) {
            is Value.Lst -> existing.v
            is Value.Str -> mutableListOf(existing.v).also { vars[name] = Value.Lst(it) }
            else         -> mutableListOf<String>().also { vars[name] = Value.Lst(it) }
        }
        list.add(value.toStr())
        return this
    }

    fun put(name: String, field: String, value: Any): UriTemplateVars {
        val existing = vars[name]
        val map = when (existing) {
            is Value.Mp -> existing.v
            else        -> mutableMapOf<String, String>().also { vars[name] = Value.Mp(it) }
        }
        map[field] = value.toStr()
        return this
    }

    companion object {
        fun of(vararg pairs: Pair<String, Any>): UriTemplateVars =
            UriTemplateVars().apply { pairs.forEach { (k, v) -> set(k, v) } }

        fun parse(bindings: List<String>): UriTemplateVars {
            val vars = UriTemplateVars()
            for (binding in bindings) {
                val eq = binding.indexOf('=')
                if (eq < 0) continue
                val lhs = binding.substring(0, eq)
                val value = binding.substring(eq + 1)
                when {
                    lhs.endsWith("[]") -> vars.add(lhs.dropLast(2), value)
                    lhs.endsWith("]")  -> {
                        val bracket = lhs.lastIndexOf('[')
                        if (bracket >= 0) {
                            val name  = lhs.substring(0, bracket)
                            val field = lhs.substring(bracket + 1, lhs.length - 1)
                            vars.put(name, field, value)
                        }
                    }
                    else -> vars.set(lhs, value)
                }
            }
            return vars
        }
    }
}
