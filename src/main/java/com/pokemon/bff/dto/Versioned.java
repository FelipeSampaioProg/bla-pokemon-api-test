package com.pokemon.bff.dto;

/**
 * A local record's representation together with the record's version (an opaque token). The API layer sends the
 * version as the {@code ETag} header (never in the body), and clients send it back in {@code If-Match} so a write
 * fails with 412 instead of overwriting a change made since they read the record.
 */
public record Versioned<T>(T body, String version) {
}
