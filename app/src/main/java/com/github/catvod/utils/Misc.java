package com.github.catvod.utils;

/**
 * URL 识别工具类。
 * 提供 VIP 视频域名识别与视频格式判断。
 */
public class Misc {

    /** VIP 视频域名列表 */
    private static final String[] VIP_DOMAINS = {
            "iqiyi.com", "youku.com", "tudou.com", "v.qq.com", "mgtv.com",
            "sohu.com", "le.com", "pptv.com", "vip.bd.com"
    };

    /** 视频文件扩展名列表 */
    private static final String[] VIDEO_EXTS = {
            ".m3u8", ".mp4", ".flv", ".avi", ".mkv", ".rm", ".wmv", ".mpg", ".m4a", ".mp3"
    };

    /**
     * 判断 URL 是否指向 VIP 视频站点。
     *
     * @param url 待检测 URL
     * @return 命中 VIP 域名返回 true，否则 false
     */
    public static boolean isVip(String url) {
        if (url == null || url.isEmpty()) return false;
        for (String domain : VIP_DOMAINS) {
            if (url.contains(domain)) return true;
        }
        return false;
    }

    /**
     * 判断 URL 是否为常见视频格式直链。
     *
     * @param url 待检测 URL
     * @return 命中视频扩展名返回 true，否则 false
     */
    public static boolean isVideoFormat(String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase();
        for (String ext : VIDEO_EXTS) {
            if (lower.contains(ext)) return true;
        }
        return false;
    }
}
