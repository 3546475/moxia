package org.example.moxia.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.example.moxia.R
import org.example.moxia.ui.MainDestination
import org.example.moxia.ui.navIcon
import org.example.moxia.ui.theme.CapsuleSurfaceAlpha
import org.example.moxia.ui.theme.capsuleHighlight
import org.example.moxia.ui.theme.liquidDroplet
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Blur radius of the capsule's material, in pixels: the same frost the docked bar uses. */
private const val CapsuleBlurRadius = 24f

/**
 * The droplet that appears under a finger on the glass.
 *
 * It is an ellipse rather than a circle: wider than it is tall, so it reads as a drop lying on the
 * bar and not as a lens held over it. [DropletHeight] is a little under the capsule's, and every
 * pixel it shows comes from the bar itself — the magnification and the rim bend together read from
 * `height / 2 / magnification` at most, which stays inside the capsule's half height. That is what
 * keeps the page, and the colour split that used to come with it, off the bar's top and bottom edge.
 */
private val DropletWidth = 112.dp
private val DropletHeight = 76.dp
private const val DropletMagnification = 1.14f

/** The droplet is the same rounded rect as the selected item's well, blown up: a wide pill. */
private val DropletShape = RoundedCornerShape(percent = 50)

/**
 * The drop's curvature, as fractions of its height: [DropletRimBand] is how far the refracting band
 * reaches in from the rim, [DropletBendDepth] how far that band pulls its samples inwards, and
 * [DropletDispersion] how far the red and blue samples spread along the bend. The band is what the
 * fringes live in; the middle of the bead is a plain magnifier and shows no colour split.
 */
private const val DropletRimBand = 0.24f
private const val DropletBendDepth = 0.14f
private const val DropletDispersion = 0.35f

/**
 * A thin dark edge under the bright one: a white bevel alone disappears on a light bar, and the
 * bead has to read as a separate piece of glass wherever it is.
 */
private val DropletBorderWidth = 1.dp
private const val DropletBorderAlpha = 0.12f

/** The selected destination's well, inset from its slot. */
private val SelectionWellHorizontalInset = 4.dp
private val SelectionWellVerticalInset = 8.dp

/**
 * The bead's travel between two destinations: it pulls away from rest, peaks in the middle and
 * settles back into rest at the destination.
 */
private val TravelEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val TravelSpec = tween<Float>(durationMillis = 150, easing = TravelEasing)

/** The capsule's shadow: a stack of faint rounded layers that follows the bar's fade. */
private val CapsuleShadowSpread = 34.dp
private val CapsuleShadowOffsetY = 4.dp

/** The capsule's own height; the shadow's corner radius is derived from it. */
private val CapsuleBarHeight = 64.dp

/**
 * Gap between the capsule and the navigation-bar inset. The system gesture handle lives inside
 * that inset and must stay free of the bar's background (and of its shadow).
 */
private val CapsuleNavigationGap = 7.dp

/**
 * Steps in the stacked shadow. Kept moderate: with dozens of very faint layers the per-layer
 * alpha can round away during blending (which made the 3% version disappear), while too many
 * 1-LSB steps build a shadow much stronger than the requested total. Sixteen steps land near the
 * reference's edge shading and still render reliably.
 */
private const val CapsuleShadowLayers = 16

/** The bar's capsule shape, reused by its items and by the selection bubble. */
private val CapsuleShape = RoundedCornerShape(percent = 50)

/**
 * Slot the compose button occupies in the capsule. It is wider than the blue squircle so that a
 * tap right next to the button still belongs to the bar instead of the content behind it.
 */
private val ComposeSlotWidth = 68.dp

/**
 * Length of the floating capsule with four destinations enabled. The bar keeps this length when
 * fewer destinations are on, so the navigation does not change shape when items are toggled.
 */
private val CapsuleBarWidth = 272.dp

/** Inset of the item row inside the capsule, shared with [NavBarSlots]'s geometry. */
private val CapsuleBarPadding = 6.dp


/** Growing the droplet is a scale, not a bounce: critically damped so it does not wobble. */
private val PressSpring = spring<Float>(dampingRatio = 1f, stiffness = 1000f)

/** Gone as soon as the finger is. */
private val ReleaseSpec = tween<Float>(durationMillis = 80)

/** The selection takes over as the bead leaves, at the same pace. */
private val SelectionRevealSpec = tween<Color>(durationMillis = 80)

/** The capsule grows out of the bottom edge, where it is anchored on screen. */
private val BottomOrigin = TransformOrigin(0.5f, 1f)

/** The shared enter/exit for the capsule and its shadow, so both settle on the same frame. */
private val capsuleEnter: EnterTransition =
    fadeIn(animationSpec = tween(180)) + scaleIn(
        animationSpec = tween(220),
        initialScale = 0.92f,
        transformOrigin = BottomOrigin
    )

private val capsuleExit: ExitTransition =
    fadeOut(animationSpec = tween(160)) + scaleOut(
        animationSpec = tween(180),
        targetScale = 0.92f,
        transformOrigin = BottomOrigin
    )

/**
 * The shadow arrives on a shorter fade than the bar: 5% black only becomes visible late in a
 * 180ms fade, so matching the bar's curve made the halo read as a separate, later event.
 */
private val capsuleShadowEnter: EnterTransition =
    fadeIn(animationSpec = tween(120)) + scaleIn(
        animationSpec = tween(220),
        initialScale = 0.92f,
        transformOrigin = BottomOrigin
    )

private val capsuleShadowExit: ExitTransition =
    fadeOut(animationSpec = tween(120)) + scaleOut(
        animationSpec = tween(180),
        targetScale = 0.92f,
        transformOrigin = BottomOrigin
    )

/**
 * The floating bottom bar: a capsule that hovers over the content with the compose button in the
 * middle, so the primary action lives in the navigation instead of a separate floating button.
 *
 * The material and the interaction are two independent axes. [blurEnabled] decides what the bar is
 * made of — frost or a solid surface — and [liquidGlass] decides whether touching it grows the
 * droplet. With the frost on the droplet refracts the frost; with it off the droplet refracts the
 * solid bar; either switch can be on without the other.
 */
@Composable
internal fun FloatingBottomBar(
    visible: Boolean,
    destinations: List<MainDestination>,
    current: MainDestination,
    onDestination: (MainDestination) -> Unit,
    onCompose: () -> Unit,
    blurEnabled: Boolean,
    liquidGlass: Boolean,
    backdrop: Backdrop,
    blurRefresh: State<Float>,
    modifier: Modifier = Modifier
) {
    // Read the refresh state in composition, not only in draw: the drawBackdrop modifier elements
    // must be updated while the destination cross-fade runs, or their recorded page stays cached
    // and the bar keeps showing the page that was just left.
    val refreshTick = blurRefresh.value
    // The capsule is the card colour, not the page surface: white in the day theme and #242424 in
    // the dark one, matching the reference. With the frost on, that same colour goes translucent so
    // the feed still frosts through; with the frost off it is solid, and the liquid glass adds no
    // tint of its own — it is an interaction, not another material.
    val lightSurface = MiuixTheme.colorScheme.surface.luminance() > 0.5f
    val frosted = blurEnabled
    val barColor = if (frosted) {
        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = CapsuleSurfaceAlpha)
    } else {
        MiuixTheme.colorScheme.surfaceContainer
    }
    val slots = remember(destinations) { NavBarSlots(destinations.size) }
    val selectedIndex = destinations.indexOf(current).coerceAtLeast(0)
    val touch = remember(destinations) { LiquidTouch() }
    val scope = rememberCoroutineScope()
    val maxIndex = (destinations.size - 1).coerceAtLeast(0)
    // The gesture detectors are keyed on the destination list, so they outlive a selection change
    // and must read the selection from a holder instead of capturing it.
    val currentIndex = rememberUpdatedState(selectedIndex)
    val selectDestination = rememberUpdatedState(onDestination)
    // The compose action rides the same way: the gesture block outlives a recomposition, and a
    // stale lambda would open an editor that no longer belongs to the screen.
    val composeAction = rememberUpdatedState(onCompose)
    // Where the finger is, in the capsule's own coordinates. Only the droplet reads it, and only
    // while it is drawn, so tracking it costs no recomposition.
    val finger = remember { mutableStateOf(Offset.Zero) }
    // Where the bead is, in capsule pixels. A plain state rather than an Animatable: the gesture
    // writes it straight from the pointer loop, while only the tap's travel animates it. With an
    // Animatable every touch had to go through a coroutine, and a fast tap could let the travel be
    // cancelled by the snap that was still queued behind it.
    val beadX = remember { mutableStateOf(0f) }
    // The slot a finger is currently on, so the item under the bead can show the selected state
    // while the bead covers it. -1 when nothing is pressed.
    val hovered = remember { mutableStateOf(-1) }
    // True for as long as the bead is on screen, including its travel and its fade. The bar keeps
    // its selection styling to itself while the bead is out: for that moment the bead is what says
    // where you are, and leaving a dark well and a tinted icon underneath it fights the lens.
    // Derived rather than read in composition: it flips twice per touch, not once per frame.
    val beadVisible = remember { derivedStateOf { touch.press.value > 0.01f } }
    // The gesture block runs outside composition and cannot read a Density receiver from here.
    val density = LocalDensity.current
    // The bar as it is drawn, recorded so the droplet can magnify it instead of the page.
    val barBackdrop = rememberLayerBackdrop()
    // The bead's programmed travel, so a drag can take the movement over from it. Without a handle
    // the two writers would take turns on every frame and the bead would stutter.
    val travelJob = remember { mutableStateOf<Job?>(null) }

    // Touch, hold and drag all run through one detector.
    //
    // The droplet has to go away whenever the finger lifts, and a tap on an item is consumed by the
    // item's own click — which is why this waits for the lift itself instead of using
    // waitForUpOrCancellation, whose cancel path would leave the droplet on screen for good.
    val touchGesture = Modifier
        .pointerInput(touch, slots, destinations) {
            fun slotAt(x: Float): Int =
                slots.indexAt(x / density.density).roundToInt().coerceIn(0, maxIndex)

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                finger.value = down.position
                val downOnCompose = slots.isComposeSlot(down.position.x / density.density)
                // The bead always grows on the destination that is selected right now, never under
                // the finger: that is what makes holding, sliding and tapping all read as the
                // selection moving away from where it was. A touch on the compose button has no
                // destination to travel from, so the bead grows on the button itself.
                val from = if (downOnCompose) {
                    slots.composeCenter() * density.density
                } else {
                    slots.center(currentIndex.value.toFloat()) * density.density
                }
                beadX.value = from
                hovered.value = if (downOnCompose) -1 else slotAt(down.position.x)
                travelJob.value?.cancel()
                // Holding a destination that is not the selected one draws the same travel a tap
                // does, only earlier: the bead sets off while the finger is still down and is
                // already waiting under it when the finger lifts.
                if (!downOnCompose) {
                    val to = slots.center(slotAt(down.position.x).toFloat()) * density.density
                    if (abs(to - from) > 1f) {
                        travelJob.value = scope.launch {
                            animate(
                                initialValue = from,
                                targetValue = to,
                                animationSpec = TravelSpec
                            ) { value, _ -> beadX.value = value }
                        }
                    }
                }
                scope.launch { touch.press.animateTo(1f, PressSpring) }
                var dragged = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (!dragged) {
                        dragged = (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                        // The finger owns the bead from here: a travel that is still running would
                        // otherwise keep pulling it back towards the slot it was heading for.
                        if (dragged) travelJob.value?.cancel()
                    }
                    if (dragged) {
                        // Past the slop this is a drag, and the items must not also read it as a tap.
                        change.consume()
                        finger.value = change.position
                        beadX.value = change.position.x
                        // Over the compose button nothing is selected, so nothing is tinted: the bead
                        // covering the button is what says a release would open the editor.
                        val x = change.position.x / density.density
                        hovered.value = if (slots.isComposeSlot(x)) -1 else slotAt(change.position.x)
                    }
                }
                hovered.value = -1
                travelJob.value?.cancel()
                if (dragged) {
                    // A drag ends on whatever the bead was left over: the button opens the editor, a
                    // slot picks that destination. A tap was already handled by the item that owns
                    // it, and reporting it here too would look like a double tap.
                    val x = finger.value.x / density.density
                    if (slots.isComposeSlot(x)) {
                        composeAction.value()
                    } else {
                        val target = slotAt(finger.value.x)
                        if (target != currentIndex.value) {
                            destinations.getOrNull(target)?.let { selectDestination.value(it) }
                        }
                    }
                    scope.launch { touch.press.animateTo(0f, ReleaseSpec) }
                } else {
                    // A tap draws the selection change: the bead is already sitting on the destination
                    // that was selected when the finger came down, so it only has to slide to the one
                    // under the finger and then go away. Nothing here is conditional on the state
                    // having updated yet — the distance alone decides whether there is a slide.
                    val to = slots.center(slotAt(finger.value.x).toFloat()) * density.density
                    val landsOnCompose = slots.isComposeSlot(finger.value.x / density.density)
                    scope.launch {
                        if (!landsOnCompose && abs(to - beadX.value) > 1f) {
                            // Solid before it moves: a quick tap would otherwise let the bead fade out
                            // while the travel is still starting, and the slide would never be seen.
                            touch.press.snapTo(1f)
                            animate(
                                initialValue = beadX.value,
                                targetValue = to,
                                animationSpec = TravelSpec
                            ) { value, _ -> beadX.value = value }
                        }
                        touch.press.animateTo(0f, ReleaseSpec)
                    }
                }
            }
        }

    Box(
        modifier = modifier
            .padding(horizontal = 20.dp)
            // The gesture handle belongs to the navigation-bar inset. Without this the capsule
            // sits on top of the handle and the system's swipe-up area runs through the bar.
            .navigationBarsPadding()
            .padding(top = CapsuleNavigationGap, bottom = CapsuleNavigationGap)
    ) {
        // The shadow gets its own enter/exit layer instead of sitting under the bar's fade: at 5%
        // black it crosses the visibility threshold only late in a 0-1 fade, which read as the
        // halo arriving a beat after the capsule. As a sibling on the same timeline the halo is
        // at full strength halfway through the fade instead of a quarter of it.
        CapsuleShadowLayer(visible = visible, lightSurface = lightSurface)
        AnimatedVisibility(
            visible = visible,
            enter = capsuleEnter,
            exit = capsuleExit
        ) {
            Box(
                Modifier
                                    // A fixed length keeps the capsule the same width whatever destination count is
                    // enabled; the two halves split it around the centred compose button.
                    .width(CapsuleBarWidth)
                    .height(CapsuleBarHeight)
            ) {
                // Everything the bar looks like — glass and icons — is recorded here, so the droplet
                // can magnify it. At rest nothing reads the recording: without a finger down the
                // bar is the plain frost.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (liquidGlass) Modifier.layerBackdrop(barBackdrop) else Modifier)
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .redrawOnBackdropChange(refreshTick, blurRefresh)
                            .clip(CapsuleShape)
                            .then(
                                if (frosted) {
                                    Modifier.drawBackdrop(
                                        backdrop = backdrop,
                                        // The capsule, not a rectangle: the effect needs the real
                                        // corner radii to frost around the bar's ends.
                                        shape = { CapsuleShape },
                                        effects = { blur(CapsuleBlurRadius, CapsuleBlurRadius) }
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .background(barColor)
                            // Drawn after the fill so it stays crisp whatever the fill is: the
                            // capsule's bevel is the bar's edge, and it is the same in both modes.
                            .capsuleHighlight(lightSurface = lightSurface)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            // Holding or dragging on the bar is what grows the droplet; the frosted
                            // bar is the plain navigation it always was, clicks and nothing else.
                            .then(if (liquidGlass) touchGesture else Modifier)
                            // The capsule floats over the feed, so a tap on the parts of it that
                            // are not a destination must not reach the memo underneath. The
                            // destinations are children of this row and keep their own clicks;
                            // this only makes the bar itself a hit target.
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false).consume()
                                }
                            }
                            .padding(horizontal = CapsuleBarPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val firstHalf = (destinations.size + 1) / 2
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            destinations.take(firstHalf).forEachIndexed { index, destination ->
                                FloatingBarItem(
                                    destination = destination,
                                    selected = current == destination && !beadVisible.value,
                                    hovered = hovered.value == index,
                                    onClick = { onDestination(destination) },
                                    // The item takes the whole slot, so with two destinations a tap
                                    // anywhere in that half of the bar reaches it.
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        ComposeCapsuleButton(
                            onClick = onCompose,
                            // The button shares the bar's material, so it frosts whenever the bar
                            // does.
                            blurEnabled = blurEnabled,
                            backdrop = backdrop
                        )
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            destinations.drop(firstHalf).forEachIndexed { index, destination ->
                                FloatingBarItem(
                                    destination = destination,
                                    selected = current == destination && !beadVisible.value,
                                    hovered = hovered.value == index + firstHalf,
                                    onClick = { onDestination(destination) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                // Over the recorded bar: the droplet is a lens on top of the glass, so it enlarges
                // the icons as well as the frost under them.
                if (liquidGlass) {
                    LiquidDroplet(
                        progress = { touch.press.value },
                        centerX = { beadX.value },
                        backdrop = barBackdrop,
                        lightSurface = lightSurface
                    )
                }
            }
        }
    }
}

/**
 * Lays the capsule out in destination slots, in dp relative to the capsule's left edge.
 *
 * The content row is two weighted halves around the fixed compose slot, so a slot is as wide as
 * its half divided by the number of destinations in that half. Slots only decide which destination
 * a finger landed on: the droplet itself follows the finger, it does not snap to them.
 */
private class NavBarSlots(count: Int) {

    private val starts: FloatArray
    private val widths: FloatArray
    private val composeStart: Float
    private val composeEnd: Float

    init {
        val content = CapsuleBarWidth.value - CapsuleBarPadding.value * 2f
        // Slot centres are measured from the capsule's left edge, while the row's halves live
        // inside its horizontal padding.
        val origin = CapsuleBarPadding.value
        val half = (content - ComposeSlotWidth.value) / 2f
        composeStart = origin + half
        composeEnd = composeStart + ComposeSlotWidth.value
        val leftCount = (count + 1) / 2
        val rightCount = count - leftCount
        val leftWidth = half / leftCount.coerceAtLeast(1)
        val rightWidth = if (rightCount > 0) half / rightCount else 0f
        starts = FloatArray(count)
        widths = FloatArray(count)
        for (i in 0 until count) {
            if (i < leftCount) {
                starts[i] = origin + i * leftWidth
                widths[i] = leftWidth
            } else {
                val slot = i - leftCount
                starts[i] = origin + half + ComposeSlotWidth.value + slot * rightWidth
                widths[i] = rightWidth
            }
        }
    }

    /** Centre of slot [index], interpolated for the fractional index a drag produces. */
    fun center(index: Float): Float {
        if (starts.isEmpty()) return 0f
        val clamped = index.coerceIn(0f, (starts.size - 1).toFloat())
        val low = floor(clamped).toInt()
        val high = ceil(clamped).toInt()
        val lowCenter = starts[low] + widths[low] / 2f
        if (low == high) return lowCenter
        val highCenter = starts[high] + widths[high] / 2f
        return lowCenter + (highCenter - lowCenter) * (clamped - low)
    }

    /**
     * Inverse of [center]: the index whose centre sits at [x].
     *
     * Dragging works in this space rather than in index units, so the pill stays under the finger
     * even though the compose slot makes the slot centres unevenly spaced.
     */
    fun indexAt(x: Float): Float {
        if (starts.isEmpty()) return 0f
        val lastIndex = (starts.size - 1).toFloat()
        val firstCenter = center(0f)
        val lastCenter = center(lastIndex)
        if (x <= firstCenter) return 0f
        if (x >= lastCenter) return lastIndex
        for (i in 0 until starts.size - 1) {
            val from = center(i.toFloat())
            val to = center(i + 1f)
            if (x <= to) return i + (x - from) / (to - from)
        }
        return lastIndex
    }

    /**
     * True when [x], in dp from the capsule's left edge, is over the compose button rather than a
     * destination: the bead must not travel to a slot nobody selected.
     */
    fun isComposeSlot(x: Float): Boolean = x >= composeStart && x <= composeEnd

    /** Centre of the compose slot, in dp from the capsule's left edge. */
    fun composeCenter(): Float = (composeStart + composeEnd) / 2f
}

/**
 * Animation state of the droplet: how far it is grown in. There is no selection state here — the
 * bar has no resting indicator any more, only the finger's lens.
 */
private class LiquidTouch {
    val press = Animatable(0f)
}

/**
 * The droplet: clear, magnifying glass that lives only while the bar is being touched.
 *
 * It only ever moves along the bar: [centerX] is in the capsule's pixels and the vertical centre is
 * the capsule's, so a drag that wanders up the page cannot pull the lens off the bar. [backdrop] is
 * the bar alone — everything the lens shows comes from the bar, which is what the bead is lying on.
 *
 * It grows by growing its own box, never by scaling a layer: a layer scale would also scale the
 * recorded surface the lens reads, and while the bead was growing that left the lens showing the
 * left end of the bar instead of the part it covers. The layer here carries the fade only.
 *
 * The rim is the only thing painted on top: a bead of clear glass has no colour of its own, and
 * without an edge the magnified image would just stop at a shape.
 */
@Composable
private fun LiquidDroplet(
    progress: () -> Float,
    centerX: () -> Float,
    backdrop: Backdrop,
    lightSurface: Boolean
) {
    // Read in composition: the bead's size is part of its layout while it grows.
    val grown = progress()
    val scale = lerp(0.72f, 1f, grown)
    val beadWidth = DropletWidth * scale
    val beadHeight = DropletHeight * scale
    Box(
        modifier = Modifier
            .offset {
                val half = beadWidth.toPx() / 2f
                // How far from its centre the lens actually reads: the magnification pulls the
                // samples in, and the rim's bend pulls them further. Holding the centre that far
                // from the capsule's ends keeps every sample inside the bar, so the bead can never
                // be bitten off at either end.
                val reach = half * (1f / DropletMagnification - DropletBendDepth * 2f)
                val sampled = reach.coerceIn(0f, CapsuleBarWidth.toPx() / 2f)
                val x = centerX().coerceIn(sampled, CapsuleBarWidth.toPx() - sampled)
                // The bar's height is fixed, so a bead taller than it is measured back down to it:
                // centre what was actually laid out, not what was asked for. Otherwise the bead
                // rides above the bar's axis by half the difference.
                val laidOutHeight = minOf(beadHeight, CapsuleBarHeight)
                IntOffset(
                    (x - half).roundToInt(),
                    // Centred on the capsule's axis, whatever the finger's height is.
                    ((CapsuleBarHeight - laidOutHeight).toPx() / 2f).roundToInt()
                )
            }
            .width(beadWidth)
            .height(beadHeight)
            .graphicsLayer { alpha = grown }
            .clip(DropletShape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { DropletShape },
                effects = {
                    // The bead is the whole node here, so its rectangle is just the node's.
                    val halfWidth = size.width / 2f
                    val halfHeight = size.height / 2f
                    liquidDroplet(
                        magnification = DropletMagnification,
                        centerX = halfWidth,
                        centerY = halfHeight,
                        halfWidth = halfWidth,
                        halfHeight = halfHeight,
                        rimHeight = halfHeight * 2f * DropletRimBand,
                        bendDepth = halfHeight * 2f * DropletBendDepth,
                        dispersion = DropletDispersion
                    )
                }
            )
            // A dark hairline first, then the bar's own bevel: the bead has to be tellable from the
            // glass it sits on, and on a light bar the bright bevel alone is nearly invisible.
            .border(
                width = DropletBorderWidth,
                color = if (lightSurface) {
                    Color.Black.copy(alpha = DropletBorderAlpha)
                } else {
                    Color.White.copy(alpha = DropletBorderAlpha * 1.5f)
                },
                shape = DropletShape
            )
            .capsuleHighlight(lightSurface = lightSurface)
    )
}

/**
 * Re-samples the material while the destination cross-fade runs.
 *
 * Neither the recorded page layer nor the surface reading it is invalidated by that fade on its
 * own, so the blur would keep showing the page that was just switched away from. The composition
 * read of [tick] keeps the modifier element fresh; the draw read of [blurRefresh] is what actually
 * invalidates every frame of the fade.
 */
private fun Modifier.redrawOnBackdropChange(
    tick: Float,
    blurRefresh: State<Float>
): Modifier = drawWithContent {
    if (tick < 0f || blurRefresh.value < 0f) {
        return@drawWithContent
    }
    drawContent()
}

/**
 * The capsule's ambient shadow: a stack of faint rounded layers on the bar's enter/exit timeline,
 * but in a layer of its own so the bar's 0-1 fade does not dim the 5% black behind it.
 */
@Composable
private fun CapsuleShadowLayer(
    visible: Boolean,
    lightSurface: Boolean
) {
    AnimatedVisibility(
        visible = visible,
        enter = capsuleShadowEnter,
        exit = capsuleShadowExit
    ) {
        Box(
            modifier = Modifier
                .width(CapsuleBarWidth)
                .height(CapsuleBarHeight)
                .drawBehind {
                    // Day keeps a soft ambient shadow; night gets none, because the reference bar
                    // is separated from black by its rim and a dark halo over gray cards was the
                    // effect to avoid.
                    val shadowMaxAlpha = if (lightSurface) 0.05f else 0f
                    if (shadowMaxAlpha <= 0f || size.height <= 0f) return@drawBehind
                    // Each stacked capsule is faint; together they reach `shadowMaxAlpha` at the
                    // bar and fade to nothing at the edge of the spread.
                    val layerAlpha = 1f - (1f - shadowMaxAlpha).pow(1f / CapsuleShadowLayers)
                    val spread = CapsuleShadowSpread.toPx()
                    val offsetY = CapsuleShadowOffsetY.toPx()
                    // Fixed bar height: a first draw before layout would otherwise produce a
                    // square-cornered block instead of a capsule.
                    val radius = CapsuleBarHeight.toPx() / 2f
                    val step = spread / CapsuleShadowLayers
                    for (i in CapsuleShadowLayers downTo 1) {
                        val grow = step * i
                        drawRoundRect(
                            color = Color.Black.copy(alpha = layerAlpha),
                            topLeft = Offset(-grow, -grow + offsetY),
                            size = Size(size.width + grow * 2f, size.height + grow * 2f),
                            cornerRadius = CornerRadius(radius + grow)
                        )
                    }
                }
        )
    }
}

@Composable
private fun FloatingBarItem(
    destination: MainDestination,
    selected: Boolean,
    hovered: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lightSurface = MiuixTheme.colorScheme.surface.luminance() > 0.5f
    // A finger on an item lights it up exactly like the selection does. Without that the bead would
    // be a magnifier over a bar with nothing selected under it: the lens covers the well of the item
    // it sits on, so the only way to see which one you are on is to have that item look selected —
    // which also puts a themed icon inside the bead.
    val active = selected || hovered
    // The tint cross-fades instead of switching, so the colour arrives with the well rather than
    // popping a frame before it.
    val color by animateColorAsState(
        targetValue = if (active) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.onSurfaceVariantActions
        },
        animationSpec = SelectionRevealSpec,
        label = "FloatingBarItemTint"
    )
    // The selected destination keeps a darker well of its own. With the glass only appearing under a
    // finger, this is the only thing that says where you are; in the dark theme it is a shade deeper
    // than the bar instead of lighter, so it still reads as a well and not as a highlight.
    //
    // A finger over an item tints its icon but does not paint a well: the bead covers that item
    // completely, so the well would only ever be seen through the lens, and the tint alone is what
    // says which item a release would land on.
    val well by animateColorAsState(
        targetValue = if (selected) {
            if (lightSurface) Color.Black.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.36f)
        } else {
            Color.Transparent
        },
        animationSpec = SelectionRevealSpec,
        label = "FloatingBarItemWell"
    )
    // Icon only: a floating bar is narrower than the docked one, and the labels made the icons
    // read as cramped. The destination name stays available to accessibility services.
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(CapsuleShape)
            // No ripple: the bar answers a finger with the droplet, and a ripple spreading underneath
            // it fought the lens for attention.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal = SelectionWellHorizontalInset,
                    vertical = SelectionWellVerticalInset
                )
                .background(well, CapsuleShape)
        )
        Icon(
            imageVector = destination.navIcon(selected),
            contentDescription = stringResource(destination.labelRes),
            tint = color,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ComposeCapsuleButton(
    onClick: () -> Unit,
    blurEnabled: Boolean,
    backdrop: Backdrop
) {
    // Squircle rather than a circle, so it reads as the capsule's own action. Two thirds of the
    // old height: the corner radius shrinks with it, otherwise it would clamp into a pill.
    val squircle = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(ComposeSlotWidth),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(35.dp)
                .clip(squircle)
                .then(
                    if (blurEnabled) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { RectangleShape },
                            effects = { blur(CapsuleButtonBlurRadius, CapsuleButtonBlurRadius) }
                        )
                    } else {
                        Modifier
                    }
                )
                .background(
                    MiuixTheme.colorScheme.primary.copy(alpha = if (blurEnabled) 0.78f else 1f)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.memo_composer_fab_new_memo),
                tint = MiuixTheme.colorScheme.onPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/** Blur radius of the capsule's compose button, in pixels. */
private const val CapsuleButtonBlurRadius = 18f
