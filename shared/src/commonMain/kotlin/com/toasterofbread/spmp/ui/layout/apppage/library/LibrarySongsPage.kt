package com.toasterofbread.spmp.ui.layout.apppage.library

import LocalPlayerState
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.toasterofbread.composekit.utils.composable.EmptyListCrossfade
import com.toasterofbread.composekit.utils.composable.SubtleLoadingIndicator
import com.toasterofbread.spmp.model.mediaitem.db.rememberLikedSongs
import com.toasterofbread.spmp.model.mediaitem.enums.MediaItemType
import com.toasterofbread.spmp.model.mediaitem.library.MediaItemLibrary
import com.toasterofbread.spmp.model.mediaitem.song.Song
import com.toasterofbread.spmp.platform.AppContext
import com.toasterofbread.spmp.platform.download.DownloadStatus
import com.toasterofbread.spmp.platform.getUiLanguage
import com.toasterofbread.spmp.platform.download.rememberSongDownloads
import com.toasterofbread.spmp.resources.getString
import com.toasterofbread.spmp.resources.uilocalisation.durationToString
import com.toasterofbread.spmp.ui.component.mediaitempreview.MediaItemPreviewLong
import com.toasterofbread.spmp.ui.component.multiselect.MediaItemMultiSelectContext
import com.toasterofbread.spmp.ui.layout.apppage.mainpage.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class LibrarySongsPage(context: AppContext): LibrarySubPage(context) {
    override fun getIcon(): ImageVector =
        MediaItemType.SONG.getIcon()

    private var sorted_songs: List<Song> by mutableStateOf(emptyList())

    override fun canShowAltContent(): Boolean = true
    override fun getAltContentButtons(): Pair<Pair<String, ImageVector>, Pair<String, ImageVector>> =
        Pair(
            Pair(getString("library_songs_downloaded"), Icons.Default.Download),
            Pair(getString("library_songs_liked"), Icons.Default.Favorite)
        )

    @Composable
    override fun Page(
        library_page: LibraryAppPage,
        content_padding: PaddingValues,
        multiselect_context: MediaItemMultiSelectContext,
        showing_alt_content: Boolean,
        modifier: Modifier
    ) {
        val player: PlayerState = LocalPlayerState.current

        val downloads: List<DownloadStatus> by rememberSongDownloads()
        val liked_songs: List<Song>? by rememberLikedSongs()

        LaunchedEffect(Unit) {
            sorted_songs = emptyList()
        }

        with(library_page) {
            LaunchedEffect(downloads, search_filter, sort_type, reverse_sort, showing_alt_content, liked_songs) {
                val songs: List<Song> =
                    if (showing_alt_content) liked_songs ?: emptyList()
                    else downloads.mapNotNull { download -> if (download.progress < 1f) null else download.song }

                val filter: String? = if (search_filter?.isNotEmpty() == true) search_filter else null
                val filtered_songs: List<Song> = songs.filter { song ->
                    filter == null || song.getActiveTitle(player.database)?.contains(filter, true) == true
                }

                sorted_songs = sort_type.sortItems(filtered_songs, player.database, reverse_sort)
            }
        }

        CompositionLocalProvider(LocalPlayerState provides remember { player.copy(onClickedOverride = { item, index ->
            onSongClicked(sorted_songs, player, item as Song, index!!)
        }) }) {
            Column(modifier) {
                EmptyListCrossfade(sorted_songs) { current_songs ->
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = content_padding,
                        verticalArrangement = Arrangement.spacedBy(15.dp)
                    ) {
                        item {
                            LibraryPageTitle(
                                if (showing_alt_content) getString("library_songs_liked_title")
                                else getString("library_songs_downloaded_title")
                            )
                        }

                        if (current_songs == null) {
                            item {
                                Text(
                                    if (library_page.search_filter != null) getString("library_no_items_match_filter")
                                    else if (showing_alt_content) getString("library_no_liked_songs")
                                    else getString("library_no_local_songs"),
                                    Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        else {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    InfoRow(current_songs, Modifier.fillMaxWidth().weight(1f), !showing_alt_content)
                                    
                                    multiselect_context.CollectionToggleButton(
                                        remember(current_songs) { 
                                            current_songs.mapIndexed { index, item -> 
                                                Pair(item, index)
                                            } 
                                        }
                                    )
                                }
                            }

                            itemsIndexed(current_songs, { _, item -> item.id }) { index, song ->
                                MediaItemPreviewLong(
                                    song,
                                    Modifier.fillMaxWidth(),
                                    multiselect_context = multiselect_context,
                                    multiselect_key = index,
                                    show_type = false,
                                    show_play_count = true,
                                    show_download_indicator = false,
                                    getExtraInfo = {
                                        val duration_string: String? = remember(song.id) {
                                            song.Duration.get(player.database)?.let { duration ->
                                                durationToString(duration, player.context.getUiLanguage(), true)
                                            }
                                        }

                                        listOfNotNull(duration_string)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    override fun SideContent(showing_alt_content: Boolean) {
        if (!showing_alt_content && sorted_songs.isEmpty()) {
            LibrarySyncButton()
        }
    }
}

@Composable
private fun InfoRow(songs: List<Song>, modifier: Modifier = Modifier, show_sync_button: Boolean = true) {
    if (songs.isEmpty()) {
        return
    }

    val player: PlayerState = LocalPlayerState.current

    var total_duration_string: String? by remember { mutableStateOf(null) }
    LaunchedEffect(songs) {
        total_duration_string = null

        val duration: Long = songs.sumOf { song ->
            song.Duration.get(player.database) ?: 0
        }
        if (duration == 0L) {
            return@LaunchedEffect
        }

        total_duration_string = durationToString(duration, hl = player.context.getUiLanguage())
    }

    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(getString("library_\$x_songs").replace("\$x", songs.size.toString()))

        total_duration_string?.also { duration ->
            Text("\u2022")
            Text(duration)
        }

        Spacer(Modifier.fillMaxWidth().weight(1f))

        if (show_sync_button) {
            LibrarySyncButton()
        }
    }
}

@Composable
private fun LibrarySyncButton() {
    val player: PlayerState = LocalPlayerState.current
    val coroutine_scope: CoroutineScope = rememberCoroutineScope()

    IconButton({
        if (MediaItemLibrary.song_sync_in_progress) {
            return@IconButton
        }

        coroutine_scope.launch {
            MediaItemLibrary.syncLocalSongs(player.context)
        }
    }) {
        Crossfade(MediaItemLibrary.song_sync_in_progress) { syncing ->
            if (syncing) {
                SubtleLoadingIndicator()
            }
            else {
                Icon(Icons.Default.Sync, null)
            }
        }
    }
}

private fun onSongClicked(songs: List<Song>, player: PlayerState, clicked_song: Song, index: Int) {
    player.withPlayer {
        val ADD_BEFORE = 0
        val ADD_AFTER = 9

        val add_songs = songs
            .mapIndexedNotNull { song_index, song ->
                if (song_index < index && index - song_index > ADD_BEFORE) {
                    return@mapIndexedNotNull null
                }

                if (song_index > index && song_index - index > ADD_AFTER) {
                    return@mapIndexedNotNull null
                }

                song
            }

        val song_index = minOf(ADD_BEFORE, index)
        assert(add_songs[song_index] == clicked_song)

        addMultipleToQueue(add_songs, clear = true)
        seekToSong(song_index)
    }
}
