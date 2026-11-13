package org.example.moxia.ui.component.item.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

/** One cell's rendered string plus the inline placeholders (math) it needs. */
private data class TableCellContent(
    val text: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>
)

@Composable
fun NativeMarkdownTable(
    content: String, node: ASTNode
) {
    val header = remember(node) { node.children.find { it.type == GFMElementTypes.HEADER } }
    val rows = remember(node) { node.children.filter { it.type == GFMElementTypes.ROW } }

    val headerCells = remember(header) {
        header?.children?.filter { it.type == GFMTokenTypes.CELL } ?: emptyList()
    }
    val rowCells = remember(rows) {
        rows.map { row -> row.children.filter { it.type == GFMTokenTypes.CELL } }
    }

    val columnCount = headerCells.size
    if (columnCount == 0) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val fontSizePx = with(density) { MaterialTheme.typography.bodyMedium.fontSize.toPx() }

    val onHashtagClick = LocalOnHashtagClick.current

    val align = remember(node) { getTableAlignments(node, content, columnCount) }

    val tableCellPadding = 8.dp
    val headerBackground = MaterialTheme.colorScheme.secondaryContainer
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) // Subtle lines
    val tableCornerSize = 8.dp
    // Every second body row gets a faint tint so a long table stays scannable.
    val zebraColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f)
    // The host's text inset; the table spends it so it spans the card's content width.
    val bleed = LocalMarkdownBleed.current
    val tableBackground = MaterialTheme.colorScheme.surface

    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val styles = MarkdownStyles(
        codeBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
        linkColor = MaterialTheme.colorScheme.primary,
        strikethroughStyle = SpanStyle(textDecoration = TextDecoration.LineThrough),
        boldStyle = SpanStyle(fontWeight = FontWeight.Bold),
        italicStyle = SpanStyle(fontStyle = FontStyle.Italic),
        codeFontFamily = FontFamily.Monospace,
        highlightBackground = highlightBackground(darkTheme),
        tagBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        tagTextColor = MaterialTheme.colorScheme.primary,
        tagFontSize = MaterialTheme.typography.labelMedium.fontSize,
        tagFontWeight = MaterialTheme.typography.labelMedium.fontWeight,
        tagLetterSpacing = MaterialTheme.typography.labelMedium.letterSpacing,
        codeTickColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cjkSpacing = LocalMarkdownFormat.current.cjkSpacing
    )
    val cellStyle = MaterialTheme.typography.bodyMedium

    // Every cell is built once here and reused by both passes below, so the table composes each
    // cell exactly once per frame; the width pass only measures the string it already has.
    val cellMatrix = remember(headerCells, rowCells, content, styles, onHashtagClick) {
        (listOf(headerCells) + rowCells).mapIndexed { rowIndex, row ->
            val isHeader = rowIndex == 0
            row.map { cellNode ->
                val inlineContent = mutableMapOf<String, InlineTextContent>()
                TableCellContent(
                    text = buildAnnotatedString {
                        // A header row is a title row, so it reads bold even when the author left
                        // the cells unstyled.
                        if (isHeader) {
                            withStyle(styles.boldStyle) {
                                appendInlineChildren(
                                    cellNode,
                                    content,
                                    styles,
                                    context,
                                    density,
                                    fontSizePx,
                                    inlineContent,
                                    onHashtagClick
                                )
                            }
                        } else {
                            appendInlineChildren(
                                cellNode,
                                content,
                                styles,
                                context,
                                density,
                                fontSizePx,
                                inlineContent,
                                onHashtagClick
                            )
                        }
                    },
                    inlineContent = inlineContent
                )
            }
        }
    }
    val textMeasurer = rememberTextMeasurer()

    val scrollState = rememberScrollState()
    BoxWithConstraints(
        modifier = Modifier
            // The table spans the card's content width instead of the text column, so a wide
            // table gets the room it needs and still lines up with the card.
            .spanHostInset(bleed.start, bleed.end)
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        // The width the host gives the table, i.e. the card's content area.
        val availableWidth = with(density) { maxWidth.roundToPx() }
        // Allow two viewport widths per column to retain horizontal scrolling,
        // but wrap huge cells before their preferred width becomes unrepresentable.
        val maxCellWidth = with(density) { (maxWidth * 2).roundToPx() }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(tableCornerSize))
                .background(tableBackground)
                .border(1.dp, lineColor, RoundedCornerShape(tableCornerSize))
        ) {
            SubcomposeLayout(
                modifier = Modifier.horizontalScroll(scrollState)
            ) { constraints ->
                val columnWidths = IntArray(columnCount)
                val totalPadding = tableCellPadding.roundToPx() * 2
                val cellConstraints = Constraints(
                    maxWidth = (maxCellWidth - totalPadding).coerceAtLeast(1)
                )

                // 1. MEASURE PASS — through TextMeasurer, without subcomposing the cells again.
                cellMatrix.forEach { row ->
                    row.forEachIndexed { index, cell ->
                        if (index < columnCount) {
                            val layout = textMeasurer.measure(
                                text = cell.text,
                                style = cellStyle,
                                constraints = cellConstraints
                            )
                            columnWidths[index] =
                                maxOf(columnWidths[index], layout.size.width + totalPadding)
                        }
                    }
                }

                var tableWidth = columnWidths.sum()
                // A table that fits takes the whole content area so it lines up with the card
                // instead of hugging its narrowest columns. A wider one keeps its own width and
                // scrolls.
                if (tableWidth in 1 until availableWidth) {
                    val extra = availableWidth - tableWidth
                    var handedOut = 0
                    for (index in 0 until columnCount) {
                        val share = extra * columnWidths[index] / tableWidth
                        columnWidths[index] += share
                        handedOut += share
                    }
                    columnWidths[columnCount - 1] += extra - handedOut
                    tableWidth = availableWidth
                }

                // 2. COMPOSITION PASS
                val contentPlaceables = subcompose("content") {
                    Column {
                        cellMatrix.forEachIndexed { rowIndex, row ->
                            val isHeader = rowIndex == 0
                            val rowBackground = when {
                                isHeader -> headerBackground
                                (rowIndex - 1) % 2 == 1 -> zebraColor
                                else -> Color.Transparent
                            }
                            Row(
                                modifier = Modifier
                                    .width(with(density) { tableWidth.toDp() })
                                    .background(rowBackground)
                                    // The row takes the height of its tallest cell. `IntrinsicSize.Min`
                                    // would ask for the height the text needs at its narrowest
                                    // possible width and leave a blank strip under the row.
                                    .height(IntrinsicSize.Max)
                            ) {
                                row.forEachIndexed { columnIndex, cell ->
                                    if (columnIndex < columnCount) {
                                        Box(
                                            modifier = Modifier
                                                .width(
                                                    with(density) {
                                                        columnWidths[columnIndex].toDp()
                                                    }
                                                )
                                                .padding(tableCellPadding)
                                                .fillMaxHeight(),
                                            contentAlignment = when (
                                                align.getOrElse(columnIndex) { TextAlign.Start }
                                            ) {
                                                TextAlign.Center -> Alignment.Center
                                                TextAlign.End -> Alignment.CenterEnd
                                                else -> Alignment.CenterStart
                                            }
                                        ) {
                                            MarkdownText(
                                                text = cell.text,
                                                style = cellStyle,
                                                textAlign = align.getOrElse(columnIndex) {
                                                    TextAlign.Start
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                inlineContent = cell.inlineContent
                                            )
                                        }
                                        // VERTICAL LINE: Add if it's not the last column
                                        if (columnIndex < columnCount - 1) {
                                            // Miuix has no vertical divider, so the line is drawn
                                            // from the theme's own divider colour instead of a
                                            // Material-only component.
                                            Box(
                                                modifier = Modifier
                                                    .width(1.dp)
                                                    .fillMaxHeight()
                                                    .background(lineColor)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }.map { it.measure(constraints) }

                layout(tableWidth, contentPlaceables.first().height) {
                    contentPlaceables.forEach { it.placeRelative(0, 0) }
                }
            }
            // Fade the edge that still has content behind it, so a wide table reads as scrollable.
            if (scrollState.maxValue > 0) {
                if (scrollState.value > 0) {
                    TableEdgeFade(Alignment.CenterStart, tableBackground)
                }
                if (scrollState.value < scrollState.maxValue) {
                    TableEdgeFade(Alignment.CenterEnd, tableBackground)
                }
            }
        }
    }
}

/**
 * Lets the table use the width the host's text column spends on its inset.
 *
 * Compose refuses negative padding, so the extra width is measured instead: the child is
 * measured with the inset added to its maximum width and is then placed over the inset. The
 * reported size stays the width the host handed out, so the surrounding layout is untouched.
 */
private fun Modifier.spanHostInset(insetStart: Dp, insetEnd: Dp) = layout { measurable, constraints ->
    val extraWidth = (insetStart + insetEnd).roundToPx()
    val childConstraints = if (constraints.maxWidth == Constraints.Infinity) {
        constraints
    } else {
        constraints.copy(maxWidth = constraints.maxWidth + extraWidth)
    }
    val placeable = measurable.measure(childConstraints)
    val width = if (constraints.maxWidth == Constraints.Infinity) {
        placeable.width
    } else {
        constraints.maxWidth
    }
    layout(width, placeable.height) {
        placeable.place(-insetStart.roundToPx(), 0)
    }
}

/**
 * Fades one edge of a scrollable table into the table background.
 *
 * The fade matches the table's own size instead of filling the incoming height: a host that caps
 * the content height would otherwise let the fade claim that whole height, and the table would
 * leave a blank strip below itself.
 */
@Composable
private fun BoxScope.TableEdgeFade(alignment: Alignment, color: Color) {
    Box(
        modifier = Modifier
            .align(alignment)
            .padding(horizontal = 1.dp)
            .width(20.dp)
            .matchParentSize()
            .background(
                Brush.horizontalGradient(
                    when (alignment) {
                        Alignment.CenterStart -> listOf(color, color.copy(alpha = 0f))
                        else -> listOf(color.copy(alpha = 0f), color)
                    }
                )
            )
    )
}

fun getTableAlignments(node: ASTNode, content: String, columnCount: Int): List<TextAlign> {
    // Try to find the separator node. It might be a direct child or a specific token type
    val separator = node.findChildOfType(GFMTokenTypes.TABLE_SEPARATOR)

    if (separator != null) {
        val sepText = separator.getTextInNode(content).toString().trim()
        val rawCells = sepText.split('|')
        // Filter out empty strings that result from splitting leading/trailing pipes
        val validCells = rawCells.map { it.trim() }.filter { it.isNotEmpty() }

        val alignList = validCells.map { cell ->
            when {
                cell.startsWith(":") && cell.endsWith(":") -> TextAlign.Center
                cell.endsWith(":") -> TextAlign.End
                else -> TextAlign.Start
            }
        }

        if (alignList.isNotEmpty()) {
            // Pad or truncate to match column count
            if (alignList.size < columnCount) {
                return alignList + List(columnCount - alignList.size) { TextAlign.Start }
            }
            return alignList.take(columnCount)
        }
    }

    return List(columnCount) { TextAlign.Start }
}
