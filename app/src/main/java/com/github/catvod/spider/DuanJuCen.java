package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

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
import java.util.Map;

public class DuanJuCen extends Spider {

    private static final String BASE_URL = "https://mov.cenguigui.cn/duanju/api.php";
    private static final String CHARSET = "UTF-8";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 11; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Mobile Safari/537.36";
    private static final String PLAY_FROM = "短剧";
    private static final String ERROR_MSG = "播放链接为空";

    private static final String CATEGORIES_JSON = "[{\"type_id\":\"总裁推荐榜\",\"type_name\":\"推荐榜\"},{\"type_id\":\"热剧榜\",\"type_name\":\"热剧榜\"},{\"type_id\":\"新剧榜\",\"type_name\":\"新剧榜\"},{\"type_id\":\"漫剧榜\",\"type_name\":\"漫剧榜\"},{\"type_id\":\"大唐\",\"type_name\":\"大唐\"},{\"type_id\":\"大秦\",\"type_name\":\"大秦\"},{\"type_id\":\"大明\",\"type_name\":\"大明\"},{\"type_id\":\"擦边\",\"type_name\":\"擦边\"},{\"type_id\":\"地府\",\"type_name\":\"地府\"},{\"type_id\":\"修罗\",\"type_name\":\"修罗\"},{\"type_id\":\"占卜\",\"type_name\":\"占卜\"},{\"type_id\":\"灵异\",\"type_name\":\"灵异\"},{\"type_id\":\"妖\",\"type_name\":\"妖\"},{\"type_id\":\"魔\",\"type_name\":\"魔\"},{\"type_id\":\"鬼\",\"type_name\":\"鬼\"},{\"type_id\":\"怪\",\"type_name\":\"怪\"},{\"type_id\":\"末世\",\"type_name\":\"末世\"},{\"type_id\":\"总裁\",\"type_name\":\"总裁\"},{\"type_id\":\"女帝\",\"type_name\":\"女帝\"},{\"type_id\":\"都市\",\"type_name\":\"都市\"},{\"type_id\":\"赘婿\",\"type_name\":\"赘婿\"},{\"type_id\":\"战神\",\"type_name\":\"战神\"},{\"type_id\":\"古代言情\",\"type_name\":\"古代言情\"},{\"type_id\":\"现代言情\",\"type_name\":\"现代言情\"},{\"type_id\":\"历史\",\"type_name\":\"历史\"},{\"type_id\":\"脑洞\",\"type_name\":\"脑洞\"},{\"type_id\":\"玄幻\",\"type_name\":\"玄幻\"},{\"type_id\":\"电视节目\",\"type_name\":\"电视节目\"},{\"type_id\":\"搞笑\",\"type_name\":\"搞笑\"},{\"type_id\":\"网剧\",\"type_name\":\"网剧\"},{\"type_id\":\"喜剧\",\"type_name\":\"喜剧\"},{\"type_id\":\"萌宝\",\"type_name\":\"萌宝\"},{\"type_id\":\"神豪\",\"type_name\":\"神豪\"},{\"type_id\":\"致富\",\"type_name\":\"致富\"},{\"type_id\":\"奇幻脑洞\",\"type_name\":\"奇幻脑洞\"},{\"type_id\":\"超能\",\"type_name\":\"超能\"},{\"type_id\":\"强者回归\",\"type_name\":\"励志\"},{\"type_id\":\"豪门恩怨\",\"type_name\":\"豪门恩怨\"},{\"type_id\":\"复仇\",\"type_name\":\"复仇\"},{\"type_id\":\"长生\",\"type_name\":\"长生\"},{\"type_id\":\"神医\",\"type_name\":\"神医\"},{\"type_id\":\"马甲\",\"type_name\":\"马甲\"},{\"type_id\":\"亲情\",\"type_name\":\"亲情\"},{\"type_id\":\"小人物\",\"type_name\":\"小人物\"},{\"type_id\":\"奇幻\",\"type_name\":\"奇幻\"},{\"type_id\":\"无敌\",\"type_name\":\"无敌\"},{\"type_id\":\"现实\",\"type_name\":\"现实\"},{\"type_id\":\"重生\",\"type_name\":\"重生\"},{\"type_id\":\"闪婚\",\"type_name\":\"闪婚\"},{\"type_id\":\"职场商战\",\"type_name\":\"职场商战\"},{\"type_id\":\"穿越\",\"type_name\":\"穿越\"},{\"type_id\":\"年代\",\"type_name\":\"年代\"},{\"type_id\":\"权谋\",\"type_name\":\"权谋\"},{\"type_id\":\"高手下山\",\"type_name\":\"高手下山\"},{\"type_id\":\"悬疑\",\"type_name\":\"悬疑\"},{\"type_id\":\"家国情仇\",\"type_name\":\"家国情仇\"},{\"type_id\":\"虐恋\",\"type_name\":\"虐恋\"},{\"type_id\":\"古装\",\"type_name\":\"古装\"},{\"type_id\":\"时空之旅\",\"type_name\":\"时空之旅\"},{\"type_id\":\"玄幻仙侠\",\"type_name\":\"玄幻仙侠\"},{\"type_id\":\"欢喜冤家\",\"type_name\":\"欢喜冤家\"},{\"type_id\":\"传承觉醒\",\"type_name\":\"传承觉醒\"},{\"type_id\":\"情感\",\"type_name\":\"情感\"},{\"type_id\":\"逆袭\",\"type_name\":\"逆袭\"},{\"type_id\":\"家庭\",\"type_name\":\"家庭\"}]";

    private JSONArray categories;

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        categories = new JSONArray(CATEGORIES_JSON);
    }

    /** Build request headers with User-Agent. */
    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        return headers;
    }

    /** Fetch vod list by name and page number. */
    private ArrayList<Vod> fetchList(String name, String pg) throws Exception {
        ArrayList<Vod> list = new ArrayList<>();
        if (TextUtils.isEmpty(name)) return list;
        String url = BASE_URL + "?name=" + URLEncoder.encode(name, CHARSET)
                + "&page=" + URLEncoder.encode(pg, CHARSET)
                + "&tab_type=19";
        String resp = OkHttp.string(url, buildHeaders());
        if (TextUtils.isEmpty(resp)) return list;
        JSONObject json = new JSONObject(resp);
        JSONArray array = json.optJSONArray("data");
        if (array == null) return list;
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject item = array.getJSONObject(i);
                String id = item.optString("book_id");
                String title = item.optString("title");
                String pic = item.optString("cover");
                String remarks = item.optString("type");
                if (TextUtils.isEmpty(remarks)) {
                    remarks = item.optString("episode_cnt");
                }
                list.add(new Vod(id, title, pic, remarks));
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        for (int i = 0; i < categories.length(); i++) {
            JSONObject item = categories.getJSONObject(i);
            String typeId = item.optString("type_id");
            String typeName = item.optString("type_name");
            classes.add(new Class(typeId, typeName));
        }
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        ArrayList<Vod> list = fetchList(tid, pg);
        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String url = BASE_URL + "?book_id=" + URLEncoder.encode(id, CHARSET);
        String resp = OkHttp.string(url, buildHeaders());
        if (TextUtils.isEmpty(resp)) {
            return Result.string(new Vod());
        }
        JSONObject json = new JSONObject(resp);
        String vodName = json.optString("book_name");
        String vodPic = json.optString("book_pic");
        String vodRemarks = json.optString("category");
        String vodContent = json.optString("desc");
        JSONArray episodes = json.optJSONArray("data");
        ArrayList<String> urls = new ArrayList<>();
        if (episodes != null) {
            for (int i = 0; i < episodes.length(); i++) {
                JSONObject ep = episodes.getJSONObject(i);
                urls.add(ep.optString("title") + "$" + ep.optString("video_id"));
            }
        }
        Vod vod = new Vod(id, vodName, vodPic, vodRemarks);
        vod.setVodContent(vodContent);
        vod.setVodPlayFrom(PLAY_FROM);
        vod.setVodPlayUrl(TextUtils.join("#", urls));
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String url = BASE_URL + "?video_id=" + URLEncoder.encode(id, CHARSET);
        String resp = OkHttp.string(url, buildHeaders());
        if (TextUtils.isEmpty(resp)) return Result.error(ERROR_MSG);
        JSONObject json = new JSONObject(resp);
        JSONObject data = json.optJSONObject("data");
        String playUrl;
        if (data != null) {
            playUrl = json.getJSONObject("data").optString("url");
        } else {
            playUrl = json.optString("url");
        }
        if (TextUtils.isEmpty(playUrl)) return Result.error(ERROR_MSG);
        return Result.get().parse(0).url(playUrl).header(buildHeaders()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        ArrayList<Vod> list = fetchList(key, "1");
        return Result.string(list);
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        ArrayList<Vod> list = fetchList(key, pg);
        return Result.string(list);
    }
}
