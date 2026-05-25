package com.helpchoice.nahal.haldish.uritemplate

import com.helpchoice.nahal.haldish.model.HalLink

class UriTemplate(val template: String) {

    fun expand(vars: UriTemplateVars): String =
        UriTemplateExpander.expand(template, vars)

    fun expand(vararg pairs: Pair<String, Any>): String =
        expand(UriTemplateVars.of(*pairs))

    fun isTemplated(): Boolean = '{' in template

    companion object {
        fun of(template: String): UriTemplate = UriTemplate(template)
    }
}

fun HalLink.expandHref(vars: UriTemplateVars): String =
    if (templated) UriTemplate(href).expand(vars) else href

fun HalLink.expandHref(vararg pairs: Pair<String, Any>): String =
    expandHref(UriTemplateVars.of(*pairs))
