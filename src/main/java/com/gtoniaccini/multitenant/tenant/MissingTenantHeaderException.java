package com.gtoniaccini.multitenant.tenant;

public class MissingTenantHeaderException extends RuntimeException {

    public MissingTenantHeaderException(String message) {
        super(message);
    }
}