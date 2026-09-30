package com.mazikox.metin_market_api.ingestion.domain;

public class ImportConflictException extends RuntimeException {
    public ImportConflictException(String message) {
        super(message);
    }
}
