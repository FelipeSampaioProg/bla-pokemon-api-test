package com.pokemon.bff.controller;

import com.pokemon.bff.service.VersionCondition;

import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * HTTP side of the optimistic concurrency of local records: turns a record's version into an {@code ETag} and an
 * {@code If-Match} header into the {@link VersionCondition} the business layer understands (RFC 9110).
 */
public final class EntityTags {
    private static final Pattern ENTITY_TAG = Pattern.compile("(W/)?\"([^\"]*)\"");

    private EntityTags() {
    }

    /** The {@code ETag} of a version: a strong entity tag. */
    public static String format(String version) {
        return version == null ? null : "\"" + version + "\"";
    }

    /**
     * The condition an {@code If-Match} header sets, or {@code null} when it is absent or blank (no condition).
     * {@code *} matches any current version. Weak tags ({@code W/"..."}) never match, because {@code If-Match} uses
     * the strong comparison (RFC 9110, 13.1.1); a header with no strong tag (or no tag at all) can never be satisfied.
     */
    public static VersionCondition parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return null;
        }
        if (ifMatch.trim().equals("*")) {
            return VersionCondition.any();
        }
        var versions = new ArrayList<String>();
        var tags = ENTITY_TAG.matcher(ifMatch);
        while (tags.find()) {
            if (tags.group(1) == null) {
                versions.add(tags.group(2));
            }
        }
        return VersionCondition.oneOf(versions);
    }
}
