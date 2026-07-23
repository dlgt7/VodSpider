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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 樱花动漫 Spider
 */
public class YHDM extends Spider {

    private static final String DEFAULT_HOST = "https://www.dmvvv.com";

    private String siteUrl = DEFAULT_HOST;

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("Referer", siteUrl + "/");
        return header;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context);
        if (TextUtils.isEmpty(extend)) return;
        String ext = extend.trim();
        if (ext.startsWith("http")) {
            siteUrl = ext.replaceAll("/+$", "");
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("guoman", "国产动漫"));
        classes.add(new Class("riman", "日本动漫"));
        classes.add(new Class("oman", "欧美动漫"));
        classes.add(new Class("dmfilm", "动漫电影"));
        
        String html = OkHttp.string(siteUrl, getHeader());
        List<Vod> list = parseList(html);
        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = Util.toInt(pg, 1);
        String url = page <= 1 
            ? siteUrl + "/type/" + tid + "/" 
            : siteUrl + "/type/" + tid + "/" + page + "/";
        String html = OkHttp.string(url, getHeader());
        List<Vod> list = parseList(html);
        int pageCount = parsePageCount(html);
        return Result.get().vod(list).page(page, pageCount, 36, list.size()).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String videoId = ids.get(0);
        String url = videoId.startsWith("http") ? videoId : siteUrl + videoId;
        String html = OkHttp.string(url, getHeader());
        
        // 提取视频数字ID
        String vodIdNum = videoId.replaceAll(".*/(\\d+)/?$", "$1");
        
        Document doc = Jsoup.parse(html);
        
        // 基本信息
        String name = doc.selectFirst(".detail h2") != null ? doc.selectFirst(".detail h2").text() : "";
        String pic = "";
        Element img = doc.selectFirst(".detail .cover img");
        if (img != null) {
            pic = img.attr("data-original");
            if (TextUtils.isEmpty(pic)) pic = img.attr("src");
        }
        
        // 线路和剧集 - 核心解析
        List<String> playFrom = new ArrayList<>();
        List<String> playUrl = new ArrayList<>();
        
        Elements tabs = doc.select(".playlist .tabs a");
        Elements rows = doc.select(".playlist .row");
        
        // 方法1：直接解析 - 使用 text() 提取完整文本
        if (!tabs.isEmpty() && !rows.isEmpty()) {
            int count = Math.min(tabs.size(), rows.size());
            for (int i = 0; i < count; i++) {
                // 线路名：使用 text() 并清理空白字符
                String lineName = tabs.get(i).text().trim();
                // 如果为空，尝试从 HTML 中提取
                if (TextUtils.isEmpty(lineName)) {
                    String tabHtml = tabs.get(i).html();
                    lineName = tabHtml.replaceAll("<[^>]+>", "").trim();
                }
                if (TextUtils.isEmpty(lineName)) lineName = "线路" + (i + 1);
                playFrom.add(lineName);
                
                // 剧集 - 使用更通用的选择器（兼容有无 class="list6" 的情况）
                Elements eps = rows.get(i).select("ul li a");
                if (eps.isEmpty()) eps = rows.get(i).select("ul.list6 li a");
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < eps.size(); j++) {
                    if (j > 0) sb.append("#");
                    sb.append(eps.get(j).text()).append("$").append(eps.get(j).attr("href"));
                }
                playUrl.add(sb.toString());
            }
        }
        
        // 方法2：正则提取（备用）
        if (playFrom.isEmpty() && !TextUtils.isEmpty(html)) {
            // 提取线路名
            Pattern tabDivPattern = Pattern.compile("<div[^>]+class=\"tabs\"[^>]*>(.*?)</div>", Pattern.DOTALL);
            Matcher tabMatcher = tabDivPattern.matcher(html);
            if (tabMatcher.find()) {
                String tabsHtml = tabMatcher.group(1);
                Pattern aPattern = Pattern.compile("<a[^>]*>(.*?)</a>", Pattern.DOTALL);
                Matcher aMatcher = aPattern.matcher(tabsHtml);
                while (aMatcher.find()) {
                    String lineName = aMatcher.group(1).replaceAll("<[^>]+>", "").trim();
                    if (!TextUtils.isEmpty(lineName) && !lineName.contains("倒序")) {
                        playFrom.add(lineName);
                    }
                }
            }
            
            // 提取剧集 - 使用更通用的正则（不强制要求 class="list6"）
            Pattern rowPattern = Pattern.compile("<div[^>]+class=\"row\"[^>]*>.*?<ul[^>]*>(.*?)</ul>", Pattern.DOTALL);
            Matcher rowMatcher = rowPattern.matcher(html);
            while (rowMatcher.find()) {
                String rowHtml = rowMatcher.group(1);
                Pattern epPattern = Pattern.compile("<a[^>]+href=\"([^\"]+)\"[^>]*>([^<]+)</a>");
                Matcher epMatcher = epPattern.matcher(rowHtml);
                StringBuilder sb = new StringBuilder();
                while (epMatcher.find()) {
                    if (sb.length() > 0) sb.append("#");
                    sb.append(epMatcher.group(2).trim()).append("$").append(epMatcher.group(1));
                }
                if (sb.length() > 0) {
                    playUrl.add(sb.toString());
                }
            }
        }
        
        // 方法3：从播放链接中动态提取线路（兜底）
        if (playFrom.isEmpty() && !TextUtils.isEmpty(html)) {
            // 提取所有播放链接并按线路索引分组
            Pattern playLinkPattern = Pattern.compile("/play/" + vodIdNum + "-(\\d+)-(\\d+)/");
            Matcher m = playLinkPattern.matcher(html);
            
            // 使用 Map 按线路索引分组
            Map<String, List<String[]>> sourceMap = new java.util.LinkedHashMap<>();
            while (m.find()) {
                String sourceIdx = m.group(1);
                String epIdx = m.group(2);
                if (!sourceMap.containsKey(sourceIdx)) {
                    sourceMap.put(sourceIdx, new ArrayList<>());
                }
                // 存储剧集索引和完整链接
                sourceMap.get(sourceIdx).add(new String[]{epIdx, m.group(0)});
            }
            
            // 按线路索引排序并构建播放列表
            List<String> sortedKeys = new ArrayList<>(sourceMap.keySet());
            java.util.Collections.sort(sortedKeys, java.util.Comparator.comparingInt(Integer::parseInt));
            
            for (String sourceIdx : sortedKeys) {
                playFrom.add("线路" + (playFrom.size() + 1));
                List<String[]> eps = sourceMap.get(sourceIdx);
                // 按剧集索引排序
                eps.sort((a, b) -> Integer.parseInt(a[0]) - Integer.parseInt(b[0]));
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < eps.size(); i++) {
                    if (sb.length() > 0) sb.append("#");
                    String epName = "第" + (i + 1) + "集";
                    sb.append(epName).append("$").append(eps.get(i)[1]);
                }
                playUrl.add(sb.toString());
            }
        }
        
        // 方法4：如果以上都失败，使用硬编码线路名（最后兜底）
        if (playFrom.isEmpty() && !TextUtils.isEmpty(vodIdNum) && vodIdNum.matches("\\d+")) {
            playFrom.add("默认");
            StringBuilder sb = new StringBuilder();
            for (int ep = 1; ep <= 12; ep++) {
                if (sb.length() > 0) sb.append("#");
                String epName = ep < 10 ? "第0" + ep + "集" : "第" + ep + "集";
                sb.append(epName).append("$").append("/play/").append(vodIdNum).append("-1-").append(ep).append("/");
            }
            playUrl.add(sb.toString());
        }
        
        // 对齐线路和剧集数量
        while (playFrom.size() > playUrl.size()) {
            playUrl.add("");
        }
        while (playUrl.size() > playFrom.size()) {
            playFrom.add("线路" + (playFrom.size() + 1));
        }
        
        Vod vod = new Vod();
        vod.setVodId(videoId);
        vod.setVodName(name);
        vod.setVodPic(pic);
        if (!playFrom.isEmpty()) {
            vod.setVodPlayFrom(TextUtils.join("$$$", playFrom));
            vod.setVodPlayUrl(TextUtils.join("$$$", playUrl));
        }
        
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        int page = Util.toInt(pg, 1);
        String url = siteUrl + "/search/?wd=" + Uri.encode(key);
        if (page > 1) url += "&pageno=" + page;
        String html = OkHttp.string(url, getHeader());
        List<Vod> list = parseList(html);
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String url = id.startsWith("http") ? id : siteUrl + id;
        String html = OkHttp.string(url, getHeader());
        
        String playUrl = "";
        Pattern p = Pattern.compile("url\\s*:\\s*['\"]([^'\"]+)['\"]");
        Matcher m = p.matcher(html);
        if (m.find()) {
            playUrl = m.group(1);
        } else {
            Pattern p2 = Pattern.compile("(https?://[^'\"]+\\.m3u8[^'\"]*)");
            Matcher m2 = p2.matcher(html);
            if (m2.find()) playUrl = m2.group(1);
        }
        
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("Referer", siteUrl + "/");
        return Result.get().url(playUrl).header(header).string();
    }

    private List<Vod> parseList(String html) {
        List<Vod> list = new ArrayList<>();
        if (TextUtils.isEmpty(html)) return list;
        Document doc = Jsoup.parse(html);
        for (Element li : doc.select("ul > li")) {
            Element a = li.selectFirst("a[href*=/detail/]");
            if (a == null) continue;
            String href = a.attr("href");
            String title = a.attr("title");
            if (TextUtils.isEmpty(title)) title = a.text();
            
            String pic = "";
            Element img = li.selectFirst("img");
            if (img != null) {
                pic = img.attr("data-original");
                if (TextUtils.isEmpty(pic)) pic = img.attr("src");
            }
            
            String remark = "";
            Element p = li.selectFirst("p");
            if (p != null) remark = p.text();
            
            list.add(new Vod(href, title, pic, remark));
        }
        return list;
    }

    private int parsePageCount(String html) {
        Pattern p = Pattern.compile("/type/[^/]+/(\\d+)/");
        Matcher m = p.matcher(html);
        int max = 1;
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n > max) max = n;
        }
        return max;
    }
}