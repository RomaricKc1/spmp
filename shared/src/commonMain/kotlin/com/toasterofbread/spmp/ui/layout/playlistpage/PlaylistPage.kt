package com.toasterofbread.spmp.ui.layout.playlistpage

import LocalPlayerState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.toasterofbread.composekit.platform.composable.ScrollBarLazyColumn
import com.toasterofbread.composekit.platform.composable.SwipeRefresh
import com.toasterofbread.composekit.utils.common.copy
import com.toasterofbread.composekit.utils.common.getThemeColour
import com.toasterofbread.composekit.utils.common.thenIf
import com.toasterofbread.composekit.utils.composable.stickyHeaderWithTopPadding
import com.toasterofbread.spmp.db.Database
import com.toasterofbread.spmp.model.mediaitem.MediaItem
import com.toasterofbread.spmp.model.mediaitem.MediaItemHolder
import com.toasterofbread.spmp.model.mediaitem.MediaItemSortType
import com.toasterofbread.spmp.model.mediaitem.db.isMediaItemHidden
import com.toasterofbread.spmp.model.mediaitem.loader.MediaItemLoader
import com.toasterofbread.spmp.model.mediaitem.loader.MediaItemThumbnailLoader
import com.toasterofbread.spmp.model.mediaitem.loader.loadDataOnChange
import com.toasterofbread.spmp.model.mediaitem.playlist.LocalPlaylistData
import com.toasterofbread.spmp.model.mediaitem.playlist.Playlist
import com.toasterofbread.spmp.model.mediaitem.playlist.PlaylistData
import com.toasterofbread.spmp.model.mediaitem.playlist.PlaylistEditor
import com.toasterofbread.spmp.model.mediaitem.playlist.PlaylistEditor.Companion.getEditorOrNull
import com.toasterofbread.spmp.model.mediaitem.playlist.RemotePlaylist
import com.toasterofbread.spmp.model.mediaitem.playlist.RemotePlaylistData
import com.toasterofbread.spmp.model.mediaitem.song.Song
import com.toasterofbread.spmp.model.settings.category.FilterSettings
import com.toasterofbread.spmp.model.settings.category.TopBarSettings
import com.toasterofbread.spmp.resources.getString
import com.toasterofbread.spmp.service.playercontroller.LocalPlayerClickOverrides
import com.toasterofbread.spmp.service.playercontroller.PlayerClickOverrides
import com.toasterofbread.spmp.ui.component.WAVE_BORDER_HEIGHT_DP
import com.toasterofbread.spmp.ui.component.WaveBorder
import com.toasterofbread.spmp.ui.component.multiselect.MediaItemMultiSelectContext
import com.toasterofbread.spmp.ui.layout.apppage.AppPageState
import com.toasterofbread.spmp.ui.layout.apppage.AppPageWithItem
import com.toasterofbread.spmp.service.playercontroller.PlayerState
import com.toasterofbread.spmp.youtubeapi.impl.youtubemusic.getOrReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.burnoutcrew.reorderable.ReorderableLazyListState
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

private enum class LoadType {
    AUTO, REFRESH, CONTINUE
}

class PlaylistPage(
    override val state: AppPageState,
    playlist: Playlist,
    val player: PlayerState
): AppPageWithItem() {
    override val item: MediaItemHolder = playlist

    private var previous_item: MediaItemHolder? by mutableStateOf(null)
    override fun onOpened(from_item: MediaItemHolder?) {
        super.onOpened(from_item)
        previous_item = from_item
    }

    var edit_in_progress: Boolean by mutableStateOf(false)
        private set

    var playlist: Playlist by mutableStateOf(playlist)
        private set

    var edited_title: String by mutableStateOf("")
    var edited_image_width: Float? by mutableStateOf(null)
    var edited_image_url: String? by mutableStateOf(null)
        private set

    fun setEditedImageUrl(image_url: String?) {
        coroutine_scope.launch {
            MediaItemThumbnailLoader.invalidateCache(playlist, player.context)
            edited_image_url = image_url
        }
    }

    var reordering: Boolean by mutableStateOf(false)
        private set
    var current_filter: String? by mutableStateOf(null)
        private set
    var sort_type: MediaItemSortType by mutableStateOf(playlist.SortType.get(player.database) ?: MediaItemSortType.NATIVE)
        private set

    var accent_colour: Color? by mutableStateOf(null)
    val coroutine_scope: CoroutineScope = CoroutineScope(Job())

    var playlist_editor: PlaylistEditor? by mutableStateOf(null)
    val multiselect_context = MediaItemMultiSelectContext { context ->
        val editor: PlaylistEditor = playlist_editor ?: return@MediaItemMultiSelectContext

        // Remove selected items from playlist
        Button({
            coroutine_scope.launch {
                val selected_items = context.getSelectedItems().sortedByDescending { it.second!! }
                for (item in selected_items) {
                    editor.removeItem(item.second!!)
                    context.setItemSelected(item.first, false, item.second)
                }
                editor.applyChanges()
            }
        }) {
            Icon(Icons.Default.PlaylistRemove, null)
            Text(getString("song_remove_from_playlist"))
        }
    }

    fun getAccentColour(): Color =
        accent_colour ?: player.theme.accent

    fun beginEdit() {
        edited_title = playlist.getActiveTitle(player.database) ?: ""
        edited_image_width = playlist.ImageWidth.get(player.database)
        edited_image_url = playlist.CustomImageUrl.get(player.database)
        edit_in_progress = true
    }

    fun finishEdit() = coroutine_scope.launch(Dispatchers.IO) {
        edit_in_progress = false

        var changes_made: Boolean = false

        val editor: PlaylistEditor? = playlist_editor
        val data: PlaylistData? = if (editor == null) playlist.getEmptyData() else null

        if (edited_title != playlist.getActiveTitle(player.database)) {
            if (editor != null) {
                editor.setTitle(edited_title)
            }
            else {
                data!!.setDataActiveTitle(edited_title)
            }
            changes_made = true
        }
        if (edited_image_width != playlist.ImageWidth.get(player.database)) {
            if (editor != null) {
                editor.setImageWidth(edited_image_width)
            }
            else {
                data!!.image_width = edited_image_width
            }
            changes_made = true
        }

        if (edited_image_url != playlist.CustomImageUrl.get(player.database)) {
            if (editor != null) {
                editor.setImage(edited_image_url)
            }
            else {
                data!!.custom_image_url = edited_image_url
            }
            changes_made = true
        }

        if (!changes_made) {
            return@launch
        }

        if (editor != null) {
            editor.applyChanges(exclude_item_changes = true)
        }
        else {
            data!!.savePlaylist(player.context)
        }

        // Replace LocalPlaylistData object with a copy to force recomposition, as LocalPlaylistData is not observable
        val local_playlist = playlist as? LocalPlaylistData
        if (local_playlist != null) {
            val replacement = LocalPlaylistData(local_playlist.id)

            local_playlist.populateData(replacement, player.database)
            replacement.title = edited_title
            replacement.custom_image_url = edited_image_url
            replacement.image_width = edited_image_width

            playlist = replacement
        }
    }

    fun setReorderable(value: Boolean) {
        val editor: PlaylistEditor? = playlist_editor
        if (editor == null) {
            reordering = false
            return
        }

        reordering = value

        if (reordering) {
            assert(editor.canMoveItems())
            setSortType(MediaItemSortType.NATIVE)
            current_filter = null
        }
        else {
            coroutine_scope.launch {
                reordering = false
                editor.applyChanges().getOrReport(player.context, "PlaylistPageItemReorder")
            }
        }
    }

    fun setCurrentFilter(value: String?) {
        check(!reordering)
        current_filter = value
    }

    fun setSortType(value: MediaItemSortType) {
        if (value == sort_type) {
            return
        }

        sort_type = value
        coroutine_scope.launch {
            playlist.setSortType(sort_type, player.context)
        }
    }

    fun onThumbnailLoaded(image: ImageBitmap?) {
        accent_colour = image?.getThemeColour()
    }

    @Composable
    override fun ColumnScope.Page(
        multiselect_context: MediaItemMultiSelectContext,
        modifier: Modifier,
        content_padding: PaddingValues,
        close: () -> Unit
    ) {
        val db: Database = player.database
        val click_overrides: PlayerClickOverrides = LocalPlayerClickOverrides.current

        val playlist_items: List<MediaItem>? by playlist.Items.observe(db)
        var sorted_items: List<Pair<MediaItem, Int>>? by remember { mutableStateOf(null) }

        var load_type: LoadType by remember { mutableStateOf(LoadType.AUTO) }
        var load_error: Throwable? by remember { mutableStateOf(null) }
        var loaded_data: RemotePlaylistData? by remember { mutableStateOf(null) }

        val loading: Boolean by playlist.loadDataOnChange(
            player.context,
            onLoadFailed = { error ->
                load_error = error
            },
            onLoadSucceeded = { data ->
                if (data is RemotePlaylistData) {
                    loaded_data = data
                }
            }
        )

        val playlist_data: Playlist = loaded_data ?: playlist
        
        LaunchedEffect(playlist_data) {
            if (playlist_editor?.playlist == playlist_data) {
                return@LaunchedEffect
            }

            val new_editor = playlist_data.getEditorOrNull(player.context).getOrNull()
            if (new_editor != null) {
                playlist_editor?.transferStateTo(new_editor)
            }
            playlist_editor = new_editor
        }

        val apply_item_filter: Boolean by FilterSettings.Key.APPLY_TO_PLAYLIST_ITEMS.rememberMutableState()

        LaunchedEffect(playlist_items, sort_type, current_filter, apply_item_filter) {
            sorted_items = playlist_items?.let { items ->
                val filtered_items = current_filter.let { filter ->
                    items.filter { item ->
                        if (filter != null && item.getActiveTitle(db)?.contains(filter, true) != true) {
                            return@filter false
                        }

                        if (apply_item_filter && !isMediaItemHidden(item, db)) {
                            return@filter false
                        }

                        return@filter true
                    }
                }

                sort_type
                    .sortItems(filtered_items, db)
                    .mapIndexed { index, value ->
                        Pair(value, index)
                    }
            }
        }

        Column(modifier) {
            val items_above: Int = if (previous_item != null) 4 else 3
            val list_state: ReorderableLazyListState = rememberReorderableLazyListState(
                onMove = { from, to ->
                    check(reordering)
                    check(current_filter == null)
                    check(sort_type == MediaItemSortType.NATIVE)

                    if (to.index >= items_above && from.index >= items_above) {
                        sorted_items = sorted_items?.toMutableList()?.apply {
                            add(to.index - items_above, removeAt(from.index - items_above))
                        }
                    }
                },
                onDragEnd = { from, to ->
                    assert(playlist_editor!!.canMoveItems())
                    if (to >= items_above && from >= items_above) {
                        playlist_editor!!.moveItem(from - items_above, to - items_above)
                    }
                }
            )

            val top_bar_showing: Boolean = player.top_bar.MusicTopBar(
                TopBarSettings.Key.SHOW_IN_SEARCH,
                Modifier.fillMaxWidth().zIndex(2f),
                padding = content_padding.copy(bottom = 0.dp)
            ).showing

            AnimatedVisibility(top_bar_showing, Modifier.zIndex(1f)) {
                WaveBorder(
                    Modifier.thenIf(list_state.listState.firstVisibleItemIndex >= items_above) {
                        alpha(0f)
                    }
                )
            }

            val top_padding by animateDpAsState(
                if (top_bar_showing) WAVE_BORDER_HEIGHT_DP.dp
                else content_padding.calculateTopPadding()
            )

            val remote_playlist: RemotePlaylist? = playlist as? RemotePlaylist

            SwipeRefresh(
                state = load_type == LoadType.REFRESH && loading && remote_playlist != null,
                onRefresh = {
                    remote_playlist?.also { remote ->
                        load_type = LoadType.REFRESH
                        load_error = null
                        coroutine_scope.launch {
                            MediaItemLoader.loadRemotePlaylist(remote.getEmptyData(), player.context)
                        }
                    }
                },
                swipe_enabled = !loading,
                modifier = Modifier.fillMaxSize()
            ) {
                CompositionLocalProvider(LocalPlayerClickOverrides provides click_overrides.copy(
                    onClickOverride = { item, multiselect_key ->
                        if (multiselect_key != null) {
                            if (sort_type == MediaItemSortType.NATIVE && current_filter == null) {
                                player.playPlaylist(playlist, multiselect_key)
                                player.onPlayActionOccurred()
                            }
                            else {
                                sorted_items?.also { items ->
                                    player.withPlayer {
                                        addMultipleToQueue(items.mapNotNull { it.first as? Song }, clear = true)
                                        seekToSong(multiselect_key)
                                        player.onPlayActionOccurred()
                                    }
                                }
                            }
                        }
                        else {
                            click_overrides.onMediaItemClicked(item, player)
                        }
                    }
                )) {
                    ScrollBarLazyColumn(
                        state = list_state.listState,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.reorderable(list_state),
                        contentPadding = content_padding.copy(top = top_padding)
                    ) {
                        previous_item?.item?.also { prev ->
                            item {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(close) {
                                        Icon(Icons.Default.KeyboardArrowLeft, null)
                                    }

                                    Spacer(Modifier.fillMaxWidth().weight(1f))

                                    val previous_item_title: String? by prev.observeActiveTitle()
                                    previous_item_title?.also { Text(it) }

                                    Spacer(Modifier.fillMaxWidth().weight(1f))

                                    IconButton({ player.showLongPressMenu(prev) }) {
                                        Icon(Icons.Default.MoreVert, null)
                                    }
                                }
                            }
                        }

                        item {
                            PlaylistTopInfo(sorted_items)
                        }

                        item {
                            PlaylistButtonBar(Modifier.fillMaxWidth())
                        }

                        stickyHeaderWithTopPadding(
                            list_state.listState,
                            if (top_bar_showing) 0.dp else top_padding,
                            Modifier.zIndex(1f).padding(bottom = 5.dp),
                            { player.theme.background }
                        ) {
                            PlaylistInteractionBar(
                                sorted_items,
                                loading = loading && load_type != LoadType.CONTINUE && sorted_items != null,
                                list_state = list_state.listState,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(remember { MutableInteractionSource() }, indication = null) {}
                            )
                        }

                        PlaylistItems(
                            this,
                            list_state,
                            sorted_items
                        )

                        item {
                            PlaylistFooter(
                                sorted_items,
                                getAccentColour(),
                                loading && load_type != LoadType.REFRESH && sorted_items == null,
                                load_error,
                                Modifier.fillMaxWidth().padding(top = 15.dp),
                                onRetry = 
                                    remote_playlist?.let { remote ->
                                        {
                                            load_type = LoadType.REFRESH
                                            load_error = null
                                            coroutine_scope.launch {
                                                MediaItemLoader.loadRemotePlaylist(remote.getEmptyData(), player.context)
                                            }
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}
