package com.github.catvod.bean.XBPQ;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.spider.XBPQ;
import com.github.catvod.utils.Util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * XBPQ 网络层工具类。
 * <p>
 * 封装请求头组装、GBK/UTF-8 源码 Fetch、POST 交互等网络层方法，
 * 从 XBPQ 主类中拆分而来，所有方法均为静态方法，通过传入 XBPQ 实例访问其内部状态。
 * </p>
 */
public final class XBPQHttp {

    /** 桌面端 UA。 */
    private static final String PC_UA = Util.CHROME;

    /** 移动端 UA。 */
    private static final String MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Xiaomi 13 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.5672.131 Mobile Safari/537.36";

    /** gzip 魔数（前两字节 0x1f 0x8b）。 */
    private static final int GZIP_MAGIC = 0x1f;

    private XBPQHttp() {
    }

    /**
     * 解析 URL 末尾的 ;; 控制后缀。
     * <p>
     * XBPQ 配置中 URL 末尾可附加 ;;flags 控制后缀，影响响应解码行为：
     * <ul>
     *   <li>z  → gzip 解压（服务器返回 gzip 压缩但未设 Content-Encoding 头）</li>
     *   <li>g  → GBK 编码解码</li>
     *   <li>c  → 缓存响应</li>
     *   <li>r1 → 跟随重定向 1 次</li>
     *   <li>mrc/RA/RAD/d0 → 扩展标记（mrc 系列，已识别但暂未实现特殊解码）</li>
     * </ul>
     * @param url 原始 URL（可能含 ;;flags 后缀）
     * @return 长度 2 的数组：[0]=清理后的 URL，[1]=flags 串（无 ;; 时为空串）
     */
    public static String[] parseControlSuffix(String url) {
        if (url == null) return new String[]{"", ""};
        int idx = url.indexOf(";;");
        if (idx < 0) return new String[]{url, ""};
        return new String[]{url.substring(0, idx), url.substring(idx + 2)};
    }

    /**
     * gzip 字节解压。
     * @param data gzip 压缩的字节数组
     * @param charset 解压后字符串编码
     * @return 解压后的字符串，失败返回 null
     */
    private static String gzipDecompress(byte[] data, String charset) {
        if (data == null || data.length < 2 || (data[0] & 0xFF) != GZIP_MAGIC) return null;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             GZIPInputStream gis = new GZIPInputStream(bis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = gis.read(buf)) != -1) {
                bos.write(buf, 0, len);
            }
            String cs = charset.isEmpty() ? "UTF-8" : charset;
            return bos.toString(cs);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        }
    }

    public static Map<String, String> buildHeaders(XBPQ main) {
        Map<String, String> headers = new HashMap<>();
        String uaConfig = main.config != null ? main.config.get("", "请求头", "请求头参数", "ua", "Headers", "UserAgent") : "";
        if (!uaConfig.isEmpty()) {
            if (uaConfig.contains("电脑")) headers.put("User-Agent", PC_UA);
            else if (uaConfig.contains("手机")) headers.put("User-Agent", MOBILE_UA);
            else if (uaConfig.contains("$")) {
                String[] parts = uaConfig.split("\\$");
                if (parts.length >= 2) {
                    String value = parts[1];
                    if ("PC_UA".equals(value)) value = PC_UA;
                    else if ("MOBILE_UA".equals(value)) value = MOBILE_UA;
                    headers.put(parts[0], value);
                }
            }
        }
        if (!headers.containsKey("User-Agent")) headers.put("User-Agent", PC_UA);
        return headers;
    }

    /** 构建请求头 Map（playerContent 用，含播放请求头）。双重检查锁定保证线程安全。 */
    static Map<String, String> buildHeaderMap(XBPQ main) {
        if (main.headerCache != null) return main.headerCache;
        synchronized (main) {
            if (main.headerCache != null) return main.headerCache;
            main.headerCache = buildHeaders(main);
            String playHeader = main.config != null ? main.config.get("", "播放请求头") : "";
            if (!playHeader.isEmpty()) {
                for (String pair : playHeader.split("#")) {
                    String[] headerPair = pair.split("\\$");
                    if (headerPair.length >= 2) main.headerCache.put(headerPair[0], headerPair[1]);
                }
            }
            return main.headerCache;
        }
    }

    /**
     * GET 请求获取 HTML 源码。
     * <p>支持 ;; 控制后缀：z=gzip解压，g=GBK编码。sniffConfig 含 'g' 也走 GBK 编码。
     * 优先用 bytes 解码避免乱码。
     */
    public static String fetchHtml(XBPQ main, String url) {
        try {
            // 解析 ;; 控制后缀（z=gzip解压, g=GBK编码, c=缓存等）
            String[] parsed = parseControlSuffix(url);
            String cleanUrl = parsed[0];
            String flags = parsed[1];
            boolean needGzip = flags.contains("z");
            boolean flagGbk = flags.contains("g");

            // 强制协议替换：forceProtocol 含 https/http 时替换 URL 协议
            if (main.forceProtocol.contains("https") && cleanUrl.startsWith("http://")) {
                cleanUrl = cleanUrl.replace("http://", "https://");
            } else if (main.forceProtocol.contains("http") && !main.forceProtocol.contains("https") && cleanUrl.startsWith("https://")) {
                cleanUrl = cleanUrl.replace("https://", "http://");
            }
            if (main.spiderApi != null) {
                main.spiderApi.log("请求源码，webUrl--> " + cleanUrl + (flags.isEmpty() ? "" : " ;;" + flags));
            }
            Map<String, String> headers = buildHeaders(main);
            // 确定目标编码：;;g 标记或 sniffConfig 含 'g' 强制 GBK，否则取配置"编码"
            String charset = "";
            if (flagGbk || main.sniffGbk()) {
                charset = "GBK";
            } else if (main.config != null) {
                charset = main.config.get("", "编码", "网页编码格式", "Coding_format");
            }

            // gzip 解压需求：必须取 bytes 再解压
            if (needGzip) {
                byte[] data = OkHttp.bytes(cleanUrl, headers);
                if (data != null && data.length > 0) {
                    // 先尝试 gzip 解压（响应体确实是 gzip 压缩）
                    String decompressed = gzipDecompress(data, charset);
                    if (decompressed != null) {
                        if (main.spiderApi != null) {
                            main.spiderApi.log("gzip解压成功--> " + (decompressed.length() > 100 ? decompressed.substring(0, 100) : decompressed));
                        }
                        return decompressed;
                    }
                    // 非 gzip 数据，直接按编码解码
                    String html = new String(data, charset.isEmpty() ? "UTF-8" : charset);
                    if (main.spiderApi != null) {
                        main.spiderApi.log("获取到源码(;;z但非gzip)--> " + (html.length() > 100 ? html.substring(0, 100) : html));
                    }
                    return html;
                }
                if (main.spiderApi != null) main.spiderApi.log("未获取到源码！");
                return "";
            }

            String html;
            if (!charset.isEmpty() && !"UTF-8".equalsIgnoreCase(charset) && !"utf-8".equalsIgnoreCase(charset)) {
                // 非 UTF-8 编码：优先用 bytes + 指定 charset 解码，避免 OkHttp 默认 UTF-8 解码导致乱码
                try {
                    byte[] data = OkHttp.bytes(cleanUrl, headers);
                    html = data != null ? new String(data, charset) : "";
                } catch (Exception e) {
                    // bytes 失败时回退到 string + 重编码方式
                    SpiderDebug.log(e);
                    html = OkHttp.string(cleanUrl, headers);
                    try {
                        html = new String(html.getBytes(StandardCharsets.ISO_8859_1), charset);
                    } catch (Exception ignored) {
                    }
                }
            } else {
                html = OkHttp.string(cleanUrl, headers);
            }
            if (main.spiderApi != null) {
                if (html.isEmpty()) main.spiderApi.log("未获取到源码！");
                else main.spiderApi.log("获取到源码--> " + (html.length() > 100 ? html.substring(0, 100) : html));
            }
            return html;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /** POST 请求获取 HTML 源码。 */
    public static String fetchPost(XBPQ main, String url) {
        try {
            // POST 也需清理 ;; 后缀
            String[] parsed = parseControlSuffix(url);
            String cleanUrl = parsed[0];
            String body = main.config != null ? main.config.get("", "POST请求数据", "sea_PtBody") : "";
            String html = OkHttp.post(cleanUrl, body, buildHeaders(main));
            return html;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }
}
