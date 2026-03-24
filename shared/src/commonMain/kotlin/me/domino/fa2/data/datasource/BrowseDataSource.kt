package me.domino.fa2.data.datasource

import me.domino.fa2.util.toPageState

import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.SubmissionListingPage
import me.domino.fa2.data.network.endpoint.BrowseEndpoint
import me.domino.fa2.data.parser.GalleryParser

/**
 * Browse 数据源。
 */
class BrowseDataSource(
    private val endpoint: BrowseEndpoint,
    private val parser: GalleryParser,
) {
    /**
     * 拉取 Browse 指定分页。
     */
    suspend fun fetchPage(url: String): PageState<SubmissionListingPage> =
        endpoint.fetchByUrl(url).toPageState { success ->
            parser.parseListing(
                html = success.body,
                baseUrl = success.url,
            )
        }
}
