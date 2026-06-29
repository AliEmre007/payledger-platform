package com.payledger.platform.payment.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class PaymentIntentRequestFingerprint {

    private PaymentIntentRequestFingerprint() {
    }

    static String calculate(CreatePaymentIntentCommand command) {
        String canonical = String.join(
                "|",
                command.customerId().toString(),
                command.sourceWalletId().toString(),
                command.merchantId().toString(),
                Long.toString(command.amountMinor()),
                command.currency()
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(canonical.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 digest is not available.",
                    exception
            );
        }
    }
}
