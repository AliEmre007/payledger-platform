package com.payledger.platform.transfer.api;

import com.payledger.platform.transfer.domain.Transfer;
import com.payledger.platform.transfer.domain.TransferStatus;

import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID sourceWalletId,
        UUID destinationWalletId,
        UUID journalEntryId,
        long amountMinor,
        String currency,
        TransferStatus status
) {
    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getSourceWalletId(),
                transfer.getDestinationWalletId(),
                transfer.getJournalEntryId(),
                transfer.getAmountMinor(),
                transfer.getCurrency(),
                transfer.getStatus()
        );
    }
}
