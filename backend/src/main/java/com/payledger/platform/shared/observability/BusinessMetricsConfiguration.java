package com.payledger.platform.shared.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class BusinessMetricsConfiguration {

    public BusinessMetricsConfiguration(
            MeterRegistry meterRegistry,
            JdbcTemplate jdbcTemplate
    ) {
        Gauge.builder(
                        "payledger_outbox_backlog",
                        jdbcTemplate,
                        template -> queryLong(
                                template,
                                "SELECT count(*) FROM outbox_events WHERE status = 'PENDING'"
                        )
                )
                .description("Pending transactional outbox events.")
                .register(meterRegistry);

        Gauge.builder(
                        "payledger_reconciliation_open_cases",
                        jdbcTemplate,
                        template -> queryLong(
                                template,
                                """
                                        SELECT count(*)
                                        FROM reconciliation_cases
                                        WHERE status IN ('OPEN', 'INVESTIGATING')
                                        """
                        )
                )
                .description("Open or investigating reconciliation cases.")
                .register(meterRegistry);
    }

    private static long queryLong(JdbcTemplate jdbcTemplate, String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
