package com.payledger.platform.transfer.api;

import com.payledger.platform.transfer.application.CreateTransferCommand;
import com.payledger.platform.transfer.application.TransferService;
import com.payledger.platform.transfer.domain.Transfer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request
    ) {
        Transfer transfer = transferService.createCompletedTransfer(
                new CreateTransferCommand(
                        request.sourceWalletId(),
                        request.destinationWalletId(),
                        request.initiatedByCustomerId(),
                        request.amountMinor(),
                        request.currency(),
                        idempotencyKey
                )
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(TransferResponse.from(transfer));
    }
}
