package com.tomdh.courseapi.school.core

import com.tomdh.courseapi.school.models.Field

sealed interface SchoolSchemaResult

data class SuccessSchoolSchema(
    val school: String,
    val inputSchema: Map<String, List<Field>>,
    val outputSchema: Map<String, Any?>
) : SchoolSchemaResult

data class ErrorSchoolSchema(
    val error: String,
    val message: String
) : SchoolSchemaResult

data class SchoolSchema(
    val inputSchema: Map<String, List<Field>>,
    val outputSchema: Map<String, Any?>
)

class SchoolRegistry(connectors: List<SchoolConnector>) {
    private val map = connectors.associateBy { it.schoolId }

    fun getConnector(schoolId: String): SchoolConnector {
        return map[schoolId] ?: throw IllegalArgumentException("Unknown school ID: $schoolId")
    }

    fun getAllConnectors(): Collection<SchoolConnector> = map.values

    /**
     * Executes a block with a validated connector.
     * This combines lookup and validation into a single step.
     */
    suspend fun <T> execute(
        schoolId: String, 
        filters: Map<String, Any?>, 
        action: suspend (SchoolConnector) -> T
    ): T {
        val connector = getConnector(schoolId)
        val violations = connector.validateFilters(filters)
        if (violations.isNotEmpty()) {
            // We'll let the caller handle the exception type since the lib shouldn't 
            // necessarily depend on the app's ValidationException.
            // Or we could define a library-specific validation exception.
            throw ConnectorValidationException(violations)
        }
        return action(connector)
    }
}

class ConnectorValidationException(val violations: List<String>) : RuntimeException("Validation failed")
