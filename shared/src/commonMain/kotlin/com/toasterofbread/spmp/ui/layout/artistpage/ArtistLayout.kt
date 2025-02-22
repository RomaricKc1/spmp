package com.toasterofbread.spmp.ui.layout.artistpage

import LocalPlayerState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.toasterofbread.composekit.platform.composable.SwipeRefresh
import com.toasterofbread.composekit.settings.ui.Theme
import com.toasterofbread.composekit.utils.common.getThemeColour
import com.toasterofbread.composekit.utils.composable.getTop
import com.toasterofbread.composekit.utils.modifier.background
import com.toasterofbread.composekit.utils.modifier.brushBackground
import com.toasterofbread.composekit.utils.modifier.drawScopeBackground
import com.toasterofbread.composekit.utils.modifier.horizontal
import com.toasterofbread.spmp.model.mediaitem.MediaItem
import com.toasterofbread.spmp.model.mediaitem.MediaItemThumbnailProvider
import com.toasterofbread.spmp.model.mediaitem.artist.Artist
import com.toasterofbread.spmp.model.mediaitem.loader.MediaItemThumbnailLoader
import com.toasterofbread.spmp.model.settings.category.TopBarSettings
import com.toasterofbread.spmp.ui.component.Thumbnail
import com.toasterofbread.spmp.ui.component.WaveBorder
import com.toasterofbread.spmp.ui.component.multiselect.MediaItemMultiSelectContext
import com.toasterofbread.spmp.ui.component.multiselect.MultiSelectItem
import com.toasterofbread.spmp.service.playercontroller.PlayerState

private const val ARTIST_IMAGE_SCROLL_MODIFIER = 0.25f

@Composable
fun ArtistLayout(
    artist: Artist,
    modifier: Modifier = Modifier,
    previous_item: MediaItem? = null,
    content_padding: PaddingValues = PaddingValues(),
    multiselect_context: MediaItemMultiSelectContext? = null,
    show_top_bar: Boolean = true,
    loading: Boolean = false,
    onReload: (() -> Unit)? = null,
    getAllSelectableItems: (() -> List<List<MultiSelectItem>>)? = null,
    content: LazyListScope.(accent_colour: Color?, Modifier) -> Unit
) {
    val player: PlayerState = LocalPlayerState.current
    val density: Density = LocalDensity.current

    val thumbnail_provider: MediaItemThumbnailProvider? by artist.ThumbnailProvider.observe(player.database)

    LaunchedEffect(thumbnail_provider) {
        thumbnail_provider?.also { provider ->
            MediaItemThumbnailLoader.loadItemThumbnail(
                artist,
                provider,
                MediaItemThumbnailProvider.Quality.HIGH,
                player.context
            )
        }
    }

    // TODO display previous_item

    val screen_width: Dp = player.screen_size.width

    val main_column_state: LazyListState = rememberLazyListState()

    val background_modifier: Modifier = Modifier.background({ player.theme.background })
    val gradient_size = 0.35f
    var accent_colour: Color? by remember { mutableStateOf(null) }

    val top_bar_over_image: Boolean by TopBarSettings.Key.DISPLAY_OVER_ARTIST_IMAGE.rememberMutableState()
    var music_top_bar_showing: Boolean by remember { mutableStateOf(false) }
    val top_bar_alpha: Float by animateFloatAsState(if (!top_bar_over_image || music_top_bar_showing || multiselect_context?.is_active == true) 1f else 0f)

    fun getBackgroundAlpha(): Float = with (density) {
        if (!top_bar_over_image || main_column_state.firstVisibleItemIndex > 0) top_bar_alpha
        else (0.5f + ((main_column_state.firstVisibleItemScrollOffset / screen_width.toPx()) * 0.5f)) * top_bar_alpha
    }

    fun Theme.getBackgroundColour(): Color = background.copy(alpha = getBackgroundAlpha())

    @Composable
    fun TopBar() {
        Column(
            Modifier
                .drawScopeBackground {
                    player.theme.getBackgroundColour()
                }
                .pointerInput(Unit) {}
                .zIndex(1f)
        ) {
            val showing = music_top_bar_showing || multiselect_context?.is_active == true
            AnimatedVisibility(showing) {
                Spacer(Modifier.height(WindowInsets.getTop()))
            }

            music_top_bar_showing = player.top_bar.MusicTopBar(
                TopBarSettings.Key.SHOW_IN_ARTIST,
                Modifier.fillMaxWidth().zIndex(1f),
                padding = content_padding.horizontal
            ).showing

            multiselect_context?.InfoDisplay(
                Modifier.padding(top = 10.dp).padding(content_padding.horizontal),
                getAllItems = getAllSelectableItems
            )

            AnimatedVisibility(showing) {
                WaveBorder(
                    Modifier.fillMaxWidth(),
                    getColour = { background },
                    getAlpha = { getBackgroundAlpha() }
                )
            }
        }
    }

    Column(modifier) {
        if (show_top_bar && !top_bar_over_image) {
            TopBar()
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (show_top_bar && top_bar_over_image) {
                TopBar()
            }

            artist.Thumbnail(
                MediaItemThumbnailProvider.Quality.HIGH,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .offset {
                        IntOffset(0, (main_column_state.firstVisibleItemScrollOffset * -ARTIST_IMAGE_SCROLL_MODIFIER).toInt())
                    },
                onLoaded = { thumbnail ->
                    if (accent_colour == null) {
                        accent_colour = thumbnail?.getThemeColour()?.let { player.theme.makeVibrant(it) }
                    }
                }
            )

            Spacer(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .brushBackground {
                        Brush.verticalGradient(
                            0f to player.theme.background,
                            gradient_size to Color.Transparent
                        )
                    }
            )

            SwipeRefresh(
                state = loading,
                onRefresh = {
                    onReload?.invoke()
                },
                swipe_enabled = !loading && onReload != null,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    main_column_state,
                    contentPadding = PaddingValues(bottom = content_padding.calculateBottomPadding())
                ) {

                    val play_button_size: Dp = 55.dp
                    val action_bar_height: Dp = 32.dp

                    // Image spacing
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.1f)
                                .brushBackground {
                                    Brush.verticalGradient(
                                        1f - gradient_size to Color.Transparent,
                                        1f to player.theme.background
                                    )
                                },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            TitleBar(
                                artist,
                                Modifier
                                    .offset {
                                        IntOffset(0, (main_column_state.firstVisibleItemScrollOffset * ARTIST_IMAGE_SCROLL_MODIFIER).toInt())
                                    }
                                    .padding(bottom = (play_button_size - action_bar_height) / 2f)
                            )
                        }
                    }

                    item {
                        ArtistActionBar(
                            artist,
                            background_modifier.padding(bottom = 20.dp, end = 10.dp).fillMaxWidth().requiredHeight(action_bar_height),
                            content_padding.horizontal,
                            height = action_bar_height,
                            play_button_size = play_button_size,
                            accent_colour = accent_colour
                        )
                    }

                    content(accent_colour, background_modifier.fillMaxWidth())
                }
            }
        }
    }
}
