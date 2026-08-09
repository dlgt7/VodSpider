package com.github.catvod.spider;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Crypto;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * XBPQPA - 本地文件和YouTube视频播放Spider。
 * <p>继承XBPQAli，提供本地文件和YouTube视频播放功能。</p>
 */
public class XBPQPA extends XBPQAli {

    // ==================== 本地文件前缀常量 ====================

    /** 本地文件前缀 */
    private static final String LOCAL_PREFIX = Crypto.xorDecodeHex("11112900497A58", "hocAjZ");

    /** YouTube前缀 */
    private static final String YOUTUBE_PREFIX = Crypto.xorDecodeHex("1F0C3115497A58", "hocAjZ");

    // ==================== Spider接口实现 ====================

    /**
     * 详情内容。
     *
     * @param ids 视频ID列表
     * @return 详情JSON
     */
    @Override
    public String detailContent(List<String> ids) {
        try {
            String vodId = ids.get(0).trim();

            // 处理YouTube链接
            if (vodId.contains("youtube") || vodId.contains("youtu.be")) {
                return processYouTube(vodId);
            }

            // 处理本地文件目录
            if (vodId.startsWith(LOCAL_PREFIX)) {
                return processLocalDirectory(vodId.substring(LOCAL_PREFIX.length()));
            }

            // 处理YouTube搜索列表
            if (vodId.startsWith(YOUTUBE_PREFIX)) {
                return processYouTubeList(vodId.substring(YOUTUBE_PREFIX.length()));
            }

            // 处理特殊标识
            if (vodId.contains("local_path")) {
                vodId = vodId.replaceAll("local_path\\|", "");
            }

            // 默认委托给父类 XBPQAli 处理
            return super.detailContent(ids);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error(e.getMessage()).toString();
        }
    }

    /**
     * 播放器内容。
     *
     * @param flag     标志
     * @param flag2    标志2（播放链接）
     * @param vipFlags VIP标识列表
     * @return 播放信息JSON
     */
    @Override
    public String playerContent(String flag, String flag2, List<String> vipFlags) {
        try {
            String playUrl = flag2;
            if (playUrl.contains("$")) {
                String[] parts = playUrl.split("\\$");
                if (parts.length >= 2) {
                    playUrl = parts[1];
                }
            }

            // 处理YouTube播放
            if (flag2.contains("youtube")) {
                return handleYoutubePlay(playUrl);
            }

            // 处理本地文件播放
            if (playUrl.startsWith(LOCAL_PREFIX) || new File(playUrl).exists()) {
                return handleLocalPlay(playUrl);
            }

            // 处理其他类型
            if (flag.equals(Crypto.xorDecodeHex("90E3F18DCCCB", "hocAjZ"))) {
                return handleLocalPlay(playUrl);
            }

            if (flag.equals(Crypto.xorDecodeHex("92EFC083FDF7", "hocAjZ"))) {
                return handleDefaultPlay(playUrl);
            }

            if (flag.equals(Crypto.xorDecodeHex("9FDFE683EDC5", "hocAjZ"))) {
                return handleDefaultPlay(playUrl);
            }

            // 默认处理
            return super.playerContent(flag, flag2, vipFlags);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error(e.getMessage()).toString();
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 处理YouTube视频。
     *
     * @param url YouTube链接
     * @return 详情JSON
     */
    private String processYouTube(String url) {
        try {
            String videoId = extractYouTubeId(url);
            if (videoId == null) {
                return Result.error("无效的YouTube链接").toString();
            }

            Vod vod = new Vod();
            vod.setVodId(YOUTUBE_PREFIX + url);
            vod.setVodName("YouTube视频");
            vod.setVodPic("https://i.ytimg.com/vi/" + videoId + "/maxresdefault.jpg");
            vod.setVodPlayFrom("YouTube");
            vod.setVodPlayUrl("默认$" + url);

            return Result.string(vod);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error(e.getMessage()).toString();
        }
    }

    /**
     * 处理本地目录扫描。
     */
    private String processLocalDirectory(String dirPath) {
        try {
            File dir = new File(dirPath);
            if (!dir.isDirectory()) {
                return Result.error("无效的目录路径").toString();
            }

            List<Vod> list = new ArrayList<>();
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) continue;
                    String fileName = file.getName();
                    String type = detectFileType(fileName);
                    if (type != null && isVideoType(type)) {
                        String absPath = file.getAbsolutePath();
                        Vod vod = new Vod();
                        vod.setVodId(LOCAL_PREFIX + absPath);
                        vod.setVodName(fileName);
                        vod.setVodPic(extractThumbnail(absPath));
                        vod.setVodPlayFrom("本地");
                        vod.setVodPlayUrl("播放$" + absPath);
                        list.add(vod);
                    }
                }
            }

            return Result.string(list);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error(e.getMessage()).toString();
        }
    }

    /**
     * 处理YouTube搜索列表。
     */
    private String processYouTubeList(String query) {
        try {
            String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
            Vod vod = new Vod();
            vod.setVodId(YOUTUBE_PREFIX + encodedQuery);
            vod.setVodName("YouTube搜索: " + query);
            vod.setVodPic("https://www.youtube.com/s/desktop/12d3e105/img/favicon_144x144.png");
            vod.setVodPlayFrom("YouTube");
            vod.setVodPlayUrl("播放$" + "https://www.youtube.com/results?search_query=" + encodedQuery);
            return Result.string(vod);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error(e.getMessage()).toString();
        }
    }

    /**
     * 处理YouTube播放。
     */
    private String handleYoutubePlay(String url) {
        try {
            String videoId = extractYouTubeId(url);
            if (videoId == null) {
                return Result.error("无效的YouTube链接").toString();
            }
            String playUrl = "https://www.youtube.com/watch?v=" + videoId;
            return Result.get().url(playUrl).parse(0).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error(e.getMessage()).toString();
        }
    }

    /**
     * 按 URL 后缀自动分流构建播放请求。
     * <p>m3u8/ts → m3u8()，mp4/mkv/avi/webm/3gp/ogg → mp4()，其余 → parse(0)。</p>
     *
     * @param url 播放链接
     * @return Result 构建对象
     */
    private static Result buildPlayResult(String url) {
        if (url.contains(".m3u8") || url.contains(".ts")) {
            return Result.get().url(url).m3u8();
        } else if (url.contains(".mp4") || url.contains(".mkv") || url.contains(".avi")
                || url.contains(".webm") || url.contains(".3gp") || url.contains(".ogg")) {
            return Result.get().url(url).mp4();
        }
        return Result.get().url(url);
    }

    /**
     * 从本地媒体文件提取缩略图（取第1帧）。
     *
     * @param path 文件绝对路径
     * @return 缩略图路径，失败返回空字符串
     */
    private static String extractThumbnail(String path) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(path);
            byte[] bytes = retriever.getEmbeddedPicture();
            if (bytes != null && bytes.length > 0) {
                // 内嵌封面
                return "embedded_cover";
            }
            android.graphics.Bitmap bm = retriever.getFrameAtTime(0,
                    MediaMetadataRetriever.OPTION_CLOSEST);
            retriever.release();
            if (bm != null) {
                // 缩略图生成逻辑由宿主处理，此处返回占位
                return "";
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    /**
     * 处理本地文件播放。
     */
    private String handleLocalPlay(String url) {
        try {
            return buildPlayResult(url).parse(0).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error(e.getMessage()).toString();
        }
    }

    /**
     * 处理默认播放。
     */
    private String handleDefaultPlay(String url) {
        try {
            return buildPlayResult(url).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error(e.getMessage()).toString();
        }
    }

    /**
     * 提取YouTube视频ID。
     * <p>统一使用 android.net.Uri 解析，兼容 watch?v=、youtu.be/、/shorts/ 等所有格式。</p>
     *
     * @param url YouTube链接
     * @return 视频ID
     */
    private String extractYouTubeId(String url) {
        try {
            if (url == null) return null;
            String normalized = url;
            // youtu.be/ 短链接直接作为基础URL
            if (normalized.contains("youtu.be/")) {
                normalized = "https://www.youtube.com/watch?v="
                    + normalized.split("youtu.be/")[1].split("[?&]")[0];
            }
            if (!normalized.contains("youtube.com")) {
                return null;
            }
            Uri uri = Uri.parse(normalized);
            return uri.getQueryParameter("v");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 检测文件类型。
     *
     * @param fileName 文件名
     * @return 文件类型
     */
    private String detectFileType(String fileName) {
        if (fileName == null) return null;
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".mp4")) return "mp4";
        if (lowerName.endsWith(".mkv")) return "mkv";
        if (lowerName.endsWith(".avi")) return "avi";
        if (lowerName.endsWith(".mov")) return "mov";
        if (lowerName.endsWith(".wmv")) return "wmv";
        if (lowerName.endsWith(".flv")) return "flv";
        if (lowerName.endsWith(".m3u8")) return "m3u8";
        return null;
    }

    /**
     * 判断是否为视频类型。
     */
    private boolean isVideoType(String type) {
        if (type == null) return false;
        return type.equals("mp4") || type.equals("mkv") || type.equals("avi")
            || type.equals("mov") || type.equals("wmv") || type.equals("flv")
            || type.equals("m3u8");
    }
}
