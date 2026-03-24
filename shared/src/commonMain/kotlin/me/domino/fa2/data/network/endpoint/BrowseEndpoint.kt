package me.domino.fa2.data.network.endpoint

import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.HtmlResponseResult

/**
 * Browse 页面端点。
 */
class BrowseEndpoint(
    private val dataSource: FaHtmlDataSource,
) {
    /**
     * 按完整 URL 拉取 Browse 分页。
     */
    suspend fun fetchByUrl(url: String): HtmlResponseResult = dataSource.get(url)
}
