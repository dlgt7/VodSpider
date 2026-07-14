package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 金牌影视爬虫（Glod）
 * 基于 HTML 解析 + 多域名自动切换
 * 支持自定义 ext 域名列表（逗号分隔）
 */
public class Glod extends Spider {

    /** 默认域名列表 */
    private static final List<String> DEFAULT_DOMAINS = Arrays.asList(
        "https://y2s52n7.com",
        "https://www.sdzhgt.com",
        "https://m.hkybqufgh.com",
        "https://m.sizhengxt.com",
        "https://m.9zhoukj.com",
        "https://m.jiabaide.cn"
    );

    /** 图片 CDN */
    private static final String IMG_CDN = "https://aka.doubaocdn.com";

    /** 当前站点 URL */
    private String host;
    /** 域名列表 */
    private List<String> domains;
    /** 缓存的成功域名（加速访问） */
    private String cachedDomain;

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context);

        // 初始化域名列表
        domains = new ArrayList<>(DEFAULT_DOMAINS);

        // 处理 ext 配置（支持自定义域名）
        if (!TextUtils.isEmpty(extend)) {
            extend = extend.trim();
            // 如果 ext 是域名列表（逗号分隔）
            if (!extend.startsWith("{") && extend.contains(",")) {
                String[] customDomains = extend.split(",");
                domains.clear();  // 清空默认域名
                for (String domain : customDomains) {
                    domain = domain.trim();
                    if (!TextUtils.isEmpty(domain)) {
                        // 确保域名以 https:// 开头
                        if (!domain.startsWith("http")) {
                            domain = "https://" + domain;
                        }
                        domains.add(domain);
                    }
                }
            } else if (extend.startsWith("http")) {
                // 单个域名
                domains.clear();
                domains.add(extend);
            }
        }

        // 设置第一个域名为默认
        host = domains.get(0);
    }

    /**
     * 构建请求头
     * @param domain 域名（用于 Referer）
     * @return 请求头 Map
     */
    private Map<String, String> getHeaders(String domain) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", domain + "/");
        return headers;
    }

    /**
     * 多域名自动切换请求（智能优化版）
     * - 首次访问：快速测试所有域名，选择最快的作为主域名
     * - 后续访问：直接使用主域名（极速）
     * - 失败切换：主域名失败才切换到备用域名
     */
    private Object[] get(String path) {
        long startTime = System.currentTimeMillis();

        // 第一优先级：使用缓存的主域名（最快路径）
        if (!TextUtils.isEmpty(cachedDomain)) {
            try {
                String url = path.startsWith("http") ? path : cachedDomain + path;
                String content = OkHttp.string(url, getHeaders(cachedDomain));

                // 检查响应有效性（降低阈值到 200）
                if (!TextUtils.isEmpty(content) && content.length() > 200) {
                    long duration = System.currentTimeMillis() - startTime;
                    if (duration > 1000) {  // 超过1秒才记录
                        SpiderDebug.log("缓存域名成功: " + cachedDomain + " 耗时: " + duration + "ms");
                    }
                    return new Object[]{content, cachedDomain};
                }
            } catch (Exception e) {
                // 缓存域名失败，清除缓存，尝试备用域名
                SpiderDebug.log("主域名失败: " + cachedDomain + " - " + e.getMessage());
                cachedDomain = null;
            }
        }

        // 第二优先级：智能测试所有域名，选择最快的（仅首次）
        if (TextUtils.isEmpty(cachedDomain)) {
            String fastestDomain = testDomainsSpeed();
            if (!TextUtils.isEmpty(fastestDomain)) {
                cachedDomain = fastestDomain;
                SpiderDebug.log("智能选择最快域名: " + fastestDomain);

                // 使用选出的最快域名
                try {
                    String url = path.startsWith("http") ? path : fastestDomain + path;
                    String content = OkHttp.string(url, getHeaders(fastestDomain));

                    if (!TextUtils.isEmpty(content) && content.length() > 200) {
                        long duration = System.currentTimeMillis() - startTime;
                        SpiderDebug.log("首次智能选择耗时: " + duration + "ms");
                        return new Object[]{content, fastestDomain};
                    }
                } catch (Exception e) {
                    SpiderDebug.log("最快域名也失败: " + e.getMessage());
                }
            }
        }

        // 第三优先级：遍历所有域名（兜底方案）
        for (String domain : domains) {
            if (domain.equals(cachedDomain)) continue;

            try {
                long reqStart = System.currentTimeMillis();
                String url = path.startsWith("http") ? path : domain + path;
                String content = OkHttp.string(url, getHeaders(domain));

                if (!TextUtils.isEmpty(content) && content.length() > 200) {
                    cachedDomain = domain;

                    long duration = System.currentTimeMillis() - reqStart;
                    SpiderDebug.log("备用域名成功: " + domain + " 耗时: " + duration + "ms");
                    return new Object[]{content, domain};
                }
            } catch (Exception e) {
                SpiderDebug.log("域名访问失败: " + domain + " - " + e.getMessage());
            }
        }

        SpiderDebug.log("❌ 所有域名尝试失败");
        return null;
    }

    /**
     * 智能测试所有域名的响应速度，返回最快的域名
     */
    private String testDomainsSpeed() {
        String fastestDomain = null;
        long fastestTime = Long.MAX_VALUE;

        for (String domain : domains) {
            try {
                long start = System.currentTimeMillis();
                String testUrl = domain + "/";
                String content = OkHttp.string(testUrl, null, getHeaders(domain), 3000);  // params传null，超时3秒

                if (!TextUtils.isEmpty(content) && content.length() > 200) {
                    long duration = System.currentTimeMillis() - start;

                    if (duration < fastestTime) {
                        fastestTime = duration;
                        fastestDomain = domain;
                    }
                }
            } catch (Exception e) {
                // 测试失败，跳过
            }
        }

        return fastestDomain;
    }

    /**
     * 提取图片（尝试多种属性 + 调试日志）
     */
    private String fixImg(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return IMG_CDN + url;
        // 调试：记录修复后的图片URL
        if (!url.startsWith("http")) {
            SpiderDebug.log("异常图片URL: " + url);
        }
        return url;
    }

    /**
     * 清理标题（去除质量标签、评分、集数等）
     */
    private String cleanTitle(String title) {
        if (TextUtils.isEmpty(title)) return "";
        // 去除开头的质量标签
        title = title.replaceAll("^(蓝光|高清|超清|正片|预告)\\s*", "");
        // 去除评分
        title = title.replaceAll("\\s*\\d+\\.\\d+.*$", "");
        // 去除括号内容
        title = title.replaceAll("\\s*\\(.*?\\)\\s*$", "");
        return title.trim();
    }

    /**
     * 解析视频列表（从 HTML 中提取）- 增强调试版
     */
    private List<Vod> parseList(String html) {
        List<Vod> list = new ArrayList<>();
        if (TextUtils.isEmpty(html)) {
            SpiderDebug.log("❌ HTML内容为空");
            return list;
        }

        SpiderDebug.log("📊 HTML长度: " + html.length() + " 字符");

        try {
            Document doc = Jsoup.parse(html);
            // 提取视频列表项（包含 /detail/ 的链接）
            Elements items = doc.select("a[href*=/detail/]");

            SpiderDebug.log("🔍 找到链接数: " + items.size());

            Set<String> seen = new HashSet<>();
            int debugCount = 0;  // 调试计数器

            for (Element item : items) {
                try {
                    String href = item.attr("href");

                    // 提取视频 ID
                    Matcher m = Pattern.compile("/detail/(\\d+)").matcher(href);
                    if (!m.find()) continue;
                    String vid = m.group(1);

                    // 去重
                    if (seen.contains(vid)) continue;
                    seen.add(vid);

                    // 提取图片（终极版：正则兜底提取）
                    String pic = "";
                    Elements imgs = item.select("img");

                    if (debugCount < 3) {
                        SpiderDebug.log("🔗 视频" + debugCount + " ID: " + vid + ", 找到img数: " + imgs.size());
                    }

                    if (!imgs.isEmpty()) {
                        Element img = imgs.first();

                        // 尝试所有可能的图片属性（按优先级）
                        String[] imgAttrs = {
                            "data-original", "data-src", "data-lazy-src",
                            "data-lazyload", "data-url", "src",
                            "data-cover", "data-thumb", "data-image"
                        };

                        for (String attr : imgAttrs) {
                            String val = img.attr(attr);
                            if (!TextUtils.isEmpty(val)) {
                                pic = val;
                                if (debugCount < 3) {
                                    SpiderDebug.log("  ✅ 图片属性: " + attr + " = " + val.substring(0, Math.min(50, val.length())));
                                }
                                break;
                            }
                        }

                        // 如果所有属性都为空，尝试从style的background-image提取
                        if (TextUtils.isEmpty(pic)) {
                            String style = img.attr("style");
                            if (!TextUtils.isEmpty(style) && style.contains("url")) {
                                Matcher styleMatcher = Pattern.compile("url\\(['\"]?([^'\"\\)]+)['\"]?\\)").matcher(style);
                                if (styleMatcher.find()) {
                                    pic = styleMatcher.group(1);
                                    if (debugCount < 3) {
                                        SpiderDebug.log("  ✅ 从style提取图片: " + pic.substring(0, Math.min(50, pic.length())));
                                    }
                                }
                            }
                        }

                        pic = fixImg(pic);
                    }

                    // 终极兜底：从item HTML中正则提取图片URL（支持CDN域名）
                    if (TextUtils.isEmpty(pic)) {
                        String itemHtml = item.html();
                        Matcher picMatcher = Pattern.compile("https://aka\\.doubaocdn\\.com/s/[A-Za-z0-9]+").matcher(itemHtml);
                        if (picMatcher.find()) {
                            pic = picMatcher.group(0);
                            if (debugCount < 3) {
                                SpiderDebug.log("  ✅ 终极兜底提取图片: " + pic);
                            }
                        }
                    }

                    // 再兜底：从item本身的style提取背景图
                    if (TextUtils.isEmpty(pic)) {
                        String style = item.attr("style");
                        if (!TextUtils.isEmpty(style) && style.contains("url")) {
                            Matcher bgMatcher = Pattern.compile("url\\(['\"]?([^'\"\\)]+)['\"]?\\)").matcher(style);
                            if (bgMatcher.find()) {
                                pic = bgMatcher.group(1);
                                if (debugCount < 3) {
                                    SpiderDebug.log("  ✅ 从item style提取图片: " + pic.substring(0, Math.min(50, pic.length())));
                                }
                                pic = fixImg(pic);
                            }
                        }
                    }

                    if (debugCount < 3 && TextUtils.isEmpty(pic)) {
                        SpiderDebug.log("  ⚠️ 无img标签，item HTML: " + item.html().substring(0, Math.min(150, item.html().length())));
                    }

                    // 提取标题
                    String title = item.attr("title");
                    if (TextUtils.isEmpty(title)) {
                        title = item.text().trim();
                    }
                    title = cleanTitle(title);

                    if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(vid)) {
                        list.add(new Vod(vid, title, pic));
                        debugCount++;
                    }
                } catch (Exception e) {
                    SpiderDebug.log("解析异常: " + e.getMessage());
                }
            }

            SpiderDebug.log("✅ 列表解析完成: 共 " + list.size() + " 个视频");
        } catch (Exception e) {
            SpiderDebug.log("❌ 解析失败: " + e.getMessage());
        }

        return list;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "最新电影"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("3", "综艺"));
        classes.add(new Class("4", "动漫"));

        List<Vod> videos = new ArrayList<>();
        Object[] result = get("/");
        if (result != null) {
            videos = parseList((String) result[0]);
        }

        return Result.string(classes, videos);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        // 构建分类 URL
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append("/vod/show/id/").append(tid);

        // 处理筛选参数
        if (extend != null) {
            for (Map.Entry<String, String> entry : extend.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (!TextUtils.isEmpty(value)) {
                    urlBuilder.append("/").append(key).append("/").append(value);
                }
            }
        }

        // 分页
        if (!TextUtils.isEmpty(pg) && !"1".equals(pg)) {
            urlBuilder.append("/page/").append(pg);
        }

        List<Vod> list = new ArrayList<>();
        Object[] result = get(urlBuilder.toString());
        if (result != null) {
            list = parseList((String) result[0]);
        }

        // 分页信息（简化处理）
        int page = Util.toInt(pg, 1);
        int pageCount = 99;
        int limit = 24;
        int total = 999;

        return Result.get().page(page, pageCount, limit, total).vod(list).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        List<Vod> list = new ArrayList<>();

        for (String vid : ids) {
            try {
                Object[] result = get("/detail/" + vid);
                if (result == null) continue;

                String html = (String) result[0];
                Document doc = Jsoup.parse(html);

                // 提取标题
                String name = "";
                Elements h1s = doc.select("h1");
                if (!h1s.isEmpty()) {
                    name = h1s.first().text().trim();
                }
                if (TextUtils.isEmpty(name)) {
                    Elements h2s = doc.select("h2");
                    if (!h2s.isEmpty()) {
                        name = h2s.first().text().trim();
                    }
                }
                if (TextUtils.isEmpty(name)) {
                    Elements titles = doc.select("title");
                    if (!titles.isEmpty()) {
                        name = titles.first().text().trim();
                        // 清理标题（去除站点名称）
                        name = name.split("-")[0].trim();
                    }
                }

                // 提取图片（增强版：覆盖所有可能的属性）
                String pic = "";
                String[] imgSelectors = {
                    "img[class*=poster]",
                    "img[class*=cover]",
                    "div[class*=poster] img",
                    "div[class*=cover] img",
                    "div[class*=detail] img",
                    "img"
                };
                String[] imgAttrs = {
                    "data-original", "data-src", "data-lazy-src",
                    "data-lazyload", "data-url", "src"
                };

                for (String selector : imgSelectors) {
                    Elements imgs = doc.select(selector);
                    if (!imgs.isEmpty()) {
                        Element img = imgs.first();
                        // 尝试所有可能的属性
                        for (String attr : imgAttrs) {
                            String val = img.attr(attr);
                            if (!TextUtils.isEmpty(val)) {
                                pic = val;
                                break;
                            }
                        }
                        if (!TextUtils.isEmpty(pic)) {
                            pic = fixImg(pic);
                            SpiderDebug.log("详情页图片提取成功: " + pic);
                            break;
                        }
                    }
                }

                // 提取剧集列表（增强版：过滤"立即播放"等干扰项）
                List<String> episodes = new ArrayList<>();
                String[] epSelectors = {
                    "a[href*=/vod/play/]",      // 标准选择器
                    "a[href*=sid]",              // sid参数匹配
                    "div[class*=playlist] a",
                    "ul[class*=episode] a"
                };

                int epIndex = 1;  // 选集序号（从1开始）

                for (String selector : epSelectors) {
                    Elements epLinks = doc.select(selector);
                    SpiderDebug.log("选集选择器 '" + selector + "' 找到 " + epLinks.size() + " 个元素");

                    if (!epLinks.isEmpty()) {
                        for (Element a : epLinks) {
                            try {
                                // 提取选集名称（优先使用链接文本）
                                String epName = a.text().trim();

                                // 过滤干扰项："立即播放"、"播放"、"立即"等非数字名称
                                if (TextUtils.isEmpty(epName) ||
                                    epName.contains("立即") ||
                                    epName.contains("播放") ||
                                    epName.equals("播放")) {
                                    continue;  // 跳过干扰项
                                }

                                // 如果链接文本为空，使用title或自动编号
                                if (TextUtils.isEmpty(epName)) {
                                    epName = a.attr("title");
                                }

                                // 如果仍然为空，使用自动编号
                                if (TextUtils.isEmpty(epName)) {
                                    epName = String.valueOf(epIndex);
                                }

                                // 提取播放URL
                                String epUrl = a.attr("href");
                                if (!TextUtils.isEmpty(epUrl)) {
                                    // 确保URL包含播放路径
                                    if (epUrl.contains("/vod/play/") || epUrl.contains("sid")) {
                                        episodes.add(epName + "$" + epUrl);

                                        // 调试：记录前5个选集
                                        if (episodes.size() <= 5) {
                                            SpiderDebug.log("选集" + episodes.size() + ": '" + epName + "' -> " + epUrl);
                                        }

                                        epIndex++;  // 递增序号
                                    }
                                }
                            } catch (Exception e) {
                                SpiderDebug.log("选集解析异常: " + e.getMessage());
                            }
                        }

                        // 如果成功提取，退出循环
                        if (!episodes.isEmpty()) {
                            SpiderDebug.log("✅ 选集提取完成: 共 " + episodes.size() + " 集");
                            break;
                        }
                    }
                }

                // 播放线路（只有一个）
                String playFrom = "爱电影播放器";
                String playUrl = TextUtils.join("#", episodes);

                Vod vod = new Vod();
                vod.setVodId(vid);
                vod.setVodName(name);
                vod.setVodPic(pic);
                vod.setVodPlayFrom(playFrom);
                vod.setVodPlayUrl(playUrl);

                list.add(vod);
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }

        return Result.string(list);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        // 构建搜索 URL
        String searchPath = "/vod/search/" + URLEncoder.encode(key, "UTF-8");

        List<Vod> list = new ArrayList<>();
        Object[] result = get(searchPath);
        if (result != null) {
            list = parseList((String) result[0]);
        }

        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // 播放 URL 是网页路径，使用 parse=1 让客户端解析器处理
        String url = id.startsWith("http") ? id : host + id;

        return Result.get()
            .parse(1)
            .url(url)
            .header(getHeaders(host))
            .string();
    }
}