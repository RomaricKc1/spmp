package com.toasterofbread.spmp.model.mediaitem.playlist

import com.toasterofbread.spmp.db.Database
import com.toasterofbread.spmp.model.mediaitem.MediaItem
import com.toasterofbread.spmp.model.mediaitem.PropertyRememberer
import com.toasterofbread.spmp.model.mediaitem.enums.MediaItemType
import com.toasterofbread.spmp.model.mediaitem.enums.PlaylistType
import com.toasterofbread.spmp.model.mediaitem.layout.MediaItemLayout
import com.toasterofbread.spmp.platform.AppContext

class RemotePlaylistData(
    id: String,
    var item_set_ids: List<String>? = null,
    var continuation: MediaItemLayout.Continuation? = null
): PlaylistData(id), RemotePlaylist {
    var playlist_url: String? = null

    override fun toString(): String = "RemotePlaylistData($id, type=$playlist_type, title=$title)"
    override fun getType(): MediaItemType = MediaItemType.PLAYLIST_REM
    override fun getReference(): RemotePlaylistRef = RemotePlaylistRef(id)

    override fun getDataValues(): Map<String, Any?> =
        super.getDataValues() + mapOf(
            "continuation" to continuation,
            "item_set_ids" to item_set_ids
        )

    override val property_rememberer: PropertyRememberer = PropertyRememberer()

    override fun createDbEntry(db: Database) {
        if (playlist_type == PlaylistType.LOCAL) {
            throw IllegalStateException(id)
        }
        db.playlistQueries.insertById(id, playlist_type?.ordinal?.toLong())
    }
    override fun getEmptyData(): RemotePlaylistData = RemotePlaylistData(id).also { data ->
        data.browse_params = browse_params
    }

    override suspend fun savePlaylist(context: AppContext) {
        saveToDatabase(context.database)
    }

    override fun saveToDatabase(db: Database, apply_to_item: MediaItem, uncertain: Boolean, subitems_uncertain: Boolean) {
        db.transaction { with(apply_to_item as RemotePlaylist) {
            super.saveToDatabase(db, apply_to_item, uncertain, subitems_uncertain)

            items?.also { items ->
                for (item in items) {
                    item.saveToDatabase(db, uncertain = subitems_uncertain)
                }
                Items.overwriteItems(items, db)
            }

            ItemCount.setNotNull(item_count, db, uncertain)
            TypeOfPlaylist.setNotNull(playlist_type, db, uncertain)
            TotalDuration.setNotNull(total_duration, db, uncertain)
            Year.setNotNull(year, db, uncertain)
            Owner.setNotNull(owner, db, uncertain)
            Continuation.setNotNull(continuation, db, uncertain)
            PlaylistUrl.setNotNull(playlist_url, db, uncertain)
            CustomImageUrl.setNotNull(custom_image_url, db, uncertain)
            ImageWidth.setNotNull(image_width, db, uncertain)
            SortType.setNotNull(sort_type, db, uncertain)
        }}
    }
}
