package com.toasterofbread.spmp.ui.component.multiselect_context

import LocalPlayerState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toasterofbread.spmp.model.mediaitem.db.observePinnedToHome
import com.toasterofbread.spmp.model.mediaitem.db.setPinned
import com.toasterofbread.spmp.model.mediaitem.playlist.LocalPlaylist
import com.toasterofbread.spmp.model.mediaitem.playlist.Playlist
import com.toasterofbread.spmp.model.mediaitem.playlist.PlaylistEditor
import com.toasterofbread.spmp.model.mediaitem.playlist.PlaylistEditor.Companion.getEditorOrNull
import com.toasterofbread.spmp.model.mediaitem.playlist.PlaylistEditor.Companion.isPlaylistEditable
import com.toasterofbread.spmp.model.mediaitem.song.Song
import com.toasterofbread.spmp.platform.download.DownloadStatus
import com.toasterofbread.spmp.platform.download.rememberSongDownloads
import com.toasterofbread.spmp.resources.getString
import com.toasterofbread.spmp.ui.component.multiselect.MediaItemMultiSelectContext
import com.toasterofbread.spmp.service.playercontroller.PlayerState
import com.toasterofbread.spmp.youtubeapi.impl.youtubemusic.getOrReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

@Composable
internal fun RowScope.MultiSelectGeneralActions(multiselect_context: MediaItemMultiSelectContext) {
    val player: PlayerState = LocalPlayerState.current
    val coroutine_scope: CoroutineScope = rememberCoroutineScope()
    val downloads: List<DownloadStatus> by rememberSongDownloads()

    val selected_items = multiselect_context.selected_items

    val all_are_pinned: Boolean =
        if (selected_items.isEmpty()) false
        else selected_items.all { item ->
            item.first.observePinnedToHome().value
        }

    val all_are_editable_playlists: Boolean by remember { derivedStateOf {
        selected_items.isNotEmpty()
        && selected_items.all { item ->
            item.first is Playlist && (item.first as Playlist).isPlaylistEditable(player.context)
        }
    } }

    val all_are_deletable: Boolean by remember { derivedStateOf {
        if (all_are_editable_playlists) {
            return@derivedStateOf true
        }

        return@derivedStateOf (
            selected_items.isNotEmpty()
            && selected_items.all { item ->
                when (item.first) {
                    is LocalPlaylist -> true
                    is Playlist -> (item.first as Playlist).isPlaylistEditable(player.context)
                    is Song -> downloads.firstOrNull { it.song.id == item.first.id }?.isCompleted() == true
                    else -> false
                }
            }
        )
    } }

    // Pin
    AnimatedVisibility(selected_items.isNotEmpty()) {
        IconButton({
            all_are_pinned.also { pinned ->
                player.database.transaction {
                    for (item in multiselect_context.getUniqueSelectedItems()) {
                        item.setPinned(!pinned, player.context)
                    }
                }
            }
            multiselect_context.onActionPerformed()
        }) {
            Icon(if (all_are_pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, null)
        }
    }

    // TODO | Move to overflow
    // Delete playlist / local song
    AnimatedVisibility(all_are_deletable) {
        IconButton({
            coroutine_scope.launch {
                multiselect_context.getUniqueSelectedItems().mapNotNull { item ->
                    if (item is Playlist) {
                        launch {
                            val editor: PlaylistEditor? = item.getEditorOrNull(player.context).getOrNull()
                            editor?.deletePlaylist()?.getOrReport(player.context, "deletePlaylist")
                        }
                    }
                    else if (item is Song) {
                        launch {
                            player.context.download_manager.deleteSongLocalAudioFile(item)
                        }
                    }
                    else null
                }.joinAll()
                multiselect_context.onActionPerformed()
            }
        }) {
            Icon(Icons.Default.Delete, null)
        }
    }

    // Play after button
    AnimatedVisibility(selected_items.isNotEmpty()) {
        Row(
            Modifier.clickable {
                player.withPlayer {
                    addMultipleToQueue(
                        multiselect_context.getUniqueSelectedItems().filterIsInstance<Song>(),
                        (active_queue_index + 1).coerceAtMost(player.status.m_song_count),
                        is_active_queue = true
                    )
                }
                multiselect_context.onActionPerformed()
            },
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.SubdirectoryArrowRight, null)
            player.withPlayerComposable {
                val distance: Int = active_queue_index - player.status.m_index + 1
                Text(
                    getString(
                        if (distance == 1) "lpm_action_play_after_1_song"
                        else "lpm_action_play_after_x_songs").replace("\$x", distance.toString()
                    ),
                    fontSize = 15.sp
                )
            }
        }
    }
}
