package com.tomdh.schoolconnector.school.miami

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tomdh.sessionawarewebclient.webclient.SessionAwareWebClient
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class MiamiClient(
    webClient: WebClient,
    private val config: MiamiConfig
) : SessionAwareWebClient(
    webClient = webClient,
    htmlCacheTimeoutMs = config.htmlCacheTimeoutMs,
    tokenTimeoutMs = config.tokenTimeoutMs,
    tokenRefreshThresholdMs = config.tokenRefreshThresholdMs,
    requestTimeoutMs = config.requestTimeoutMs,
    baseUrl = config.url,
    referer = "https://www.apps.miamioh.edu/courselist/",
    origin = "https://www.apps.miamioh.edu"
) {
    private val logger = LoggerFactory.getLogger(MiamiClient::class.java)
    private val tokenRegex = Regex("""<input[^>]*name="_token"[^>]*value="([^"]+)"""")

    /**
     * Fetches the course list HTML on application startup to prepopulate
     * the token and html caches, reducing latency for the first actual query.
     */
    @PostConstruct
    fun warmUpConnection() {
        val warmupScope = CoroutineScope(Dispatchers.IO)
        warmupScope.launch {
            try { getCourseList() }
            catch (e: Exception) { logger.warn("Failed to warm up connection", e) }
        }
    }

    suspend fun getCourseList(forceFresh: Boolean = false): String {
        val now = System.currentTimeMillis()
        val cached = cachedHtml
        val age = now - cachedHtmlTs

        if (!forceFresh && cached != null && age < config.htmlCacheTimeoutMs) return cached

        return htmlCacheLock.withLock {
            val againNow = System.currentTimeMillis()
            val againCached = cachedHtml
            val againAge = againNow - cachedHtmlTs

            val requestFreshAgain = !forceFresh && againCached != null && againAge < config.htmlCacheTimeoutMs
            if (requestFreshAgain) return againCached!!

            logger.info("Fetching fresh course list from {}", config.url)
            val result = webClient.get()
                .uri(config.url)
                .header("Accept", "text/html")
                .header("User-Agent", "Mozilla/5.0")
                .exchangeToMono { response ->
                    response.cookies().forEach { (name, cookieList) ->
                        if (cookieList.isNotEmpty()) cookies[name] = cookieList[0].value
                    }
                    response.bodyToMono(String::class.java)
                }.awaitSingle()

            cachedHtml = result
            cachedHtmlTs = System.currentTimeMillis()
            result
        }
    }

    override suspend fun fetchFreshToken(forceFresh: Boolean): String {
        val html = getCourseList(forceFresh)
        return tokenRegex.find(html)
            ?.groupValues
            ?.get(1)
            ?: ""
    }

    suspend fun getCourseDetails(term: String, crn: String): JsonNode {
        val mapper = jacksonObjectMapper()
        val uri = "${config.url}sectionDetail/${term}/${crn}"
        return htmlCacheLock.withLock {
            val result = webClient.get()
                .uri(uri)
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .exchangeToMono { response ->
                    response.cookies().forEach { (name, cookieList) ->
                        if (cookieList.isNotEmpty()) cookies[name] = cookieList[0].value
                    }
                    response.bodyToMono(String::class.java)
                }.awaitSingle()
            mapper.readTree(result)
        }
    }
}
