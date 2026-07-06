package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
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
import java.util.Map;  // ⚠️ 重要：必须导入Map类

/**
 * 爬虫名称：Kanqiu（88看球）
 * 爬虫类型：体育直播源（HTML解析型）
 * 网站地址：http://www.88kanqiu.tw
 * 功能说明：提供体育赛事直播（NBA、CBA、世界杯、英超、西甲等）
 */
public class Kanqiu extends Spider {

    private static String siteUrl = "http://www.88kanqiu.tw";
    
    private final Map<String, String> headers = new HashMap<String, String>() {{
        put("User-Agent", Util.CHROME);
    }};
    
    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        return header;
    }
    
    @Override
    public void init(Context context, String extend) throws Exception {
        // ⚠️ 重要：先调用父类init(context)方法
        super.init(context);
        
        // ⚠️ 修复：使用TextUtils.isEmpty检查null和空串，避免NPE
        if (!TextUtils.isEmpty(extend)) {
            extend = extend.trim();
            // 支持多源站配置（逗号分隔）
            if (extend.contains(",")) {
                String[] hosts = extend.split(",");
                // 使用第一个源站作为主站
                siteUrl = hosts[0].trim();
            } else {
                siteUrl = extend;
            }
        }
    }
    
    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        
        try {
            // 定义主要分类
            List<String> typeIds = Arrays.asList("", "1", "8", "21");
            List<String> typeNames = Arrays.asList("全部直播", "篮球直播", "足球直播", "其他直播");
            
            for (int i = 0; i < typeIds.size(); i++) {
                classes.add(new Class(typeIds.get(i), typeNames.get(i)));
            }
            
            // 定义筛选条件（JSON格式）
            String filterJson = "{\"1\": [{\"key\": \"cateId\", \"name\": \"类型\", \"value\": [{\"n\": \"NBA\", \"v\": \"1\"}, {\"n\": \"CBA\", \"v\": \"2\"}, {\"n\": \"篮球综合\", \"v\": \"4\"}, {\"n\": \"纬来体育\", \"v\": \"21\"}]}],\"8\": [{\"key\": \"cateId\", \"name\": \"类型\", \"value\": [{\"n\": \"英超\", \"v\": \"8\"}, {\"n\": \"西甲\", \"v\": \"9\"}, {\"n\": \"意甲\", \"v\": \"10\"}, {\"n\": \"欧冠\", \"v\": \"12\"}, {\"n\": \"欧联\", \"v\": \"13\"}, {\"n\": \"德甲\", \"v\": \"14\"}, {\"n\": \"法甲\", \"v\": \"15\"}, {\"n\": \"欧国联\", \"v\": \"16\"}, {\"n\": \"足总杯\", \"v\": \"27\"}, {\"n\": \"国王杯\", \"v\": \"33\"}, {\"n\": \"中超\", \"v\": \"7\"}, {\"n\": \"亚冠\", \"v\": \"11\"}, {\"n\": \"足球综合\", \"v\": \"23\"}, {\"n\": \"欧协联\", \"v\": \"28\"}, {\"n\": \"美职联\", \"v\": \"26\"}]}], \"29\": [{\"key\": \"cateId\", \"name\": \"类型\", \"value\": [{\"n\": \"网球\", \"v\": \"29\"}, {\"n\": \"斯洛克\", \"v\": \"30\"}, {\"n\": \"MLB\", \"v\": \"38\"}, {\"n\": \"UFC\", \"v\": \"32\"}, {\"n\": \"NFL\", \"v\": \"25\"}, {\"n\": \"CCTV5\", \"v\": \"18\"}]}]}";
            JSONObject filterConfig = new JSONObject(filterJson);
            
            return Result.string(classes, filterConfig);
            
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("获取分类失败: " + e.getMessage());
        }
    }
    
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();
        
        try {
            // 获取筛选参数（如果有）
            String cateId = extend != null && extend.get("cateId") != null ? extend.get("cateId") : tid;
            
            // 构建URL
            String urlPath = TextUtils.isEmpty(cateId) ? "" : String.format("/match/%s/live", cateId);
            String url = siteUrl + urlPath;
            
            // 请求HTML内容
            String content = OkHttp.string(url, getHeader());
            Elements lis = Jsoup.parse(content).select(".list-group-item");
            
            // 解析直播列表
            for (Element li : lis) {
                // 获取播放链接
                String vid = siteUrl + li.select(".btn.btn-primary").attr("href");
                
                // 获取比赛名称
                String name = li.select(".row.d-none").text();
                if (TextUtils.isEmpty(name)) {
                    name = li.text();
                }
                
                // 获取队伍图片
                String pic = li.select(".col-xs-1").eq(0).select("img").attr("src");
                if (TextUtils.isEmpty(pic)) {
                    pic = "https://pic.imgdb.cn/item/657673d6c458853aeff94ab9.jpg";  // 默认图片
                }
                if (!pic.startsWith("http")) {
                    pic = siteUrl + pic;
                }
                
                // 获取状态（直播中、已结束、未开始等）
                String remark = li.select(".btn.btn-primary").text();
                
                list.add(new Vod(vid, name, pic, remark));
            }
            
            // 直播列表没有分页，所有数据都在一页
            return Result.get().page(1, 1, 0, lis.size()).vod(list).string();
            
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("获取直播列表失败: " + e.getMessage());
        }
    }
    
    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) {
            return Result.error("无内容");
        }
        
        String vid = ids.get(0);
        
        // 如果URL只是站点地址，说明比赛尚未开始
        if (vid.equals(siteUrl)) {
            return Result.error("比赛尚未开始");
        }
        
        try {
            // 获取播放地址（通过-url接口）
            String content = OkHttp.string(vid + "-url", getHeader());
            
            // 解析JSON响应
            JSONObject json = new JSONObject(content);
            String result = json.optString("data");
            
            if (TextUtils.isEmpty(result)) {
                return Result.error("获取播放数据失败");
            }
            
            // Base64解码（去掉前缀和后缀）
            result = result.substring(6);  // 去掉前缀
            result = result.substring(0, result.length() - 2);  // 去掉后缀
            
            String jsonStr = new String(Base64.decode(result, Base64.DEFAULT));
            JSONObject playData = new JSONObject(jsonStr);
            
            // 解析播放链接列表
            JSONArray linksArray = playData.optJSONArray("links");
            if (linksArray == null || linksArray.length() == 0) {
                return Result.error("无播放源");
            }
            
            List<String> vodItems = new ArrayList<>();
            for (int i = 0; i < linksArray.length(); i++) {
                JSONObject linkObject = linksArray.optJSONObject(i);
                if (linkObject == null) continue;
                
                String text = linkObject.optString("name");
                String href = linkObject.optString("url").replace("#", "***");  // 替换#防止解析错误
                
                vodItems.add(text + "$" + href);
            }
            
            // 构造Vod对象
            Vod vod = new Vod();
            vod.setVodId(vid);
            vod.setVodPlayFrom("88看球");
            vod.setVodPlayUrl(TextUtils.join("#", vodItems));
            
            return Result.string(vod);
            
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("获取播放地址失败: " + e.getMessage());
        }
    }
    
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            // 恢复#号（从***还原）
            String playUrl = id.replace("***", "#");
            
            // 返回播放地址（需要解析模式）
            return Result.get()
                .url(playUrl)
                .parse()  // 需要解析模式
                .header(getHeader())
                .string();
                
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("播放失败: " + e.getMessage());
        }
    }
    
    @Override
    public void destroy() {
        // 清理资源（如果有缓存等）
    }
}