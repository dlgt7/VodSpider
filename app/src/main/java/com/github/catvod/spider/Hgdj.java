package com.github.catvod.spider;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 红果短剧爬虫
 * 网站地址：https://www.tjmyjd.com
 * 类型：短剧站（MacCMS）
 *
 * @author Trae
 * @date 2026-07-14
 */
public class Hgdj extends Spider {

    private static final String SITE_URL = "https://www.tjmyjd.com";
    private String siteUrl = SITE_URL;

    private final Map<String, String> headers = new HashMap<String, String>() {{
        put("User-Agent", Util.CHROME);
        put("Referer", SITE_URL);
    }};

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context);

        if (!TextUtils.isEmpty(extend)) {
            extend = extend.trim();
            if (extend.startsWith("http")) {
                siteUrl = extend;
            }
        }
    }

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();

        // 分类列表（根据网站实际分类）
        classes.add(new Class("r", "言情总裁"));
        classes.add(new Class("8", "反转爽剧"));
        classes.add(new Class("W", "古装仙侠"));
        classes.add(new Class("j", "重生穿越"));
        classes.add(new Class("L", "脑洞悬疑"));
        classes.add(new Class("g", "现代都市"));
        classes.add(new Class("1", "有声动漫"));
        classes.add(new Class("G", "AI漫剧"));

        List<Vod> list = new ArrayList<>();

        try {
            Document doc = Jsoup.parse(OkHttp.string(siteUrl, headers));

            List<String> seen = new ArrayList<>();

            // 1. 提取热播榜（TOP 1/2/3）- 使用播放链接（/kpplay/）
            for (Element link : doc.select("a[href*=/kpplay/]")) {
                String playUrl = link.attr("href");
                if (TextUtils.isEmpty(playUrl) || !playUrl.contains("/kpplay/")) continue;

                // 从播放链接提取video_id（格式：/kpplay/dBBHPS-1-1/）
                String[] parts = playUrl.split("/");
                if (parts.length < 4) continue;

                String lastPart = parts[parts.length - 1]; // dBBHPS-1-1
                String videoId = lastPart.split("-")[0]; // dBBHPS

                // 构造详情链接
                String vid = "/kpd/" + videoId + "/";

                // 去重
                if (seen.contains(videoId)) continue;
                seen.add(videoId);

                // 从链接文本提取备注（如"全集免费观看"）
                String remark = link.text().trim();
                if (TextUtils.isEmpty(remark) || remark.contains("立即播放")) {
                    continue; // 跳过"立即播放"链接
                }

                // 尝试从父元素提取标题（格式：## 标题）
                String name = "";
                Element parent = link.parent();
                if (parent != null) {
                    // 查找标题元素（h1, h2, h3等）
                    Element titleElem = parent.selectFirst("h1, h2, h3, strong, b");
                    if (titleElem != null) {
                        name = titleElem.text().trim();
                    }
                }

                // 如果没有找到标题，尝试从页面其他位置提取
                if (TextUtils.isEmpty(name)) {
                    // 查找包含video_id的标题元素
                    for (Element elem : doc.select("h1, h2, h3")) {
                        String text = elem.text();
                        if (!TextUtils.isEmpty(text) && !text.contains("TOP")) {
                            name = text;
                            break;
                        }
                    }
                }

                if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(videoId)) {
                    list.add(new Vod(vid, name, "", remark));
                }
            }

            // 2. 提取正在热播短剧推荐 - 使用详情链接（/kpd/）
            for (Element link : doc.select("a[href*=/kpd/]")) {
                String vid = link.attr("href");
                if (TextUtils.isEmpty(vid) || !vid.contains("/kpd/")) continue;

                String videoId = vid.replace("/kpd/", "").replace("/", "");

                // 去重
                if (seen.contains(videoId)) continue;
                seen.add(videoId);

                String name = "";
                String pic = "";
                String remark = "";

                // 提取标题（优先从title属性）
                name = link.attr("title");
                if (TextUtils.isEmpty(name)) {
                    name = link.text().trim();
                }

                // 提取图片（优先懒加载属性）
                Element img = link.selectFirst("img");
                if (img != null) {
                    pic = img.attr("data-original");
                    if (TextUtils.isEmpty(pic)) {
                        pic = img.attr("data-src");
                    }
                    if (TextUtils.isEmpty(pic)) {
                        pic = img.attr("src");
                    }
                    pic = fixUrl(pic);
                }

                // 提取备注
                String linkText = link.text().trim();
                if (!TextUtils.isEmpty(linkText) && linkText.matches(".*\\d+\\.\\d+.*")) {
                    remark = linkText;
                }

                if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(videoId)) {
                    list.add(new Vod(vid, name, pic, remark));
                }

                if (list.size() >= 30) break;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        List<Vod> list = new ArrayList<>();
        int page = Util.toInt(pg, 1);
        int pageCount = 1;
        int total = 0;
        int limit = 40;

        try {
            // 构建分类URL（修正：分类ID直接拼接，没有分隔符）
            // 格式：/kpshow/dBBBB{频道ID}-----------/
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append(siteUrl).append("/kpshow/dBBBB");

            // 频道筛选（直接拼接，无分隔符）
            if (!TextUtils.isEmpty(tid)) {
                urlBuilder.append(tid);
            }

            // 排序（从extend参数获取，默认为最新）
            String sort = extend != null ? extend.get("sort") : null;
            if (!TextUtils.isEmpty(sort)) {
                urlBuilder.append("--").append(sort);
            }

            urlBuilder.append("---------");

            // 页码（第1页不需要页码，第2页及以后需要页码）
            if (page > 1) {
                urlBuilder.append(page);
            }

            urlBuilder.append("/");

            String url = urlBuilder.toString();
            Document doc = Jsoup.parse(OkHttp.string(url, headers));

            // 解析视频列表（修正：处理两种不同的链接格式）
            List<String> seen = new ArrayList<>();

            for (Element link : doc.select("a[href*=/kpd/]")) {
                String vid = link.attr("href");
                if (TextUtils.isEmpty(vid) || !vid.contains("/kpd/")) continue;

                String videoId = vid.replace("/kpd/", "").replace("/", "");

                // 去重
                if (seen.contains(videoId)) continue;
                seen.add(videoId);

                String name = "";
                String pic = "";
                String remark = "";

                // 提取标题（从链接文本或title属性）
                name = link.attr("title");
                if (TextUtils.isEmpty(name)) {
                    name = link.text().trim();
                    // 如果文本包含评分和状态，提取备注
                    if (name.matches(".*\\d+\\.\\d+.*")) {
                        remark = name;
                        // 尝试从父元素提取标题
                        Element parent = link.parent();
                        if (parent != null) {
                            Element titleLink = parent.selectFirst("a[title]");
                            if (titleLink != null) {
                                name = titleLink.attr("title");
                            }
                        }
                    }
                }

                // 提取图片（优先懒加载属性）
                Element img = link.selectFirst("img");
                if (img != null) {
                    pic = img.attr("data-original");
                    if (TextUtils.isEmpty(pic)) {
                        pic = img.attr("data-src");
                    }
                    if (TextUtils.isEmpty(pic)) {
                        pic = img.attr("src");
                    }
                    pic = fixUrl(pic);
                } else {
                    // 如果没有img标签，尝试从链接文本提取图片URL（Markdown格式）
                    String linkText = link.text();
                    if (linkText.contains("http") && linkText.contains(".jpg")) {
                        // 提取URL（格式：图片描述(图片URL)）
                        int start = linkText.indexOf("http");
                        int end = linkText.indexOf(".jpg") + 4;
                        if (end > start) {
                            pic = linkText.substring(start, end);
                        }
                    }
                }

                if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(videoId)) {
                    list.add(new Vod(vid, name, pic, remark));
                }
            }

            // 解析分页信息
            String pageText = doc.text();
            if (pageText.contains("/") && pageText.contains("页")) {
                try {
                    // 提取总页数
                    int pageIdx = pageText.indexOf("/");
                    if (pageIdx > 0) {
                        String afterSlash = pageText.substring(pageIdx + 1);
                        String pageCountStr = afterSlash.split("页")[0].trim();
                        pageCount = Integer.parseInt(pageCountStr.replaceAll("[^0-9]", ""));
                    }
                } catch (Exception e) {
                    pageCount = 1;
                }
            }

            total = list.size() * pageCount;
            if (pageCount == 0) pageCount = 1;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.get().page(page, pageCount, limit, total).vod(list).string();
    }

    @Override
    public String detailContent(List<String> ids) {
        List<Vod> list = new ArrayList<>();

        try {
            String vid = ids.get(0);
            String detailUrl = siteUrl + vid;

            Document doc = Jsoup.parse(OkHttp.string(detailUrl, headers));

            // 提取基本信息
            String name = "";
            String pic = "";
            String area = "";
            String year = "";
            String director = "";
            String actor = "";
            String remarks = "";
            String brief = "";

            // 标题（提取书名号内的内容）
            Element titleElem = doc.selectFirst("h1, h2, title");
            if (titleElem != null) {
                name = titleElem.text().trim();
                // 移除书名号
                if (name.startsWith("《") && name.endsWith("》")) {
                    name = name.substring(1, name.length() - 1);
                }
            }

            // 图片（优先懒加载属性）
            Element imgElem = doc.selectFirst("img[data-original], img[data-src], img[src]");
            if (imgElem != null) {
                pic = imgElem.attr("data-original");
                if (TextUtils.isEmpty(pic)) {
                    pic = imgElem.attr("data-src");
                }
                if (TextUtils.isEmpty(pic)) {
                    pic = imgElem.attr("src");
                }
                pic = fixUrl(pic);
            }

            // 从页面文本中提取参数信息
            String pageText = doc.text();

            // 提取地区
            if (pageText.contains("地区：")) {
                try {
                    int idx = pageText.indexOf("地区：");
                    String afterArea = pageText.substring(idx + 3);
                    area = afterArea.split("年份|类型|频道")[0].trim();
                } catch (Exception e) {
                    area = "";
                }
            }

            // 提取年份
            if (pageText.contains("年份：")) {
                try {
                    int idx = pageText.indexOf("年份：");
                    String afterYear = pageText.substring(idx + 3);
                    year = afterYear.split("类型|频道|上映")[0].trim();
                } catch (Exception e) {
                    year = "";
                }
            }

            // 提取导演
            if (pageText.contains("导演：")) {
                try {
                    int idx = pageText.indexOf("导演：");
                    String afterDirector = pageText.substring(idx + 3);
                    director = afterDirector.split("主演|年份|简介")[0].trim();
                } catch (Exception e) {
                    director = "";
                }
            }

            // 提取主演
            if (pageText.contains("主演：")) {
                try {
                    int idx = pageText.indexOf("主演：");
                    String afterActor = pageText.substring(idx + 3);
                    actor = afterActor.split("导演|年份|简介")[0].trim();
                } catch (Exception e) {
                    actor = "";
                }
            }

            // 提取状态
            if (pageText.contains("状态：")) {
                try {
                    int idx = pageText.indexOf("状态：");
                    String afterStatus = pageText.substring(idx + 3);
                    remarks = afterStatus.split("主演|导演|年份")[0].trim();
                } catch (Exception e) {
                    remarks = "";
                }
            }

            // 提取简介
            if (pageText.contains("简介：")) {
                try {
                    int idx = pageText.indexOf("简介：");
                    brief = pageText.substring(idx + 3).trim();
                } catch (Exception e) {
                    brief = "";
                }
            }

            // 提取播放线路和剧集（重写：简化逻辑）
            StringBuilder vodPlayFrom = new StringBuilder();
            StringBuilder vodPlayUrl = new StringBuilder();

            // 查找所有播放链接
            Elements playLinks = doc.select("a[href*=/kpplay/]");

            if (playLinks.size() > 0) {
                // 简单模式：所有剧集放在一个线路中
                vodPlayFrom.append("默认$$$");

                for (int i = 0; i < playLinks.size(); i++) {
                    Element link = playLinks.get(i);
                    String playUrl = link.attr("href");
                    String epName = link.text().trim();

                    if (TextUtils.isEmpty(epName)) {
                        epName = "全集";
                    }

                    vodPlayUrl.append(epName).append("$").append(playUrl);

                    if (i < playLinks.size() - 1) {
                        vodPlayUrl.append("#");
                    }
                }
            }

            // 构建Vod对象
            Vod vod = new Vod();
            vod.setVodId(vid);
            vod.setVodName(name);
            vod.setVodPic(pic);
            vod.setVodArea(area);
            vod.setVodYear(year);
            vod.setVodDirector(director);
            vod.setVodActor(actor);
            vod.setVodRemarks(remarks);
            vod.setVodContent(brief);
            vod.setVodPlayFrom(vodPlayFrom.toString());
            vod.setVodPlayUrl(vodPlayUrl.toString());

            list.add(vod);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.string(list);
    }

    @Override
    public String searchContent(String key, boolean quick) {
        List<Vod> list = new ArrayList<>();

        try {
            // 搜索URL格式：/kpsearch/------------关键词-/
            String searchUrl = siteUrl + "/kpsearch/------------" + Uri.encode(key) + "-/";
            Document doc = Jsoup.parse(OkHttp.string(searchUrl, headers));

            // 解析搜索结果
            for (Element item : doc.select("a[href*=/kpd/]")) {
                String vid = item.attr("href");
                if (TextUtils.isEmpty(vid) || !vid.contains("/kpd/")) continue;

                String name = "";
                String pic = "";
                String remark = "";

                // 提取标题
                name = item.text().trim();

                // 提取图片
                Element img = item.selectFirst("img");
                if (img != null) {
                    pic = img.attr("data-original");
                    if (TextUtils.isEmpty(pic)) {
                        pic = img.attr("data-src");
                    }
                    if (TextUtils.isEmpty(pic)) {
                        pic = img.attr("src");
                    }
                    pic = fixUrl(pic);
                }

                // 提取备注
                Element remarkElem = item.parent().selectFirst("span");
                if (remarkElem != null) {
                    remark = remarkElem.text().trim();
                }

                if (!TextUtils.isEmpty(name) && vid.length() > 5) {
                    list.add(new Vod(vid, name, pic, remark));
                }
            }

            // 限制搜索结果数量
            if (list.size() > 20) {
                list = list.subList(0, 20);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            // MacCMS短剧站点：使用parse=1，让客户端解析器处理播放链接
            // 不需要自己解密，客户端会自动处理
            String playUrl = id;
            if (!id.startsWith("http")) {
                playUrl = siteUrl + id;
            }

            return Result.get()
                    .parse(1)
                    .url(playUrl)
                    .header(headers)
                    .string();

        } catch (Exception e) {
            e.printStackTrace();
            return Result.get().parse(1).url("").string();
        }
    }

    /**
     * 修复URL（处理相对路径和懒加载）
     */
    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";

        // 处理协议相对路径
        if (url.startsWith("//")) {
            return "https:" + url;
        }

        // 处理绝对路径
        if (url.startsWith("/")) {
            return siteUrl + url;
        }

        // 已经是完整URL
        if (url.startsWith("http")) {
            return url;
        }

        // 其他情况（相对路径）
        return siteUrl + "/" + url;
    }
}