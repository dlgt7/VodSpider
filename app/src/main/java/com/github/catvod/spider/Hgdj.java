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

            // 解析首页推荐视频（修正：使用更精确的选择器）
            List<String> seen = new ArrayList<>();

            for (Element link : doc.select("a[href*=/kpd/]")) {
                String vid = link.attr("href");
                if (TextUtils.isEmpty(vid) || !vid.contains("/kpd/")) continue;

                // 去重
                if (seen.contains(vid)) continue;
                seen.add(vid);

                String name = "";
                String pic = "";
                String remark = "";

                // 提取标题（优先从title属性，其次从链接文本）
                name = link.attr("title");
                if (TextUtils.isEmpty(name)) {
                    name = link.text().trim();
                    // 移除评分和状态信息
                    if (name.contains("更新") || name.contains("全集")) {
                        // 如果链接文本包含评分和状态，尝试从父元素提取标题
                        Element parent = link.parent();
                        if (parent != null) {
                            Element titleLink = parent.selectFirst("a[title]");
                            if (titleLink != null) {
                                name = titleLink.attr("title");
                            }
                        }
                    }
                }

                // 提取图片（懒加载）
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

                // 提取备注（评分和状态）
                String linkText = link.text().trim();
                if (!TextUtils.isEmpty(linkText)) {
                    // 提取评分和状态（如：7.0更新全集）
                    if (linkText.matches(".*\\d+\\.\\d+.*")) {
                        remark = linkText;
                    }
                }

                if (!TextUtils.isEmpty(name) && vid.length() > 5) {
                    list.add(new Vod(vid, name, pic, remark));
                }

                // 限制首页推荐数量
                if (list.size() >= 20) break;
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

            // 解析视频列表（修正：使用更精确的选择器）
            for (Element link : doc.select("a[href*=/kpd/]")) {
                String vid = link.attr("href");
                if (TextUtils.isEmpty(vid) || !vid.contains("/kpd/")) continue;

                String name = "";
                String pic = "";
                String remark = "";

                // 提取标题（从链接文本或title属性获取）
                name = link.attr("title");
                if (TextUtils.isEmpty(name)) {
                    name = link.text().trim();
                }

                // 提取图片（懒加载）
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

                // 提取备注（评分和状态）
                // 从链接文本中提取（格式：评分状态）
                String linkText = link.text().trim();
                if (!TextUtils.isEmpty(linkText) && linkText.contains("集")) {
                    remark = linkText;
                }

                if (!TextUtils.isEmpty(name) && vid.length() > 5) {
                    list.add(new Vod(vid, name, pic, remark));
                }
            }

            // 去重（同一视频可能被多次匹配）
            List<Vod> uniqueList = new ArrayList<>();
            List<String> seen = new ArrayList<>();
            for (Vod vod : list) {
                if (!seen.contains(vod.getVodId())) {
                    seen.add(vod.getVodId());
                    uniqueList.add(vod);
                }
            }
            list = uniqueList;

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

            // 标题（修正：提取书名号内的内容）
            Element titleElem = doc.selectFirst("h1, h2, title");
            if (titleElem != null) {
                name = titleElem.text().trim();
                // 移除书名号
                if (name.startsWith("《") && name.endsWith("》")) {
                    name = name.substring(1, name.length() - 1);
                }
            }

            // 图片（修正：使用更通用的选择器）
            Element imgElem = doc.selectFirst("img[src*='upload'], img[data-original], img[data-src], img[src]");
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

            // 从页面文本中提取参数信息（修正：使用正则提取）
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

            // 提取播放线路和剧集（修正：使用实际的选择器）
            StringBuilder vodPlayFrom = new StringBuilder();
            StringBuilder vodPlayUrl = new StringBuilder();

            // 查找所有播放链接
            Elements playLinks = doc.select("a[href*=/kpplay/]");

            if (playLinks.size() > 0) {
                // 分析播放URL，提取线路信息
                // URL格式：/kpplay/{video_id}-{线路序号}-{集数序号}/
                Map<String, List<String[]>> sourceMap = new HashMap<>();

                for (Element link : playLinks) {
                    String playUrl = link.attr("href");
                    String epName = link.text().trim();
                    if (TextUtils.isEmpty(epName)) {
                        epName = "全集";
                    }

                    // 从URL中提取线路序号
                    String[] parts = playUrl.split("/");
                    if (parts.length >= 4) {
                        String lastPart = parts[parts.length - 1]; // dBByD4-2-1
                        String[] ids_parts = lastPart.split("-");
                        if (ids_parts.length >= 3) {
                            String sourceId = ids_parts[1]; // 线路序号

                            // 线路名称（从页面文本中提取，如果找不到就用默认名称）
                            String sourceName = "线路" + sourceId;

                            // 尝试从页面提取线路名称（如果有）
                            if (!sourceMap.containsKey(sourceId)) {
                                sourceMap.put(sourceId, new ArrayList<>());
                            }

                            sourceMap.get(sourceId).add(new String[]{epName, playUrl});
                        }
                    }
                }

                // 尝试从页面提取线路名称
                Elements sourceElements = doc.select("div, span, p");
                String pageContent = doc.text();
                if (pageContent.contains("剧场")) {
                    // 提取线路名称（如：河马剧场、顶好剧场）
                    String[] lines = pageContent.split("\n");
                    int sourceIndex = 1;
                    for (String line : lines) {
                        if (line.contains("剧场") && !line.contains("相关")) {
                            String sourceName = line.trim();
                            if (sourceMap.containsKey(String.valueOf(sourceIndex))) {
                                // 更新线路名称
                                sourceName = sourceName.split(" ")[0]; // 只取第一个词
                            }
                            sourceIndex++;
                        }
                    }
                }

                // 构建vod_play_from和vod_play_url
                int sourceIndex = 1;
                for (Map.Entry<String, List<String[]>> entry : sourceMap.entrySet()) {
                    String sourceName = "线路" + sourceIndex;

                    // 尝试从页面提取线路名称
                    if (pageContent.contains("河马剧场") && sourceIndex == 2) {
                        sourceName = "河马剧场";
                    } else if (pageContent.contains("顶好剧场") && sourceIndex == 1) {
                        sourceName = "顶好剧场";
                    }

                    vodPlayFrom.append(sourceName).append("$$$");

                    List<String[]> episodes = entry.getValue();
                    for (int i = 0; i < episodes.size(); i++) {
                        String[] ep = episodes.get(i);
                        vodPlayUrl.append(ep[0]).append("$").append(ep[1]);
                        if (i < episodes.size() - 1) {
                            vodPlayUrl.append("#");
                        }
                    }
                    vodPlayUrl.append("$$$");

                    sourceIndex++;
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