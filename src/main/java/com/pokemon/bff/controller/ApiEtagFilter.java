package com.pokemon.bff.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * Adds an {@code ETag} to successful {@code GET} responses of the API and answers {@code 304 Not Modified} when the
 * client sends a matching {@code If-None-Match}, so an unchanged page or detail is not transferred again.
 * <p>
 * The tag is a hash of the body, computed after the response is built: it saves bandwidth, not server work.
 * Responses sent with {@code Cache-Control: no-store} (partial PokeAPI data) get no ETag.
 */
@Component
public class ApiEtagFilter extends ShallowEtagHeaderFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }
}
