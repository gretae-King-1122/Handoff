package com.handoff.hook;

import com.handoff.api.MiniAppEvent;
import com.handoff.api.Source;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResolvedAppDataTest {
    static class BaseInfo {
        String appId = "1109937557";
        String name = "哔哩哔哩";
        FirstPage firstPage = new FirstPage();
        String link = "https://m.q.qq.com/a/s/1095da49c8b851de6182afd0ce8eadda";
        // 不允许出现在事件或日志中。
        String openId = "secret-open-id";
    }

    static class SdkInfo extends BaseInfo {
        Params launchParam = new Params();
    }

    static class FirstPage {
        String pagePath = "pages/video/video?bvid=BV1xx411c7mD";
    }

    static class Params {
        String miniAppId;
        String entryPath;
        String fakeUrl;
        int scene = 1036;
        long launchClickTimeMillis = 42;
    }

    static class Config {
        BaseInfo config = new BaseInfo();
        Params launchParam = new Params();
        String entryPath;
        String link = "original-share-link";
    }

    private MiniAppEvent read(Object value) {
        return ResolvedAppData.read(value, Source.RESOLVED, "qq:mini", null, Map.of("__hook", "test"));
    }

    @Test void readsInheritedSdkIdentityAndFirstPageWithoutLaunchId() {
        MiniAppEvent e = read(new SdkInfo());
        assertEquals("1109937557", e.getMiniAppId());
        assertEquals("pages/video/video?bvid=BV1xx411c7mD", e.getEntryPath());
        assertEquals(1036, e.getScene());
        assertEquals("firstPage.pagePath", e.getRawExtras().get("__pathOrigin"));
        assertEquals("42", e.getRawExtras().get("__launchClickTimeMillis"));
        assertFalse(e.toString().contains("secret-open-id"));
        assertFalse(e.getRawExtras().containsKey("openId"));
    }

    @Test void readsConfigAndRetainsConflictingPathCandidates() {
        Config c = new Config();
        c.launchParam.miniAppId = "1109937557";
        c.launchParam.entryPath = "pages/video/video?aid=170001";
        c.entryPath = "another-path";
        MiniAppEvent e = read(c);
        assertEquals(c.launchParam.entryPath, e.getEntryPath());
        assertEquals(c.config.firstPage.pagePath, e.getRawExtras().get("__firstPagePath"));
        assertEquals(c.entryPath, e.getRawExtras().get("__configPath"));
        assertEquals("original-share-link", e.getLink());
    }

    @Test void currentPageIsLocalToThisNotification() {
        SdkInfo info = new SdkInfo();
        String path = "pages/pgcvideo/pgcvideo?ep_id=123";
        MiniAppEvent e = ResolvedAppData.read(info, Source.PAGE, "qq:mini", path, Map.of("__status", "2"));
        assertEquals(Source.PAGE, e.getSource());
        assertEquals(path, e.getEntryPath());
        assertEquals("currentPath", e.getRawExtras().get("__pathOrigin"));
        assertEquals("2", e.getRawExtras().get("__status"));
        assertNotEquals(path, read(info).getEntryPath());
    }

    @Test void identityMismatchIsVisibleAndNoStateLeaksToNextObject() {
        SdkInfo info = new SdkInfo();
        info.launchParam.miniAppId = "other-app";
        assertEquals("true", read(info).getRawExtras().get("__identityConflict"));
        MiniAppEvent missing = read(new Object());
        assertNull(missing.getMiniAppId());
        assertNull(missing.getEntryPath());
        assertNull(missing.getScene());
        assertFalse(missing.getRawExtras().containsKey("__identityConflict"));
    }

    @Test void nullAndEmptyFieldsStayNull() {
        SdkInfo info = new SdkInfo();
        info.appId = "";
        info.firstPage.pagePath = "";
        info.launchParam.entryPath = "";
        MiniAppEvent e = read(info);
        assertNull(e.getMiniAppId());
        assertNull(e.getEntryPath());
        assertNull(e.getRawExtras().get("__pathOrigin"));
        assertNull(read(null).getMiniAppId());
    }
}
