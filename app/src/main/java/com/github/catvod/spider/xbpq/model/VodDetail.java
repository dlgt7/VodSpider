package com.github.catvod.spider.xbpq.model;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 视频详情模型
 * <p>
 * 输出 TVBox detailContent 标准结构：{"list":[{vod字段...}]}，
 * 播放源列表折叠为 vod_play_from/vod_play_url（$$$ 分隔线路，# 分隔集数）。
 */
public class VodDetail {
    private VodItem item;
    private List<PlaySource> playSources;
    private List<String> episodes;

    public VodDetail() {
        this.item = new VodItem();
        this.playSources = new ArrayList<>();
        this.episodes = new ArrayList<>();
    }

    public VodDetail(VodItem item) {
        this.item = item;
        this.playSources = new ArrayList<>();
        this.episodes = new ArrayList<>();
    }

    /**
     * 输出 detailContent 标准结果
     */
    public String toJSON() {
        JSONObject vod = item.toJSON();
        if (!playSources.isEmpty()) {
            StringBuilder from = new StringBuilder();
            StringBuilder url = new StringBuilder();
            for (PlaySource source : playSources) {
                if (from.length() > 0) {
                    from.append("$$$");
                    url.append("$$$");
                }
                from.append(source.getName());
                StringBuilder eps = new StringBuilder();
                for (String ep : source.getEpisodes()) {
                    if (eps.length() > 0) eps.append("#");
                    eps.append(ep);
                }
                url.append(eps);
            }
            vod.put("vod_play_from", from.toString());
            vod.put("vod_play_url", url.toString());
        }
        JSONArray list = new JSONArray();
        list.put(vod);
        JSONObject result = new JSONObject();
        result.put("list", list);
        return result.toString();
    }

    public VodItem getItem() { return item; }
    public void setItem(VodItem item) { this.item = item; }
    public List<PlaySource> getPlaySources() { return playSources; }
    public void setPlaySources(List<PlaySource> playSources) { this.playSources = playSources; }
    public List<String> getEpisodes() { return episodes; }
    public void setEpisodes(List<String> episodes) { this.episodes = episodes; }
}
