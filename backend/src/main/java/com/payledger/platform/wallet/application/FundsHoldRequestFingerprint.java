package com.payledger.platform.wallet.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class FundsHoldRequestFingerprint {

    private FundsHoldRequestFingerprint() {
    }

    static String calculate(CreateFundsHoldCommand command) {
        String canonicalRequest = String.join(
                "|",
                command.walletId().toString(),
                Long.toString(command.amountMinor()),
                command.currency(),
                command.reason(),
                command.referenceType(),
                command.referenceId().toString()
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
