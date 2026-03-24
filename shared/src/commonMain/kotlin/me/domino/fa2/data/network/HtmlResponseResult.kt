package me.domino.fa2.data.network

/**
 * HTML 请求分类结果。
 */
sealed interface HtmlResponseResult {
    /**
     * 正常页面。
     * @property body 原始 HTML。
     * @property url 请求地址。
     */
    data class Success(
        /** 原始 HTML 文本。 */
        val body: String,
        /** 最终请求地址。 */
        val url: String,
    ) : HtmlResponseResult

    /**
     * 请求错误。
     * @property statusCode HTTP 状态码。
     * @property message 错误信息。
     */
    data class Error(
        /** HTTP 状态码。 */
        val statusCode: Int,
        /** 错误描述文案。 */
        val message: String,
    ) : HtmlResponseResult

    /**
     * Mature 页面拦截。
     * @property reason 拦截原因。
     */
    data class MatureBlocked(
        /** mature 拦截说明。 */
        val reason: String,
    ) : HtmlResponseResult

    /**
     * Cloudflare challenge 页面。
     * @property cfRay Cloudflare 请求标识。
     */
    data class CfChallenge(
        /** Cloudflare 的 CF-Ray 标识。 */
        val cfRay: String?,
    ) : HtmlResponseResult

    companion object {
        /** Cloudflare 常见 challenge 状态码。 */
        private val cfStatusCodes = setOf(403, 429, 503)

        /** Cloudflare challenge 关键标记。 */
        private val cfMarkers = listOf(
            "<title>just a moment",
            "<title>attention required",
            "<title>checking your browser",
            "id=\"challenge-running\"",
            "id=\"cf-challenge-running\"",
            "id=\"challenge-form\"",
            "name=\"cf-turnstile-response\"",
            "name=\"h-captcha-response\"",
            "__cf_chl_managed_tk__",
            "cf_chl_opt",
        )

        /**
         * 分类入口。
         * @param statusCode HTTP 状态码。
         * @param headers 响应头。
         * @param body 响应体。
         * @param url 请求地址。
         */
        fun classify(
            statusCode: Int,
            headers: Map<String, List<String>>,
            body: String,
            url: String,
        ): HtmlResponseResult {
            val normalizedBody = body.lowercase()
            val server = firstHeader(headers, "server").orEmpty()
            val cfRay = firstHeader(headers, "cf-ray")
            val matchedCfMarker = cfMarkers.any { marker -> normalizedBody.contains(marker) }

            if (matchedCfMarker &&
                (statusCode in cfStatusCodes ||
                        server.contains("cloudflare", ignoreCase = true) ||
                        !cfRay.isNullOrBlank())
            ) {
                return CfChallenge(cfRay = cfRay)
            }

            if (normalizedBody.contains("this submission contains mature") ||
                normalizedBody.contains("registered and enabled")
            ) {
                return MatureBlocked(reason = "Mature content is blocked")
            }

            if (statusCode in 200..299) {
                return Success(body = body, url = url)
            }

            return Error(
                statusCode = statusCode,
                message = "HTTP $statusCode for $url",
            )
        }

        /**
         * 读取第一个同名响应头。
         * @param headers 响应头集合。
         * @param name 目标 header 名。
         */
        private fun firstHeader(
            headers: Map<String, List<String>>,
            name: String,
        ): String? =
            headers.entries
                .firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }
                ?.value
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
    }
}
