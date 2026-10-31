package com.handoff.api.links;

import java.util.Objects;

/** A recognized content destination, not evidence of short-link resolution or successful playback. */
public record ParsedLink(Platform platform, Kind kind, String canonicalUrl, String contentId) {
    public enum Platform { DOUYIN, KUAISHOU }
    public enum Kind { SHORT_LINK, VIDEO }

    public ParsedLink {
        Objects.requireNonNull(platform);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(canonicalUrl);
        String idPattern = switch (platform) {
            case DOUYIN -> "[1-9][0-9]{0,19}";
            case KUAISHOU -> "[A-Za-z0-9_-]{1,128}";
        };
        if (kind == Kind.VIDEO && (contentId == null || !contentId.matches(idPattern)))
            throw new IllegalArgumentException("Invalid content ID");
        if (kind == Kind.SHORT_LINK && contentId != null)
            throw new IllegalArgumentException("A short code is not a content ID");
    }
}
