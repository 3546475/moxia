package org.example.moxia.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.moxia.R
import org.example.moxia.model.Space
import org.example.moxia.model.SpaceState
import org.example.moxia.ui.component.ActionSheet

/**
 * The single header entry that replaced the notes/explore tab row: it names the feed the list is
 * showing and opens the switcher. One entry instead of a tab row is also what makes spaces fit: a
 * space is a scope of the notes feed, not another pager page.
 */
@Composable
internal fun FeedSwitcher(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = label,
                onClick = onClick
            )
            .defaultMinSize(minHeight = 40.dp, minWidth = 44.dp)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp)
        )
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** The switcher sheet: browse feeds first, then the spaces, then the management entry points. */
@Composable
internal fun FeedSwitcherSheet(
    activeFeed: MemoFeed,
    spaces: SpaceState,
    onSelectFeed: (MemoFeed) -> Unit,
    onSelectSpace: (String?) -> Unit,
    onCreateSpace: () -> Unit,
    onManageSpaces: () -> Unit,
    onDismiss: () -> Unit
) {
    ActionSheet(onDismissRequest = onDismiss) {
        SwitcherSectionTitle(stringResource(R.string.space_browse))
        SwitcherRow(
            title = stringResource(R.string.nav_memos),
            selected = activeFeed == MemoFeed.MEMOS && spaces.selected == null,
            leading = { Icon(Icons.AutoMirrored.Outlined.Notes, contentDescription = null) },
            onClick = {
                onSelectFeed(MemoFeed.MEMOS)
                onSelectSpace(null)
            }
        )
        SwitcherRow(
            title = stringResource(R.string.nav_explore),
            selected = activeFeed == MemoFeed.EXPLORE,
            leading = { Icon(Icons.Outlined.Explore, contentDescription = null) },
            onClick = { onSelectFeed(MemoFeed.EXPLORE) }
        )
        if (spaces.isSupported) {
            SwitcherSectionTitle(stringResource(R.string.space_title))
            spaces.spaces.forEach { space ->
                SwitcherRow(
                    title = space.title,
                    summary = if (space.memberCount > 0) {
                        stringResource(R.string.space_member_count, space.memberCount)
                    } else {
                        null
                    },
                    selected = activeFeed == MemoFeed.MEMOS && spaces.selected == space.name,
                    leading = { SpaceGlyph(space) },
                    onClick = {
                        onSelectFeed(MemoFeed.MEMOS)
                        onSelectSpace(space.name)
                    }
                )
            }
            SwitcherActionRow(
                title = stringResource(R.string.space_create),
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                onClick = onCreateSpace
            )
            SwitcherActionRow(
                title = stringResource(R.string.space_manage),
                icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                onClick = onManageSpaces
            )
        }
    }
}

/** A space shows its emoji when it has one, so the list reads like the web client. */
@Composable
internal fun SpaceGlyph(space: Space, modifier: Modifier = Modifier) {
    val emoji = space.icon?.emoji
    if (!emoji.isNullOrEmpty()) {
        Box(modifier = modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Text(text = emoji, style = MaterialTheme.typography.titleMedium)
        }
    } else {
        Icon(
            imageVector = Icons.Outlined.Groups,
            contentDescription = null,
            modifier = modifier.size(24.dp)
        )
    }
}

@Composable
private fun SwitcherSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun SwitcherRow(
    title: String,
    selected: Boolean,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    summary: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SwitcherActionRow(
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        icon()
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}
