package com.github.catvod.parser;

import android.os.Build;
import android.annotation.TargetApi;

import com.github.catvod.api.AdSetting;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Notify;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M3U8去广告处理类 - 优化精简版
 * 
 * 广告检测策略：
 * 1. 少数URL过滤 - 移除次要域名/路径片段（仅激进模式）
 * 2. 广告信号标签 - CUE/SCTE35/VAST/VMAP等
 * 3. DISCONTINUITY分组 - 分组分析智能丢弃
 * 4. URI/域名特征 - 广告URL模式匹配
 * 
 * 安全机制：
 * - 纯广告检测(>80%)不处理
 * - 去广告过多(>50片段)安全回退
 * - 处理后无效播放列表回退
 */
public class M3u8Fix {

    // 广告时长阈值（秒）
    private static final float AD_DURATION_THRESHOLD = 4.0f;
    
    // 少数URL过滤阈值
    private static final int MIN_TIMES_THRESHOLD = 15;
    
    // 安全回退阈值
    private static final int MAX_AD_COUNT = 50;
    
    // 使用AtomicInteger避免线程安全问题
    private static final AtomicInteger currentAdCount = new AtomicInteger(0);

    // 标签常量
    private static final String TAG_DISCONTINUITY = "#EXT-X-DISCONTINUITY";
    private static final String TAG_MEDIA_DURATION = "#EXTINF";
    private static final String TAG_ENDLIST = "#EXT-X-ENDLIST";
    private static final String TAG_KEY = "#EXT-X-KEY";
    private static final String TAG_CUE_OUT = "#EXT-X-CUE-OUT";
    private static final String TAG_CUE_IN = "#EXT-X-CUE-IN";

    // 广告URI正则
    private static final Pattern PATTERN_AD_URI = Pattern.compile(
            "(?i)(^|[/?&=_.-])(ads?|adv|advert(ise(ment)?)?|commercial|preroll|pre-roll|midroll|mid-roll|postroll|post-roll|sponsor|scte|vast|vmap|interstitial|bumper)([/?&=_.-]|$)"
    );

    // 广告域名关键词
    private static final String[] AD_DOMAIN_KEYWORDS = {
            "adservice", "adserver", "adsystem", "doubleclick", "googlesyndication",
            "advertising", "2mdn.net", "moatads", "scorecardresearch", "quantserve",
            "adbreak", "adclick", "adlog", "analytics", "tracking",
            "beacon", "pixel", "sponsor", "commercial"
    };

    // 正则
    private static final Pattern REGEX_DURATION = Pattern.compile(TAG_MEDIA_DURATION + ":([\\d\\.]+)\\b");
    private static final Pattern REGEX_URI = Pattern.compile("URI=\"(.+?)\"");

    @TargetApi(Build.VERSION_CODES.N)
    public static String fixM3u8(String url, Map<String, String> params) {
        return fixM3u8(url, params, 0, 0);
    }

    @TargetApi(Build.VERSION_CODES.N)
    public static String fixM3u8(String url, Map<String, String> params, int skipStart, int skipEnd) {
        if (!AdSetting.isAdblock()) {
            return OkHttp.string(url, getHeaders(params));
        }

        try {
            String content = OkHttp.string(url, getHeaders(params));
            if (content == null || content.isEmpty()) return "";

            // 重置计数器
            currentAdCount.set(0);
            SpiderDebug.log("=== M3u8Fix开始处理 ===");

            // 纯广告检测
            if (isPureAds(content)) {
                SpiderDebug.log("检测为纯广告内容，不处理");
                return content;
            }

            // 执行去广告
            String result = purify(url, content, skipStart, skipEnd);

            // 安全回退
            if (currentAdCount.get() > MAX_AD_COUNT) {
                SpiderDebug.log("⚠️ 去广告过多(" + currentAdCount.get() + ")，安全回退");
                currentAdCount.set(0);
                return content;
            }

            if (currentAdCount.get() > 0) {
                Notify.show(String.format("已移除 %d 个广告片段", currentAdCount.get()));
            }

            SpiderDebug.log("=== M3u8Fix处理完成 === 删除:" + currentAdCount.get());
            return result;

        } catch (Exception e) {
            SpiderDebug.log("M3u8Fix 异常: " + e.getMessage());
            return "";
        }
    }

    /**
     * 去广告主流程
     */
    private static String purify(String baseUrl, String content, int skipStart, int skipEnd) {
        boolean aggressive = AdSetting.isAdblockAggressiveMode();
        SpiderDebug.log("模式: " + (aggressive ? "激进" : "保守"));

        String result = content;

        // 1. 少数URL过滤（仅激进模式）
        if (aggressive) {
            String filtered = removeMinorityUrls(baseUrl, result);
            if (filtered != null && currentAdCount.get() > 0) {
                result = filtered;
                SpiderDebug.log("少数URL过滤完成: " + currentAdCount.get() + "个");
            }
        }

        // 2. 广告标记清理
        result = cleanAdMarkers(baseUrl, result);
        SpiderDebug.log("广告标记清理完成: " + currentAdCount.get() + "个");

        // 3. DISCONTINUITY分组清理
        result = cleanDiscontinuityGroups(baseUrl, result);
        SpiderDebug.log("DISCONTINUITY分组清理完成: " + currentAdCount.get() + "个");

        // 4. 片尾跳过
        if (skipEnd > 0) {
            result = skipEndSegments(baseUrl, result, skipEnd);
            SpiderDebug.log("片尾跳过完成: " + currentAdCount.get() + "个");
        }

        return result;
    }

    // ==================== 纯广告检测 ====================

    /**
     * 检测是否为纯广告内容
     * 如果超过80%的片段是广告，认为是纯广告
     */
    private static boolean isPureAds(String content) {
        if (content == null || content.isEmpty()) return false;
        if (!content.startsWith("#EXTM3U")) return false;

        int totalSegments = 0;
        int adSegments = 0;

        for (String line : content.split("\n")) {
            if (line.length() > 0 && line.charAt(0) != '#') {
                totalSegments++;
                if (isAdSegmentUri(line) || hasAdDomain(line)) {
                    adSegments++;
                }
            }
        }

        return totalSegments > 0 && (float) adSegments / totalSegments > 0.8f;
    }

    // ==================== 少数URL过滤 ====================

    /**
     * 少数URL过滤
     * 统计URL前缀/域名出现次数，保留主要模式
     */
    private static String removeMinorityUrls(String baseUrl, String content) {
        String[] lines = content.split("\n");
        
        // 统计URL前缀出现次数
        HashMap<String, Integer> prefixCount = new HashMap<>();
        List<String> segmentLines = new ArrayList<>();
        
        for (String line : lines) {
            if (line.length() == 0 || line.charAt(0) == '#') continue;
            
            String absoluteUrl = toAbsoluteUrl(baseUrl, line);
            String prefix = getUrlPrefix(absoluteUrl);
            
            prefixCount.put(prefix, prefixCount.getOrDefault(prefix, 0) + 1);
            segmentLines.add(absoluteUrl);
        }

        if (prefixCount.size() <= 1) return null;

        // 找最大出现次数
        int maxCount = 0;
        String maxPrefix = "";
        for (Map.Entry<String, Integer> entry : prefixCount.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                maxPrefix = entry.getKey();
            }
        }

        // 如果最大占比<80%，尝试域名过滤
        int totalSegments = segmentLines.size();
        if ((float) maxCount / totalSegments < 0.8) {
            prefixCount.clear();
            for (String url : segmentLines) {
                String domain = getDomain(url);
                if (domain != null) {
                    prefixCount.put(domain, prefixCount.getOrDefault(domain, 0) + 1);
                }
            }
            
            maxCount = 0;
            for (Map.Entry<String, Integer> entry : prefixCount.entrySet()) {
                if (entry.getValue() > maxCount) {
                    maxCount = entry.getValue();
                    maxPrefix = entry.getKey();
                }
            }
            
            // 如果还是<80%，且所有都超过阈值，则不过滤
            boolean allExceedThreshold = true;
            for (Integer count : prefixCount.values()) {
                if (count <= MIN_TIMES_THRESHOLD) {
                    allExceedThreshold = false;
                    break;
                }
            }
            if (allExceedThreshold) return null;
        }

        // 构建过滤后的内容
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line.length() == 0 || line.charAt(0) == '#') {
                sb.append(line).append("\n");
                continue;
            }
            
            String absoluteUrl = toAbsoluteUrl(baseUrl, line);
            String prefix = getUrlPrefix(absoluteUrl);
            
            // 检查是否应该保留
            if (shouldKeepUrl(absoluteUrl, maxPrefix, prefixCount)) {
                sb.append(absoluteUrl).append("\n");
            } else {
                currentAdCount.incrementAndGet();
            }
        }

        // 如果去掉太多片段，跳过此过滤
        if (totalSegments > 0 && currentAdCount.get() > totalSegments * 0.3) {
            SpiderDebug.log("可疑广告过多(" + currentAdCount.get() + "/" + totalSegments + ")，跳过URL过滤");
            currentAdCount.set(0);
            return null;
        }

        return sb.toString();
    }

    private static String getUrlPrefix(String url) {
        int lastDot = url.lastIndexOf('.');
        int lastSlash = url.lastIndexOf('/');
        if (lastDot > 0 && lastSlash > 0 && lastDot > lastSlash - 4) {
            return url.substring(0, lastDot - 4);
        }
        return url;
    }

    private static String getDomain(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null;
        int start = url.indexOf("://") + 3;
        int end = url.indexOf('/', start);
        return end > start ? url.substring(start, end) : url.substring(start);
    }

    private static boolean shouldKeepUrl(String url, String maxPrefix, HashMap<String, Integer> prefixCount) {
        // 优先使用URL前缀匹配
        if (url.startsWith(maxPrefix)) return true;
        
        // 域名匹配
        String domain = getDomain(url);
        if (domain != null && domain.equals(maxPrefix)) return true;
        
        // 超过阈值的域名也保留
        Integer count = prefixCount.get(domain);
        return count != null && count > MIN_TIMES_THRESHOLD;
    }

    // ==================== 广告标记清理 ====================

    /**
     * 清理广告标记
     */
    private static String cleanAdMarkers(String baseUrl, String content) {
        String[] lines = content.split("\n");
        StringBuilder sb = new StringBuilder();
        List<String> pending = new ArrayList<>();
        boolean inAdBreak = false;

        for (String raw : lines) {
            String line = raw.trim();
            
            if (line.isEmpty()) {
                if (pending.isEmpty()) {
                    sb.append(raw).append("\n");
                } else {
                    pending.add(raw);
                }
                continue;
            }

            if (line.startsWith("#")) {
                // 广告中断开始
                if (line.startsWith(TAG_CUE_OUT)) {
                    inAdBreak = true;
                    pending.clear();
                    currentAdCount.incrementAndGet();
                    continue;
                }
                
                // 广告中断结束
                if (line.startsWith(TAG_CUE_IN)) {
                    inAdBreak = false;
                    pending.clear();
                    continue;
                }
                
                // 广告信号标签
                if (isAdSignalTag(line)) {
                    pending.add(raw);
                    continue;
                }
                
                // 片段标签
                if (isSegmentTag(line)) {
                    pending.add(raw);
                    continue;
                }
                
                // KEY标签需要处理URI
                if (line.startsWith(TAG_KEY)) {
                    pending.add(resolveKeyLine(baseUrl, raw));
                    continue;
                }
                
                // 其他标签直接输出
                if (pending.isEmpty()) {
                    sb.append(raw).append("\n");
                } else {
                    pending.add(raw);
                }
                continue;
            }

            // URI行
            if (inAdBreak || hasAdSignal(pending) || isAdSegmentUri(line) || hasAdDomain(line)) {
                pending.clear();
                currentAdCount.incrementAndGet();
                continue;
            }

            flush(sb, pending);
            sb.append(line).append("\n");
        }

        return sb.toString();
    }

    private static boolean isAdSignalTag(String line) {
        String lower = line.toLowerCase();
        return lower.contains("scte") || lower.contains("cue") || 
               lower.contains("interstitial") || lower.contains("vmap") || 
               lower.contains("vast") || lower.contains("advert") ||
               lower.contains("commercial") || lower.contains("preroll") ||
               lower.contains("midroll") || lower.contains("postroll");
    }

    private static boolean isSegmentTag(String line) {
        return line.startsWith(TAG_MEDIA_DURATION) ||
               line.startsWith("#EXT-X-BYTERANGE") ||
               line.startsWith("#EXT-X-PROGRAM-DATE-TIME") ||
               line.startsWith(TAG_DISCONTINUITY);
    }

    private static boolean hasAdSignal(List<String> pending) {
        for (String line : pending) {
            if (isAdSignalTag(line.trim())) return true;
        }
        return false;
    }

    private static String resolveKeyLine(String baseUrl, String line) {
        Matcher m = REGEX_URI.matcher(line);
        if (m.find()) {
            String uri = m.group(1);
            String absoluteUri = toAbsoluteUrl(baseUrl, uri);
            return line.replace(uri, absoluteUri);
        }
        return line;
    }

    private static void flush(StringBuilder sb, List<String> pending) {
        for (String line : pending) sb.append(line).append("\n");
        pending.clear();
    }

    // ==================== DISCONTINUITY分组清理 ====================

    /**
     * DISCONTINUITY分组清理
     */
    private static String cleanDiscontinuityGroups(String baseUrl, String content) {
        String[] lines = content.split("\n");
        List<Group> groups = buildGroups(lines);
        
        if (groups.size() < 3) return content;

        // 找主组
        Group main = null;
        for (Group g : groups) {
            if (g.segmentCount == 0) continue;
            if (main == null || g.duration > main.duration) main = g;
        }
        
        if (main == null || main.segmentCount < 3) return content;

        // 分析并丢弃广告组
        StringBuilder sb = new StringBuilder();
        for (Group g : groups) {
            if (shouldDropGroup(g, main)) {
                currentAdCount.addAndGet(g.segmentCount);
                continue;
            }
            g.appendTo(sb);
        }

        return sb.toString();
    }

    private static List<Group> buildGroups(String[] lines) {
        List<Group> groups = new ArrayList<>();
        Group group = new Group();
        
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith(TAG_DISCONTINUITY) && group.hasMedia()) {
                groups.add(group);
                group = new Group();
            }
            group.add(raw);
        }
        
        if (group.hasMedia() || !group.lines.isEmpty()) {
            groups.add(group);
        }
        
        return groups;
    }

    private static boolean shouldDropGroup(Group g, Group main) {
        if (g == main || g.segmentCount == 0) return false;

        // 短片段组
        boolean shortGroup = g.segmentCount <= 2 || 
                (main.duration > 0 && g.duration > 0 && g.duration < main.duration * 0.18);

        // 不同域名
        boolean differentHost = !g.host.isEmpty() && !main.host.isEmpty() && !g.host.equals(main.host);

        // 不同路径
        boolean differentPath = !g.pathPrefix.isEmpty() && !main.pathPrefix.isEmpty() && 
                                !g.pathPrefix.equals(main.pathPrefix);

        // 有广告特征
        boolean hasAdFeature = g.hasAdSignal || hasAdDomain(g.host) || isAdSegmentUri(g.pathPrefix);

        boolean adLike = hasAdFeature || differentHost || (g.segmentCount <= 2 && differentPath);

        return shortGroup && adLike;
    }

    // ==================== 片尾跳过 ====================

    /**
     * 跳过片尾片段
     */
    private static String skipEndSegments(String baseUrl, String content, int skipEnd) {
        String[] lines = content.split("\n");
        
        // 计算总时长
        double totalDuration = 0;
        List<Double> durations = new ArrayList<>();
        
        for (String line : lines) {
            Matcher m = REGEX_DURATION.matcher(line.trim());
            if (m.find()) {
                try {
                    double d = Double.parseDouble(m.group(1));
                    durations.add(d);
                    totalDuration += d;
                } catch (NumberFormatException ignored) {}
            }
        }

        if (totalDuration <= skipEnd) return content;

        // 找到片尾起始位置
        double skipStartTime = totalDuration - skipEnd;
        double currentTime = 0;

        StringBuilder sb = new StringBuilder();
        boolean inSkipArea = false;

        for (String raw : lines) {
            String line = raw.trim();
            
            Matcher m = REGEX_DURATION.matcher(line);
            if (m.find()) {
                try {
                    double duration = Double.parseDouble(m.group(1));
                    if (currentTime + duration > skipStartTime) {
                        inSkipArea = true;
                    }
                    currentTime += duration;
                } catch (NumberFormatException ignored) {}
            }

            if (line.startsWith("#")) {
                if (!inSkipArea) {
                    sb.append(raw).append("\n");
                }
                continue;
            }

            if (inSkipArea) {
                currentAdCount.incrementAndGet();
                continue;
            }

            sb.append(line).append("\n");
        }

        return sb.toString();
    }

    // ==================== 工具方法 ====================

    private static boolean isAdSegmentUri(String line) {
        return PATTERN_AD_URI.matcher(line).find();
    }

    private static boolean hasAdDomain(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        for (String keyword : AD_DOMAIN_KEYWORDS) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }

    private static String toAbsoluteUrl(String base, String url) {
        if (url == null) return "";
        String line = url.trim();
        if (line.isEmpty() || line.startsWith("http://") || line.startsWith("https://")) return line;
        try {
            return new java.net.URI(base).resolve(line).toString();
        } catch (Exception e) {
            return line;
        }
    }

    private static HashMap<String, String> getHeaders(Map<String, String> params) {
        if (params == null) params = new HashMap<>();
        params.put("Connection", "Keep-Alive");
        params.put("User-Agent", "okhttp/4.0.1");
        params.remove("do");
        params.remove("url");
        return (HashMap<String, String>) params;
    }

    public static Object[] loadM3u8(String context) {
        try {
            return new Object[]{200, "text/plain; charset=utf-8",
                    new ByteArrayInputStream(context.getBytes("UTF-8"))};
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 内部类：DISCONTINUITY分组 ====================

    private static class Group {
        List<String> lines = new ArrayList<>();
        int segmentCount = 0;
        boolean hasAdSignal = false;
        double duration = 0;  // 统一使用double
        String host = "";
        String pathPrefix = "";

        void add(String raw) {
            lines.add(raw);
            String line = raw.trim();
            
            Matcher m = REGEX_DURATION.matcher(line);
            if (m.find()) {
                try {
                    duration += Double.parseDouble(m.group(1));
                } catch (Exception ignored) {}
            }
            
            if (line.startsWith("#")) {
                if (isAdSignalTag(line)) hasAdSignal = true;
                return;
            }
            
            segmentCount++;
            if (isAdSegmentUri(line) || hasAdDomain(line)) hasAdSignal = true;
            if (host.isEmpty()) host = getHost(line);
            if (pathPrefix.isEmpty()) pathPrefix = getPathPrefix(line);
        }

        boolean hasMedia() {
            return segmentCount > 0;
        }

        void appendTo(StringBuilder sb) {
            for (String line : lines) sb.append(line).append("\n");
        }

        private String getHost(String url) {
            if (!url.startsWith("http://") && !url.startsWith("https://")) return "";
            int start = url.indexOf("://") + 3;
            int end = url.indexOf('/', start);
            return end > start ? url.substring(start, end) : url.substring(start);
        }

        private String getPathPrefix(String url) {
            String clean = url;
            int query = clean.indexOf('?');
            if (query >= 0) clean = clean.substring(0, query);
            int slash = clean.lastIndexOf('/');
            return slash > 0 ? clean.substring(0, slash + 1) : "";
        }
    }
}
