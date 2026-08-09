package com.github.catvod.spider;

import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.api.AliYun;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Sub;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Response;

/**
 * XBPQ 阿里云盘与 PA（磁力/直链）专属解析分支。
 *
 * <p>从 XBPQ 主类抽取的阿里云盘详情/播放、本地与远程字幕扫描、PA Vod 构造及
 * 磁力/直链分发逻辑，统一以静态方法形式提供，供 XBPQ 主类按需调用。</p>
 */
public final class XBPQAliPa {

    /** 阿里云盘分享链接正则。 */
    private static final Pattern ALIYUN_PATTERN =
            Pattern.compile("https?://www\\.(aliyundrive|alipan)\\.com/s/([^/]+)(/folder/([^/]+))?");

    /** 阿里云盘短链接提取正则。 */
    private static final Pattern ALIYUN_SHORT_PATTERN =
            Pattern.compile("[\\S\\s]*(https://www\\.aliyundrive\\.com/s/\\S{11})[\\S\\s]*");

    /** 磁力链接提取正则（统一磁链识别，宽松匹配整段文本，支持 hex/base32 btih；matches() 后 group(1) 取纯磁链）。 */
    private static final Pattern MAGNET_EXTRACT_PATTERN =
            Pattern.compile("[\\S\\s]*(magnet:\\?xt=urn:btih:[0-9a-fA-F]{32,40}|magnet:\\?xt=urn:btih:[A-Z2-7]{32})[\\S\\s]*");

    /** PA 默认封面。 */
    private static final String DEFAULT_COVER =
            "https://pic.rmb.bdstatic.com/bjh/1d0b02d0f57f0a42201f92caba5107ed.jpeg";

    /** PA 播放源名称。 */
    private static final String PLAY_FROM_NAME = "XBPQ";

    /** PA 视频扩展名（字幕探测用）。 */
    private static final List<String> VIDEO_EXTS = Arrays.asList("mp4", "mkv");

    /** PA 字幕扩展名（HTTP 字幕探测用）。 */
    private static final List<String> SUB_EXTS = Arrays.asList("srt", "ass");

    /** PA 默认请求头。 */
    private static final Map<String, String> DEFAULT_HEADERS;
    static {
        DEFAULT_HEADERS = new HashMap<>();
        DEFAULT_HEADERS.put("User-Agent", Util.CHROME);
    }

    private XBPQAliPa() {
    }

    /**
     * 阿里云盘详情解析。
     * group(4) 非 null 表示存在 folder_id 子目录。
     */
    static String detailAliContent(XBPQ main, List<String> ids) {
        try {
            String id = ids.get(0).trim();
            id = id.replace("www.alipan.com", "www.aliyundrive.com");
            Matcher matcher = ALIYUN_PATTERN.matcher(id);
            if (!matcher.find()) return "";
            String shareId = matcher.group(2);
            String fileId = matcher.group(4) != null ? matcher.group(4) : "";
            Vod vod = AliYun.get().getVod(id, shareId, fileId);
            return Result.string(vod);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /** 阿里云盘播放（格式：share_id+file_id1+file_id2...）。 */
    static String playerAliContent(XBPQ main, String flag, String id, List<String> vipFlags) {
        try {
            String[] ids = id.split("\\+");
            return AliYun.get().playerContent(ids, flag);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    // ==================== PA 多源处理 ====================

    /**
     * 扫描本地文件或远程同名字幕。
     *
     * @param path 文件路径（file:// 或 http://）
     * @return 字幕列表
     */
    static List<Sub> scanSubtitles(String path) {
        List<Sub> subs = new ArrayList<>();
        if (path == null || path.isEmpty()) return subs;

        if (path.startsWith("file://")) {
            try {
                File file = new File(path.replace("file://", ""));
                File parent = file.getParentFile();
                if (parent != null) {
                    File[] files = parent.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            String ext = getExt(f.getName());
                            if (Util.isSub(ext)) {
                                String name = removeExt(f.getName());
                                subs.add(Sub.create()
                                        .name(name)
                                        .ext(ext)
                                        .url("file://" + f.getAbsolutePath()));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        } else if (path.startsWith("http://") || path.startsWith("https://")) {
            try {
                String ext = getExt(path);
                if (VIDEO_EXTS.contains(ext)) {
                    String baseName = removeExt(path);
                    for (String subExt : SUB_EXTS) {
                        String subUrl = baseName + "." + subExt;
                        try {
                            Response resp = OkHttp.newCall(subUrl, DEFAULT_HEADERS);
                            if (resp.code() == 200) {
                                String fileName = Uri.parse(subUrl).getLastPathSegment();
                                if (fileName == null) fileName = "subtitle." + subExt;
                                subs.add(Sub.create()
                                        .name(fileName)
                                        .ext(subExt)
                                        .url(subUrl));
                            }
                            resp.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }
        return subs;
    }

    /**
     * 根据 vodId 类型构造 PA 详情 Vod，决定播放源顺序。
     *
     * @param vodId 视频 ID（磁链/直链/普通 URL）
     * @return 构造好的 Vod
     */
    static Vod buildPaVod(String vodId) {
        Vod vod = new Vod();
        vod.setVodId(vodId);
        vod.setVodName(vodId);
        vod.setVodPic(DEFAULT_COVER);
        vod.setTypeName(PLAY_FROM_NAME);

        List<String> playFrom;
        if (isMagnet(vodId)) {
            playFrom = Arrays.asList("解析", "嗅探", "直链");
        } else if (isPaDirectLink(vodId) || vodId.startsWith("magnet")) {
            playFrom = Arrays.asList("直连", "嗅探", "解析");
        } else {
            playFrom = Arrays.asList("嗅探", "解析", "直连");
        }
        vod.setVodPlayFrom(TextUtils.join("$$$", playFrom));

        List<String> episodes = new ArrayList<>();
        for (int i = 0; i < playFrom.size(); i++) {
            episodes.add(vodId);
        }
        vod.setVodPlayUrl(TextUtils.join("$$$", episodes));
        return vod;
    }

    /**
     * PA 详情解析：磁链 / 阿里云盘 / 直链等不同类型分发。
     */
    static String detailPaContent(XBPQ main, List<String> ids) {
        try {
            String id = ids.get(0).trim();
            if (id.contains("magnet")) {
                Matcher magnetMatcher = MAGNET_EXTRACT_PATTERN.matcher(id);
                String magnet = magnetMatcher.matches() ? magnetMatcher.group(1) : id;
                return Result.string(buildPaVod(magnet));
            } else if (id.contains("aliyundrive") || id.contains("alipan")) {
                return detailAliContent(main, ids);
            } else {
                Matcher aliShortMatcher = ALIYUN_SHORT_PATTERN.matcher(id);
                if (aliShortMatcher.matches()) {
                    String aliUrl = aliShortMatcher.group(1);
                    return detailAliContent(main, Arrays.asList(aliUrl));
                }
                return Result.string(buildPaVod(id));
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * PA 播放：YouTube 直链 / 直连 / 嗅探 / 解析 / 阿里云盘分发。
     */
    static String playerPaContent(XBPQ main, String flag, String id, List<String> vipFlags) {
        try {
            if (id.contains("youtube.com")) {
                return Result.get().parse(0).url(id).header(DEFAULT_HEADERS).string();
            } else if ("直连".equals(flag)) {
                List<Sub> subs = scanSubtitles(id);
                return Result.get().parse(0).url(id).header(DEFAULT_HEADERS).subs(subs).string();
            } else if ("嗅探".equals(flag)) {
                return Result.get().parse(1).url(id).header(DEFAULT_HEADERS).string();
            } else if ("解析".equals(flag)) {
                return Result.get().parse(1).jx().url(id).header(DEFAULT_HEADERS).string();
            } else {
                return playerAliContent(main, flag, id, vipFlags);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    // ==================== PA 工具方法 ====================

    /** 判断是否为磁力链接。 */
    static boolean isMagnet(String url) {
        return url != null && url.startsWith("magnet:");
    }

    /**
     * 判断是否为直链（PA 版，基于扩展名检查）。
     * 与 {@link #isDirectLink} 区别：使用 Util.isVideo/isAudio 工具方法。
     */
    static boolean isPaDirectLink(String url) {
        if (url == null || url.isEmpty()) return false;
        String ext = getExt(url);
        return Util.isVideo(ext) || Util.isAudio(ext);
    }

    /** 获取文件扩展名（小写）。 */
    static String getExt(String name) {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf(".") + 1).toLowerCase();
    }

    /** 移除文件扩展名。 */
    static String removeExt(String name) {
        if (name == null || !name.contains(".")) return name;
        return name.substring(0, name.lastIndexOf("."));
    }
}
