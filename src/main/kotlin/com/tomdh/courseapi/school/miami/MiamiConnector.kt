package com.tomdh.courseapi.school.miami

import com.tomdh.courseapi.course.SchedulableSection
import com.tomdh.courseapi.exceptions.types.APIException
import com.tomdh.courseapi.exceptions.types.QueryException
import com.tomdh.courseapi.field.Field
import com.tomdh.courseapi.school.SchoolConnector
import com.tomdh.courseapi.school.SchoolSchema
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Component
class MiamiConnector(
    private val client: MiamiClient,
    private val config: MiamiConfig
) : SchoolConnector {

    private val logger = LoggerFactory.getLogger(MiamiConnector::class.java)

    override val schoolId: String = "miami"

    @Volatile private var cachedValidFields: MiamiValidFields? = null
    @Volatile private var fieldsCacheTimestamp: Long = 0
    private val fieldsCacheLock = Mutex()

    override suspend fun isAvailable(): Boolean {
        return try { client.getCourseList().isNotEmpty() }
        catch (e: Exception) {
            logger.warn("Miami availability check failed", e)
            false
        }
    }

    override suspend fun queryCourses(filters: Map<String, Any?>): List<SchedulableSection> {
        val token = client.getOrFetchToken()
        if (token.isEmpty()) throw APIException("Empty Token")

        val formParts = ArrayList<String>(24)
        val formBody = buildFormRequest(formParts, filters, token).joinToString("&")
        var resp = client.postResultResponse(formBody)

        val isExpired = resp.status == 419 || resp.body.contains("Page Expired", ignoreCase = true)
        if (isExpired) {
            val freshToken = client.forceFetchToken()
            if (freshToken.isNotEmpty()) {
                formParts[0] = "_token=${
                    URLEncoder.encode(
                        freshToken,
                        StandardCharsets.UTF_8)
                }"
                resp = client.postResultResponse(formParts.joinToString("&"))
            }
        }

        if (resp.body.contains("Your query returned too many results.", ignoreCase = true)) {
            throw QueryException("Query returned too many results.")
        }

        val courses = resp.body.parseMiamiCoursesToSections()
        for (course in courses) {
            val temp = course.data + ("details" to client.getCourseDetails(filters["term"].toString(), course.data["crn"].toString()))
            course.data = temp
        }
        return courses
    }

    override suspend fun validateFilters(filters: Map<String, Any?>): List<String> {
        val errors = mutableListOf<String>()
        val fields = getOrFetchValidFields()

        val allowedKeys = getSchema().inputSchema.keys
        for (key in filters.keys) {
            if (key !in allowedKeys) {
                errors.add("Unknown filter key: '$key'")
            }
        }

        val term = filters["term"] as? String
        if (term.isNullOrEmpty()) {
            errors.add("'term' is required")
        } else if (term !in fields.terms) {
            errors.add("'term' value '$term' is not valid")
        }

        val campus = filters.asStringListSafe("campus")
        if (campus.isEmpty()) {
            errors.add("'campus' is required and must be a non-empty array")
        } else if (!campus.all { it in fields.campuses }) {
            errors.add("'campus' contains invalid values")
        }

        val subjects = filters.asStringListSafe("subject")
        if (subjects.isNotEmpty() && !subjects.all { it in fields.subjects }) {
            errors.add("'subject' contains invalid values")
        }

        val courseNum = filters["courseNum"] as? String
        if (!courseNum.isNullOrEmpty()) {
            if (subjects.isEmpty()) errors.add("'courseNum' requires a 'subject' to be specified")
            if (subjects.size > 1) errors.add("'courseNum' requires exactly one 'subject', got ${subjects.size}")
        }

        val delivery = filters.asStringListSafe("delivery")
        if (delivery.isNotEmpty() && !delivery.all { it in fields.deliveryTypes }) {
            errors.add("'delivery' contains invalid values")
        }

        val attributes = filters.asStringListSafe("attributes")
        if (attributes.isNotEmpty() && !attributes.all { it in fields.attributes }) {
            errors.add("'attributes' contains invalid values")
        }

        val openWaitlist = filters["openWaitlist"] as? String
        if (!openWaitlist.isNullOrEmpty() && openWaitlist !in fields.waitlistTypes) {
            errors.add("'openWaitlist' value '$openWaitlist' is not valid")
        }

        val level = filters["level"] as? String
        if (!level.isNullOrEmpty() && level !in fields.levels) {
            errors.add("'level' value '$level' is not valid")
        }

        val daysFilter = filters.asStringListSafe("daysFilter")
        if (daysFilter.isNotEmpty() && !daysFilter.all { it in fields.days }) {
            errors.add("'daysFilter' contains invalid values")
        }

        val startEndTime = filters.asStringListSafe("startEndTime")
        if (startEndTime.isNotEmpty() && startEndTime.size != 2) {
            errors.add("'startEndTime' must contain exactly 2 values (start and end)")
        }

        return errors
    }

    override fun getSchema(): SchoolSchema = SchoolSchema(
        inputSchema = mapOf(
            "term" to mapOf("type" to "string", "required" to true, "description" to "Academic term code (e.g. '202510')"),
            "campus" to mapOf("type" to "array<string>", "required" to true, "description" to "Campus codes (e.g. ['O', 'H'])"),
            "subject" to mapOf("type" to "array<string>", "required" to false, "description" to "Subject codes (e.g. ['CSE', 'MTH'])"),
            "courseNum" to mapOf("type" to "string", "required" to false, "description" to "Course number (requires exactly one subject)"),
            "crn" to mapOf("type" to "int", "required" to false, "description" to "Course Reference Number"),
            "delivery" to mapOf("type" to "array<string>", "required" to false, "description" to "Delivery methods (e.g. ['Face2Face'])"),
            "attributes" to mapOf("type" to "array<string>", "required" to false, "description" to "Course attributes (e.g. ['PA1C'])"),
            "openWaitlist" to mapOf("type" to "string", "required" to false, "description" to "Waitlist filter (e.g. 'open')"),
            "level" to mapOf("type" to "string", "required" to false, "description" to "Course level (e.g. 'UG', 'GR')"),
            "courseTitle" to mapOf("type" to "string", "required" to false, "description" to "Keywords in the course title"),
            "daysFilter" to mapOf("type" to "array<string>", "required" to false, "description" to "Days of the week (e.g. ['M', 'W', 'F'])"),
            "creditHours" to mapOf("type" to "int", "required" to false, "description" to "Exact credit hours"),
            "startEndTime" to mapOf("type" to "array<string>", "required" to false, "description" to "Time range filter (exactly 2 values)"),
            "partOfTerm" to mapOf("type" to "array<string>", "required" to false, "description" to "Part of term filter")
        ),
        outputSchema = mapOf(
            "subject" to mapOf("type" to "string", "description" to "Subject code"),
            "courseNum" to mapOf("type" to "string", "description" to "Course number"),
            "title" to mapOf("type" to "string", "description" to "Course title"),
            "section" to mapOf("type" to "string", "description" to "Section identifier"),
            "crn" to mapOf("type" to "int", "description" to "Course Reference Number"),
            "campus" to mapOf("type" to "string", "description" to "Campus code"),
            "credits" to mapOf("type" to "int", "description" to "Credit hours"),
            "capacity" to mapOf("type" to "string", "description" to "Section capacity"),
            "requests" to mapOf("type" to "string", "description" to "Enrollment requests"),
            "delivery" to mapOf("type" to "string", "description" to "Delivery/time info (e.g. 'MWF 10:00am-10:50am 01/13-05/02')")
        )
    )

    override suspend fun getTerms(): List<Field> {
        val html = client.getCourseList()
        if (html.isEmpty()) throw APIException("Empty terms")
        return html.parseMiamiTerms()
    }

    internal suspend fun getOrFetchValidFields(): MiamiValidFields = fieldsCacheLock.withLock {
        val now = System.currentTimeMillis()
        val cached = cachedValidFields
        if (cached != null && now - fieldsCacheTimestamp < config.fieldsCacheTimeoutMs) return cached

        val html = client.getCourseList()
        if (html.isEmpty()) throw APIException("Empty response when fetching valid fields")

        val miamiFields = html.parseMiamiFields()
        cachedValidFields = miamiFields
        fieldsCacheTimestamp = now
        miamiFields
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
