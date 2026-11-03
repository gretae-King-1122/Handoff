package com.handoff.rules.links;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/** Classifies only the URL supplied by the host browser dispatch. No network or nested-URL decoding. */
public final class OrdinaryWebLink {
    // Keep social content (including unsupported pages and short shares) on its existing route.
    // QQ/WeChat domains also carry mini-app cards, authentication and host-owned confirmation pages.
    private static final Set<String> RESERVED_DOMAINS = Set.of(
            "bilibili.com", "b23.tv", "bili2233.cn",
            "douyin.com", "iesdouyin.com", "douyin.net", "amemv.com",
            "kuaishou.com", "chenzhongtech.com", "gifshow.com", "kwai.com",
            "qq.com", "qzone.com", "weixin.com", "wechat.com", "url.cn",
            "weibo.com", "weibo.cn", "t.cn", "xiaohongshu.com", "xhslink.com",
            "zhihu.com", "zhihu.link", "douban.com", "doubanio.com", "tieba.baidu.com",
            "twitter.com", "x.com", "t.co", "facebook.com", "fb.com", "fb.me",
            "instagram.com", "threads.net", "threads.com", "reddit.com", "redd.it",
            "youtube.com", "youtu.be", "tiktok.com", "linkedin.com", "lnkd.in"
    );

    private OrdinaryWebLink() { }

    public static boolean matches(String url) {
        if (url == null || url.isEmpty() || url.length() > 16384) return false;
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) return false;
            if (uri.getRawUserInfo() != null || uri.getHost() == null || uri.getPort() > 65535) return false;
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
            for (String reserved : RESERVED_DOMAINS) {
                if (host.equals(reserved) || host.endsWith("." + reserved)) return false;
            }
            return true;
        } catch (URISyntaxException ignored) {
            return false;
        }
    }
}
