package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XBPQAli - 阿里OSS视频源Spider。
 * <p>继承XBPQ，提供阿里云盘视频播放功能。</p>
 */
public class XBPQAli extends XBPQ {

    /** 阿里盘分享链接提取正则 */
    private static final Pattern ALI_SHARE_ID_PATTERN = Pattern.compile(
        "https?://www\\.(alipan|aliyundrive)\\.com/s/([a-zA-Z0-9]+)"
    );

    // ==================== 生命周期方法 ====================

    /**
     * 初始化Spider。
     *
     * @param ctx    上下文
     * @param extend 站点扩展配置
     */
    @Override
    public void init(Context ctx, String extend) throws Exception {
        super.init(ctx, extend);
        this.name = "阿里盘";
    }

    /**
     * 代理请求。
     */
    public static Object[] proxy(Map<String, String> params) {
        try {
            String action = params.get("action");
            if ("oss".equals(action) || "token".equals(action)) {
                String url = params.get("url");
                if (url == null || url.isEmpty()) {
                    return new Object[]{404, "text/plain", "URL not found"};
                }
                okhttp3.Response response = OkHttp.newCall(url, null);
                if (response == null || !response.isSuccessful()) {
                    return new Object[]{502, "text/plain", "Proxy request failed"};
                }
                byte[] data = response.body().bytes();
                String contentType = response.header("Content-Type", "application/octet-stream");
                response.close();
                return new Object[]{200, contentType, data};
            }
            String url = params.get("url");
            if (url == null || url.isEmpty()) {
                return new Object[]{404, "text/plain", "URL not found"};
            }
            String result = OkHttp.string(url, null);
            return new Object[]{200, "application/json", result};
        } catch (Exception e) {
            SpiderDebug.log(e);
            return new Object[]{500, "text/plain", e.getMessage()};
        }
    }

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

            // 替换协议头
            String processedUrl = vodId.replace("http:", "https:");

            // 检测阿里盘分享链接（复用父类的 ALIYUN_PATTERN）
            if (!ALIYUN_PATTERN.matcher(processedUrl).find()) {
                return Result.error("无效的阿里盘链接").toString();
            }

            // 提取分享ID - 匹配 alipan.com/s/xxxxxxxx 或 aliyundrive.com/s/xxxxxxxx
            Matcher matcher = ALI_SHARE_ID_PATTERN.matcher(processedUrl);
            if (!matcher.find()) {
                return Result.error("无法提取阿里盘分享ID").toString();
            }

            String shareId = matcher.group(2);
            SpiderDebug.log("Aliyun shareId: " + shareId);

            // 尝试从缓存中获取已解析的阿里盘信息
            String cached = XBPQ.getVerifyState("aliyun_" + shareId);
            if (cached != null && !cached.isEmpty()) {
                Vod vod = new Vod();
                vod.setVodId(vodId);
                vod.setVodName("阿里云盘-" + shareId);
                vod.setVodPlayFrom("阿里盘");
                vod.setVodPlayUrl("播放$" + vodId);
                return Result.string(vod);
            }

            // 阿里盘链接直接透传播放
            Vod vod = new Vod();
            vod.setVodId(vodId);
            vod.setVodName("阿里云盘分享");
            vod.setVodPic("");
            vod.setVodPlayFrom("阿里盘");
            vod.setVodPlayUrl("播放$" + vodId);
            return Result.string(vod);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error(e.getMessage()).toString();
        }
    }

    /**
     * 播放器内容。
     *
     * @param flag  标志
     * @param flag2 标志2（播放链接）
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

            // 阿里盘直链检测
            if (playUrl.contains("alipan.com") || playUrl.contains("aliyundrive.com")) {
                // 阿里盘链接直接返回，由客户端处理
                SpiderDebug.log("XBPQAli playerContent 阿里盘直链: " + playUrl);
                return Result.get().url(playUrl).parse(0).string();
            }

            // m3u8/mp4 直链检测
            if (playUrl.contains(".m3u8")) {
                return Result.get().url(playUrl).m3u8().parse(0).string();
            } else if (playUrl.contains(".mp4") || playUrl.contains(".avi") || playUrl.contains(".mkv")) {
                return Result.get().url(playUrl).mp4().parse(0).string();
            }

            // 默认：需要外部解析器
            return Result.get().url(playUrl).parse(1).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error(e.getMessage()).toString();
        }
    }
}
