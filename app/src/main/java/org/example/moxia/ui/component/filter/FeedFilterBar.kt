package org.example.moxia.ui.component.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.example.moxia.R
import org.example.moxia.model.MemoContentType
import org.example.moxia.model.Shortcut
import org.example.moxia.model.filter.MemoFilter
import org.example.moxia.model.filter.MemoSort
import org.example.moxia.ui.theme.AppTextButton
import org.example.moxia.ui.theme.MiuixTokens

/**
 * Fixed height of [FeedFilterBar]. The list's content padding has to reserve exactly this much
 * while a filter is active, so the bar keeps its own height instead of being measured: measuring
 * would leave the first card one frame too high after every apply.
 */
val FeedFilterBarHeight: Dp = 52.dp

/** One applied condition, rendered as a removable token. */
data class FeedFilterToken(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val kind: Kind,
    /** Tag name (without `#`) for [Kind.TAG] tokens. */
    val tag: String? = null,
    /** Memo kind for [Kind.TYPE] tokens. */
    val type: MemoContentType? = null
) {
    enum class Kind { VIEW, TAG, DATE, SORT, TYPE }
}

/**
 * Turns the structured filter into the summary bar's tokens. [view] is the saved view the filter
 * was built from, when it is still in the shortcut list; [fallbackLabel] covers a persisted view
 * that the server no longer returns. [dateLabel], [sortLabel] and [typeLabels] are what the UI can
 * render: null (or a missing entry) simply omits that token. The space the feed is scoped to is
 * named by the header switcher, so it is not repeated in the summary bar.
 */
fun memoFilterTokens(
    filter: MemoFilter,
    view: Shortcut?,
    fallbackLabel: String,
    dateLabel: String?,
    sortLabel: String?,
    typeLabels: Map<MemoContentType, String>
): List<FeedFilterToken> = buildList {
    if (filter.hasLegacyClause) {
        val label = view?.title?.takeIf { it.isNotBlank() }
            ?: filter.viewName?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: fallbackLabel
        add(
            FeedFilterToken(
                id = "view",
                label = label,
                icon = Icons.Outlined.Bookmarks,
                kind = FeedFilterToken.Kind.VIEW
            )
        )
    }
    filter.tags.sorted().forEach { tag ->
        add(
            FeedFilterToken(
                id = "tag:$tag",
                label = "#$tag",
                icon = Icons.Outlined.Tag,
                kind = FeedFilterToken.Kind.TAG,
                tag = tag
            )
        )
    }
    if (filter.date != null && dateLabel != null) {
        add(
            FeedFilterToken(
                id = "date",
                label = dateLabel,
                icon = Icons.Outlined.CalendarMonth,
                kind = FeedFilterToken.Kind.DATE
            )
        )
    }
    if (filter.sort != MemoSort.PINNED_DESC && sortLabel != null) {
        add(
            FeedFilterToken(
                id = "sort",
                label = sortLabel,
                icon = Icons.AutoMirrored.Outlined.Sort,
                kind = FeedFilterToken.Kind.SORT
            )
        )
    }
    filter.types.sortedBy { it.ordinal }.forEach { type ->
        add(
            FeedFilterToken(
                id = "type:${type.name}",
                label = typeLabels[type] ?: type.name,
                icon = when (type) {
                    MemoContentType.LINK -> Icons.Outlined.Link
                    MemoContentType.CODE -> Icons.Outlined.Code
                    MemoContentType.TODO -> Icons.Outlined.TaskAlt
                },
                kind = FeedFilterToken.Kind.TYPE,
                type = type
            )
        )
    }
}

/**
 * The active-filter summary that stays pinned under the feed's chrome. The chips it replaces
 * lived inside the scrolling list header, so once they scrolled away nothing on screen said the
 * feed was filtered. Tokens are the only place a condition can be removed: the picker chips no
 * longer carry their own close button.
 */
@Composable
fun FeedFilterBar(
    tokens: List<FeedFilterToken>,
    onRemove: (FeedFilterToken) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(FeedFilterBarHeight)
            // No container colour: the bar lives inside the chrome surface, which already paints
            // either the page background or the frosted backdrop.
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(tokens, key = { it.id }) { token ->
                FeedFilterTokenChip(token = token, onClick = { onRemove(token) })
            }
        }
        // The token row scrolls; "clear" must not: it is the way out when the feed looks empty.
        AppTextButton(
            text = stringResource(R.string.memo_filter_clear),
            onClick = onClearAll
        )
    }
}

@Composable
private fun FeedFilterTokenChip(token: FeedFilterToken, onClick: () -> Unit) {
    val removeLabel = stringResource(R.string.memo_filter_remove)
    Row(
        modifier = Modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(MiuixTokens.PillCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                onClickLabel = removeLabel,
                role = Role.Button,
                onClick = onClick
            )
            .padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = token.icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = token.label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
