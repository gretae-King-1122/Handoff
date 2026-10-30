package com.tencent.qqmini.sdk.launcher.model;

/** Sanitised model fixture; only the class name and fields consumed by the adapter are represented. */
public class MiniAppInfo {
    public String appId = "1109937557";
    public LaunchParam launchParam = new LaunchParam();
    public FirstPage firstPage = new FirstPage();

    public static class LaunchParam {
        public String miniAppId = "1109937557";
        public String entryPath = "pages/video/video?bvid=BV1ZQxV6NE6G";
    }

    public static class FirstPage {
        public String pagePath = "pages/video/video?bvid=BV1ZQxV6NE6G";
    }
}
