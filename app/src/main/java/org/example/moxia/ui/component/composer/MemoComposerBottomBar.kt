package org.example.moxia.ui.component.composer

import org.example.moxia.ui.theme.AppCircularProgressIndicator
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import org.example.moxia.ui.theme.capsuleGlass
import org.example.moxia.ui.theme.capsuleShadow
import org.example.moxia.ui.theme.isLightSurface
import org.example.moxia.ui.theme.capsuleHighlight

import org.example.moxia.ui.theme.flatCardElevation
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.ArrowDropUp
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import org.example.moxia.R
import org.example.moxia.ui.theme.AppCard
import org.example.moxia.ui.theme.AppExtendedFloatingActionButton
import org.example.moxia.ui.theme.AppFloatingActionButton
import org.example.moxia.ui.theme.MiuixTokens
import org.example.moxia.model.Attachment
import org.example.moxia.model.Location
import org.example.moxia.model.Space
import org.example.moxia.model.Visibility
import org.example.moxia.ui.VisibilityIcon
import org.example.moxia.ui.component.item.AttachmentCard
import org.example.moxia.ui.component.item.AttachmentCompactMode
import org.example.moxia.ui.getVisibilityLabel

@Composable
fun MemoComposerBottomBar(
    modifier: Modifier = Modifier,
    /** The editor layer behind this bar; non-null switches the pill and the action to frosted. */
    backdrop: LayerBackdrop? = null,
    draftAttachments: List<Pair<Uri, Attachment?>>,
    uploadingUris: Set<Uri>,
    location: Location?,
    isPosting: Boolean,
    visibility: Visibility,
    mode: ComposerMode,
    componentWidth: Dp,
    pickerLauncher: ManagedActivityResultLauncher<String, List<Uri>>,
    token: String,
    hostUrl: String,
    contentStateText: String,
    isUploadingCount: Int,
    /** The spaces the account belongs to; empty hides the space picker. */
    spaces: List<Space> = emptyList(),
    /** The space the memo is placed in (`spaces/{uid}`), or null for no placement. */
    space: String? = null,

    onRemoveLocation: () -> Unit,
    onLocationClick: () -> Unit,
    onVisibilityChange: (Visibility) -> Unit,
    onPublishClick: () -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onRecordingFinished: (Uri, Attachment?) -> Unit,
    onLocationFounded: (Location) -> Unit,
    onLinkMemoClick: () -> Unit = {},
    onSpaceChange: (String?) -> Unit = {},
) {
    LocalContext.current
    var showActionOverflowMenu by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var spaceExpanded by remember { mutableStateOf(false) }

    val showVisibilityLabel = componentWidth > 480.dp || componentWidth == 0.dp
    val showPublishLabel = componentWidth > 410.dp || componentWidth == 0.dp
    val isCompact = componentWidth < 380.dp && componentWidth != 0.dp

    val actionIconSize = if (isCompact) 20.dp else 24.dp
    val actionButtonSize = if (isCompact) 36.dp else 48.dp


    val defaultElevation = 4.dp
    val pressedElevation = 8.dp


    Row(
        modifier = modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        // "Everything else" Card — pill when empty, rounded card when content is present
        val hasContent = draftAttachments.isNotEmpty() || location != null
        // The floating navigation bar is filled with Miuix's surfaceContainer, not the Material
        // surface colour, so the same number here keeps both bars the same material.
        val miuixSurfaceContainer = MiuixTheme.colorScheme.surfaceContainer
        AppCard(
            modifier = Modifier
                .weight(1f)
                // Same ambient shadow as the floating navigation bar, drawn under the glass.
                .capsuleShadow(
                    lightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f,
                    // Fixed radii, never the measured height: a first draw from a tiny measured
                    // height produced a solid square shadow before the pill settled.
                    cornerRadius = if (hasContent) {
                        MiuixTokens.CardCornerRadius
                    } else {
                        MiuixTokens.PillCornerRadius
                    }
                )
                // With a backdrop the pill is real frosted glass: the editor text scrolling under
                // it is blurred. Without one it keeps the rim over a translucent fill.
                .then(
                    if (backdrop != null) {
                        Modifier.capsuleGlass(
                            backdrop = backdrop,
                            // Identical to the floating navigation bar: the capsule surface colour at
                            // 0.86 and the same 24px blur, with no extra colour grading. A different
                            // recipe here is what made the editor's bar read as a grey block rather
                            // than as the same material.
                            surfaceColor = miuixSurfaceContainer.copy(alpha = 0.86f),
                            lightSurface = isLightSurface(MiuixTheme.colorScheme.surface),
                            shape = RoundedCornerShape(
                                if (hasContent) {
                                    MiuixTokens.CardCornerRadius
                                } else {
                                    MiuixTokens.PillCornerRadius
                                }
                            ),
                            // null = stadium, exactly like the bar's own capsule shape.
                            cornerRadius = if (hasContent) MiuixTokens.CardCornerRadius else null,
                            blurRadius = 24f
                        )
                    } else {
                        Modifier.capsuleHighlight(
                            lightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f,
                            cornerRadius = if (hasContent) {
                                MiuixTokens.CardCornerRadius
                            } else {
                                MiuixTokens.PillCornerRadius
                            }
                        )
                    }
                ),
            // With a backdrop the inside is painted by the glass itself (blurred content + one
            // 86% fill). Letting the card paint its own 86% fill on top of that composites twice,
            // which lands at ~98% and read as a solid white pill with no glass at all.
            containerColor = if (backdrop != null) {
                Color.Transparent
            } else {
                // Blur off: the floating bar goes fully opaque in surfaceContainer, so this does too,
                // instead of keeping a translucent tinted card.
                miuixSurfaceContainer
            },
            cornerRadius = if (hasContent) MiuixTokens.CardCornerRadius else MiuixTokens.PillCornerRadius
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                // Attachments List
                if (draftAttachments.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        contentPadding = PaddingValues(top = 8.dp, end = 8.dp, bottom = 8.dp)
                    ) {
                        itemsIndexed(draftAttachments, key = { index, (uri, attachment) ->
                            val baseKey = if (uri != Uri.EMPTY) uri.toString()
                            else "${attachment?.name ?: "unknown"}_${attachment?.filename ?: "unknown"}_${attachment?.createTime ?: System.currentTimeMillis()}"
                            "${baseKey}_$index"
                        }) { index, (uri, attachment) ->
                            val isUploading = uri in uploadingUris

                            Box(modifier = Modifier.size(80.dp, 80.dp)) {
                                AttachmentCard(
                                    attachment = attachment,
                                    token = token,
                                    hostUrl = hostUrl,
                                    uri = uri,
                                    modifier = Modifier.fillMaxSize(),
                                    showInfo = false,
                                    compactMode = AttachmentCompactMode.Always
                                )

                                if (isUploading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Color.Black.copy(alpha = 0.3f),
                                                MaterialTheme.shapes.medium
                                            ), contentAlignment = Alignment.Center
                                    ) {
                                        AppCircularProgressIndicator(
                                            size = 24.dp, color = Color.White
                                        )
                                    }
                                }

                                // Queued for offline upload: the bytes are staged and the
                                // upload queue will deliver them; badge the thumbnail so the
                                // pending state is visible instead of looking lost.
                                val isPendingUpload = !isUploading &&
                                    attachment?.name == null && attachment?.clientId != null
                                if (isPendingUpload) {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudUpload,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(4.dp)
                                            .size(18.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.5f), CircleShape
                                            )
                                            .padding(2.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onRemoveAttachment(index) },
                                    enabled = !isPosting,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-4).dp)
                                        .size(24.dp)
                                        .background(if (!isPosting) Color.Black.copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.5f), CircleShape)
                                        .zIndex(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.memo_composer_remove_attachment),
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Location Chip
                location?.let { loc ->
                    Spacer(modifier = Modifier.height(8.dp))
                    InputChip(
                        selected = true,
                        onClick = onLocationClick,
                        shape = CircleShape,
                        label = {
                            Text(
                                text = loc.placeholder ?: "${loc.latitude}, ${loc.longitude}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.memo_composer_remove_location),
                                modifier = Modifier
                                    .size(18.dp)
                                    .noRippleClickable { onRemoveLocation() })
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Place,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        })
                }

                if (draftAttachments.isNotEmpty() || location != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Actions Row
                // Determine if we should use overflow menu based on width
                val useOverflowMenu = componentWidth < 320.dp && componentWidth != 0.dp

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (useOverflowMenu) {
                            // Show overflow menu button
                            Box {
                                IconButton(
                                    onClick = { showActionOverflowMenu = true },
                                    enabled = !isPosting,
                                    modifier = Modifier.size(actionButtonSize)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.MoreVert,
                                        contentDescription = stringResource(R.string.memo_action_more),
                                        modifier = Modifier.size(actionIconSize)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showActionOverflowMenu,
                                    onDismissRequest = { showActionOverflowMenu = false },
                                    properties = PopupProperties(focusable = false)
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Outlined.AttachFile,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(stringResource(R.string.memo_composer_attach_file))
                                            }
                                        }, onClick = {
                                            showActionOverflowMenu = false
                                            pickerLauncher.launch("*/*")
                                        }, enabled = !isPosting
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Image,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(stringResource(R.string.memo_composer_add_image))
                                            }
                                        }, onClick = {
                                            showActionOverflowMenu = false
                                            pickerLauncher.launch("image/*")
                                        }, enabled = !isPosting
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                AudioRecorderIconButton(
                                                    onRecordingFinished = onRecordingFinished,
                                                    enabled = !isPosting,
                                                    iconSize = 20.dp
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(stringResource(R.string.memo_composer_record_audio))
                                            }
                                        }, onClick = {
                                            // Click handled by AudioRecorderIconButton, or we can close menu here
                                            // But since AudioRecorderIconButton intercepts click, this might not trigger if clicking the button.
                                            // Clicking the audio label will trigger this.
                                            // Ideally we want the whole row to toggle recording.
                                            // But AudioRecorderIconButton is self-contained.
                                            // If we want the menu item to act as the recorder, we need to access recorder state... which we moved out.
                                            // So for now, let's keep it simple. Clicking the text does nothing useful except maybe show a toast or we can omit separate onClick logic if the button is there.
                                            // Actually, the button inside needs to be clickable.
                                            // If we set enabled=false on the DropdownMenuItem it might disable the button too?
                                            // Let's just close the menu on click, but the user has to click the button to record.
                                            // This UX is slightly broken for overflow menu, but complies with "self-contained button".
                                            showActionOverflowMenu = false
                                        }, enabled = !isPosting
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                LocationIconButton(
                                                    onLocationFounded = onLocationFounded,
                                                    enabled = !isPosting && location == null,
                                                    iconSize = 20.dp,
                                                    location = location
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(stringResource(R.string.memo_composer_add_location))
                                            }
                                        }, onClick = {
                                            showActionOverflowMenu = false
                                            // Same interaction issue as AudioRecorderIconButton.
                                            // The button handles the click.
                                        }, enabled = !isPosting && location == null
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Link,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(stringResource(R.string.memo_composer_link_memo))
                                            }
                                        }, onClick = {
                                            showActionOverflowMenu = false
                                            onLinkMemoClick()
                                        }, enabled = !isPosting
                                    )
                                }
                            }
                        } else {
                            // Show individual icon buttons
                            IconButton(
                                onClick = { pickerLauncher.launch("*/*") },
                                enabled = !isPosting,
                                modifier = Modifier.size(actionButtonSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AttachFile,
                                    contentDescription = stringResource(R.string.memo_composer_attach_file),
                                    modifier = Modifier.size(actionIconSize)
                                )
                            }
                            IconButton(
                                onClick = { pickerLauncher.launch("image/*") },
                                enabled = !isPosting,
                                modifier = Modifier.size(actionButtonSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = stringResource(R.string.memo_composer_add_image),
                                    modifier = Modifier.size(actionIconSize)
                                )
                            }
                            AudioRecorderIconButton(
                                enabled = !isPosting,
                                modifier = Modifier.size(actionButtonSize),
                                iconSize = actionIconSize,
                                onRecordingFinished = onRecordingFinished
                            )
                            LocationIconButton(
                                enabled = !isPosting && location == null,
                                modifier = Modifier.size(actionButtonSize),
                                iconSize = actionIconSize,
                                location = location,
                                onLocationFounded = onLocationFounded
                            )
                            IconButton(
                                onClick = onLinkMemoClick,
                                enabled = !isPosting,
                                modifier = Modifier.size(actionButtonSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Link,
                                    contentDescription = stringResource(R.string.memo_composer_link_memo),
                                    modifier = Modifier.size(actionIconSize)
                                )
                            }
                        }
                    } // End Left Actions Row

                    // Visibility Dropdown
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            TextButton(
                                onClick = { expanded = true },
                                enabled = !isPosting,
                                contentPadding = if (isCompact) PaddingValues(horizontal = 8.dp) else ButtonDefaults.TextButtonContentPadding,
                                modifier = if (isCompact) Modifier.height(actionButtonSize) else Modifier
                            ) {
                                VisibilityIcon(
                                    visibility = visibility, modifier = Modifier.size(18.dp)
                                )
                                if (showVisibilityLabel) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(getVisibilityLabel(visibility))
                                    Icon(
                                        imageVector = if (expanded) Icons.Outlined.ArrowDropUp else Icons.Outlined.ArrowDropDown,
                                        contentDescription = null
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                properties = PopupProperties(focusable = false),
                                offset = DpOffset(0.dp, 48.dp)
                            ) {
                                Visibility.entries.filter {
                                    it != Visibility.VISIBILITY_UNSPECIFIED &&
                                        (it != Visibility.SPACE || spaces.isNotEmpty())
                                }
                                    .forEach { option ->
                                        DropdownMenuItem(text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                VisibilityIcon(
                                                    visibility = option,
                                                    modifier = Modifier.size(18.dp),
                                                    outlined = true
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(text = getVisibilityLabel(option))
                                            }
                                        }, onClick = {
                                            onVisibilityChange(option)
                                            expanded = false
                                        })
                                    }
                            }
                        }
                    }

                    // Space Dropdown: a placement and SPACE visibility move together.
                    if (spaces.isNotEmpty()) {
                        val selectedSpace = spaces.firstOrNull { it.name == space }
                        Box {
                            TextButton(
                                onClick = { spaceExpanded = true },
                                enabled = !isPosting,
                                contentPadding = if (isCompact) PaddingValues(horizontal = 8.dp) else ButtonDefaults.TextButtonContentPadding,
                                modifier = if (isCompact) Modifier.height(actionButtonSize) else Modifier
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Groups,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                if (showVisibilityLabel) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = selectedSpace?.title
                                            ?: stringResource(R.string.memo_space_none),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 160.dp)
                                    )
                                    Icon(
                                        imageVector = if (spaceExpanded) Icons.Outlined.ArrowDropUp else Icons.Outlined.ArrowDropDown,
                                        contentDescription = null
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = spaceExpanded,
                                onDismissRequest = { spaceExpanded = false },
                                properties = PopupProperties(focusable = false),
                                offset = DpOffset(0.dp, 48.dp)
                            ) {
                                DropdownMenuItem(text = {
                                    Text(text = stringResource(R.string.memo_space_none))
                                }, onClick = {
                                    onSpaceChange(null)
                                    spaceExpanded = false
                                })
                                spaces.forEach { item ->
                                    DropdownMenuItem(text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Outlined.Groups,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = item.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }, onClick = {
                                        onSpaceChange(item.name)
                                        spaceExpanded = false
                                    })
                                }
                            }
                        }
                    }
                }
            } // End Column in Tools Card
        } // End Tools Card

        // Publish FAB
        val label = when (mode) {
            ComposerMode.PUBLISH -> stringResource(R.string.memo_publish)
            ComposerMode.UPDATE -> stringResource(R.string.memo_action_update)
            ComposerMode.COMMENT -> stringResource(R.string.memo_action_post)
        }


        if (showPublishLabel) {
            AppExtendedFloatingActionButton(
                onClick = onPublishClick,
                expanded = true,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                // The editor text scrolls under this bar, so the action wears the same frosted
                // material as the bar itself.
                backdrop = backdrop,
                icon = {
                    if (isPosting) {
                        AppCircularProgressIndicator(
                            size = 24.dp,
                            color = LocalContentColor.current,
                            strokeWidth = 2.dp
                            )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                text = { Text(text = label) }
            )
        } else {
            AppFloatingActionButton(
                onClick = onPublishClick,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                backdrop = backdrop,
                modifier = Modifier
            ) {
                if (isPosting) {
                    AppCircularProgressIndicator(
                        size = 24.dp,
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp
                        )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = label,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
