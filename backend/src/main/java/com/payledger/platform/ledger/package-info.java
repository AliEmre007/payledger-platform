/**
 * Financial source of truth for PayLedger.
 *
 * This module owns journal entries, immutable ledger postings,
 * double-entry validation, and accounting invariants.
 *
 * Other modules must not write ledger records directly.
 */
package com.payledger.platform.ledger;
