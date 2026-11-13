package org.example.moxia.ui.component.item

import org.example.moxia.ui.theme.AppDialog
import org.example.moxia.ui.theme.AppDropdownMenu
import org.example.moxia.ui.theme.AppDropdownMenuItem
import org.example.moxia.ui.theme.AppReactionChip
import org.example.moxia.ui.theme.AppTextButton
import org.example.moxia.ui.theme.flatCardElevation
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.util.Log
import androidx.compose.foundation.background
import org.example.moxia.ui.component.item.markdown.hashtagsIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.material.icons.outlined.Share
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import org.example.moxia.ui.theme.AppCard
import org.example.moxia.ui.theme.AppDotLoader
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.network.httpHeaders
import org.example.moxia.R
import org.example.moxia.model.EditGesture
import org.example.moxia.model.Memo
import org.example.moxia.model.MemoRelationType
import org.example.moxia.model.MemoState
import org.example.moxia.model.Reaction
import org.example.moxia.model.Space
import org.example.moxia.model.User
import org.example.moxia.ui.VisibilityIcon
import org.example.moxia.ui.component.LocalMemoEditGesture
import org.example.moxia.ui.component.LocalPendingMemoNames
import org.example.moxia.ui.component.LocalSyncInProgress
import org.example.moxia.ui.component.item.markdown.NativeComposeMarkdown
import org.example.moxia.ui.component.item.media.AttachmentOrigins
import org.example.moxia.ui.component.item.media.attachmentOrigin
import org.example.moxia.ui.component.item.media.FullScreenAttachmentViewer
import org.example.moxia.ui.component.resolveResourceUrl
import org.example.moxia.ui.nav.MemoSpacePickerSheet
import org.example.moxia.ui.nav.SpaceGlyph


/**
 * Card-corner sync status: the frozen dot matrix while the write waits in the queue, and the same
 * grid running while the sync pass is actually pushing it. It vanishes when the memo stops being
 * pending, i.e. when the server acknowledges the write.
 */
@Composable
private fun MemoSyncIndicator() {
    val syncing = LocalSyncInProgress.current
    val label = stringResource(R.string.memo_sync_pending)
    AppDotLoader(
        modifier = Modifier
            .size(14.dp)
            .semantics { contentDescription = label },
        progress = if (syncing) null else 0.5f,
        // A queued write that is not moving is a resting note, not activity: keep the frozen
        // frame noticeably lighter than the live loader.
        intensity = if (syncing) 1f else 0.45f
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MemoItem(
    modifier: Modifier = Modifier,
    memo: Memo,
    user: User? = null,
    currentUser: User? = null,
    token: String,
    hostUrl: String = "",
    colors: CardColors = CardDefaults.cardColors(),
    onClick: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null,
    onUnarchive: (() -> Unit)? = null,
    onPin: ((Boolean) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onUpsertReaction: ((String) -> Unit)? = null,
    onDeleteReaction: ((Reaction) -> Unit)? = null,
    onContentUpdate: ((String) -> Unit)? = null,
    maxHeight: Dp = Dp.Unspecified,
    isDetailView: Boolean = false,
    reactionOptions: List<String> = emptyList(),
    headerScale: Float,
    onHashtagClick: ((String) -> Unit)? = null,
    /** Spaces the memo can be moved into; empty hides the action. */
    spaces: List<Space> = emptyList(),
    /** Moves the memo to the given space, or out of its space when null. */
    onMoveToSpace: ((String?) -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    var showSpacePicker by remember { mutableStateOf(false) }
    var showReactionPicker by remember { mutableStateOf(false) }
    // The card is recorded into a layer only while a share is being prepared, so scrolling a long
    // list does not pay for an offscreen pass on every frame.
    val shareLayer = rememberGraphicsLayer()
    var capturingForShare by remember { mutableStateOf(false) }
    // True when the body had to be clipped, i.e. some of it (and its inline tags) is not on screen.
    var contentTruncated by remember { mutableStateOf(false) }
    // The composed image waits here until the user confirms the preview.
    var sharePreview by remember { mutableStateOf<ImageBitmap?>(null) }
    val scope = rememberCoroutineScope()
    val pageColor = MaterialTheme.colorScheme.background
    val editGesture = LocalMemoEditGesture.current
    // The card's content padding. A markdown table keeps an equal distance from both card edges,
    // so it cannot simply reuse the text column's insets: the card's own padding is asymmetric.
    val cardPaddingStart = 12.dp
    val cardPaddingEnd = 4.dp
    // A queued write (create, edit, pin, archive, delete) for this memo has not been
    // acknowledged by the server yet. Name-based, so it also covers edits; the old check
    // only matched locally created memos whose name still carried OfflineMemoPrefix.
    val isPendingSync = memo.name?.let { it in LocalPendingMemoNames.current } == true
    // The same shortcut applies in the detail page: it is the same card, and editing from there is
    // the same intent. Only owners get an onEdit callback, so guests see no difference.
    val cardEditGesture = editGesture
    val attachmentOrigins = remember { AttachmentOrigins() }
    var showFullScreenViewer by remember { mutableStateOf(false) }
    var fullScreenInitialIndex by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val density = LocalDensity.current

    val unknownTime = stringResource(R.string.memo_unknown_time)

    val formattedTime = remember(memo.displayTime) {
        memo.displayTime?.let { instant ->
            try {
                DateUtils.getRelativeTimeSpanString(
                    instant.toEpochMilliseconds(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                ).toString()
            } catch (_: Exception) {
                instant.toString()
            }
        } ?: unknownTime
    }



    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .drawWithContent {
                if (capturingForShare) {
                    shareLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(shareLayer)
                } else {
                    drawContent()
                }
            },
        containerColor = colors.containerColor,
        cornerRadius = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    // The configured edit gesture short-circuits the card: it opens the editor
                    // instead of the detail page, or alongside a plain tap as a modifier.
                    when {
                        onEdit == null ->
                            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
                        cardEditGesture == EditGesture.SINGLE_TAP -> Modifier.clickable { onEdit() }
                        cardEditGesture == EditGesture.DOUBLE_TAP -> Modifier.combinedClickable(
                            onClick = { onClick?.invoke() },
                            onDoubleClick = { onEdit() }
                        )
                        cardEditGesture == EditGesture.LONG_PRESS -> Modifier.combinedClickable(
                            onClick = { onClick?.invoke() },
                            onLongClick = { onEdit() }
                        )
                        else ->
                            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
                    }
                )
                .padding(
                    start = cardPaddingStart,
                    top = if (user != null) 12.dp else 4.dp,
                    end = cardPaddingEnd,
                    bottom = 12.dp
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)
                ) {
                    if (user != null) {
                        val avatarUrl = remember(user.avatarUrl, hostUrl) {
                            resolveResourceUrl(hostUrl, user.avatarUrl)
                        }
                        if (avatarUrl != null) {
                            val imageRequest = remember(avatarUrl, token) {
                                val headers = coil3.network.NetworkHeaders.Builder().apply {
                                    if (token.isNotEmpty()) {
                                        set("Authorization", "Bearer $token")
                                    }
                                }.build()

                                coil3.request.ImageRequest.Builder(context).data(avatarUrl)
                                    .httpHeaders(headers).build()
                            }
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = stringResource(R.string.profile_avatar_description),
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(verticalArrangement = Arrangement.Center) {
                            Text(
                                text = user.displayName ?: user.username
                                ?: stringResource(R.string.memo_unknown_user),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                lineHeight = MaterialTheme.typography.labelSmall.lineHeight
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formattedTime,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isPendingSync) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    MemoSyncIndicator()
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                if (memo.pinned == true) {
                                    Icon(
                                        imageVector = Icons.Outlined.PushPin,
                                        contentDescription = stringResource(R.string.profile_stats_pinned),
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                VisibilityIcon(
                                    visibility = memo.visibility,
                                    modifier = Modifier.size(10.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                val commentCount = remember(memo.relations) {
                                    memo.relations?.count { it.type == MemoRelationType.COMMENT }
                                        ?: 0
                                }
                                if (commentCount > 0 && !isDetailView) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Comment,
                                        contentDescription = stringResource(
                                            R.string.memo_detail_comments, commentCount
                                        ),
                                        modifier = Modifier.size(10.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = commentCount.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.7f
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isPendingSync) {
                                Spacer(modifier = Modifier.width(6.dp))
                                MemoSyncIndicator()
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            if (memo.pinned == true) {
                                Icon(
                                    imageVector = Icons.Outlined.PushPin,
                                    contentDescription = stringResource(R.string.profile_stats_pinned),
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            VisibilityIcon(
                                visibility = memo.visibility,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            val commentCount = remember(memo.relations) {
                                memo.relations?.count { it.type == MemoRelationType.COMMENT } ?: 0
                            }
                            if (commentCount > 0 && !isDetailView) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Comment,
                                    contentDescription = stringResource(
                                        R.string.memo_detail_comments, commentCount
                                    ),
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = commentCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                if (onEdit != null || onDelete != null || onUpsertReaction != null || memo.name != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Reacting and pinning happen often enough to be one tap away; only the
                        // rarely used actions stay behind the overflow.
                        if (onUpsertReaction != null) {
                            Box {
                                IconButton(
                                    onClick = { showReactionPicker = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AddReaction,
                                        contentDescription = stringResource(R.string.memo_action_add_reaction),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                // A grid that wraps its content: two rows of four, still anchored in
                                // place instead of a sheet sliding over the card it belongs to.
                                if (showReactionPicker) {
                                    val choices = reactionOptions.ifEmpty { listOf("\uD83D\uDE2D") }
                                    Popup(
                                        alignment = Alignment.TopEnd,
                                        offset = IntOffset(
                                            0,
                                            with(LocalDensity.current) { ReactionPopupOffset.roundToPx() }
                                        ),
                                        onDismissRequest = { showReactionPicker = false },
                                        properties = PopupProperties(focusable = true)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(18.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            shadowElevation = 8.dp
                                        ) {
                                            FlowRow(
                                                modifier = Modifier
                                                    .heightIn(max = ReactionGridMaxHeight)
                                                    .verticalScroll(rememberScrollState())
                                                    .padding(8.dp),
                                                maxItemsInEachRow = 4,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                choices.forEach { emoji ->
                                                    Box(
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .clip(CircleShape)
                                                            .clickable {
                                                                showReactionPicker = false
                                                                onUpsertReaction(emoji)
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = emoji,
                                                            fontSize = 20.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // The menu is anchored to this button so it opens in place, next to the card
                        // it belongs to, instead of as a sheet sliding up from the bottom edge.
                        Box {
                            IconButton(
                                onClick = { showMenu = true }, modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = stringResource(R.string.memo_action_more),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            MemoActionsMenu(
                                expanded = showMenu,
                                memo = memo,
                                hostUrl = hostUrl,
                                onDismissRequest = { showMenu = false },
                                onOpenWeb = { memoId ->
                                    val baseUrl = if (hostUrl.endsWith("/")) hostUrl else "$hostUrl/"
                                    val webUrl = "${baseUrl}memos/$memoId"
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, webUrl.toUri()))
                                    } catch (e: Exception) {
                                        Log.e("MemoItem", "Failed to open web URL: $webUrl", e)
                                    }
                                },
                                onShareImage = if (memo.content.isNotBlank()) {
                                    { capturingForShare = true }
                                } else null,
                                onEdit = onEdit,
                                onPin = onPin,
                                onArchive = onArchive,
                                onUnarchive = onUnarchive,
                                onDelete = onDelete,
                                spaces = spaces,
                                onMoveToSpace = if (onMoveToSpace != null && spaces.isNotEmpty()) {
                                    { showSpacePicker = true }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(if (user != null) 10.dp else 2.dp))

            Column(modifier = Modifier.padding(start = 0.dp, end = 0.dp)) {
                val captureHeightCap = remember(maxHeight) {
                    if (maxHeight != Dp.Unspecified) maxHeight else Dp.Infinity
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            contentTruncated = maxHeight != Dp.Unspecified &&
                                size.height >= with(density) { captureHeightCap.toPx() } - 1f
                        }
                        .then(
                            if (maxHeight != Dp.Unspecified) {
                                Modifier
                                    .heightIn(max = maxHeight)
                                    .clip(RectangleShape)
                                    .graphicsLayer {
                                        compositingStrategy = CompositingStrategy.Offscreen
                                    }
                                    .drawWithContent {
                                        drawContent()
                                        if (size.height >= maxHeight.toPx() - 1.dp.toPx()) {
                                            drawRect(
                                                brush = Brush.verticalGradient(
                                                    0.7f to Color.Black, 1.0f to Color.Transparent
                                                ), blendMode = BlendMode.DstIn
                                            )
                                        }
                                    }
                            } else {
                                Modifier
                            })) {
                    // The text column's inset. A markdown table spends it, so the table spans the
                    // card's content width instead of the narrower text column.
                    val textInsetStart = 12.dp
                    val textInsetEnd = 16.dp
                    // The table's frame keeps the same distance from both card edges. Its bleed is
                    // therefore measured from the card's edges: the table's left edge lines up
                    // with the card's content column, and the right edge is pulled in by the
                    // difference the card's asymmetric padding would otherwise create.
                    val tableMargin = cardPaddingStart
                    val tableBleedStart = textInsetStart + cardPaddingStart - tableMargin
                    val tableBleedEnd = textInsetEnd + cardPaddingEnd - tableMargin
                    NativeComposeMarkdown(
                        content = memo.content,
                        token = token,
                        hostUrl = hostUrl,
                        selectable = isDetailView,
                        onContentChange = onContentUpdate,
                        onHashtagClick = onHashtagClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = textInsetStart, end = textInsetEnd, bottom = 8.dp),
                        bleedStart = tableBleedStart,
                        bleedEnd = tableBleedEnd,
                        headerScale = headerScale
                    )
                }

                // Tags also live outside the text: the inline pills can sit behind the truncation
                // (or inside the detail page's selection container), and filtering must not depend
                // on hitting a word inside the body.
                val tags = remember(memo.content) { hashtagsIn(memo.content) }
                // Only clipped cards need this row: on a short card the inline tags are already on
                // screen, and a second copy under the text would just be noise.
                if (!isDetailView && contentTruncated && tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(start = 12.dp, end = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tags.forEach { tag ->
                            MemoTagChip(
                                tag = tag,
                                onClick = onHashtagClick?.let { handler -> { handler(tag) } }
                            )
                        }
                    }
                }

                memo.location?.let { loc ->
                    val isClickable = loc.latitude != null && loc.longitude != null
                    Surface(
                        onClick = {
                            if (isClickable) {
                                val label = loc.placeholder ?: ""
                                val geoUri =
                                    "geo:${loc.latitude},${loc.longitude}?q=${loc.latitude},${loc.longitude}${
                                        if (label.isNotEmpty()) "(${
                                            Uri.encode(label)
                                        })" else ""
                                    }"
                                val intent = Intent(Intent.ACTION_VIEW, geoUri.toUri())
                                context.startActivity(intent)
                            }
                        },
                        enabled = isClickable,
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Place,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = loc.placeholder ?: "${loc.latitude}, ${loc.longitude}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                val attachments = remember(memo.attachments) {
                    memo.attachments ?: emptyList()
                }

                if (attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isDetailView) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp)
                        ) {
                            attachments.forEachIndexed { index, attachment ->
                                var aspectRatio by remember(
                                    attachment.name ?: attachment.filename
                                ) {
                                    mutableFloatStateOf(16f / 9f)
                                }
                                AttachmentCard(
                                    attachment = attachment,
                                    mediaModifier = Modifier.attachmentOrigin(attachmentOrigins, attachment),
                                    token = token,
                                    hostUrl = hostUrl,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(aspectRatio),
                                    compactMode = AttachmentCompactMode.Never,
                                    onClick = {
                                        fullScreenInitialIndex = index
                                        showFullScreenViewer = true
                                    },
                                    onRatioAvailable = { ratio, _ -> aspectRatio = ratio })
                            }
                        }
                    } else {
                        val scrollState = rememberLazyListState()
                        LazyRow(
                            state = scrollState,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                                .drawWithContent {
                                    drawContent()
                                    val canScrollBackward = scrollState.canScrollBackward
                                    val canScrollForward = scrollState.canScrollForward

                                    if (canScrollBackward || canScrollForward) {
                                        val leftFade =
                                            if (canScrollBackward) Color.Transparent else Color.Black
                                        val rightFade =
                                            if (canScrollForward) Color.Transparent else Color.Black

                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                0f to leftFade,
                                                0.05f to Color.Black,
                                                0.95f to Color.Black,
                                                1f to rightFade
                                            ), blendMode = BlendMode.DstIn
                                        )
                                    }
                                }) {
                            itemsIndexed(
                                attachments,
                                key = { index, it -> "${it.externalLink ?: "link"}_${it.filename}_${it.createTime ?: 0}_$index" }) { index, attachment ->
                                AttachmentCard(
                                    attachment = attachment,
                                    mediaModifier = Modifier.attachmentOrigin(attachmentOrigins, attachment),
                                    token = token,
                                    hostUrl = hostUrl,
                                    modifier = Modifier.size(width = 240.dp, height = 160.dp),
                                    showInfo = false,
                                    compactMode = AttachmentCompactMode.Area,
                                    onClick = {
                                        fullScreenInitialIndex = index
                                        showFullScreenViewer = true
                                    }
                                )
                            }
                        }
                    }
                }

                // Reactions
                val reactions = remember(memo.reactions) {
                    memo.reactions ?: emptyList()
                }
                if (reactions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val groupedReactions = remember(reactions) {
                        reactions.groupBy { it.reactionType }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        groupedReactions.forEach { (type, reactionList) ->
                            val myReaction = reactionList.find { it.creator == currentUser?.name }
                            AppReactionChip(
                                selected = myReaction != null,
                                onClick = {
                                    if (myReaction != null) {
                                        // Pass the emoji type or the reaction name to the viewmodel
                                        onDeleteReaction?.invoke(myReaction)
                                    } else {
                                        onUpsertReaction?.invoke(type)
                                    }
                                },
                                content = {
                                    Text(
                                        text = type, style = MaterialTheme.typography.labelMedium
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = reactionList.size.toString(),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }


    // The capture waits for the frame that records the card, then composes the final image and
    // shows it: sharing is irreversible once pasted somewhere, so it is previewed first.
    LaunchedEffect(capturingForShare) {
        if (!capturingForShare) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        val captured = runCatching { shareLayer.toImageBitmap() }.getOrNull()
        capturingForShare = false
        sharePreview = captured?.let { image ->
            runCatching { composeMemoCardImage(image, pageColor) }
                .onFailure { Log.e("MemoItem", "Could not compose the memo image", it) }
                .getOrNull()
        }
    }

    val previewImage = sharePreview
    if (previewImage != null) {
        MemoSharePreviewDialog(
            image = previewImage,
            onShare = {
                sharePreview = null
                scope.launch {
                    runCatching { shareMemoImage(context, previewImage, memo.name) }
                        .onFailure {
                            Log.e("MemoItem", "Could not share the memo image", it)
                            Toast.makeText(
                                context, R.string.common_operation_failed, Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            },
            onSave = {
                sharePreview = null
                scope.launch {
                    val saved = runCatching { saveMemoImage(context, previewImage, memo.name) }
                        .getOrDefault(false)
                    Toast.makeText(
                        context,
                        if (saved) R.string.memo_share_image_saved else R.string.common_operation_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = { sharePreview = null }
        )
    }

    if (showSpacePicker && onMoveToSpace != null) {
        MemoSpacePickerSheet(
            spaces = spaces,
            selected = memo.space,
            onSelect = onMoveToSpace,
            onDismiss = { showSpacePicker = false }
        )
    }

    if (showFullScreenViewer) {
        val attachments = remember(memo.attachments) { memo.attachments ?: emptyList() }
        FullScreenAttachmentViewer(
            attachments = attachments,
            initialIndex = fullScreenInitialIndex,
            token = token,
            hostUrl = hostUrl,
            onDismiss = { showFullScreenViewer = false },
            originBounds = attachmentOrigins::boundsFor
        )
    }
}

/**
 * The card's overflow menu, anchored to the button that opened it. It opens in place rather than as
 * a sheet from the bottom edge, so the actions stay next to the card they act on.
 */
@Composable
private fun MemoActionsMenu(
    expanded: Boolean,
    memo: Memo,
    hostUrl: String,
    onDismissRequest: () -> Unit,
    onOpenWeb: (String) -> Unit,
    onShareImage: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onPin: ((Boolean) -> Unit)?,
    onArchive: (() -> Unit)?,
    onUnarchive: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    spaces: List<Space>,
    onMoveToSpace: (() -> Unit)?
) {
    if (!expanded) return
    AppDropdownMenu(expanded = true, onDismissRequest = onDismissRequest) {
        if (memo.name != null && hostUrl.isNotBlank()) {
            AppDropdownMenuItem(
                text = stringResource(R.string.memo_action_open_web),
                onClick = {
                    onDismissRequest()
                    onOpenWeb(memo.name.removePrefix("memos/"))
                },
                leadingIcon = { Icon(Icons.Outlined.Language, contentDescription = null) }
            )
        }

        if (onEdit != null) {
            AppDropdownMenuItem(
                text = stringResource(R.string.memo_action_edit),
                onClick = {
                    onDismissRequest()
                    onEdit()
                },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) }
            )
        }
        if (onPin != null && memo.state == MemoState.NORMAL) {
            val isPinned = memo.pinned == true
            AppDropdownMenuItem(
                text = stringResource(
                    if (isPinned) R.string.memo_action_unpin else R.string.memo_action_pin
                ),
                onClick = {
                    onDismissRequest()
                    onPin(!isPinned)
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.PushPin,
                        contentDescription = null,
                        tint = if (isPinned) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
            )
        }
        if (onMoveToSpace != null) {
            val placedIn = spaces.firstOrNull { it.name == memo.space }
            AppDropdownMenuItem(
                text = stringResource(R.string.memo_action_move_space),
                onClick = {
                    onDismissRequest()
                    onMoveToSpace()
                },
                leadingIcon = {
                    if (placedIn != null) {
                        SpaceGlyph(placedIn)
                    } else {
                        Icon(Icons.Outlined.Groups, contentDescription = null)
                    }
                }
            )
        }
        if (onShareImage != null) {
            AppDropdownMenuItem(
                text = stringResource(R.string.memo_action_share_image),
                onClick = {
                    onDismissRequest()
                    onShareImage()
                },
                leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) }
            )
        }
        if (onArchive != null && memo.state == MemoState.NORMAL) {
            AppDropdownMenuItem(
                text = stringResource(R.string.memo_action_archive),
                onClick = {
                    onDismissRequest()
                    onArchive()
                },
                leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) }
            )
        }
        if (onUnarchive != null && memo.state == MemoState.ARCHIVED) {
            AppDropdownMenuItem(
                text = stringResource(R.string.memo_action_unarchive),
                onClick = {
                    onDismissRequest()
                    onUnarchive()
                },
                leadingIcon = { Icon(Icons.Outlined.Unarchive, contentDescription = null) }
            )
        }
        if (onDelete != null) {
            AppDropdownMenuItem(
                text = stringResource(R.string.memo_action_delete),
                onClick = {
                    onDismissRequest()
                    onDelete()
                },
                destructive = true,
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    }
}
/** Offset of the reaction grid below its anchor button. */
private val ReactionPopupOffset = 36.dp

/** Two rows of 40dp cells plus padding; more reactions scroll. */
private val ReactionGridMaxHeight = 96.dp

/** A tag as a tappable pill; without a handler it stays readable but inert. */
@Composable
private fun MemoTagChip(tag: String, onClick: (() -> Unit)?) {
    Text(
        text = tag,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
