package com.payledger.platform.kyc.application;

import com.payledger.platform.audit.application.AuditEventCommand;
import com.payledger.platform.audit.application.AuditEventService;
import com.payledger.platform.customer.domain.Customer;
import com.payledger.platform.customer.domain.KycStatus;
import com.payledger.platform.customer.infrastructure.CustomerRepository;
import com.payledger.platform.outbox.application.OutboxEventCommand;
import com.payledger.platform.outbox.application.OutboxEventService;
import com.payledger.platform.shared.error.BusinessRuleViolationException;
import com.payledger.platform.shared.error.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class KycOperationsService {

    private final CustomerRepository customerRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditEventService auditEventService;
    private final OutboxEventService outboxEventService;

    public KycOperationsService(
            CustomerRepository customerRepository,
            JdbcTemplate jdbcTemplate,
            AuditEventService auditEventService,
            OutboxEventService outboxEventService
    ) {
        this.customerRepository = customerRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.auditEventService = auditEventService;
        this.outboxEventService = outboxEventService;
    }

    @Transactional
    public Customer submitForReview(
            UUID customerId,
            String actorExternalSubject,
            String reason
    ) {
        Customer customer = getCustomer(customerId);
        KycStatus fromStatus = customer.getKycStatus();

        try {
            customer.submitKycForReview();
        } catch (IllegalStateException exception) {
            throw new BusinessRuleViolationException(
                    "KYC_INVALID_TRANSITION",
                    exception.getMessage()
            );
        }

        Customer saved = customerRepository.saveAndFlush(customer);
        recordKycChange(
                saved,
                fromStatus,
                saved.getKycStatus(),
                actorExternalSubject,
                reason,
                "KYC_SUBMITTED_FOR_REVIEW"
        );

        return saved;
    }

    @Transactional
    public Customer approve(
            UUID customerId,
            String actorExternalSubject,
            String reason
    ) {
        Customer customer = getCustomer(customerId);
        KycStatus fromStatus = customer.getKycStatus();

        try {
            customer.approveKyc();
        } catch (IllegalStateException exception) {
            throw new BusinessRuleViolationException(
                    "KYC_INVALID_TRANSITION",
                    exception.getMessage()
            );
        }

        Customer saved = customerRepository.saveAndFlush(customer);
        recordKycChange(
                saved,
                fromStatus,
                saved.getKycStatus(),
                actorExternalSubject,
                reason,
                "KYC_APPROVED"
        );

        return saved;
    }

    @Transactional
    public Customer reject(
            UUID customerId,
            String actorExternalSubject,
            String reason
    ) {
        Customer customer = getCustomer(customerId);
        KycStatus fromStatus = customer.getKycStatus();

        try {
            customer.rejectKyc();
        } catch (IllegalStateException exception) {
            throw new BusinessRuleViolationException(
                    "KYC_INVALID_TRANSITION",
                    exception.getMessage()
            );
        }

        Customer saved = customerRepository.saveAndFlush(customer);
        recordKycChange(
                saved,
                fromStatus,
                saved.getKycStatus(),
                actorExternalSubject,
                reason,
                "KYC_REJECTED"
        );

        return saved;
    }

    private Customer getCustomer(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer not found: " + customerId
                ));
    }

    private void recordKycChange(
            Customer customer,
            KycStatus fromStatus,
            KycStatus toStatus,
            String actorExternalSubject,
            String reason,
            String actionType
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO kyc_review_events (
                            id,
                            customer_id,
                            from_status,
                            to_status,
                            reason,
                            actor_external_subject
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                customer.getId(),
                fromStatus.name(),
                toStatus.name(),
                normalizeReason(reason),
                normalizeActor(actorExternalSubject)
        );

        auditEventService.record(
                new AuditEventCommand(
                        actionType,
                        normalizeActor(actorExternalSubject),
                        null,
                        "CUSTOMER",
                        customer.getId(),
                        Map.of(
                                "fromStatus", fromStatus.name(),
                                "toStatus", toStatus.name()
                        )
                )
        );

        if ("KYC_APPROVED".equals(actionType)
                || "KYC_REJECTED".equals(actionType)) {
            outboxEventService.enqueue(
                    new OutboxEventCommand(
                            actionType,
                            "CUSTOMER",
                            customer.getId(),
                            Map.of(
                                    "customerId", customer.getId().toString(),
                                    "fromStatus", fromStatus.name(),
                                    "toStatus", toStatus.name()
                            )
                    )
            );
        }
    }

    private String normalizeActor(String actorExternalSubject) {
        if (actorExternalSubject == null || actorExternalSubject.isBlank()) {
            throw new IllegalArgumentException(
                    "actorExternalSubject is required."
            );
        }

        return actorExternalSubject.trim();
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required.");
        }

        String normalized = reason.trim();

        if (normalized.length() > 500) {
            throw new IllegalArgumentException(
                    "reason must not exceed 500 characters."
            );
        }

        return normalized;
    }
}
