# ADR-003: Store Money in Minor Units

## Status
Accepted

## Context

Floating-point types can introduce rounding errors and must not be used for financial amounts.

## Decision

PayLedger will store monetary amounts as integer minor units plus an ISO currency code.

Examples:
- TRY 10.50 is stored as 1050 minor units.
- USD 10.50 is stored as 1050 minor units.

## Consequences

Benefits:
- Exact arithmetic for supported currencies.
- Simple comparison and aggregation.
- No floating-point rounding errors.

Trade-offs:
- Currency-specific decimal rules must be handled carefully.
