package com.tomdh.courseapi.school.health

import com.tomdh.courseapi.school.core.SchoolRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

@Component
class SchoolHealthIndicator(private val registry: SchoolRegistry) : HealthIndicator {
    private val logger = LoggerFactory.getLogger(SchoolHealthIndicator::class.java)

    override fun health(): Health {
        val connectors = registry.getAllConnectors()
        val builder = Health.up()
        
        var allUp = true
        connectors.forEach { connector ->
            // Basic health check: can we fetch the schema/token?
            // In a real scenario, we might want to cache this or use a background job
            // since calling external APIs during health check can be slow.
            // For now, we just report UP if they are registered.
            builder.withDetail(connector.schoolId, "REGISTERED")
        }

        return if (allUp) builder.build() else Health.down().withDetail("reason", "One or more schools unreachable").build()
    }
}
