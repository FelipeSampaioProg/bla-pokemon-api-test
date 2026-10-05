package com.pokemon.bff.exception;

/**
 * The version condition of a write does not match the record's current version: someone changed it since the client
 * read it. Answered with 412 and the current version as {@code ETag}, so the client knows its copy is stale.
 */
public class PreconditionFailedException extends RuntimeException {
    private final String currentVersion;

    public PreconditionFailedException(String message, String currentVersion) {
        super(message);
        this.currentVersion = currentVersion;
    }

    /** The record's current version, or {@code null} when it does not exist. */
    public String getCurrentVersion() {
        return currentVersion;
    }
}
