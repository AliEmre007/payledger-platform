# ADR-004: Require Idempotency for Money-Moving Commands

## Status
Accepted

## Context

Clients, networks, payment providers, and message queues may retry requests after timeouts or failures.

Without idempotency, one customer action could cause duplicated transfers or duplicated charges.

## Decision

Every endpoint that can move money, create a payment, issue a refund, or change settlement state will require an idempotency key.

The platform will store the key, request fingerprint, processing state, and final response.

## Consequences

Benefits:
- Safe retries.
- Reduced duplicate financial operations.
- Better operational recovery.

Trade-offs:
- Additional persistence and lifecycle management.
- Request payload consistency must be validated.
