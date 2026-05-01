package com.tomdh.courseapi.school.miami

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tomdh.courseapi.webclient.SessionAwareWebClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.sync.withLock
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.http.ResponseEntity
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
    private val logger = org.slf4j.LoggerFactory.getLogger(MiamiClient::class.java)
    private val tokenRegex = Regex("""<input[^>]*name="_token"[^>]*value="([^"]+)"""")

    @jakarta.annotation.PostConstruct
    fun warmUpConnection() {
        val refreshScope = CoroutineScope(Dispatchers.IO)
        refreshScope.launch {
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
            val entity = webClient.get()
                .uri(config.url)
                .header("Accept", "text/html")
                .header("User-Agent", "Mozilla/5.0")
                .retrieve()
                .toEntity(String::class.java)
                .awaitSingle()

            val setCookie = entity.headers.getValuesAsList("Set-Cookie")
            var cookieIdx = 0
            while (cookieIdx < setCookie.size) {
                val cookieStr = setCookie[cookieIdx]
                val parts = cookieStr.split(";")
                if (parts.isNotEmpty()) {
                    val kv = parts[0].split("=")
                    if (kv.size == 2) cookies[kv[0]] = kv[1]
                }
                cookieIdx++
            }
            val result = entity.body ?: ""

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
            val entity = webClient.get()
                .uri(uri)
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .retrieve()
                .toEntity(String::class.java)
                .awaitSingle()

            val setCookie = entity.headers.getValuesAsList("Set-Cookie")
            var cookieIdx = 0
            while (cookieIdx < setCookie.size) {
                val cookieStr = setCookie[cookieIdx]
                val parts = cookieStr.split(";")
                if (parts.isNotEmpty()) {
                    val kv = parts[0].split("=")
                    if (kv.size == 2) cookies[kv[0]] = kv[1]
                }
                cookieIdx++
            }
            mapper.readTree(entity.body ?: "")
        }
    }

    private fun String.encode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8)

    fun buildFormRequest(formParts: ArrayList<String>, filters: Map<String, Any?>, token: String): ArrayList<String> {
        formParts.add("_token=${token.encode()}")
        formParts.add("term=${(filters["term"] as? String ?: "").encode()}")

        val campus = filters.asStringListSafe("campus")
        campus.forEach { formParts.add("campusFilter%5B%5D=${it.encode()}") }

        val subjects = filters.asStringListSafe("subject")
        subjects.forEach { formParts.add("subject%5B%5D=${it.encode()}") }

        formParts.add("courseNumber=${(filters["courseNum"] as? String ?: "").encode()}")
        formParts.add("openWaitlist=${(filters["openWaitlist"] as? String ?: "").encode()}")
        formParts.add("crnNumber=${filters["crn"]?.toString()?.encode() ?: ""}")
        formParts.add("level=${(filters["level"] as? String ?: "").encode()}")
        formParts.add("courseTitle=${(filters["courseTitle"] as? String ?: "").encode()}")
        formParts.add("instructor=")
        formParts.add("instructorUid=")
        formParts.add("creditHours=${filters["creditHours"]?.toString()?.encode() ?: ""}")

        val startEndTime = filters.asStringListSafe("startEndTime")
        if (startEndTime.isNotEmpty()) {
            startEndTime.forEach { formParts.add("startEndTime%5B%5D=${it.encode()}") }
        } else {
            formParts.addAll(listOf("startEndTime%5B%5D=", "startEndTime%5B%5D="))
        }

        formParts.add("courseSearch=Find")

        val delivery = filters.asStringListSafe("delivery")
        delivery.forEach { formParts.add("sectionAttributes%5B%5D=${it.encode()}") }

        val attributes = filters.asStringListSafe("attributes")
        attributes.forEach { formParts.add("sectionFilterAttributes%5B%5D=${it.encode()}") }

        val partOfTerm = filters.asStringListSafe("partOfTerm")
        partOfTerm.forEach { formParts.add("partOfTerm%5B%5D=${it.encode()}") }

        val daysFilter = filters.asStringListSafe("daysFilter")
        daysFilter.forEach { formParts.add("daysFilter%5B%5D=${it.encode()}") }

        return formParts
    }

    private fun Map<String, Any?>.asStringListSafe(key: String): List<String> {
        val value = this[key] ?: return emptyList()
        return when (value) {
            is List<*> -> value
                .filterNotNull()
                .map { it.toString() }
            is String -> listOf(value)
            else -> emptyList()
        }
    }
}
