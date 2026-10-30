package com.handoff.hook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MiniBoundarySummaryTest {
    @Test void acceptedModelSummaryDoesNotExposeIdentifiersOrQueryValues() {
        MiniAppDispatchTest.Info info = new MiniAppDispatchTest.Info();
        info.launchParam.entryPath += "&token=private-secret";
        String summary = MiniBoundarySummary.describe(info);
        assertTrue(summary.contains("modelResult=accepted"));
        assertTrue(summary.contains("entryPath=valid-video"));
        for (String secret : new String[]{"BV1ZQxV6NE6G", "1109937557", "private-secret", "pages/", "qq_ugc"}) {
            assertFalse(summary.contains(secret), secret);
        }
    }

    @Test void reportsIdentityAndPathRejectionsWithoutChangingTheModel() {
        MiniAppDispatchTest.Info info = new MiniAppDispatchTest.Info();
        info.appId = "other-private-app-id";
        assertTrue(MiniBoundarySummary.describe(info).contains("modelResult=app-identity"));
        info.appId = "1109937557";
        info.launchParam.miniAppId = "other-private-app-id";
        assertTrue(MiniBoundarySummary.describe(info).contains("modelResult=launch-identity-conflict"));
        info.launchParam.miniAppId = "1109937557";
        info.launchParam.entryPath = "pages/index/index?token=secret";
        assertTrue(MiniBoundarySummary.describe(info).contains("modelResult=authoritative-path-rejected"));
        assertEquals("pages/index/index?token=secret", info.launchParam.entryPath);
        assertNull(MiniAppVideo.read(info));
    }

    @Test void identifiesConflictingVideosAndMissingCandidates() {
        MiniAppDispatchTest.Info info = new MiniAppDispatchTest.Info();
        info.firstPage.pagePath = "pages/video/video?aid=170001";
        assertTrue(MiniBoundarySummary.describe(info).contains("modelResult=video-conflict"));
        info.launchParam.entryPath = null;
        info.firstPage.pagePath = null;
        assertTrue(MiniBoundarySummary.describe(info).contains("modelResult=first-page-rejected"));
    }

    @Test void unknownModelsAndWrongFieldTypesAreSafeAndNeverStringified() {
        class Unknown {
            Object appId = new Object() {
                @Override public String toString() { throw new AssertionError("must not stringify"); }
            };
        }
        assertTrue(MiniBoundarySummary.describe(new Unknown()).contains("appId=not-string"));
        assertTrue(MiniBoundarySummary.describe(null).contains("appId=missing"));
    }
}
