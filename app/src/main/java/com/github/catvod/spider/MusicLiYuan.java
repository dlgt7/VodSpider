package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class MusicLiYuan extends Spider {

    private static final String HOST = "https://fly.daoran.tv/API_ROP";
    private static final String PIC_HOST = "https://ottphoto.daoran.tv/HD/";
    private static final String MD5_HEADER = "SkvyrWqK9QHTdCT12Rhxunjx+WwMTe9y4KwgeASFDhbYabRSPskR0Q==";

    private static final String[][] CATEGORIES = new String[][]{
            {"lvjv", "吕剧"}, {"jingju", "京剧"}, {"yueju", "越剧"}, {"hbbz", "河北梆子"},
            {"gj", "赣剧"}, {"pingju", "评剧"}, {"pingshu", "评书"}, {"hmx", "黄梅戏"},
            {"yuju", "豫剧"}, {"gddx", "粤剧"}, {"xiang", "相声"}, {"hnzz", "坠子"},
            {"ejx", "二夹弦"}, {"quju", "曲剧"}, {"hndgs", "河南大鼓书"}, {"hnqs", "河南琴书"},
            {"jydg", "京韵大鼓"}, {"kunqu", "昆曲"}, {"yued", "越调"}, {"shaojv", "绍剧"},
            {"huju", "沪剧"}, {"luju", "庐剧"}, {"qinq", "秦腔"}, {"jinju", "晋剧"},
            {"chaoju", "潮剧"}, {"xiju", "锡剧"}, {"chuanju", "川剧"}, {"huagx", "花鼓戏"},
            {"huaiju", "淮剧"}, {"jiju", "吉剧"}, {"tjsd", "天津时调"}, {"bdld", "保定老调"},
            {"huaju", "话剧"}, {"yangju", "扬剧"}, {"dianju", "滇剧"}, {"wuju", "婺剧"},
            {"wb", "宛梆"}, {"spd", "四平调"}, {"lq", "乐腔"}, {"tkdq", "太康道情"},
            {"dpd", "大平调"}, {"danxian", "单弦"}, {"zzx", "正字戏"}, {"caidiao", "彩调"},
            {"other", "其他"}, {"hj", "汉剧"}, {"xqx", "西秦戏"}, {"pxx", "莆仙戏"},
            {"bzx", "白字戏"}, {"pujv", "蒲剧"}, {"mhdg", "梅花大鼓"}, {"xhdg", "西河大鼓"},
            {"chuju", "楚剧"}, {"liuqx", "柳琴戏"}, {"ERT", "二人台"}
    };

    public String fetch(String path, HashMap<String, String> params) {
        try {
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json;charset=utf-8");
            headers.put("User-Agent", "okhttp/3.9.1");
            headers.put("md5", MD5_HEADER);
            String url = HOST.concat(path);
            String body = new JSONObject(params).toString();
            return OkHttp.post(url, body, headers);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "戏曲大全"));
        classes.add(new Class("2", "名家大腕"));
        ArrayList<Filter.Value> values = new ArrayList<>();
        for (String[] pair : CATEGORIES) {
            values.add(new Filter.Value(pair[1], pair[0]));
        }
        ArrayList<Filter> filters = new ArrayList<>();
        filters.add(new Filter("class", "剧种", values));
        LinkedHashMap<String, List<Filter>> filtersMap = new LinkedHashMap<>();
        filtersMap.put("1", filters);
        filtersMap.put("2", filters);
        return Result.string(classes, filtersMap);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String type = "jingju";
        if (extend != null && !TextUtils.isEmpty((CharSequence) extend.get("class"))) {
            type = extend.get("class");
        }
        HashMap<String, String> params = new HashMap<>();
        params.put("cur", pg);
        params.put("pageSize", "50");
        params.put("project", "lyhxcx");
        params.put("userId", "yszyz");
        params.put("type", type);
        params.put("item", "2".equals(tid) ? "o6" : "o5");
        try {
            JSONObject response = new JSONObject(fetch("/search/album/list", params));
            JSONArray list = response.getJSONObject("pb").getJSONArray("dataList");
            ArrayList<Vod> vodList = new ArrayList<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.getJSONObject(i);
                String code = item.optString("code");
                String name = item.optString("name");
                String pic = PIC_HOST + item.optString("img");
                String remark = item.optString("publishTime");
                vodList.add(new Vod(code, name, pic, remark));
            }
            return Result.get().page(Integer.parseInt(pg), 9999, 50, 999999).vod(vodList).string();
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        HashMap<String, String> params = new HashMap<>();
        params.put("albumCode", vodId);
        params.put("cur", "1");
        params.put("project", "lyhxcx");
        params.put("pageSize", "2147483647");
        params.put("selectFlag", "0");
        params.put("userId", "yszyz");
        JSONObject response = new JSONObject(fetch("/album/res/list", params));
        JSONObject album = response.getJSONObject("album");
        String name = album.optString("name");
        String pic = PIC_HOST + album.optString("img");
        Vod vod = new Vod(vodId, name, pic);
        vod.setTypeName(album.optString("sect"));
        vod.setVodContent(album.optString("des"));
        vod.setVodActor(album.optString("artistName"));
        vod.setVodYear(album.optString("publishTime"));
        JSONArray list = response.getJSONObject("pb").getJSONArray("dataList");
        ArrayList<String> episodeList = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.getJSONObject(i);
            StringBuilder sb = new StringBuilder();
            sb.append(item.optString("name")).append("$").append(item.optString("code"));
            episodeList.add(sb.toString());
        }
        vod.setVodPlayFrom("梨园戏曲");
        vod.setVodPlayUrl(TextUtils.join("#", episodeList));
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        HashMap<String, String> params = new HashMap<>();
        params.put("cur", "1");
        params.put("free", "0");
        params.put("item", "o5");
        params.put("pageSize", "30");
        params.put("keyword", key);
        params.put("project", "lyhxcx");
        params.put("userId", "yszyz");
        try {
            JSONObject response = new JSONObject(fetch("/search/album/list", params));
            JSONArray list = response.getJSONObject("pb").getJSONArray("dataList");
            ArrayList<Vod> vodList = new ArrayList<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.getJSONObject(i);
                String code = item.optString("code");
                String name = item.optString("name");
                String pic = PIC_HOST + item.optString("img");
                String remark = item.optString("publishTime");
                vodList.add(new Vod(code, name, pic, remark));
            }
            return Result.string(vodList);
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        HashMap<String, String> params = new HashMap<>();
        params.put("item", "y9");
        params.put("nodeCode", "001000");
        params.put("project", "lyhxcx");
        params.put("px", "2");
        params.put("resCode", id);
        params.put("userId", "92315ec6e58a45ba7f47fd143b3d7956");
        try {
            JSONObject response = new JSONObject(fetch("/play/get/playurl", params));
            String url = response.getJSONObject("playres").optString("playurl");
            return Result.get().url(url).parse(0).string();
        } catch (Exception e) {
            return Result.get().url("").parse(0).string();
        }
    }
}
