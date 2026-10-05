package com.pokemon.bff.exception;

/**
 * A request field with an invalid value. Answered with 400, like any {@link IllegalArgumentException}, and the
 * problem body also names the field ({@code "field": "stats[1].value"}) so a client can point at it.
 */
public class InvalidFieldException extends IllegalArgumentException {
    private final String field;

    public InvalidFieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
