package com.handoff.api.links;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParsedLinkTest {
    @Test void kuaishouOpaqueIdsDoNotRelaxTheExistingDouyinNumericContract() {
        assertDoesNotThrow(() -> new ParsedLink(ParsedLink.Platform.KUAISHOU, ParsedLink.Kind.VIDEO, "fixture", "3xSampleVideo123"));
        assertThrows(IllegalArgumentException.class,
                () -> new ParsedLink(ParsedLink.Platform.DOUYIN, ParsedLink.Kind.VIDEO, "fixture", "3xSampleVideo123"));
        assertDoesNotThrow(() -> new ParsedLink(ParsedLink.Platform.DOUYIN, ParsedLink.Kind.VIDEO, "fixture", "123"));
        assertThrows(IllegalArgumentException.class,
                () -> new ParsedLink(ParsedLink.Platform.DOUYIN, ParsedLink.Kind.VIDEO, "fixture", "0"));
    }

    @Test void opaqueIdsCannotContainPathOrQuerySyntax() {
        for (String id : new String[]{"", "../123", "a/b", "a?b", "a%2fb", "a b", "a".repeat(129)})
            assertThrows(IllegalArgumentException.class,
                    () -> new ParsedLink(ParsedLink.Platform.KUAISHOU, ParsedLink.Kind.VIDEO, "fixture", id));
    }

    @Test void noPlatformMayPretendAShortCodeIsAResolvedVideoId() {
        for (var platform : ParsedLink.Platform.values()) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ParsedLink(platform, ParsedLink.Kind.SHORT_LINK, "fixture", "123"));
            assertDoesNotThrow(() -> new ParsedLink(platform, ParsedLink.Kind.SHORT_LINK, "fixture", null));
        }
    }
}
