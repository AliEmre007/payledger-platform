package com.payledger.platform.ledger.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class UnbalancedJournalEntryDatabaseConstraintTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void databaseRejectsUnbalancedJournalEntryAtCommit() throws Exception {
        UUID debitAccountId = UUID.randomUUID();
        UUID creditAccountId = UUID.randomUUID();
        UUID journalEntryId = UUID.randomUUID();

        String token = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase(Locale.ROOT);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try {
                insertLedgerAccount(
                        connection,
                        debitAccountId,
                        "TEST_ASSET_" + token,
                        "ASSET",
                        "DEBIT"
                );

                insertLedgerAccount(
                        connection,
                        creditAccountId,
                        "TEST_LIABILITY_" + token,
                        "LIABILITY",
                        "CREDIT"
                );

                insertJournalEntry(connection, journalEntryId);

                insertPosting(
                        connection,
                        journalEntryId,
                        debitAccountId,
                        (short) 1,
                        "DEBIT",
                        10_000
                );

                insertPosting(
                        connection,
                        journalEntryId,
                        creditAccountId,
                        (short) 2,
                        "CREDIT",
                        9_000
                );

                assertThatThrownBy(connection::commit)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("not balanced");
            } finally {
                connection.rollback();
            }
        }
    }

    private void insertLedgerAccount(
            Connection connection,
            UUID accountId,
            String accountCode,
            String accountType,
            String normalBalance
    ) throws SQLException {
        String sql = """
                INSERT INTO ledger_accounts (
                    id,
                    account_code,
                    account_type,
                    normal_balance,
                    owner_type,
                    wallet_id,
                    currency,
                    status,
                    created_at
                )
                VALUES (?, ?, ?, ?, 'PLATFORM', NULL, 'TRY', 'ACTIVE', now())
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            statement.setString(2, accountCode);
            statement.setString(3, accountType);
            statement.setString(4, normalBalance);
            statement.executeUpdate();
        }
    }

    private void insertJournalEntry(
            Connection connection,
            UUID journalEntryId
    ) throws SQLException {
        String sql = """
                INSERT INTO journal_entries (
                    id,
                    entry_kind,
                    journal_type,
                    reference_type,
                    reference_id,
                    currency,
                    description,
                    effective_at,
                    created_at
                )
                VALUES (
                    ?,
                    'NORMAL',
                    'TEST_UNBALANCED',
                    'DATABASE_CONSTRAINT_TEST',
                    ?,
                    'TRY',
                    'Intentional invalid entry for constraint testing.',
                    now(),
                    now()
                )
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, journalEntryId);
            statement.setObject(2, UUID.randomUUID());
            statement.executeUpdate();
        }
    }

    private void insertPosting(
            Connection connection,
            UUID journalEntryId,
            UUID ledgerAccountId,
            short lineNumber,
            String direction,
            long amountMinor
    ) throws SQLException {
        String sql = """
                INSERT INTO ledger_postings (
                    id,
                    journal_entry_id,
                    ledger_account_id,
                    line_number,
                    direction,
                    amount_minor,
                    currency,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, 'TRY', now())
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, journalEntryId);
            statement.setObject(3, ledgerAccountId);
            statement.setShort(4, lineNumber);
            statement.setString(5, direction);
            statement.setLong(6, amountMinor);
            statement.executeUpdate();
        }
    }
}
