package com.payledger.platform.transfer.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class TransferRequestFingerprint {

    private TransferRequestFingerprint() {
    }

    static String calculate(CreateTransferCommand command) {
        String canonicalRequest = String.join(
                "|",
                command.sourceWalletId().toString(),
                command.destinationWalletId().toString(),
                command.initiatedByCustomerId().toString(),
                Long.toString(command.amountMinor()),
                command.currency()
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(
                    digest.digest(
                            canonicalRequest.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 must be available in the Java runtime.",
                    exception
            );
        }
    }
}
