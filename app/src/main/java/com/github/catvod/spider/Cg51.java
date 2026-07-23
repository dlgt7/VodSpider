package com.github.catvod.spider;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.CgImageUtil;
import com.github.catvod.utils.Util;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Cg51 extends Spider {

    private static final String siteUrl = "https://carrier.ujaumgp.cc";
    private static final String cateUrl = siteUrl + "/category/";
    private static final String detailUrl = siteUrl + "/archives/";
    private static final String searchUrl = siteUrl + "/search?keywords=";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        return headers;
    }

//    private List<Vod> parseVods(Document doc) {
//        List<Vod> list = new ArrayList<>();
//        for (Element element : doc.select("article")) {
//            String pic = String.valueOf(element.select("script"));
//            String pattern = "'(https?://[^']+)";
//            Pattern regex = Pattern.compile(pattern);
//            Matcher matcher = regex.matcher(pic);
//            String PicAddress = "";
//            if (matcher.find()) {
//                PicAddress = proxyImgUrl + matcher.group(1);
//            } else {
//            }
//            String url = element.select("a").attr("href");
//            String name = element.select(".post-card-title").text();
//            String id = url.split("/")[2];
//            if (name != "" && url != ""){
//                list.add(new Vod(id, name, PicAddress));
//            }
//        }
//        return list;
//    }

    private List<Vod> parseVods(Document doc) {
        List<Vod> list = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(20); // 创建一个线程池，最大并发数为10

        List<Callable<String>> tasks = new ArrayList<>(); // 用于存储所有的任务

        for (Element element : doc.select("article")) {
            String pic = String.valueOf(element.select("script"));
            String pattern = "'(https?://[^']+)";
            Pattern regex = Pattern.compile(pattern);
            Matcher matcher = regex.matcher(pic);
            String PicAddress = "";

            if (matcher.find()) {
                String imageUrl = matcher.group(1);
                tasks.add(() -> CgImageUtil.loadBackgroundImage(imageUrl)); // 创建一个任务，并将其添加到任务列表中
            }

            String url = element.select("a").attr("href");
            String name = element.select(".post-card-title").text();
            String id = url.split("/")[2];
            if (!name.isEmpty() && !url.isEmpty()) {
                list.add(new Vod(id, name, PicAddress));
            }
        }
        try {
            // 执行所有的任务，并获取结果
            List<Future<String>> futures = executorService.invokeAll(tasks);

            // 遍历任务结果，并将结果设置到对应的Vod对象中
            for (int i = 0; i < futures.size(); i++) {
                if (i < list.size()) { // 确保索引不超出列表范围
                    Vod vod = list.get(i);
                    String result = futures.get(i).get();
                    vod.setVodPic(result);
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        executorService.shutdown(); // 关闭线程池
        return list;
    }
    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        String[] typeIdList = {"wpcz","mrdg","rdsj","bkdg","whhl","xsxy","cbdj","thjx","whhj","rrcg","ldcg","ysyl","lldd","gcjq","snsn","hwcg","jpll","qubk","dcbq","zzs","cgxw","yczq","whmx"};
        String[] typeNameList = {"今日吃瓜","每日大瓜","热门吃瓜","必看大瓜","网红黑料","学生学校","成人短剧","探花精选","网黄合集","人人吃瓜","领导干部","看片娱乐","伦理道德","国产剧情","骚男骚女","海外吃瓜","软萌甜妹","吃瓜看戏","擦边撩骚","51涨知识","吃瓜新闻","51原创","明星黑料"};
        for (int i = 0; i < typeNameList.length; i++) {
            classes.add(new Class(typeIdList[i], typeNameList[i]));
        }
        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeaders()));
        List<Vod> list = parseVods(doc);
        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String target = cateUrl + tid + "/" + pg + "/";
        Document doc = Jsoup.parse(OkHttp.string(target, getHeaders()));
        List<Vod> list = parseVods(doc);
        Integer total = (Integer.parseInt(pg)+1)*20;
        return Result.string(Integer.parseInt(pg),Integer.parseInt(pg)+1,20,total,list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        Document doc = Jsoup.parse(OkHttp.string(detailUrl.concat(ids.get(0)), getHeaders()));
        String playUrl = "";
        int index = 1;
        
        // 方式1：标准 dplayer 播放器
        Elements dplayers = doc.select("div.dplayer");
        if (!dplayers.isEmpty()) {
            for (Element element : dplayers) {
                String play = element.attr("data-config");
                if (play == null || play.isEmpty()) continue;
                try {
                    JSONObject jsonObject = new JSONObject(play);
                    JSONObject video = jsonObject.getJSONObject("video");
                    String url = video.optString("url", "");
                    if (url.isEmpty()) continue;
                    if (playUrl.isEmpty()) {
                        playUrl = "第" + index + "集$" + url;
                    } else {
                        playUrl = playUrl + "#第" + index + "集$" + url;
                    }
                    index++;
                } catch (Exception e) {
                    // 忽略解析错误
                }
            }
        }
        
        // 方式2：从文章内容中提取 TOP 列表（每日大瓜、网黄合集等）
        if (playUrl.isEmpty()) {
            // 获取文章正文内容
            Element articleContent = doc.selectFirst(".entry-content, .post-content, article .content");
            if (articleContent == null) articleContent = doc.selectFirst("article");
            
            if (articleContent != null) {
                // 提取所有包含 /archives/ 的链接，按顺序编号
                Elements links = articleContent.select("a[href*=/archives/]");
                for (Element a : links) {
                    String href = a.attr("href");
                    String text = a.text().trim();
                    // 过滤掉导航链接和当前页面链接
                    if (href.contains("/archives/") && !href.equals(ids.get(0)) && !href.contains("category")) {
                        // 清理链接文本，只保留主要内容
                        if (text.contains("点击查看") || text.contains("上一篇") || text.contains("下一篇")) continue;
                        
                        String epName = text.isEmpty() ? "TOP " + index : text;
                        // 截取前30个字符作为标题
                        if (epName.length() > 30) epName = epName.substring(0, 30) + "...";
                        
                        if (playUrl.isEmpty()) {
                            playUrl = epName + "$" + href;
                        } else {
                            playUrl = playUrl + "#" + epName + "$" + href;
                        }
                        index++;
                        // 限制最多10个链接
                        if (index > 10) break;
                    }
                }
            }
        }
        
        // 方式3：如果仍然没有，尝试从整个页面提取（兜底）
        if (playUrl.isEmpty()) {
            for (Element a : doc.select("a[href*=/archives/]")) {
                String href = a.attr("href");
                String text = a.text().trim();
                if (href.contains("/archives/") && !href.equals(ids.get(0)) && !href.contains("category")) {
                    if (text.contains("点击查看") || text.contains("上一篇") || text.contains("下一篇")) continue;
                    String epName = text.isEmpty() ? "视频" + index : text;
                    if (epName.length() > 30) epName = epName.substring(0, 30) + "...";
                    
                    if (playUrl.isEmpty()) {
                        playUrl = epName + "$" + href;
                    } else {
                        playUrl = playUrl + "#" + epName + "$" + href;
                    }
                    index++;
                    if (index > 10) break;
                }
            }
        }
        
        String name = doc.select("meta[property=og:title]").attr("content");
        String pic = doc.select("meta[property=og:image]").attr("content");
        String year = doc.select("meta[property=video:release_date]").attr("content");

        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodPic(pic);
        vod.setVodYear(year);
        vod.setVodName(name);
        if (!playUrl.isEmpty()) {
            vod.setVodPlayFrom("Cg51");
            vod.setVodPlayUrl(playUrl);
        }
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        Document doc = Jsoup.parse(OkHttp.string(searchUrl.concat(URLEncoder.encode(key)), getHeaders()));
        List<Vod> list = parseVods(doc);
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // 如果是详情页链接，需要二次解析获取真实播放地址
        if (id.contains("/archives/")) {
            Document doc = Jsoup.parse(OkHttp.string(siteUrl + id, getHeaders()));
            for (Element element : doc.select("div.dplayer")) {
                String play = element.attr("data-config");
                if (play == null || play.isEmpty()) continue;
                try {
                    JSONObject jsonObject = new JSONObject(play);
                    JSONObject video = jsonObject.getJSONObject("video");
                    String url = video.optString("url", "");
                    if (!url.isEmpty()) {
                        return Result.get().url(url).header(getHeaders()).string();
                    }
                } catch (Exception e) {
                    // 忽略解析错误
                }
            }
            // 如果仍然找不到，返回原链接（让客户端尝试）
            return Result.get().url(siteUrl + id).header(getHeaders()).string();
        }
        return Result.get().url(id).header(getHeaders()).string();
    }
}
