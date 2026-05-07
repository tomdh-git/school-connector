package com.tomdh.schoolconnector.school

import com.tomdh.schoolconnector.course.SchedulableSection
import com.tomdh.schoolconnector.field.Field

interface SchoolConnector {
    val schoolId: String

    fun supportsScheduling(): Boolean = true

    suspend fun isAvailable(): Boolean

    /**
     * Query courses using school-specific filters.
     * Each connector maps the raw JSON filter map to its own query format,
     * then returns results as [SchedulableSection]s with canonical fields + raw data.
     * Includes full details (e.g. section detail API calls).
     */
    suspend fun queryCourses(filters: Map<String, Any?>): List<SchedulableSection>

    /**
     * Query courses without fetching per-section details.
     * Much faster than [queryCourses] — only parses the listing page.
     * Use this when you only need timeWindows for scheduling, then call
     * [fetchSectionDetails] selectively on the sections you actually need.
     */
    suspend fun queryCoursesLight(filters: Map<String, Any?>): List<SchedulableSection> =
        queryCourses(filters)

    /**
     * Populate details for a batch of sections in parallel.
     * [term] is the academic term code. Each section's data["crn"] is used to fetch details.
     * Mutates each section's [SchedulableSection.data] in place.
     */
    suspend fun fetchSectionDetails(term: String, sections: List<SchedulableSection>) {}

    /**
     * Validate input filters and return a list of ALL validation errors.
     * Empty list = valid input. This replaces the old standalone validators.
     */
    suspend fun validateFilters(filters: Map<String, Any?>): List<String>

    /**
     * Describe the input/output schema for this school.
     * Used by the getSchoolSchema introspection query.
     */
    fun getSchema(): SchoolSchema

    /**
     * Get available terms/periods for this school.
     */
    suspend fun getTerms(): List<Field>
}

/**
 * Describes what filter keys a school accepts (inputSchema)
 * and what fields its course output contains (outputSchema).
 */
data class SchoolSchema(
    val inputSchema: Map<String, Any?>,
    val outputSchema: Map<String, Any?>
)
