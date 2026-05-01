package com.tomdh.courseapi.school.core

import com.tomdh.courseapi.school.models.Field
import com.tomdh.courseapi.school.models.SchedulableSection

/**
 * Generic interface for school API connectors.
 */
interface SchoolConnector {
    val schoolId: String
    /**
     * Validates that the filters provided are supported by the school.
     * Returns a list of validation error messages, or empty if valid.
     */
    suspend fun validateFilters(filters: Map<String, Any?>): List<String>

    /**
     * Returns the available terms, campuses, etc. for this school.
     */
    suspend fun getSchema(): SchoolSchema

    /**
     * Helper to get just the terms for this school.
     */
    suspend fun getTerms(): List<Field>

    /**
     * Fetches raw course data from the school API.
     * Returns native JSON-like objects (maps/lists).
     */
    suspend fun queryCourses(filters: Map<String, Any?>): List<Any>

    /**
     * Specialized method for the scheduling engine.
     * Returns strongly-typed sections with time intervals.
     */
    suspend fun querySchedulableSections(filters: Map<String, Any?>): List<SchedulableSection>
}
