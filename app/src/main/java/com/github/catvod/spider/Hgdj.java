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
    public void init(Context context, String extend) {
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

            // 解析首页推荐视频
            for (Element item : doc.select("a[href*=/kpd/]")) {
                String vid = item.attr("href");
                if (TextUtils.isEmpty(vid) || !vid.contains("/kpd/")) continue;

                String name = "";
                String pic = "";
                String remark = "";

                // 提取标题
                name = item.text().trim();

                // 提取图片（懒加载）
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

                // 提取备注（评分/状态）
                Element remarkElem = item.parent().selectFirst("span");
                if (remarkElem != null) {
                    remark = remarkElem.text().trim();
                }

                if (!TextUtils.isEmpty(name) && vid.length() > 5) {
                    list.add(new Vod(vid, name, pic, remark));
                }
            }

            // 去重（避免重复添加）
            if (list.size() > 20) {
                list = list.subList(0, 20);
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
            // 构建分类URL
            // 格式：/kpshow/dBBBBB-{频道}-----------{字母}-{排序}--{页码}---/
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append(siteUrl).append("/kpshow/dBBBBB-");

            // 频道筛选
            if (!TextUtils.isEmpty(tid)) {
                urlBuilder.append(tid);
            }
            urlBuilder.append("-----------");

            // 字母筛选（从extend参数获取）
            String letter = extend != null ? extend.get("letter") : null;
            if (!TextUtils.isEmpty(letter)) {
                urlBuilder.append(letter).append("------");
            } else {
                urlBuilder.append("------");
            }

            // 排序（从extend参数获取，默认为最新）
            String sort = extend != null ? extend.get("sort") : null;
            if (!TextUtils.isEmpty(sort)) {
                urlBuilder.append("-").append(sort).append("--");
            } else {
                urlBuilder.append("-time--");
            }

            // 页码
            urlBuilder.append(page).append("---/");

            String url = urlBuilder.toString();
            Document doc = Jsoup.parse(OkHttp.string(url, headers));

            // 解析视频列表
            for (Element item : doc.select("a[href*=/kpd/]")) {
                String vid = item.attr("href");
                if (TextUtils.isEmpty(vid) || !vid.contains("/kpd/")) continue;

                String name = "";
                String pic = "";
                String remark = "";

                // 提取标题
                name = item.text().trim();

                // 提取图片（懒加载）
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

            // 解析分页信息（从页面文本中提取）
            String pageText = doc.text();
            if (pageText.contains("页")) {
                try {
                    int pageIdx = pageText.indexOf("/");
                    if (pageIdx > 0) {
                        String pageCountStr = pageText.substring(pageIdx + 1).split("页")[0].trim();
                        pageCount = Integer.parseInt(pageCountStr.replaceAll("[^0-9]", ""));
                    }
                } catch (Exception e) {
                    pageCount = page;
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

            // 标题
            Element titleElem = doc.selectFirst("h1");
            if (titleElem != null) {
                name = titleElem.text().trim();
            }

            // 图片
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
            if (pageText.contains("地区：")) {
                int idx = pageText.indexOf("地区：");
                area = pageText.substring(idx + 3, Math.min(idx + 20, pageText.length())).split("年份")[0].trim();
            }
            if (pageText.contains("年份：")) {
                int idx = pageText.indexOf("年份：");
                year = pageText.substring(idx + 3, Math.min(idx + 10, pageText.length())).split("类型")[0].trim();
            }
            if (pageText.contains("导演：")) {
                int idx = pageText.indexOf("导演：");
                director = pageText.substring(idx + 3, Math.min(idx + 30, pageText.length())).split("主演")[0].trim();
            }
            if (pageText.contains("主演：")) {
                int idx = pageText.indexOf("主演：");
                actor = pageText.substring(idx + 3, Math.min(idx + 50, pageText.length())).split("简介")[0].trim();
            }

            // 简介
            Element briefElem = doc.selectFirst("div[class*=content], div[class*=intro], p[class*=intro]");
            if (briefElem != null) {
                brief = briefElem.text().trim();
            }

            // 提取播放线路和剧集
            StringBuilder vodPlayFrom = new StringBuilder();
            StringBuilder vodPlayUrl = new StringBuilder();

            // 查找所有播放源
            Elements sources = doc.select("div[class*=playlist], ul[class*=playlist]");
            Elements circuitTabs = doc.select("a[href^=#playlist], a[class*=tab]");

            if (circuitTabs.size() > 0) {
                // 多线路模式
                for (int i = 0; i < circuitTabs.size(); i++) {
                    String sourceName = circuitTabs.get(i).text().trim();
                    if (TextUtils.isEmpty(sourceName)) {
                        sourceName = "线路" + (i + 1);
                    }

                    vodPlayFrom.append(sourceName).append("$$$");

                    // 提取该线路下的所有剧集
                    Elements episodes = sources.size() > i ? sources.get(i).select("a") : new Elements();
                    for (int j = 0; j < episodes.size(); j++) {
                        Element ep = episodes.get(j);
                        String epName = ep.text().trim();
                        String epUrl = ep.attr("href");

                        vodPlayUrl.append(epName).append("$").append(epUrl);

                        if (j < episodes.size() - 1) {
                            vodPlayUrl.append("#");
                        }
                    }
                    vodPlayUrl.append("$$$");
                }
            } else {
                // 单线路模式（直接查找所有播放链接）
                Elements episodes = doc.select("a[href*=/kpplay/]");
                if (episodes.size() > 0) {
                    vodPlayFrom.append("默认$$$");

                    for (int j = 0; j < episodes.size(); j++) {
                        Element ep = episodes.get(j);
                        String epName = ep.text().trim();
                        if (TextUtils.isEmpty(epName)) {
                            epName = "全集";
                        }
                        String epUrl = ep.attr("href");

                        vodPlayUrl.append(epName).append("$").append(epUrl);

                        if (j < episodes.size() - 1) {
                            vodPlayUrl.append("#");
                        }
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