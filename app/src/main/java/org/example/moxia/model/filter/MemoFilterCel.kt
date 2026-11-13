package org.example.moxia.model.filter

import org.example.moxia.model.MemoContentType

/**
 * Compiles [this] filter into the server's CEL dialect, ANDed with the creator clause that scopes
 * the feed to the signed-in user. Returns null when nothing would be filtered, which keeps the
 * request indistinguishable from an unfiltered feed.
 */
fun MemoFilter.toCel(creatorFilter: String? = null): String? {
    val clauses = buildList {
        creatorFilter?.takeIf { it.isNotBlank() }?.let(::add)
        legacyCel.filter { it.isNotBlank() }.forEach(::add)
        tagClause()?.let(::add)
        date?.toCelClause()?.let(::add)
        typesClause()?.let(::add)
        spaceClause()?.let(::add)
    }
    return clauses.joinToString(" && ").takeIf { it.isNotBlank() }
}

/**
 * One clause per selected tag path, ORed for [TagMatch.ANY]. A path also covers its subtree
 * (`type` matches `type/book`), which is what the collapsible tree in the panel shows: a branch's
 * count includes its children, so selecting it has to include them too. The `tags` list is used
 * rather than the scalar `tag` variable because a memo carries several tags.
 *
 * CEL's `exists` macro and the standard `startsWith` string function do the subtree test; both are
 * part of the CEL environment the server compiles filters with.
 */
private fun MemoFilter.tagClause(): String? {
    if (tags.isEmpty()) return null
    val clauses = tags.sorted().map { path ->
        val exact = "${celString(path)} in tags"
        val subtree = "tags.exists(t, t.startsWith(${celString("$path/")}))"
        "($exact || $subtree)"
    }
    return when (tagMatch) {
        TagMatch.ALL -> clauses.joinToString(" && ")
        TagMatch.ANY -> if (clauses.size == 1) clauses.single() else clauses.joinToString(" || ")
    }
}

private fun MemoDateRange.toCelClause(): String? {
    val parts = buildList {
        if (startMillis > 0L) add("created_ts >= ${startMillis / 1000}")
        if (endMillisExclusive != MemoDateRange.UNBOUNDED) {
            add("created_ts < ${endMillisExclusive / 1000}")
        }
    }
    return parts.joinToString(" && ").takeIf { it.isNotBlank() }
}

/**
 * The server has first-class clauses for links and task lists. Code has none, so the closest
 * approximation is a backtick anywhere in the content (fenced or inline), which is also what the
 * local matcher treats as code. A memo whose type the clauses cannot see exactly is still picked
 * out locally once cached, so the difference only shows for rows the device has never loaded.
 */
private fun MemoFilter.typesClause(): String? {
    if (types.isEmpty()) return null
    val clauses = types.sortedBy { it.ordinal }.map { it.toCelClause() }
    return if (clauses.size == 1) clauses.single() else clauses.joinToString(" || ", "(", ")")
}

private fun MemoContentType.toCelClause(): String = when (this) {
    MemoContentType.LINK -> "has_link"
    MemoContentType.TODO -> "has_task_list"
    MemoContentType.CODE -> "content.contains(${celString("`")})"
}

/**
 * The space clause. The server only accepts a non-negated equality on `space`, so a feed can be
 * scoped to one space but never to "every space but this one".
 */
private fun MemoFilter.spaceClause(): String? {
    val target = space?.takeIf { it.isNotBlank() } ?: return null
    return "space == ${celString(target)}"
}

/**
 * A CEL string literal. Tags come from user content, so a quote or a backslash inside one used to
 * break the whole expression and the feed failed with a generic load error.
 */
fun celString(value: String): String {
    val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}
