package com.toasterofbread.spmp.ui.layout.nowplaying.maintab.thumbnailrow

import LocalNowPlayingExpansion
import LocalPlayerState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import com.toasterofbread.composekit.platform.composable.BackHandler
import com.toasterofbread.composekit.platform.composable.platformClickable
import com.toasterofbread.composekit.utils.common.getInnerSquareSizeOfCircle
import com.toasterofbread.composekit.utils.common.getValue
import com.toasterofbread.composekit.utils.common.thenIf
import com.toasterofbread.composekit.utils.composable.OnChangedEffect
import com.toasterofbread.composekit.utils.modifier.background
import com.toasterofbread.composekit.utils.modifier.disableParentScroll
import com.toasterofbread.spmp.model.mediaitem.MediaItemThumbnailProvider
import com.toasterofbread.spmp.model.mediaitem.db.observePropertyActiveTitle
import com.toasterofbread.spmp.model.mediaitem.song.Song
import com.toasterofbread.spmp.model.mediaitem.song.observeThumbnailRounding
import com.toasterofbread.spmp.model.settings.category.PlayerSettings
import com.toasterofbread.spmp.model.settings.category.ThemeSettings
import com.toasterofbread.spmp.model.settings.getEnum
import com.toasterofbread.spmp.platform.getPixel
import com.toasterofbread.spmp.ui.component.Thumbnail
import com.toasterofbread.spmp.service.playercontroller.PlayerState
import com.toasterofbread.spmp.ui.layout.nowplaying.EXPANDED_THRESHOLD
import com.toasterofbread.spmp.ui.layout.nowplaying.getNPOnBackground
import com.toasterofbread.spmp.ui.layout.nowplaying.maintab.OVERLAY_MENU_ANIMATION_DURATION
import com.toasterofbread.spmp.ui.layout.nowplaying.overlay.*
import com.toasterofbread.spmp.youtubeapi.EndpointNotImplementedException
import kotlin.math.absoluteValue
import kotlin.math.min

internal fun handleThumbnailColourPick(image: ImageBitmap, image_size: IntSize, tap_offset: Offset, onPicked: (Color) -> Unit) {
    val bitmap_size: Int = min(image.width, image.height)
    var x: Float = (tap_offset.x / image_size.width) * bitmap_size
    var y: Float = (tap_offset.y / image_size.height) * bitmap_size

    if (image.width > image.height) {
        x += (image.width - image.height) / 2
    }
    else if (image.height > image.width) {
        y += (image.height - image.width) / 2
    }

    onPicked(image.getPixel(x.toInt(), y.toInt()))
}

@Composable
fun SmallThumbnailRow(
    modifier: Modifier = Modifier,
    horizontal_arrangement: Arrangement.Horizontal,
    onThumbnailLoaded: (Song?, ImageBitmap?) -> Unit,
    setThemeColour: (Color?) -> Unit,
    getSeekState: () -> Float,
    disable_parent_scroll_while_menu_open: Boolean = true,
    overlayContent: (@Composable () -> Unit)? = null
) {
    val player = LocalPlayerState.current
    val expansion = LocalNowPlayingExpansion.current
    val current_song = player.status.m_song

    val song_title: String? by current_song?.observeActiveTitle()
    val song_artist_title: String? by current_song?.Artist?.observePropertyActiveTitle()

    val thumbnail_rounding: Int = current_song.observeThumbnailRounding()
    val thumbnail_shape: RoundedCornerShape = RoundedCornerShape(thumbnail_rounding)

    val overlay_menu: PlayerOverlayMenu? by player.np_overlay_menu
    var current_thumb_image: ImageBitmap? by remember { mutableStateOf(null) }
    var image_size: IntSize by remember { mutableStateOf(IntSize(1, 1)) }

    var colourpick_callback by remember { mutableStateOf<((Color?) -> Unit)?>(null) }
    LaunchedEffect(overlay_menu) {
        colourpick_callback = null
    }

    val main_overlay_menu = remember {
        MainPlayerOverlayMenu(
            { player.openNpOverlayMenu(it) },
            { colourpick_callback = it },
            setThemeColour,
            { player.screen_size.width }
        )
    }

    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontal_arrangement
    ) {
        var opened by remember { mutableStateOf(false) }
        val expanded by remember { derivedStateOf {
            (expansion.get() - 1f).absoluteValue <= EXPANDED_THRESHOLD
        } }

        OnChangedEffect(expanded) {
            if (expanded) {
                opened = true
            }
            else if (opened) {
                player.openNpOverlayMenu(null)
            }
        }

        // Keep thumbnail centered
        Spacer(Modifier)

        Box(Modifier.aspectRatio(1f)) {
            fun performPressAction(long_press: Boolean) {
                if (overlay_menu != null || expansion.get() !in 0.9f .. 1.1f) {
                    return
                }

                val custom_action: Boolean =
                    if (PlayerSettings.Key.OVERLAY_SWAP_LONG_SHORT_PRESS_ACTIONS.get()) !long_press
                    else long_press

                val action: PlayerOverlayMenuAction =
                    if (custom_action) PlayerSettings.Key.OVERLAY_CUSTOM_ACTION.getEnum()
                    else PlayerOverlayMenuAction.DEFAULT

                when (action) {
                    PlayerOverlayMenuAction.OPEN_MAIN_MENU -> player.openNpOverlayMenu(main_overlay_menu)
                    PlayerOverlayMenuAction.OPEN_THEMING -> {
                        player.openNpOverlayMenu(
                            SongThemePlayerOverlayMenu(
                                { colourpick_callback = it },
                                setThemeColour
                            )
                        )
                    }
                    PlayerOverlayMenuAction.PICK_THEME_COLOUR -> {
                        colourpick_callback = { colour ->
                            if (colour != null) {
                                setThemeColour(colour)
                                colourpick_callback = null
                            }
                        }
                    }
                    PlayerOverlayMenuAction.ADJUST_NOTIFICATION_IMAGE_OFFSET -> {
                        player.openNpOverlayMenu(NotifImagePlayerOverlayMenu())
                    }
                    PlayerOverlayMenuAction.OPEN_LYRICS -> {
                        player.openNpOverlayMenu(PlayerOverlayMenu.getLyricsMenu())
                    }
                    PlayerOverlayMenuAction.OPEN_RELATED -> {
                        val related_endpoint = player.context.ytapi.SongRelatedContent
                        if (related_endpoint.isImplemented()) {
                            player.openNpOverlayMenu(RelatedContentPlayerOverlayMenu(related_endpoint))
                        }
                        else {
                            throw EndpointNotImplementedException(related_endpoint)
                        }
                    }
                    PlayerOverlayMenuAction.DOWNLOAD -> {
                        current_song?.also { song ->
                            player.onSongDownloadRequested(song)
                        }
                    }
                }
            }

            Crossfade(current_song, animationSpec = tween(250)) { song ->
                if (song == null) {
                    return@Crossfade
                }

                song.Thumbnail(
                    MediaItemThumbnailProvider.Quality.HIGH,
                    getContentColour = { player.getNPOnBackground() },
                    onLoaded = {
                        current_thumb_image = it
                        onThumbnailLoaded(song, it)
                    },
                    modifier = Modifier
                        .aspectRatio(1f)
                        .onSizeChanged {
                            image_size = it
                        }
                        .thenIf(current_thumb_image != null) {
                            songThumbnailShadow(song, thumbnail_shape)
                        }
                        .platformClickable(
                            onClick = {
                                performPressAction(false)
                            },
                            onAltClick = {
                                performPressAction(true)
                            }
                        )
                )
            }

            // Thumbnail overlay menu
            androidx.compose.animation.AnimatedVisibility(
                overlay_menu != null || colourpick_callback != null,
                Modifier.fillMaxSize(),
                enter = fadeIn(tween(OVERLAY_MENU_ANIMATION_DURATION)),
                exit = fadeOut(tween(OVERLAY_MENU_ANIMATION_DURATION))
            ) {
                val overlay_background_alpha by animateFloatAsState(if (colourpick_callback != null) 0.4f else 0.8f)

                Box(
                    Modifier
                        .thenIf(disable_parent_scroll_while_menu_open) {
                            disableParentScroll(child_does_not_scroll = true)
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { offset ->
                                    colourpick_callback?.also { callback ->
                                        current_thumb_image?.also { image ->
                                            handleThumbnailColourPick(image, image_size, offset, callback)
                                            colourpick_callback = null
                                            return@detectTapGestures
                                        }
                                    }

                                    if (expansion.get() in 0.9f .. 1.1f && overlay_menu?.closeOnTap() == true) {
                                        player.openNpOverlayMenu(null)
                                    }
                                }
                            )
                        }
                        .graphicsLayer { alpha = expansion.getAbsolute() }
                        .fillMaxSize()
                        .background(
                            thumbnail_shape,
                            { Color.DarkGray.copy(alpha = overlay_background_alpha) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(with(LocalDensity.current) {
                                getInnerSquareSizeOfCircle(
                                    radius = image_size.height.toDp().value,
                                    corner_percent = thumbnail_rounding
                                ).dp
                            }),
                        contentAlignment = Alignment.Center
                    ) {
                        BackHandler(overlay_menu != null) {
                            player.navigateNpOverlayMenuBack()
                            colourpick_callback = null
                        }

                        Crossfade(overlay_menu) { menu ->
                            CompositionLocalProvider(LocalContentColor provides Color.White) {
                                menu?.Menu(
                                    { player.status.m_song!! },
                                    { expansion.getAbsolute() },
                                    { player.openNpOverlayMenu(main_overlay_menu) },
                                    getSeekState
                                ) { current_thumb_image }
                            }
                        }
                    }
                }
            }

            overlayContent?.invoke()
        }

        Row(
            Modifier
//                .fillMaxWidth(1f - expansion.getAbsolute())
                .fillMaxWidth()
                .scale(
                    minOf(1f, if (expansion.getAbsolute() < 0.5f) 1f else (1f - ((expansion.getAbsolute() - 0.5f) * 2f))),
                    1f
                ),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.requiredWidth(10.dp))

            Column(Modifier.fillMaxSize().weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
                Text(
                    song_title ?: "",
                    maxLines = 1,
                    color = player.getNPOnBackground(),
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song_artist_title ?: "",
                    maxLines = 1,
                    color = player.getNPOnBackground(),
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            val show_prev_button: Boolean by PlayerSettings.Key.MINI_SHOW_PREV_BUTTON.rememberMutableState()
            ThumbnailRowControlButtons(Modifier.size(40.dp), show_prev_button = show_prev_button)
        }
    }
}

@Composable
internal fun Modifier.songThumbnailShadow(
    song: Song?,
    shape: Shape,
    apply_expansion_to_colour: Boolean = true,
    inGraphicsLayer: GraphicsLayerScope.() -> Unit = {}
): Modifier {
    val player: PlayerState = LocalPlayerState.current
    val default_shadow_radius: Float by ThemeSettings.Key.NOWPLAYING_DEFAULT_SHADOW_RADIUS.rememberMutableState()
    val shadow_radius: Float? by song?.ShadowRadius?.observe(player.database)

    return graphicsLayer {
        shadowElevation = (20.dp * (shadow_radius ?: default_shadow_radius)).toPx()
        this.shape = shape
        clip = true

        val shadow_colour: Color = Color.Black.thenIf(apply_expansion_to_colour) { copy(alpha = player.expansion.get().coerceIn(0f, 1f)) }
        ambientShadowColor = shadow_colour
        spotShadowColor = shadow_colour

        inGraphicsLayer()
    }
}
