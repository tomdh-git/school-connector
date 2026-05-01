package com.tomdh.courseapi.school.models

data class CanonicalTimeWindow(
    val day: String, // e.g. "MONDAY", "TUESDAY"
    val startTime: String, // e.g. "10:05am"
    val endTime: String // e.g. "11:00am"
)

data class SchedulableSection(
    val name: String,
    val timeWindows: List<CanonicalTimeWindow>,
    var data: Map<String, Any?> // Raw school-specific data
)

data class Field(val name: String)
