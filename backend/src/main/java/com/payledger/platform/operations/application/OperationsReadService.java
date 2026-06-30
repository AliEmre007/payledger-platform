package com.payledger.platform.operations.application;

import com.payledger.platform.operations.api.OperationPageResponse;
import com.payledger.platform.operations.api.OperationalAuditEventResponse;
import com.payledger.platform.operations.api.OperationalCustomerResponse;
import com.payledger.platform.operations.api.OperationalPaymentIntentResponse;
import com.payledger.platform.operations.api.OperationalReconciliationCaseResponse;
import com.payledger.platform.operations.api.OperationalWalletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class OperationsReadService {

    private final JdbcTemplate jdbcTemplate;

    public OperationsReadService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public OperationPageResponse<OperationalAuditEventResponse> auditEvents(
            String actionType,
            String resourceType,
            int page,
            int size
    ) {
        QueryParts query = new QueryParts(
                "FROM audit_events WHERE 1 = 1"
        );
        query.addEquals("action_type", normalizeUpper(actionType));
        query.addEquals("resource_type", normalizeUpper(resourceType));

        return page(
                """
                        SELECT id, action_type, actor_external_subject,
                               actor_customer_id, resource_type, resource_id,
                               metadata::text AS metadata, created_at
                        """,
                query,
                "ORDER BY created_at DESC, id DESC",
                page,
                size,
                this::mapAuditEvent
        );
    }

    @Transactional(readOnly = true)
    public OperationPageResponse<OperationalCustomerResponse> customers(
            String status,
            String kycStatus,
            int page,
            int size
    ) {
        QueryParts query = new QueryParts(
                "FROM customers WHERE 1 = 1"
        );
        query.addEquals("status", normalizeUpper(status));
        query.addEquals("kyc_status", normalizeUpper(kycStatus));

        return page(
                """
                        SELECT id, customer_type, legal_name, email,
                               status, kyc_status, created_at
                        """,
                query,
                "ORDER BY created_at DESC, id DESC",
                page,
                size,
                this::mapCustomer
        );
    }

    @Transactional(readOnly = true)
    public OperationPageResponse<OperationalWalletResponse> wallets(
            UUID customerId,
            String status,
            int page,
            int size
    ) {
        QueryParts query = new QueryParts(
                "FROM wallets WHERE 1 = 1"
        );
        query.addEquals("customer_id", customerId);
        query.addEquals("status", normalizeUpper(status));

        return page(
                """
                        SELECT id, customer_id, currency, status, created_at
                        """,
                query,
                "ORDER BY created_at DESC, id DESC",
                page,
                size,
                this::mapWallet
        );
    }

    @Transactional(readOnly = true)
    public OperationPageResponse<OperationalPaymentIntentResponse> paymentIntents(
            UUID customerId,
            UUID merchantId,
            String status,
            int page,
            int size
    ) {
        QueryParts query = new QueryParts(
                "FROM payment_intents WHERE 1 = 1"
        );
        query.addEquals("customer_id", customerId);
        query.addEquals("merchant_id", merchantId);
        query.addEquals("status", normalizeUpper(status));

        return page(
                """
                        SELECT id, customer_id, source_wallet_id, merchant_id,
                               amount_minor, currency, status, created_at,
                               authorized_at, captured_at, refunded_at
                        """,
                query,
                "ORDER BY created_at DESC, id DESC",
                page,
                size,
                this::mapPaymentIntent
        );
    }

    @Transactional(readOnly = true)
    public OperationPageResponse<OperationalReconciliationCaseResponse> reconciliationCases(
            UUID merchantId,
            String status,
            int page,
            int size
    ) {
        QueryParts query = new QueryParts(
                "FROM reconciliation_cases WHERE 1 = 1"
        );
        query.addEquals("merchant_id", merchantId);
        query.addEquals("status", normalizeUpper(status));

        return page(
                """
                        SELECT id, settlement_batch_id, merchant_id,
                               provider_reference, status,
                               expected_amount_minor, actual_amount_minor,
                               expected_currency, actual_currency,
                               discrepancy_reason, created_at
                        """,
                query,
                "ORDER BY created_at DESC, id DESC",
                page,
                size,
                this::mapReconciliationCase
        );
    }

    private <T> OperationPageResponse<T> page(
            String selectClause,
            QueryParts query,
            String orderBy,
            int requestedPage,
            int requestedSize,
            RowMapper<T> mapper
    ) {
        int page = Math.max(0, requestedPage);
        int size = Math.min(Math.max(1, requestedSize), 100);
        int offset = Math.multiplyExact(page, size);

        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) " + query.fromAndWhere(),
                Long.class,
                query.parameters().toArray()
        );

        List<Object> parameters = new ArrayList<>(query.parameters());
        parameters.add(size);
        parameters.add(offset);

        List<T> items = jdbcTemplate.query(
                selectClause
                        + " "
                        + query.fromAndWhere()
                        + " "
                        + orderBy
                        + " LIMIT ? OFFSET ?",
                (resultSet, rowNumber) -> mapper.map(resultSet),
                parameters.toArray()
        );

        long totalElements = total == null ? 0 : total;

        return new OperationPageResponse<>(
                page,
                size,
                totalElements,
                (long) offset + items.size() < totalElements,
                items
        );
    }

    private OperationalAuditEventResponse mapAuditEvent(ResultSet resultSet)
            throws SQLException {
        return new OperationalAuditEventResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("action_type"),
                resultSet.getString("actor_external_subject"),
                resultSet.getObject("actor_customer_id", UUID.class),
                resultSet.getString("resource_type"),
                resultSet.getObject("resource_id", UUID.class),
                resultSet.getString("metadata"),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }

    private OperationalCustomerResponse mapCustomer(ResultSet resultSet)
            throws SQLException {
        return new OperationalCustomerResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("customer_type"),
                resultSet.getString("legal_name"),
                resultSet.getString("email"),
                resultSet.getString("status"),
                resultSet.getString("kyc_status"),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }

    private OperationalWalletResponse mapWallet(ResultSet resultSet)
            throws SQLException {
        return new OperationalWalletResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("customer_id", UUID.class),
                resultSet.getString("currency"),
                resultSet.getString("status"),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }

    private OperationalPaymentIntentResponse mapPaymentIntent(
            ResultSet resultSet
    ) throws SQLException {
        return new OperationalPaymentIntentResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("customer_id", UUID.class),
                resultSet.getObject("source_wallet_id", UUID.class),
                resultSet.getObject("merchant_id", UUID.class),
                resultSet.getLong("amount_minor"),
                resultSet.getString("currency"),
                resultSet.getString("status"),
                timestamp(resultSet, "created_at"),
                timestamp(resultSet, "authorized_at"),
                timestamp(resultSet, "captured_at"),
                timestamp(resultSet, "refunded_at")
        );
    }

    private OperationalReconciliationCaseResponse mapReconciliationCase(
            ResultSet resultSet
    ) throws SQLException {
        return new OperationalReconciliationCaseResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("settlement_batch_id", UUID.class),
                resultSet.getObject("merchant_id", UUID.class),
                resultSet.getString("provider_reference"),
                resultSet.getString("status"),
                resultSet.getLong("expected_amount_minor"),
                resultSet.getLong("actual_amount_minor"),
                resultSet.getString("expected_currency"),
                resultSet.getString("actual_currency"),
                resultSet.getString("discrepancy_reason"),
                timestamp(resultSet, "created_at")
        );
    }

    private Instant timestamp(ResultSet resultSet, String column)
            throws SQLException {
        java.sql.Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String normalizeUpper(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim().toUpperCase(Locale.ROOT);
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }

    private static final class QueryParts {
        private final StringBuilder fromAndWhere;
        private final List<Object> parameters = new ArrayList<>();

        private QueryParts(String fromAndWhere) {
            this.fromAndWhere = new StringBuilder(fromAndWhere);
        }

        private void addEquals(String column, Object value) {
            if (value == null) {
                return;
            }

            fromAndWhere.append(" AND ").append(column).append(" = ?");
            parameters.add(value);
        }

        private String fromAndWhere() {
            return fromAndWhere.toString();
        }

        private List<Object> parameters() {
            return parameters;
        }
    }
}
