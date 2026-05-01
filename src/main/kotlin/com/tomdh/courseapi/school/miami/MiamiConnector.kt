package com.tomdh.courseapi.school.miami

import com.tomdh.courseapi.school.core.SchoolConnector
import com.tomdh.courseapi.school.core.SchoolSchema
import com.tomdh.courseapi.school.models.Field
import com.tomdh.courseapi.school.models.SchedulableSection
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

@Component
class MiamiConnector(
    private val client: MiamiClient,
    private val config: MiamiConfig
) : SchoolConnector {

    override val schoolId: String = "miami"

    override suspend fun validateFilters(filters: Map<String, Any?>): List<String> {
        val errors = mutableListOf<String>()
        val fields = getOrFetchValidFields()

        // Term is always required for Miami
        val term = filters["term"] as? String
        if (term.isNullOrBlank()) {
            errors.add("'term' filter is required")
        } else if (!fields.terms.contains(term)) {
            errors.add("Invalid term: '$term'")
        }

        // Check for unknown keys
        val validKeys = setOf(
            "term", "campus", "subject", "courseNum", "openWaitlist", 
            "crn", "level", "courseTitle", "creditHours", "startEndTime", 
            "delivery", "attributes", "partOfTerm", "daysFilter"
        )
        filters.keys.forEach { key ->
            if (!validKeys.contains(key)) {
                errors.add("Unknown filter key: '$key'")
            }
        }

        return errors
    }

    @Cacheable("schoolSchema")
    override suspend fun getSchema(): SchoolSchema {
        val fields = getOrFetchValidFields()
        return SchoolSchema(
            inputSchema = mapOf(
                "term" to fields.terms.map { Field(it) },
                "campus" to fields.campuses.map { Field(it) },
                "subject" to fields.subjects.map { Field(it) },
                "level" to fields.levels.map { Field(it) },
                "partOfTerm" to fields.partOfTerms.map { Field(it) },
                "attributes" to fields.attributes.map { Field(it) },
                "delivery" to fields.deliveries.map { Field(it) }
            ),
            outputSchema = mapOf(
                "subject" to "String",
                "courseNum" to "String",
                "title" to "String",
                "crn" to "Int",
                "delivery" to "String"
            )
        )
    }

    override suspend fun getTerms(): List<Field> {
        return getSchema().inputSchema["term"] ?: emptyList()
    }

    override suspend fun queryCourses(filters: Map<String, Any?>): List<Any> {
        val token = client.getOrFetchToken()
        val formBody = client.buildFormRequest(ArrayList(), filters, token).joinToString("&")
        
        val response = client.postResultResponse(formBody)
        if (response.status != 200) {
            // If session expired (common 419), force fresh token once
            val newToken = client.forceFetchToken()
            val retryBody = client.buildFormRequest(ArrayList(), filters, newToken).joinToString("&")
            return client.postResultResponse(retryBody).body.parseMiamiCoursesToMaps()
        }
        
        return response.body.parseMiamiCoursesToMaps()
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun querySchedulableSections(filters: Map<String, Any?>): List<SchedulableSection> {
        val rawCourses = queryCourses(filters) as List<Map<String, Any?>>
        return rawCourses.map { it.toSchedulableSection() }
    }

    private suspend fun getOrFetchValidFields(): MiamiFields {
        val html = client.getCourseList()
        return html.parseMiamiFields()
    }
}
