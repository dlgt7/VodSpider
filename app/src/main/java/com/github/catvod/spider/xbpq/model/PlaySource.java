package com.github.catvod.spider.xbpq.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 播放源模型
 * <p>
 * 单条播放线路：线路名 + 集数列表（每集格式 "标题$链接"）。
 */
public class PlaySource {
    private String name;
    private List<String> episodes;

    public PlaySource(String name) {
        this.name = name;
        this.episodes = new ArrayList<>();
    }

    public PlaySource(String name, List<String> episodes) {
        this.name = name;
        this.episodes = new ArrayList<>(episodes);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getEpisodes() { return episodes; }
    public void setEpisodes(List<String> episodes) { this.episodes = episodes; }

    public void addEpisode(String episode) {
        this.episodes.add(episode);
    }
}
