package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KafLiveAmns extends Spider {

    private String host = "https://www.kafeizhibo.cc";

    private static String extractRoomId(JSONObject obj) throws JSONException {
        JSONObject archor = obj.optJSONObject("archor");
        if (archor != null) {
            String roomId = archor.optString("room_id");
            if (!TextUtils.isEmpty(roomId)) {
                return archor.optString("room_id");
            }
        }
        JSONArray archors = obj.optJSONArray("archors");
        if (archors != null && archors.length() > 0) {
            String roomId = archors.getJSONObject(0).optString("room_id");
            if (!TextUtils.isEmpty(roomId)) {
                return roomId;
            }
        }
        return obj.optString("room_id", String.valueOf(obj.optInt("id")));
    }

    private static String buildRemark(int homeScore, int awayScore, String status) {
        if ("live".equalsIgnoreCase(status)) {
            return new StringBuilder().append(homeScore).append("-").append(awayScore).append(" 直播中").toString();
        }
        if ("finished".equalsIgnoreCase(status) || "end".equalsIgnoreCase(status)) {
            return new StringBuilder().append(homeScore).append("-").append(awayScore).append(" 已结束").toString();
        }
        if ("not_started".equalsIgnoreCase(status)) {
            return "未开赛";
        }
        if (TextUtils.isEmpty(status)) {
            return "赛程";
        }
        return status;
    }

    private JSONObject fetchJson(String url) throws Exception {
        String resp = OkHttp.string(url, getHeaders());
        if (TextUtils.isEmpty(resp)) {
            return new JSONObject();
        }
        JSONObject obj = new JSONObject(resp);
        if (obj.optInt("code", 0) != 200) {
            throw new Exception(obj.optString("message", "API error"));
        }
        return obj;
    }

    private Map<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36");
        headers.put("Referer", new StringBuilder().append(host).append("/").toString());
        headers.put("Accept", "application/json");
        return headers;
    }

    private String buildUrl(String path) {
        String base = host;
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!path.startsWith("/")) {
            path = "/".concat(path);
        }
        return new StringBuilder(base).append(path).toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String path = "/api/v1/schedule?size=30&page=";
        int page = 1;
        try {
            page = Math.max(1, Integer.parseInt(pg));
        } catch (Exception ignored) {
        }
        try {
            if (TextUtils.isEmpty(tid)) {
                tid = "all";
            }
            String url = buildUrl(new StringBuilder(path).append(page).append("&type=").append(tid).append("&platform=h5").toString());
            JSONArray data = fetchJson(url).optJSONArray("data");
            ArrayList<Vod> list = new ArrayList<>();
            if (data != null) {
                for (int i = 0; i < data.length(); i++) {
                    JSONObject item = data.getJSONObject(i);
                    String homeTeam = item.optString("home_team");
                    String awayTeam = item.optString("away_team");
                    String leagueName = item.optString("league_name");
                    int homeScore = item.optInt("home_score");
                    int awayScore = item.optInt("away_score");
                    Vod vod = new Vod();
                    vod.setVodId(extractRoomId(item));
                    vod.setVodName(new StringBuilder().append(homeTeam).append(" vs ").append(awayTeam).toString());
                    String logo = item.optString("home_team_logo");
                    if (TextUtils.isEmpty(logo)) {
                        JSONObject homeTeamObj = item.optJSONObject("homeTeam");
                        logo = homeTeamObj != null ? homeTeamObj.optString("logo", "") : "";
                    }
                    vod.setVodPic(logo);
                    vod.setVodRemarks(new StringBuilder().append(leagueName).append(" ").append(buildRemark(homeScore, awayScore, item.optString("status"))).toString());
                    list.add(vod);
                }
            }
            return Result.get().vod(list).page(page, 30, 30, list.size()).string();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("咖啡体育列表失败: " + e.getMessage());
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) {
            return Result.error("room_id 为空");
        }
        try {
            String id = ids.get(0);
            JSONObject data = fetchJson(buildUrl(new StringBuilder("/api/v1/room/").append(id).toString())).optJSONObject("data");
            if (data == null) {
                return Result.error("房间不存在");
            }
            JSONObject roomInfo = data.optJSONObject("room_info");
            if (roomInfo == null) {
                roomInfo = data;
            }
            String title = roomInfo.optString("title");
            if (TextUtils.isEmpty(title)) {
                title = new StringBuilder().append(roomInfo.optString("home_team")).append(" vs ").append(roomInfo.optString("away_team")).toString();
            }
            ArrayList<String> playFrom = new ArrayList<>();
            ArrayList<String> playUrl = new ArrayList<>();
            JSONArray signals = data.optJSONArray("signals");
            if (signals != null) {
                for (int i = 0; i < signals.length(); i++) {
                    JSONObject signal = signals.getJSONObject(i);
                    String streamUrl = signal.optString("stream_url");
                    if (TextUtils.isEmpty(streamUrl)) {
                        continue;
                    }
                    String name = signal.optString("name", new StringBuilder().append("线路").append(i + 1).toString());
                    playFrom.add(name);
                    playUrl.add(new StringBuilder().append(name).append("$").append(streamUrl).toString());
                }
            }
            if (playFrom.isEmpty()) {
                JSONObject archor = data.optJSONObject("archor");
                if (archor != null) {
                    String streamUrl = archor.optString("stream_url");
                    if (!TextUtils.isEmpty(streamUrl)) {
                        playFrom.add("咖啡Live");
                        playUrl.add(new StringBuilder("直播$").append(streamUrl).toString());
                    }
                }
            }
            if (playFrom.isEmpty()) {
                return Result.error("暂无可用直播流");
            }
            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(title);
            vod.setVodRemarks(roomInfo.optString("league", "咖啡体育"));
            vod.setVodContent(new StringBuilder().append("此接口免费，请勿相信视频中任何广告。\n").append(roomInfo.optString("notice_h5", roomInfo.optString("notice", ""))).toString());
            vod.setVodPlayFrom(TextUtils.join("$$$", playFrom));
            vod.setVodPlayUrl(TextUtils.join("#", playUrl));
            return Result.string(vod);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("详情失败: " + e.getMessage());
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        classes.add(new Class("all", "全部"));
        classes.add(new Class("hot", "热门"));
        classes.add(new Class("1", "足球"));
        classes.add(new Class("2", "篮球"));
        return Result.get().classes(classes).string();
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (!TextUtils.isEmpty(extend) && extend.startsWith("http")) {
            host = extend.trim();
        }
    }

    @Override
    public String liveContent(String url) throws Exception {
        try {
            JSONArray data = fetchJson(buildUrl("/api/v1/archor?platform=h5")).optJSONArray("data");
            if (data == null || data.length() == 0) {
                return "";
            }
            StringBuilder sb = new StringBuilder("#EXTM3U\n");
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                String streamUrl = item.optString("stream_url");
                if (TextUtils.isEmpty(streamUrl)) {
                    continue;
                }
                String title = item.optString("title");
                if (TextUtils.isEmpty(title)) {
                    title = item.optString("name", "直播");
                }
                String league = item.optString("league_name", "体育");
                JSONObject matchInfo = item.optJSONObject("match_info");
                String logo = matchInfo != null ? matchInfo.optString("home_team_logo", "") : "";
                sb.append("#EXTINF:-1 tvg-id=\"").append(item.optString("room_id", item.optString("archor_id", ""))).append("\" tvg-name=\"").append(title.replace("\"", "'")).append("\" tvg-logo=\"").append(logo).append("\" group-title=\"").append(league).append("\",").append(title).append("\n").append(streamUrl).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (TextUtils.isEmpty(id)) {
            return Result.error("播放地址为空");
        }
        return Result.get().url(id).header(getHeaders()).string();
    }
}
