package com.helpchoice.nahal.haldish.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One hop in a [ResourcePath] from a root [HalDocument] to a link or property that yields a URL.
 *
 * A valid path follows the grammar `Item? Embedded* (Link | Property+)`:
 *  - an optional leading [Item] (a top-level array response),
 *  - a chain of [Embedded] descents,
 *  - then a terminal — either a single [Link] or a chain of [Property] names.
 *
 * A path with no terminal step resolves to the reached resource's `self` link.
 */
sealed interface PathStep {
    /** Descend into a top-level array item (`HalDocument.items[index]`). Only valid as the first step. */
    data class Item(val index: Int) : PathStep

    /** Descend into an embedded sub-resource: `_embedded[rel][index]`. */
    data class Embedded(val rel: String, val index: Int = 0) : PathStep

    /** Terminal: a link in `_links[rel][index]`. */
    data class Link(val rel: String, val index: Int = 0) : PathStep

    /**
     * Terminal chain: a (possibly nested) property whose string value is the URL.
     * `Property("data"), Property("url")` addresses `$.data.url`.
     */
    data class Property(val name: String) : PathStep
}

/**
 * The result of resolving a [ResourcePath] against a root [HalDocument]:
 * the target [link] (real, synthesized from a property, or the reached resource's `self`),
 * the [container] document that directly holds it, and the ordered chain of [documents]
 * from root to container (inclusive).
 */
data class ResolvedTarget(
    val link: HalLink,
    val container: HalDocument,
    val documents: List<HalDocument>,
)

/**
 * An addressable path from a root [HalDocument] to a link or property that produces a URL.
 * See [PathStep] for the grammar. The relation name a plugin sees is [terminalRel].
 */
data class ResourcePath(val steps: List<PathStep>) {

    init {
        var i = 0
        if (i < steps.size && steps[i] is PathStep.Item) i++
        while (i < steps.size && steps[i] is PathStep.Embedded) i++
        // Remaining steps must be the terminal: a single Link, or one-or-more Property.
        val terminal = steps.drop(i)
        val valid = when {
            terminal.isEmpty() -> true
            terminal.size == 1 && terminal[0] is PathStep.Link -> true
            terminal.all { it is PathStep.Property } -> true
            else -> false
        }
        require(valid) {
            "Invalid ResourcePath: expected Item? Embedded* (Link | Property+), got $steps"
        }
        require(steps.count { it is PathStep.Item } <= 1) { "ResourcePath allows at most one Item step: $steps" }
    }

    /** Steps that descend into sub-resources ([PathStep.Item] / [PathStep.Embedded]). */
    private val prefix: List<PathStep> get() = steps.takeWhile { it is PathStep.Item || it is PathStep.Embedded }

    /** Terminal steps ([PathStep.Link] or [PathStep.Property] chain); empty means "self". */
    private val terminal: List<PathStep> get() = steps.drop(prefix.size)

    /** The relation name handed to plugins: the link rel, the last property name, or `self`. */
    val terminalRel: String
        get() = when (val last = terminal.lastOrNull()) {
            is PathStep.Link -> last.rel
            is PathStep.Property -> last.name
            else -> SELF_REL
        }

    /** Documents from [root] down to the container of the terminal (inclusive), or empty if the path is broken. */
    fun documentsToContainer(root: HalDocument): List<HalDocument> {
        val docs = mutableListOf(root)
        var current = root
        for (step in prefix) {
            current = when (step) {
                is PathStep.Item -> current.items.getOrNull(step.index) ?: return emptyList()
                is PathStep.Embedded -> current.embedded(step.rel).getOrNull(step.index) ?: return emptyList()
                else -> return emptyList()
            }
            docs += current
        }
        return docs
    }

    /**
     * Resolves this path against [root], producing the target link and its document context,
     * or `null` when any hop, link, or property is missing.
     */
    fun resolve(root: HalDocument): ResolvedTarget? {
        val docs = documentsToContainer(root)
        if (docs.isEmpty()) return null
        val container = docs.last()

        val link: HalLink? = when (val head = terminal.firstOrNull()) {
            null -> container.link(SELF_REL)
            is PathStep.Link -> container.links(head.rel).getOrNull(head.index)
            is PathStep.Property -> resolveProperty(container)
            else -> null
        }
        return link?.let { ResolvedTarget(it, container, docs) }
    }

    /** Walks the [PathStep.Property] chain into [container]'s JSON properties and builds a link from the string value. */
    private fun resolveProperty(container: HalDocument): HalLink? {
        val names = terminal.filterIsInstance<PathStep.Property>().map { it.name }
        var element: JsonElement = container.properties[names.first()] ?: return null
        for (name in names.drop(1)) {
            val obj: Map<String, JsonElement> = element as? JsonObject ?: return null
            element = obj[name] ?: return null
        }
        val href = (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        return HalLink(href = href, templated = '{' in href)
    }

    companion object {
        const val SELF_REL = "self"

        /** A top-level link: `_links[rel][index]`. */
        fun link(rel: String, index: Int = 0): ResourcePath =
            ResourcePath(listOf(PathStep.Link(rel, index)))

        /** The `self` link of the reached resource (empty terminal). */
        fun self(): ResourcePath = ResourcePath(emptyList())
    }
}
