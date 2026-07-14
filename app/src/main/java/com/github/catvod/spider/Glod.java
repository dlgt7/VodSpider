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
     * 多域名自动切换请求
     * @param path 相对路径（如 "/"、"/vod/show/id/1"）
     * @return [响应内容, 成功的域名]，失败返回 null
     */
    private Object[] get(String path) {
        // 尝试每个域名
        for (String domain : domains) {
            try {
                String url = path.startsWith("http") ? path : domain + path;
                String content = OkHttp.string(url, getHeaders(domain));

                // 检查响应有效性（长度 > 1000）
                if (!TextUtils.isEmpty(content) && content.length() > 1000) {
                    // 更新当前域名
                    host = domain;
                    return new Object[]{content, domain};
                }
            } catch (Exception e) {
                SpiderDebug.log("域名访问失败: " + domain + " - " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * 修复图片 URL
     */
    private String fixImg(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return IMG_CDN + url;
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
     * 解析视频列表（从 HTML 中提取）
     */
    private List<Vod> parseList(String html) {
        List<Vod> list = new ArrayList<>();
        if (TextUtils.isEmpty(html)) return list;

        try {
            Document doc = Jsoup.parse(html);
            // 提取视频列表项（包含 /detail/ 的链接）
            Elements items = doc.select("a[href*=/detail/]");

            Set<String> seen = new HashSet<>();
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

                    // 提取图片
                    String pic = "";
                    Elements imgs = item.select("img");
                    if (!imgs.isEmpty()) {
                        Element img = imgs.first();
                        pic = img.attr("data-original");
                        if (TextUtils.isEmpty(pic)) {
                            pic = img.attr("data-src");
                        }
                        if (TextUtils.isEmpty(pic)) {
                            pic = img.attr("src");
                        }
                        pic = fixImg(pic);
                    }

                    // 提取标题
                    String title = item.attr("title");
                    if (TextUtils.isEmpty(title)) {
                        title = item.text().trim();
                    }
                    title = cleanTitle(title);

                    if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(vid)) {
                        list.add(new Vod(vid, title, pic));
                    }
                } catch (Exception e) {
                    SpiderDebug.log(e);
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
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

                // 提取图片（多种选择器尝试）
                String pic = "";
                String[] imgSelectors = {
                    "img[class*=poster]",
                    "img[class*=cover]",
                    "div[class*=poster] img",
                    "div[class*=cover] img",
                    "div[class*=detail] img",
                    "img"
                };
                for (String selector : imgSelectors) {
                    Elements imgs = doc.select(selector);
                    if (!imgs.isEmpty()) {
                        pic = imgs.first().attr("data-original");
                        if (TextUtils.isEmpty(pic)) {
                            pic = imgs.first().attr("data-src");
                        }
                        if (TextUtils.isEmpty(pic)) {
                            pic = imgs.first().attr("src");
                        }
                        if (!TextUtils.isEmpty(pic)) {
                            pic = fixImg(pic);
                            break;
                        }
                    }
                }

                // 提取剧集列表（多种选择器尝试）
                List<String> episodes = new ArrayList<>();
                String[] epSelectors = {
                    "a[href*=/vod/play/]",
                    "div[class*=playlist] a",
                    "ul[class*=episode] a"
                };
                for (String selector : epSelectors) {
                    Elements epLinks = doc.select(selector);
                    if (!epLinks.isEmpty()) {
                        int i = 1;
                        for (Element a : epLinks) {
                            String epName = a.text().trim();
                            if (TextUtils.isEmpty(epName)) {
                                epName = "第" + i + "集";
                            }
                            String epUrl = a.attr("href");
                            if (!TextUtils.isEmpty(epUrl) && epUrl.contains("/vod/play/")) {
                                episodes.add(epName + "$" + epUrl);
                                i++;
                            }
                        }
                        if (!episodes.isEmpty()) break;
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