package me.domino.fa2.util.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.network.HtmlResponseResult

/**
 * 日志摘要函数测试。
 */
class LogSummariesTest {
    @Test
    fun summarizePageStateReturnsChineseShortText() {
        assertEquals("加载中", summarizePageState(PageState.Loading))
        assertEquals("Cloudflare 验证", summarizePageState(PageState.CfChallenge))
        assertEquals("成功", summarizePageState(PageState.Success(Unit)))
        assertEquals("受限:仅好友可见", summarizePageState(PageState.MatureBlocked("仅好友可见")))
        assertEquals("失败:网络异常", summarizePageState(PageState.Error(IllegalStateException("网络异常"))))
    }

    @Test
    fun summarizeHtmlResultReturnsChineseShortText() {
        assertEquals(
            "成功",
            summarizeHtmlResult(
                HtmlResponseResult.Success(
                    body = "<html/>",
                    url = "https://www.furaffinity.net/",
                ),
            ),
        )
        assertEquals(
            "Cloudflare验证(cf-ray=abc123)",
            summarizeHtmlResult(HtmlResponseResult.CfChallenge("abc123")),
        )
        assertEquals(
            "受限:Mature content is blocked",
            summarizeHtmlResult(HtmlResponseResult.MatureBlocked("Mature content is blocked")),
        )
        assertEquals(
            "HTTP503:HTTP 503 for /browse/",
            summarizeHtmlResult(HtmlResponseResult.Error(503, "HTTP 503 for /browse/")),
        )
    }
}
