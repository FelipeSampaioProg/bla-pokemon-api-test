package com.pokemon.bff.service;

/** Pagination rules shared by the PokeAPI and the local enumerations. */
public final class Pagination {
    public static final int MAX_PAGE_SIZE = 100;

    private Pagination() {
    }

    /** Validates the pagination input and returns the upstream offset, rejecting values that overflow it. */
    public static int offset(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        long offset = (long) page * size;
        if (offset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("page is too large for the requested size");
        }
        return (int) offset;
    }
}
