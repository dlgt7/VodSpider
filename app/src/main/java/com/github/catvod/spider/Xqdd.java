package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Xqdd extends Spider {

    private static final String siteUrl = "https://main.xiquduoduo.com";

    private final HashMap<String, String> headers = new HashMap<String, String>() {{
        put("User-Agent", "okhttp/3.12.6");
    }};

    @Override
    public void init(Context context, String extend) {
        try {
            super.init(context, extend);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();
        String[] cateNames = {
            "花鼓戏", "黄梅戏", "京剧", "老电影", "小品相声", "儿歌多多", "越剧", "车载歌曲",
            "采茶戏", "豫剧", "晋剧", "经典歌曲", "祁剧", "桂剧", "民间小调", "二人转", "河南坠子",
            "评剧", "歌仔戏", "曲剧", "湘剧", "川剧", "秦腔", "婺剧", "大平调", "越调", "莲花落",
            "淮剧", "赣剧", "春晚戏曲", "昆曲", "锡剧", "二人台", "上党梆子", "河北梆子", "眉户戏",
            "扬剧", "皮影戏", "绍兴莲花落", "北路梆子", "泗州戏", "楚剧", "庐剧", "蒲剧", "潮剧",
            "评书", "沪剧", "粤剧", "莆仙戏", "琼剧", "藏剧", "布袋戏", "吕剧", "闽剧", "黔剧",
            "滇剧", "新疆曲子戏", "陇剧", "漫瀚剧"
        };
        String[] cateIds = {
            "80000084", "80000018", "80000013", "80000221", "80000083", "80000069", "80000086",
            "80000214", "80000103", "80000017", "80000020", "80000219", "80000100", "80000102",
            "80000224", "80000014", "80000023", "80000091", "80000087", "80000022", "80000096",
            "80000088", "80000019", "80000216", "80000217", "80000218", "80000136", "80000095",
            "80000099", "80000015", "80000090", "80000097", "80000021", "80000025", "80000027",
            "80000110", "80000048", "80000092", "80000137", "80000024", "80000220", "80000098",
            "80000101", "80000093", "80000026", "80000223", "80000089", "80000085", "80000215",
            "80000104", "80000112", "80000114", "80000094", "80000105", "80000106", "80000107",
            "80000108", "80000109", "80000113"
        };

        for (int i = 0; i < cateNames.length; i++) {
            classes.add(new Class(cateIds[i], cateNames[i]));
        }

        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        int page = Integer.parseInt(pg);
        int realPage = page >= 1 ? page - 1 : 0;

        String url = siteUrl + "/bama/service/s.php?type=getcollabellist&collid=" + tid +
                "&label=默认&pg=" + realPage + "&ps=30";

        String content = OkHttp.string(url, headers);
        List<Vod> list = new ArrayList<>();

        try {
            JSONObject json = new JSONObject(content);
            JSONArray array = json.optJSONArray("list");

            if (array == null || array.length() == 0) {
                return Result.get().vod(list).page(page, page, 30, 0).string();
            }

            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String vodId = item.optString("ddurl");
                String name = item.optString("name");
                String pic = fixUrl(item.optString("pic"));
                String remarks = formatDuration(item.optString("duration"));

                list.add(new Vod(vodId, name, pic, remarks));
            }

            int limit = 30;
            int total = list.size() == limit ? Integer.MAX_VALUE : (page - 1) * limit + list.size();
            int pageCount = list.size() == limit ? Integer.MAX_VALUE : page;

            return Result.get().vod(list).page(page, pageCount, limit, total).string();

        } catch (Exception e) {
            e.printStackTrace();
            return Result.get().vod(list).page(page, page, 30, 0).string();
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        String playUrl = ids.get(0);

        Vod vod = new Vod();
        vod.setVodId(playUrl);
        vod.setVodName("正在播放");
        vod.setVodPic("https://img2.baidu.com/it/u=4261893679,2570494742&fm=253&fmt=auto&app=138&f=JPEG?w=750&h=500");
        vod.setVodPlayFrom("戏曲多多");
        vod.setVodPlayUrl("播放$" + playUrl);

        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        int page = Integer.parseInt(pg);
        int realPage = page >= 1 ? page - 1 : 0;

        String encodedKey = URLEncoder.encode(key);

        String url = siteUrl + "/bama/service/s.php?type=ddsearch&keyword=" + encodedKey +
                "&pg=" + realPage + "&ps=30&album=true&allbz=true&origin=true";

        String content = OkHttp.string(url, headers);
        List<Vod> list = new ArrayList<>();

        try {
            JSONObject json = new JSONObject(content);
            JSONArray array = json.optJSONArray("list");

            if (array == null || array.length() == 0) {
                return Result.get().vod(list).page(page, page, 30, 0).string();
            }

            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String vodId = item.optString("ddurl");
                String name = item.optString("name");
                String pic = fixUrl(item.optString("pic"));
                String remarks = formatDuration(item.optString("duration"));

                list.add(new Vod(vodId, name, pic, remarks));
            }

            int limit = 30;
            int total = list.size() == limit ? Integer.MAX_VALUE : (page - 1) * limit + list.size();
            int pageCount = list.size() == limit ? Integer.MAX_VALUE : page;

            return Result.get().vod(list).page(page, pageCount, limit, total).string();

        } catch (Exception e) {
            e.printStackTrace();
            return Result.get().vod(list).page(page, page, 30, 0).string();
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return Result.get().url(id).header(headers).string();
    }

    private String fixUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        if (url.startsWith("http")) return url;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return siteUrl + url;
        return siteUrl + "/" + url;
    }

    private String formatDuration(String duration) {
        if (duration == null || duration.isEmpty()) return "";
        duration = duration.trim();
        try {
            int seconds = Integer.parseInt(duration);
            int m = seconds / 60;
            int s = seconds % 60;
            return String.format("%d:%02d", m, s);
        } catch (NumberFormatException e) {
            return duration;
        }
    }
}
