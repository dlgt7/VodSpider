package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 88看球直播源爬虫实现。
 * 支持篮球、足球、网球等体育赛事直播列表、详情解析及播放地址获取。
 */
public class Kanqiu extends Spider {

    private static String siteUrl = "http://www.88kanqiu.one";

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        return header;
    }

    @Override
    public void init(Context context, String extend) {
        if (!extend.isEmpty()) siteUrl = extend;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        List<String> typeIds = Arrays.asList("", "1", "8", "21");
        List<String> typeNames = Arrays.asList("全部直播", "篮球直播", "足球直播", "其他直播");
        for (int i = 0; i < typeIds.size(); i++) classes.add(new Class(typeIds.get(i), typeNames.get(i)));
        String f = "{\"1\": [{\"key\": \"cateId\", \"name\": \"类型\", \"value\": [{\"n\": \"NBA\", \"v\": \"1\"}, {\"n\": \"CBA\", \"v\": \"2\"}, {\"n\": \"篮球综合\", \"v\": \"4\"}, {\"n\": \"纬来体育\", \"v\": \"21\"}]}],\"8\": [{\"key\": \"cateId\", \"name\": \"类型\", \"value\": [{\"n\": \"英超\", \"v\": \"8\"}, {\"n\": \"西甲\", \"v\": \"9\"}, {\"n\": \"意甲\", \"v\": \"10\"}, {\"n\": \"欧冠\", \"v\": \"12\"}, {\"n\": \"欧联\", \"v\": \"13\"}, {\"n\": \"德甲\", \"v\": \"14\"}, {\"n\": \"法甲\", \"v\": \"15\"}, {\"n\": \"欧国联\", \"v\": \"16\"}, {\"n\": \"足总杯\", \"v\": \"27\"}, {\"n\": \"国王杯\", \"v\": \"33\"}, {\"n\": \"中超\", \"v\": \"7\"}, {\"n\": \"亚冠\", \"v\": \"11\"}, {\"n\": \"足球综合\", \"v\": \"23\"}, {\"n\": \"欧协联\", \"v\": \"28\"}, {\"n\": \"美职联\", \"v\": \"26\"}]}], \"29\": [{\"key\": \"cateId\", \"name\": \"类型\", \"value\": [{\"n\": \"网球\", \"v\": \"29\"}, {\"n\": \"斯洛克\", \"v\": \"30\"}, {\"n\": \"MLB\", \"v\": \"38\"}, {\"n\": \"UFC\", \"v\": \"32\"}, {\"n\": \"NFL\", \"v\": \"25\"}, {\"n\": \"CCTV5\", \"v\": \"18\"}]}]}";
        JSONObject filterConfig = new JSONObject(f);
        return Result.string(classes, filterConfig);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        String cateId = extend.get("cateId") == null ? tid : extend.get("cateId");
        // 处理 "all" 类型
        if ("all".equals(cateId)) cateId = "";
        String urlPath = cateId == null || cateId.isEmpty() ? "" : String.format("/match/%s/live", cateId);
        Elements lis = Jsoup.parse(OkHttp.string(siteUrl + urlPath, getHeader())).select("li.group-game-item");
        List<Vod> list = new ArrayList<>();
        for (Element li : lis) {
            // 获取直播链接
            Elements linkElements = li.select("a[href*=/live/]");
            if (linkElements.isEmpty()) continue;
            Element linkElement = linkElements.first();
            String href = linkElement.attr("href");
            if (href.isEmpty()) continue;
            String vid = href.startsWith("http") ? href : siteUrl + href;

            // 获取按钮状态文本（备注）
            String remark = linkElement.text().trim();
            // 如果按钮被禁用且无文本，标记为"未开始"
            if (linkElement.hasClass("btn-disabled") && remark.isEmpty()) {
                remark = "未开始";
            }

            // 获取比赛名称（队伍名称）
            Elements teamElements = li.select(".team-name[title]");
            String name;
            if (teamElements.size() >= 2) {
                // 双方对阵
                name = teamElements.get(0).attr("title") + " vs " + teamElements.get(1).attr("title");
            } else if (teamElements.size() == 1) {
                // 单方
                name = teamElements.get(0).attr("title");
            } else {
                // 使用游戏类型或备注
                Element gameType = li.select(".game-type").first();
                name = gameType != null ? gameType.text() : remark.isEmpty() ? vid : remark;
            }

            // 获取图片
            String pic = li.select("img[data-src]").attr("data-src");
            if (pic.isEmpty()) pic = li.select("img").attr("src");
            if (pic.isEmpty()) pic = "https://pic.imgdb.cn/item/657673d6c458853aeff94ab9.jpg";
            if (!pic.startsWith("http")) pic = siteUrl + pic;

            list.add(new Vod(vid, name, pic.trim(), remark));
        }
        return Result.get().page(1, 1, 0, lis.size()).vod(list).string();
    }

    /**
     * 解码并解析播放链接。
     * 从 Base64 编码的数据中提取播放链接列表。
     */
    private List<String> decodeAndParsePlayLinks(String encodedData) {
        List<String> playLinks = new ArrayList<>();
        try {
            // 剪切掉前6个字符和后2个字符
            String trimmed = encodedData.substring(6, encodedData.length() - 2);
            // Base64 解码
            String json = new String(Base64.decode(trimmed, Base64.DEFAULT));
            JSONArray linksArray = new JSONObject(json).getJSONArray("links");
            for (int i = 0; i < linksArray.length(); i++) {
                JSONObject linkObject = linksArray.getJSONObject(i);
                String name = linkObject.optString("name");
                String url = linkObject.optString("url").replace("#", "***");
                playLinks.add(name + "$" + url);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return playLinks;
    }

    @Override
    public String detailContent(List<String> ids) {
        if (ids == null || ids.isEmpty()) return Result.error("无内容");
        String videoId = ids.get(0);
        if (videoId.equals(siteUrl)) return Result.error("比赛尚未开始");

        List<String> playLinks = new ArrayList<>();
        try {
            // 尝试第一种解析方式：从 -url 接口获取
            String content = OkHttp.string(videoId + "-url", getHeader());
            String encodedData = new JSONObject(content).optString("data");
            if (!encodedData.isEmpty()) {
                playLinks = decodeAndParsePlayLinks(encodedData);
            }
        } catch (Exception e1) {
            // 第一种方式失败，尝试备用方案：从详情页提取
            try {
                String html = OkHttp.string(videoId, getHeader());
                Element inputElement = Jsoup.parse(html).select("#t[value]").first();
                if (inputElement != null) {
                    String value = inputElement.attr("value");
                    if (!value.isEmpty()) {
                        playLinks = decodeAndParsePlayLinks(value);
                    }
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }

        if (playLinks.isEmpty()) return Result.error("比赛尚未开始或暂无线路");

        Vod vod = new Vod();
        vod.setVodId(videoId);
        vod.setVodPlayFrom("Qile");
        vod.setVodPlayUrl(TextUtils.join("#", playLinks));
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return Result.get().url(id.replace("***", "#")).parse().header(getHeader()).string();
    }
}
