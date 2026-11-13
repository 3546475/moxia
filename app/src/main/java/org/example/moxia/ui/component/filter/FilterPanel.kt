package org.example.moxia.ui.component.filter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import org.example.moxia.R
import org.example.moxia.model.MemoContentType
import org.example.moxia.model.Shortcut
import org.example.moxia.model.filter.MemoDateRange
import org.example.moxia.model.filter.MemoFilter
import org.example.moxia.model.filter.MemoSort
import org.example.moxia.model.filter.TagMatch
import org.example.moxia.model.filter.normalizeTag
import org.example.moxia.model.filter.toCel
import org.example.moxia.ui.component.TagNode
import org.example.moxia.ui.component.buildTagTree
import org.example.moxia.ui.component.collapsibleTagPaths
import org.example.moxia.ui.component.flattenTagTree
import org.example.moxia.ui.theme.AppButton
import org.example.moxia.ui.theme.AppDialog
import org.example.moxia.ui.theme.AppFilterChip
import org.example.moxia.ui.theme.AppRadioButton
import org.example.moxia.ui.theme.AppTextField
import org.example.moxia.ui.theme.MiuixTokens

/** The two pages of the filter panel. */
private enum class FilterPage(val labelRes: Int) {
    CONDITIONS(R.string.memo_filter_page_conditions),
    TAGS(R.string.memo_filter_section_tags)
}

/**
 * Height of the swipeable pages. The pages plus the switcher add up to the 420dp the single
 * list used to take, so the dialog does not change size when the panel is opened.
 */
private val FilterContentHeight = 376.dp

/** Height of the page switcher above the pages. */
private val FilterTabsHeight = 44.dp

/**
 * The page switcher above the filter pages. Plain text with a short underline, the same quiet
 * treatment the feed switcher uses: a full tab bar would compete with the dialog title.
 */
@Composable
private fun FilterPageTabs(
    pages: List<FilterPage>,
    currentPage: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(FilterTabsHeight)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        pages.forEachIndexed { index, page ->
            val selected = index == currentPage
            val contentColor by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "filterTabContent"
            )
            val indicatorAlpha by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                label = "filterTabIndicator"
            )
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = stringResource(page.labelRes)
                    ) { onSelect(index) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(page.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = contentColor
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .alpha(indicatorAlpha)
                        .width(16.dp)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
    }
}

/**
 * The structured filter panel: saved views, tags (AND/OR), the memo kind, a created-time window
 * and the feed order. It edits a draft and only commits on "apply", so toggling several
 * conditions costs one request instead of one per tap.
 */
@Composable
fun FilterPanel(
    show: Boolean,
    filter: MemoFilter,
    shortcuts: List<Shortcut>,
    tagCounts: Map<String, Int>,
    onDismiss: () -> Unit,
    onApply: (MemoFilter) -> Unit,
    onManageShortcuts: () -> Unit,
    onSaveAsView: (String) -> Unit,
    onEditShortcut: (Shortcut) -> Unit
) {
    // Reopening the panel starts from what is actually applied; closing throws the draft away.
    var draft by remember(show, filter) { mutableStateOf(filter) }
    var tagQuery by remember(show) { mutableStateOf("") }
    // The tag search is a tool, not the resting view: as a permanent full-width field it was the
    // largest block of the panel even when nobody was searching.
    var tagSearchOpen by remember(show) { mutableStateOf(false) }
    // The custom window is picked in one calendar dialog instead of two chained Material pickers.
    var showDatePicker by remember(show) { mutableStateOf(false) }
    val today = remember(show) { LocalDate.now() }
    val zone = remember(show) { ZoneId.systemDefault() }
    val locale = remember(show) { Locale.getDefault() }

    val counts = remember(tagCounts) {
        tagCounts.entries.associate { (tag, count) -> normalizeTag(tag) to count }
    }
    // The tree is built from the known tags plus whatever is already selected, so a restored
    // filter never loses a row just because the stats snapshot has not seen that tag yet.
    val treeCounts = remember(counts, draft.tags) {
        counts.toMutableMap().apply {
            draft.tags.filter { it.isNotBlank() }.forEach { tag -> putIfAbsent(tag, 0) }
        }
    }
    val tagTree = remember(treeCounts) { buildTagTree(treeCounts) }
    var collapsedTags by remember(show) { mutableStateOf(emptySet<String>()) }
    val collapsibleTags = remember(tagTree) { collapsibleTagPaths(tagTree) }
    val filteredTagTree = remember(tagTree, tagQuery) { filterTagTree(tagTree, tagQuery) }
    val tagRows = remember(filteredTagTree, collapsedTags, tagQuery) {
        // While searching every surviving branch is expanded, so a hit is never hidden behind a
        // collapsed ancestor.
        flattenTagTree(
            filteredTagTree,
            if (tagQuery.isBlank()) collapsedTags else emptySet()
        )
    }
    val compiledCel = draft.toCel(null)
    val datePresets = remember(today, zone, locale) {
        FilterDatePreset.entries.associateWith { it.range(today, zone, locale) }
    }
    // The panel is split into two pages that the user swipes between, so the pager lives with
    // the dialog instead of with the feed it filters.
    val pagerState = rememberPagerState(pageCount = { FilterPage.entries.size })
    val scope = rememberCoroutineScope()

    AppDialog(
        show = show,
        title = stringResource(R.string.memo_filter_title),
        onDismissRequest = onDismiss,
        buttons = {
            // Applying is the one action that carries the accent; reset stays a quiet secondary
            // fill. As two equal text buttons neither of them read as the primary action.
            // The space is where the feed is scoped, not a condition: resetting the conditions
            // leaves the user inside the space they are browsing.
            AppButton(
                onClick = { draft = MemoFilter(space = filter.space) },
                modifier = Modifier.weight(1f),
                secondary = true
            ) {
                Text(stringResource(R.string.memo_filter_reset))
            }
            AppButton(
                onClick = { onApply(draft) },
                modifier = Modifier.weight(2f)
            ) {
                Text(
                    // The parenthesised number used to read like a result count; it is the number
                    // of conditions that will be applied.
                    if (draft.activeCount > 0) {
                        pluralStringResource(
                            R.plurals.memo_filter_apply_count,
                            draft.activeCount,
                            draft.activeCount
                        )
                    } else {
                        stringResource(R.string.memo_filter_apply_plain)
                    }
                )
            }
        }
    ) {
        // One long list hid the other conditions behind the tag tree. Two swipeable pages
        // keep every condition reachable, and the strip above says which page is open.
        FilterPageTabs(
            pages = FilterPage.entries,
            currentPage = pagerState.currentPage,
            onSelect = { page -> scope.launch { pagerState.animateScrollToPage(page) } }
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(FilterContentHeight),
            key = { FilterPage.entries[it] }
        ) { page ->
            when (FilterPage.entries[page]) {
                FilterPage.CONDITIONS -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item(key = "views") {
                        Column(Modifier.padding(top = 2.dp)) {
                            SectionHeader(
                                title = stringResource(R.string.memo_filter_section_views),
                                trailing = {
                                    // Saving and managing views belong to the views list; in a footer below
                                    // the fold nobody ever found them.
                                    IconButton(
                                        onClick = { compiledCel?.let(onSaveAsView) },
                                        enabled = compiledCel != null
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.BookmarkAdd,
                                            contentDescription = stringResource(R.string.memo_filter_save_view),
                                            modifier = Modifier.size(MiuixTokens.IconSmall)
                                        )
                                    }
                                    IconButton(onClick = onManageShortcuts) {
                                        Icon(
                                            imageVector = Icons.Outlined.Tune,
                                            contentDescription = stringResource(R.string.memo_filter_manage),
                                            modifier = Modifier.size(MiuixTokens.IconSmall)
                                        )
                                    }
                                }
                            )
                            ChoiceRow(
                                selected = !draft.hasLegacyClause,
                                label = stringResource(R.string.memo_filter_all),
                                onClick = { draft = draft.copy(viewName = null, legacyCel = emptyList()) }
                            )
                            shortcuts.forEach { shortcut ->
                                val cel = shortcut.filter?.trim().orEmpty()
                                ChoiceRow(
                                    selected = draft.viewName != null && draft.viewName == shortcut.name,
                                    label = shortcut.title.orEmpty(),
                                    onClick = {
                                        // A view is a preset: picking it replaces the base clause and any
                                        // facets added on top of it. The sort is a preference, not part of
                                        // the view, so it survives.
                                        draft = if (cel.isEmpty()) {
                                            draft.copy(viewName = null, legacyCel = emptyList())
                                        } else {
                                            MemoFilter(
                                                viewName = shortcut.name,
                                                legacyCel = listOf(cel),
                                                sort = draft.sort
                                            )
                                        }
                                    },
                                    trailing = {
                                        IconButton(onClick = { onEditShortcut(shortcut) }) {
                                            Icon(
                                                imageVector = Icons.Outlined.Edit,
                                                contentDescription = stringResource(R.string.memo_filter_edit_raw),
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                )
                            }
                            if (shortcuts.isEmpty()) {
                                Hint(stringResource(R.string.memo_filter_none))
                            }
                        }
                    }

                    item(key = "types") {
                        Column(Modifier.padding(top = SectionGap)) {
                            SectionHeader(stringResource(R.string.memo_filter_section_types))
                            ChipFlow {
                                MemoContentType.entries.forEach { type ->
                                    AppFilterChip(
                                        selected = type in draft.types,
                                        onClick = {
                                            draft = draft.copy(
                                                types = if (type in draft.types) {
                                                    draft.types - type
                                                } else {
                                                    draft.types + type
                                                }
                                            )
                                        }
                                    ) {
                                        Text(type.typeLabel())
                                    }
                                }
                            }
                        }
                    }

                    item(key = "date") {
                        Column(Modifier.padding(top = SectionGap)) {
                            SectionHeader(stringResource(R.string.memo_filter_section_date))
                            val customRange = draft.date?.takeIf {
                                filterDatePresetOf(it, today, zone, locale) == null
                            }
                            ChipFlow {
                                FilterDatePreset.entries.forEach { preset ->
                                    val range = datePresets[preset]
                                    AppFilterChip(
                                        selected = draft.date == range,
                                        onClick = { draft = draft.copy(date = range) }
                                    ) {
                                        Text(preset.dateLabel())
                                    }
                                }
                                // The picked window becomes the chip's own label: the extra grey line that
                                // used to spell it out below the row is gone.
                                AppFilterChip(
                                    selected = customRange != null,
                                    onClick = { showDatePicker = true }
                                ) {
                                    Text(
                                        customRange?.chipLabel(zone)
                                            ?: stringResource(R.string.memo_filter_date_custom)
                                    )
                                }
                            }
                        }
                    }

                    item(key = "sort") {
                        Column(Modifier.padding(top = SectionGap)) {
                            SectionHeader(stringResource(R.string.memo_filter_section_sort))
                            ChipFlow {
                                MemoSort.entries.forEach { sort ->
                                    AppFilterChip(
                                        selected = draft.sort == sort,
                                        onClick = { draft = draft.copy(sort = sort) }
                                    ) {
                                        Text(sort.sortLabel())
                                    }
                                }
                            }
                        }
                    }
                }

                FilterPage.TAGS -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item(key = "tags-header") {
                        Column(Modifier.padding(top = SectionGap)) {
                            SectionHeader(
                                // The tab strip above already names this page, so the
                                // heading keeps only the tools that act on the list.
                                title = "",
                                trailing = {
                                    IconButton(onClick = {
                                        tagSearchOpen = !tagSearchOpen
                                        if (!tagSearchOpen) tagQuery = ""
                                    }) {
                                        Icon(
                                            imageVector = if (tagSearchOpen) {
                                                Icons.Outlined.Close
                                            } else {
                                                Icons.Outlined.Search
                                            },
                                            contentDescription = stringResource(
                                                if (tagSearchOpen) {
                                                    R.string.common_close
                                                } else {
                                                    R.string.memo_filter_tag_search
                                                }
                                            ),
                                            modifier = Modifier.size(MiuixTokens.IconSmall)
                                        )
                                    }
                                    if (collapsibleTags.isNotEmpty()) {
                                        val allCollapsed = collapsedTags.containsAll(collapsibleTags)
                                        IconButton(onClick = {
                                            collapsedTags = if (allCollapsed) emptySet() else collapsibleTags
                                        }) {
                                            Icon(
                                                imageVector = if (allCollapsed) {
                                                    Icons.Outlined.UnfoldMore
                                                } else {
                                                    Icons.Outlined.UnfoldLess
                                                },
                                                contentDescription = stringResource(
                                                    if (allCollapsed) {
                                                        R.string.tags_expand_all
                                                    } else {
                                                        R.string.tags_collapse_all
                                                    }
                                                ),
                                                modifier = Modifier.size(MiuixTokens.IconSmall)
                                            )
                                        }
                                    }
                                }
                            )
                            // The AND/OR switch is a row of its own: as chips beside the heading it stole
                            // the heading's line and left both looking cramped.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.memo_filter_tag_match),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                AppFilterChip(
                                    selected = draft.tagMatch == TagMatch.ALL,
                                    onClick = { draft = draft.copy(tagMatch = TagMatch.ALL) }
                                ) {
                                    Text(stringResource(R.string.memo_filter_tags_all))
                                }
                                Spacer(Modifier.width(8.dp))
                                AppFilterChip(
                                    selected = draft.tagMatch == TagMatch.ANY,
                                    onClick = { draft = draft.copy(tagMatch = TagMatch.ANY) }
                                ) {
                                    Text(stringResource(R.string.memo_filter_tags_any))
                                }
                            }
                        }
                    }

                    item(key = "tags-search") {
                        // A permanent item whose content expands: inserting and removing the item itself
                        // made the field pop open instead of growing.
                        AnimatedVisibility(
                            visible = tagSearchOpen,
                            enter = expandVertically(animationSpec = tween(180)) + fadeIn(tween(120)),
                            exit = shrinkVertically(animationSpec = tween(150)) + fadeOut(tween(100))
                        ) {
                            AppTextField(
                                value = tagQuery,
                                onValueChange = { tagQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, top = 8.dp, end = 16.dp),
                                placeholder = stringResource(R.string.memo_filter_tag_search),
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }

                    if (tagRows.isEmpty()) {
                        item(key = "tags-empty") {
                            Hint(
                                if (treeCounts.isEmpty()) {
                                    stringResource(R.string.memo_filter_no_tags)
                                } else {
                                    stringResource(R.string.memo_filter_no_match)
                                }
                            )
                        }
                    } else {
                        items(tagRows, key = { "tag:${it.path}" }) { node ->
                            TagTreeRow(
                                node = node,
                                selected = node.path in draft.tags,
                                descendantSelected = hasSelectedDescendant(draft.tags, node.path),
                                expanded = node.path !in collapsedTags,
                                onToggleSelect = {
                                    draft = draft.copy(
                                        tags = if (node.path in draft.tags) {
                                            draft.tags - node.path
                                        } else {
                                            draft.tags + node.path
                                        }
                                    )
                                },
                                onToggleExpand = {
                                    collapsedTags = if (node.path in collapsedTags) {
                                        collapsedTags - node.path
                                    } else {
                                        collapsedTags + node.path
                                    }
                                },
                                // Folding a branch or filtering the list animates the rows into their new
                                // places instead of re-drawing the pane.
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                }
            }
        }

        // The custom window is picked in the app's own calendar: Material's picker renders a broken
        // Chinese weekday row under core library desugaring (seven "星"), and one dialog can carry
        // both ends instead of chaining two pickers.
        if (showDatePicker) {
            val current = draft.date?.toLocalDates(zone)
            AppCalendarDialog(
                title = stringResource(R.string.memo_filter_date_range_title),
                initialStart = current?.first,
                initialEnd = current?.second,
                rangeSelection = true,
                zone = zone,
                onDismissRequest = { showDatePicker = false },
                onClear = {
                    draft = draft.copy(date = null)
                    showDatePicker = false
                },
                onConfirm = { start, end ->
                    draft = draft.copy(date = localDatesToRange(start, end, zone))
                    showDatePicker = false
                }
            )
        }
    }
}

@Composable
fun MemoSort.sortLabel(): String = stringResource(
    when (this) {
        MemoSort.PINNED_DESC -> R.string.memo_filter_sort_pinned
        MemoSort.NEWEST -> R.string.memo_filter_sort_newest
        MemoSort.OLDEST -> R.string.memo_filter_sort_oldest
    }
)

@Composable
private fun MemoContentType.typeLabel(): String = stringResource(
    when (this) {
        MemoContentType.LINK -> R.string.profile_stats_links
        MemoContentType.CODE -> R.string.profile_stats_code
        MemoContentType.TODO -> R.string.profile_stats_todo
    }
)

/** Vertical gap between sections; the lazy list already contributes 2dp between items. */
private val SectionGap = 8.dp

/**
 * A section heading. Deliberately quiet: the accent belongs to selected chips and rows, so
 * headings used to compete with them for the same blue and the panel lost its hierarchy. Every
 * heading keeps the same height whether or not it carries trailing actions, so the sections read
 * as a rhythm instead of a ragged stack.
 */
@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            // end = 2dp puts the trailing icon glyphs on the same 16dp inset as the chips: an
            // IconButton carries 14dp of its own padding.
            .padding(start = 16.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke(this)
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 2.dp)
    )
}

/**
 * Chips wrap instead of scrolling sideways: every option stays visible, and the last one can no
 * longer be clipped by the dialog edge the way the custom date was.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

/** "10-01 – 10-25": a window short enough to live inside its own chip. */
private fun MemoDateRange.chipLabel(zone: ZoneId): String {
    val formatter = DateTimeFormatter.ofPattern("MM-dd")
    val start = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
    val end = if (endMillisExclusive == MemoDateRange.UNBOUNDED) {
        start
    } else {
        Instant.ofEpochMilli(endMillisExclusive - 1).atZone(zone).toLocalDate()
    }
    return if (end == start) {
        formatter.format(start)
    } else {
        "${formatter.format(start)} – ${formatter.format(end)}"
    }
}

@Composable
private fun ChoiceRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppRadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

private val TagTreeRowHeight = 48.dp
private val TagTreeIndent = 24.dp
private val TagTreeLabelStart = 12.dp
private val TagTreeBarWidth = 3.dp
private val TagTreeBarGap = 3.dp
private val TagTreeChevronSlot = 18.dp
private val TagTreeLabelGap = 4.dp
private val TagTreeGuideGap = 3.dp
private val TagTreeConnectorGap = 5.dp
private val TagTreeAccentHeight = 18.dp

/**
 * One row of the file-tree-style tag picker: no checkbox, the whole row toggles the tag, and the
 * fold arrow stays at the head of the row. The 1dp guides hang under the parent's `#` rather than
 * under the arrow, and a child's horizontal connector runs to just short of its own `#`, so the
 * arrow sits on the connector like a node on the line. A selected row gets an accent bar plus
 * accent text; a branch whose subtree holds selections gets a dimmed bar.
 */
@Composable
private fun TagTreeRow(
    node: TagNode,
    selected: Boolean,
    descendantSelected: Boolean,
    expanded: Boolean,
    onToggleSelect: () -> Unit,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    // Selecting, clearing and branch-level highlighting all cross-fade: a row that flips colour
    // in a single frame reads as a redraw rather than as a selection.
    val rowTint by animateColorAsState(
        targetValue = when {
            selected -> accent.copy(alpha = 0.10f)
            descendantSelected -> accent.copy(alpha = 0.04f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 150),
        label = "tagRowTint"
    )
    val barColor by animateColorAsState(
        targetValue = when {
            selected -> accent
            descendantSelected -> accent.copy(alpha = 0.35f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 150),
        label = "tagRowBar"
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) accent else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 150),
        label = "tagRowLabel"
    )
    // The fold arrow turns instead of jumping between two angles.
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "tagRowChevron"
    )
    val contentStart = TagTreeLabelStart + TagTreeIndent * node.depth
    val labelOffset = TagTreeBarWidth + TagTreeBarGap + TagTreeChevronSlot + TagTreeLabelGap
    val labelX = contentStart + labelOffset
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TagTreeRowHeight)
            .drawBehind {
                // The row tint is always painted; it is simply transparent while the row is out
                // of the selection, which is what lets the transition interpolate.
                val inset = 8.dp.toPx()
                drawRoundRect(
                    color = rowTint,
                    topLeft = Offset(inset, 2.dp.toPx()),
                    size = Size(size.width - inset * 2, size.height - 4.dp.toPx()),
                    cornerRadius = CornerRadius(10.dp.toPx())
                )
                val stroke = 1.dp.toPx()
                val overshoot = 2.dp.toPx()
                val centerY = size.height / 2f
                val labelPx = labelX.toPx()
                // A guide sits three dp left of its level's '#', so the tree column reads as the
                // tag's own column instead of the arrow gutter.
                for (level in 0 until node.depth) {
                    val x = (
                        TagTreeLabelStart + TagTreeIndent * level + labelOffset - TagTreeGuideGap
                        ).toPx()
                    drawLine(guideColor, Offset(x, -overshoot), Offset(x, size.height + overshoot), stroke)
                }
                if (node.depth > 0) {
                    val parentX = (
                        TagTreeLabelStart + TagTreeIndent * (node.depth - 1) + labelOffset -
                            TagTreeGuideGap
                        ).toPx()
                    drawLine(
                        guideColor,
                        Offset(parentX, centerY),
                        Offset(labelPx - TagTreeConnectorGap.toPx(), centerY),
                        stroke
                    )
                }
                // Same as the tint: always drawn, transparent when the row is not selected, so
                // the colour has something to animate.
                val barHeight = TagTreeAccentHeight.toPx()
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(contentStart.toPx(), centerY - barHeight / 2f),
                    size = Size(TagTreeBarWidth.toPx(), barHeight),
                    cornerRadius = CornerRadius(TagTreeBarWidth.toPx() / 2f)
                )
            }
            .clip(RoundedCornerShape(8.dp))
            .selectable(
                selected = selected,
                role = Role.Checkbox,
                onClick = onToggleSelect
            )
            .padding(start = contentStart, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(TagTreeBarWidth + TagTreeBarGap))
        Box(
            modifier = Modifier
                .width(TagTreeChevronSlot)
                .fillMaxHeight()
                .then(
                    if (node.children.isNotEmpty()) {
                        Modifier.clickable(role = Role.Button, onClick = onToggleExpand)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (node.children.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = stringResource(
                        if (expanded) R.string.tags_collapse else R.string.tags_expand
                    ),
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(chevronRotation),
                    tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(TagTreeLabelGap))
        Text(
            text = "#${node.path}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        node.count.takeIf { it > 0 }?.let { count ->
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    accent.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 8.dp).widthIn(min = 24.dp)
            )
        }
    }
}
