package com.gtoniaccini.multitenant.tenant;

public class UnknownTenantException extends RuntimeException {

    public UnknownTenantException(String message) {
        super(message);
    }
}