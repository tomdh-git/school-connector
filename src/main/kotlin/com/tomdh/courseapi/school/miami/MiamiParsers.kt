package com.tomdh.courseapi.school.miami

import com.tomdh.courseapi.school.models.CanonicalTimeWindow
import com.tomdh.courseapi.school.models.Field
import com.tomdh.courseapi.school.models.SchedulableSection
import org.jsoup.Jsoup

/**
 * Parses Miami University course list HTML using Jsoup.
 */

fun String.parseMiamiCoursesToMaps(): List<Map<String, Any?>> {
    val doc = Jsoup.parse(this)
    val rows = doc.select("#courseSectionSummary tr.resultrow")
    
    return rows.map { row ->
        val cols = row.select("td")
        val subject = cols[0].text()
        val courseNum = cols[1].text()
        val title = cols[2].text()
        val section = cols[3].text()
        val crn = cols[4].text().toIntOrNull() ?: 0
        val campus = cols[5].text()
        val creditHours = cols[6].text()
        val enrollment = cols[7].text()
        val waitlist = cols[8].text()
        val delivery = cols[9].text()

        mapOf(
            "subject" to subject,
            "courseNum" to courseNum,
            "title" to title,
            "section" to section,
            "crn" to crn,
            "campus" to campus,
            "creditHours" to creditHours,
            "enrollment" to enrollment,
            "waitlist" to waitlist,
            "delivery" to delivery
        )
    }
}

fun Map<String, Any?>.toSchedulableSection(): SchedulableSection {
    val subject = this["subject"]?.toString() ?: ""
    val courseNum = this["courseNum"]?.toString() ?: ""
    val title = this["title"]?.toString() ?: ""
    val delivery = this["delivery"]?.toString() ?: ""

    return SchedulableSection(
        name = "$subject $courseNum - $title",
        timeWindows = parseMiamiDeliveryToTimeWindows(delivery),
        data = this
    )
}

fun parseMiamiDeliveryToTimeWindows(delivery: String): List<CanonicalTimeWindow> {
    if (delivery.isBlank() || delivery.contains("TBA")) return emptyList()

    val windows = mutableListOf<CanonicalTimeWindow>()
    val parts = delivery.split(Regex("""(?<=\d{2}/\d{2})\s+"""))

    for (part in parts) {
        val match = Regex("""([MTWRF]+)\s+(\d{1,2}:\d{2}[ap]m)-(\d{1,2}:\d{2}[ap]m)\s+(\d{2}/\d{2})-(\d{2}/\d{2})""")
            .find(part) ?: continue

        val days = match.groupValues[1]
        val startTime = match.groupValues[2]
        val endTime = match.groupValues[3]

        days.forEach { char ->
            val day = when (char) {
                'M' -> "MONDAY"
                'T' -> "TUESDAY"
                'W' -> "WEDNESDAY"
                'R' -> "THURSDAY"
                'F' -> "FRIDAY"
                else -> null
            }
            if (day != null) {
                windows.add(CanonicalTimeWindow(day, startTime, endTime))
            }
        }
    }
    return windows
}

fun String.parseMiamiFields(): MiamiFields {
    val doc = Jsoup.parse(this)
    return MiamiFields(
        terms = doc.select("#termFilter option").map { it.attr("value") }.filter { it.isNotEmpty() },
        campuses = doc.select("#campusFilter option").map { it.attr("value") }.filter { it.isNotEmpty() },
        subjects = doc.select("#subject option").map { it.attr("value") }.filter { it.isNotEmpty() },
        levels = doc.select("#level option").map { it.attr("value") }.filter { it.isNotEmpty() },
        partOfTerms = doc.select("#partOfTerm option").map { it.attr("value") }.filter { it.isNotEmpty() },
        attributes = doc.select("#sectionFilterAttributes option").map { it.attr("value") }.filter { it.isNotEmpty() },
        deliveries = doc.select("#sectionAttributes option").map { it.attr("value") }.filter { it.isNotEmpty() }
    )
}

fun String.parseMiamiTerms(): List<Field> {
    val doc = Jsoup.parse(this)
    return doc.select("#termFilter option")
        .map { Field(it.attr("value")) }
        .filter { it.name.isNotEmpty() }
}

data class MiamiFields(
    val terms: List<String>,
    val campuses: List<String>,
    val subjects: List<String>,
    val levels: List<String>,
    val partOfTerms: List<String>,
    val attributes: List<String>,
    val deliveries: List<String>
)
