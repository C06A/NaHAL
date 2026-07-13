package com.helpchoice.nahal.ui.state

import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.PathStep
import com.helpchoice.nahal.haldish.model.ResourcePath
import com.helpchoice.nahal.ui.model.HistoryNode

/** A [ResourcePath] and the fetched document it is resolved against. */
data class RootedPath(val path: ResourcePath, val rootDocument: HalDocument)

/**
 * Builds the path to [terminal] (a link or property chain inside [node]'s document) as addressed
 * from the nearest *fetched* ancestor of [node].
 *
 * Embedded resources and array items are opened without an HTTP request, so their documents are
 * fragments of an ancestor's. Rooting the path at that ancestor — rather than at the fragment —
 * is what lets a plugin walk the embedding stack (e.g. to find a `CURIE` prefix defined in the
 * parent document) via `path.documentsToContainer(rootDocument)`.
 *
 * Returns null when the ancestor chain cannot be expressed as a [ResourcePath] — the grammar
 * (`Item? Embedded* (Link | Property+)`) allows an [PathStep.Item] only at the head, so opening
 * an array item *inside* an embedded resource has no representation. Callers fall back to
 * addressing [terminal] against [node]'s own document, which loses ancestor context but still
 * resolves any absolute URL.
 */
fun resolveNodePath(
    history: List<HistoryNode>,
    node: HistoryNode,
    terminal: List<PathStep>,
): RootedPath? {
    val prefix = ArrayDeque<PathStep>()
    var current: HistoryNode? = node
    while (current?.originStep != null) {
        prefix.addFirst(current.originStep!!)
        val parentId = current.parentId ?: return null
        current = history.firstOrNull { it.id == parentId } ?: return null
    }
    val root = current?.response?.document ?: return null
    val path = runCatching { ResourcePath(prefix.toList() + terminal) }.getOrNull() ?: return null
    return RootedPath(path, root)
}

/** Renders a property terminal the way a reader writes it: `items[0].url`, `mirrors[1]`. */
fun List<PathStep.Property>.jsonPathLabel(): String =
    joinToString(".") { step -> step.name + (step.index?.let { "[$it]" } ?: "") }
