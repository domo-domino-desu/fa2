package me.domino.fa2.utils.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.summarizeResourceResult
import me.domino.fa2.data.remote.fa.core.HtmlResponseResult
import me.domino.fa2.data.remote.fa.core.summarizeHtmlResult

/** 日志摘要函数测试。 */
class LogSummariesTest {
  @Test
  fun summarizeResourceResultReturnsChineseShortText() {
    assertEquals("Cloudflare 验证", summarizeResourceResult(ResourceResult.CfChallenge))
    assertEquals("成功", summarizeResourceResult(ResourceResult.Success(Unit)))
    assertEquals("受限:仅好友可见", summarizeResourceResult(ResourceResult.MatureBlocked("仅好友可见")))
    assertEquals(
        "失败:网络异常",
        summarizeResourceResult(ResourceResult.Error(IllegalStateException("网络异常"))),
    )
  }

  @Test
  fun summarizeHtmlResultReturnsChineseShortText() {
    assertEquals(
        "成功",
        summarizeHtmlResult(
            HtmlResponseResult.Success(body = "<html/>", url = "https://www.furaffinity.net/")
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
