package com.payledger.platform.shared.error;

public class WalletAccessDeniedException extends RuntimeException {

    public WalletAccessDeniedException(String message) {
        super(message);
    }
}
