package com.toasterofbread.spmp.youtubeapi.impl.youtubemusic.endpoint

import com.toasterofbread.spmp.model.FilterChip
import com.toasterofbread.spmp.model.mediaitem.MediaItemData
import com.toasterofbread.spmp.model.mediaitem.enums.MediaItemType
import com.toasterofbread.spmp.model.mediaitem.layout.MediaItemLayout
import com.toasterofbread.spmp.model.mediaitem.layout.MediaItemViewMore
import com.toasterofbread.spmp.model.mediaitem.layout.PlainViewMore
import com.toasterofbread.spmp.model.mediaitem.layout.ViewMore
import com.toasterofbread.spmp.model.sortFilterChips
import com.toasterofbread.spmp.platform.getDataLanguage
import com.toasterofbread.spmp.resources.uilocalisation.AppLocalisedString
import com.toasterofbread.spmp.resources.uilocalisation.LocalisedString
import com.toasterofbread.spmp.resources.uilocalisation.RawLocalisedString
import com.toasterofbread.spmp.resources.uilocalisation.YoutubeLocalisedString
import com.toasterofbread.spmp.youtubeapi.endpoint.HomeFeedEndpoint
import com.toasterofbread.spmp.youtubeapi.endpoint.HomeFeedLoadResult
import com.toasterofbread.spmp.youtubeapi.impl.youtubemusic.DataParseException
import com.toasterofbread.spmp.youtubeapi.impl.youtubemusic.PLAIN_HEADERS
import com.toasterofbread.spmp.youtubeapi.impl.youtubemusic.YoutubeMusicApi
import com.toasterofbread.spmp.youtubeapi.model.BrowseEndpoint
import com.toasterofbread.spmp.youtubeapi.model.NavigationEndpoint
import com.toasterofbread.spmp.youtubeapi.model.TextRuns
import com.toasterofbread.spmp.youtubeapi.model.YoutubeiBrowseResponse
import com.toasterofbread.spmp.youtubeapi.model.YoutubeiHeaderContainer
import com.toasterofbread.spmp.youtubeapi.model.YoutubeiShelf
import com.toasterofbread.spmp.youtubeapi.model.YoutubeiShelfContentsItem
import com.toasterofbread.spmp.youtubeapi.radio.YoutubeiNextResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response

class YTMGetHomeFeedEndpoint(override val api: YoutubeMusicApi): HomeFeedEndpoint() {
    override suspend fun getHomeFeed(
        min_rows: Int,
        allow_cached: Boolean,
        params: String?,
        continuation: String?
    ): Result<HomeFeedLoadResult> {
        val hl: String = api.context.getDataLanguage()
        var last_request: Request? = null

        suspend fun performRequest(ctoken: String?): Result<YoutubeiBrowseResponse> = withContext(Dispatchers.IO) {
            last_request = null

            val endpoint: String = "/youtubei/v1/browse"
            val request: Request = Request.Builder()
                .endpointUrl(if (ctoken == null) endpoint else "$endpoint?ctoken=$ctoken&continuation=$ctoken&type=next")
                .addAuthApiHeaders()
                .addApiHeadersNoAuth(PLAIN_HEADERS)
                .postWithBody(
                    params?.let {
                        mapOf("params" to it)
                    }
                )
                .build()

            last_request = request

            val result: Result<Response> = api.performRequest(request)
            val parsed: YoutubeiBrowseResponse = result.parseJsonResponse {
                return@withContext Result.failure(it)
            }

            return@withContext Result.success(parsed)
        }

        try {
            var data: YoutubeiBrowseResponse = performRequest(continuation).getOrThrow()
            val header_chips: List<FilterChip>? = data.getHeaderChips(api.context)?.sortFilterChips()

            val rows: MutableList<MediaItemLayout> = processRows(data.getShelves(continuation != null), hl).toMutableList()

            var ctoken: String? = data.ctoken
            while (ctoken != null && min_rows >= 1 && rows.size < min_rows) {
                data = performRequest(ctoken).getOrThrow()
                ctoken = data.ctoken

                val shelves = data.getShelves(true)
                if (shelves.isEmpty()) {
                    break
                }

                rows.addAll(processRows(shelves, hl))
            }

            return Result.success(HomeFeedLoadResult(rows, ctoken, header_chips))
        }
        catch (error: Throwable) {
            val request: Request = last_request ?: return Result.failure(error)
            return Result.failure(
                DataParseException.ofYoutubeJsonRequest(
                    request,
                    api,
                    cause = error
                )
            )
        }
    }

    private suspend fun processRows(rows: List<YoutubeiShelf>, hl: String): List<MediaItemLayout> = withContext(Dispatchers.Default) {
        val ret: MutableList<MediaItemLayout> = mutableListOf<MediaItemLayout>()
        for (row in rows) {
            when (val renderer = row.getRenderer()) {
                is YoutubeiHeaderContainer -> {
                    val header = renderer.header?.header_renderer ?: continue

                    fun add(
                        title: LocalisedString,
                        subtitle: LocalisedString? = null,
                        view_more: ViewMore? = null,
                        type: MediaItemLayout.Type? = null
                    ) {
                        val items: MutableList<MediaItemData> = row.getMediaItems(hl).toMutableList()
                        api.database.transaction {
                            for (item in items) {
                                item.saveToDatabase(api.database)
                            }
                        }

                        ret.add(
                            MediaItemLayout(
                                items,
                                title, subtitle,
                                view_more = view_more,
                                type = type
                            )
                        )
                    }

                    val browse_endpoint: BrowseEndpoint? = header.title?.runs?.first()?.navigationEndpoint?.browseEndpoint
                    if (browse_endpoint == null) {
                        add(
                            YoutubeLocalisedString.Type.HOME_FEED.createFromKey(header.title!!.first_text, api.context),
                            header.subtitle?.first_text?.let { YoutubeLocalisedString.Type.HOME_FEED.createFromKey(it, api.context) }
                        )
                        continue
                    }

                    val view_more_page_title_key: String? =
                        when (browse_endpoint.browseId) {
                            "FEmusic_listen_again" -> "home_feed_listen_again"
                            "FEmusic_mixed_for_you" -> "home_feed_mixed_for_you"
                            "FEmusic_new_releases_albums" -> "home_feed_new_releases"
                            "FEmusic_moods_and_genres" -> "home_feed_moods_and_genres"
                            "FEmusic_charts" -> "home_feed_charts"
                            else -> null
                        }

                    if (view_more_page_title_key != null) {
                        add(
                            AppLocalisedString(view_more_page_title_key),
                            null,
                            view_more = PlainViewMore(browse_endpoint.browseId!!),
                            type = when(browse_endpoint.browseId) {
                                "FEmusic_listen_again" -> MediaItemLayout.Type.GRID_ALT
                                else -> null
                            }
                        )
                        continue
                    }

                    val page_type: String? = browse_endpoint.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType
                    if (page_type != null && browse_endpoint.browseId != null) {
                        val media_item = MediaItemType.fromBrowseEndpointType(page_type).referenceFromId(browse_endpoint.browseId).apply {
                            Title.set(header.title.runs?.getOrNull(0)?.text, api.database)
                        }

                        add(
                            RawLocalisedString(header.title.first_text),
                            header.subtitle?.first_text?.let { YoutubeLocalisedString.Type.HOME_FEED.createFromKey(it, api.context) },
                            view_more = MediaItemViewMore(media_item, null)
                        )
                    }
                }
                else -> continue
            }
        }

        return@withContext ret
    }

    data class MusicShelfRenderer(
        val title: TextRuns?,
        val contents: List<YoutubeiShelfContentsItem>? = null,
        val continuations: List<YoutubeiNextResponse.Continuation>? = null,
        val bottomEndpoint: NavigationEndpoint?
    )
}
