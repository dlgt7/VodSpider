package com.github.catvod.spider;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * FenghuangFM Spider - 凤凰FM
 */
public class FenghuangFM extends Spider {

    private final String request(String path) {
        String url = "https://s.fm.renbenai.com" + path;
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "okhttp/3.12.11");
            return OkHttp.string(url, headers);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String homeContent(boolean filter) {
        ArrayList<Class> classes = new ArrayList<>();
        try {
            String json = request("/fm/read/fmd/static/categoryTvGet_100.html");
            JSONObject root = new JSONObject(json);
            JSONObject data = root.getJSONObject("data");
            JSONArray list = data.getJSONArray("list");
            JSONObject firstItem = list.getJSONObject(0);
            JSONArray channelContent = firstItem.getJSONArray("channelContent");

            for (int i = 0; i < channelContent.length(); i++) {
                JSONObject item = channelContent.getJSONObject(i);
                String id = item.optString("id");
                String name = item.optString("nodeName");
                classes.add(new Class(id, name));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.string(classes, new LinkedHashMap<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        String path = "/fm/read/fmd/android/600/getProgramList.html&cid=";
        if (TextUtils.isEmpty(pg)) {
            pg = "1";
        }
        try {
            StringBuilder sb = new StringBuilder(path);
            sb.append(tid);
            sb.append("&pagenum=");
            sb.append(pg);
            String json = request(sb.toString());
            JSONObject root = new JSONObject(json);
            JSONObject data = root.getJSONObject("data");
            JSONArray hotList = data.getJSONArray("hotList");

            ArrayList<Vod> list = new ArrayList<>();
            for (int i = 0; i < hotList.length(); i++) {
                JSONObject item = hotList.getJSONObject(i);
                String programName = item.optString("programName");
                
                // 过滤掉名称匹配 ".*(名称|排除).*" 的节目
                if (programName.matches(".*(名称|排除).*")) {
                    continue;
                }

                String img = item.optString("img640_640");
                if (TextUtils.isEmpty(img)) {
                    img = item.optString("img370_370");
                }

                String id = item.optString("id");
                String resourceTitle = item.optString("resourceTitle");
                Vod vod = new Vod(id, programName, img, resourceTitle);
                list.add(vod);
            }

            int page = Integer.parseInt(pg);
            int total = list.isEmpty() ? page : page + 1;
            int limit = 20;
            int maxPage = 9999;

            return Result.get().page(page, total, limit, maxPage).vod(list).string();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.string(new ArrayList<>());
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String id = ids.get(0);
            String path = "/fm/read/fmd/android/getProgramAudioList_620.html&pid=" + id;
            String json = request(path);
            JSONObject root = new JSONObject(json);
            JSONObject data = root.getJSONObject("data");
            JSONArray list = data.getJSONArray("list");
            JSONObject firstItem = list.getJSONObject(0);

            String title = firstItem.optString("title");
            String img = firstItem.optString("img370_370");
            Vod vod = new Vod(id, title, img);

            String programDetails = firstItem.optString("programDetails");
            vod.setVodContent(programDetails);

            String tags = firstItem.optString("tags");
            vod.setVodRemarks(tags);

            // 构建播放列表
            StringBuilder playUrl = new StringBuilder();
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.getJSONObject(i);
                JSONArray audiolist = item.optJSONArray("audiolist");
                if (audiolist == null || audiolist.length() == 0) {
                    continue;
                }

                for (int j = 0; j < audiolist.length(); j++) {
                    JSONObject audio = audiolist.getJSONObject(j);
                    String filePath = audio.optString("filePath");
                    if (TextUtils.isEmpty(filePath)) {
                        continue;
                    }

                    if (playUrl.length() > 0) {
                        playUrl.append("#");
                    }

                    String audioTitle = audio.optString("title", item.optString("title"));
                    playUrl.append(audioTitle);
                    playUrl.append("$");
                    playUrl.append(filePath);
                }
            }

            vod.setVodPlayFrom("凤凰FM");
            vod.setVodPlayUrl(playUrl.toString());

            return Result.string(vod);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("获取详情失败");
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "okhttp/3.12.11");

        return Result.get()
                .url(id)
                .header(headers)
                .parse(0)
                .string();
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    public String searchContent(String key, boolean quick, String pg) {
        if (TextUtils.isEmpty(pg)) {
            pg = "1";
        }
        try {
            String encodedKey = URLEncoder.encode(key, "UTF-8");
            StringBuilder sb = new StringBuilder("/fm/read/fmd/public/search_720.html&keyWord=");
            sb.append(encodedKey);
            sb.append("&searchType=1&pageNum=");
            sb.append(pg);

            String json = request(sb.toString());
            JSONObject root = new JSONObject(json);
            JSONObject data = root.getJSONObject("data");
            JSONArray program = data.getJSONArray("program");

            Pattern pattern = Pattern.compile(key, Pattern.CASE_INSENSITIVE);
            ArrayList<Vod> list = new ArrayList<>();

            for (int i = 0; i < program.length(); i++) {
                JSONObject item = program.getJSONObject(i);
                String programName = item.optString("programName");

                // 过滤掉名称匹配 ".*(名称|排除).*" 的节目
                if (programName.matches(".*(名称|排除).*")) {
                    continue;
                }

                // 检查是否匹配搜索关键词
                if (!pattern.matcher(programName).find()) {
                    continue;
                }

                String img = item.optString("img640_640");
                String id = item.optString("id");
                String resourceTitle = item.optString("resourceTitle");
                Vod vod = new Vod(id, programName, img, resourceTitle);
                list.add(vod);
            }

            return Result.string(list);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.string(new ArrayList<>());
        }
    }
}