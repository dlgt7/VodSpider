package com.github.catvod.spider.xbpq.model;

import org.json.JSONObject;

/**
 * 视频项模型
 * <p>
 * 对应 TVBox 标准的 vod 字段结构，字段名为空时 toJSON 不输出该键。
 */
public class VodItem {
    private String vod_id;
    private String vod_name;
    private String vod_pic;
    private String vod_remarks;
    /** 影片类型（TVBox 标准字段，详情提取器写入，勿在 toJSON 丢弃） */
    private String type_name;
    private String vod_year;
    private String vod_area;
    private String vod_actor;
    private String vod_director;
    private String vod_author;
    private String vod_state;
    private String vod_content;
    private String vod_play_from;
    private String vod_play_url;

    public VodItem() {}

    public VodItem(JSONObject json) {
        this.vod_id = json.optString("vod_id", "");
        this.vod_name = json.optString("vod_name", "");
        this.vod_pic = json.optString("vod_pic", "");
        this.vod_remarks = json.optString("vod_remarks", "");
        this.type_name = json.optString("type_name", "");
        this.vod_year = json.optString("vod_year", "");
        this.vod_area = json.optString("vod_area", "");
        this.vod_actor = json.optString("vod_actor", "");
        this.vod_director = json.optString("vod_director", "");
        this.vod_author = json.optString("vod_author", "");
        this.vod_state = json.optString("vod_state", "");
        this.vod_content = json.optString("vod_content", "");
        this.vod_play_from = json.optString("vod_play_from", "");
        this.vod_play_url = json.optString("vod_play_url", "");
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        try {
            putIfNotEmpty(json, "vod_id", vod_id);
            putIfNotEmpty(json, "vod_name", vod_name);
            putIfNotEmpty(json, "vod_pic", vod_pic);
            putIfNotEmpty(json, "vod_remarks", vod_remarks);
            putIfNotEmpty(json, "type_name", type_name);
            putIfNotEmpty(json, "vod_year", vod_year);
            putIfNotEmpty(json, "vod_area", vod_area);
            putIfNotEmpty(json, "vod_actor", vod_actor);
            putIfNotEmpty(json, "vod_director", vod_director);
            putIfNotEmpty(json, "vod_author", vod_author);
            putIfNotEmpty(json, "vod_state", vod_state);
            putIfNotEmpty(json, "vod_content", vod_content);
            putIfNotEmpty(json, "vod_play_from", vod_play_from);
            putIfNotEmpty(json, "vod_play_url", vod_play_url);
        } catch (Exception e) {
            // ignore
        }
        return json;
    }

    private static void putIfNotEmpty(JSONObject json, String key, String value) {
        if (value != null && !value.isEmpty()) {
            json.put(key, value);
        }
    }

    // Getters and Setters
    public String getVodId() { return vod_id; }
    public void setVodId(String vod_id) { this.vod_id = vod_id; }
    public String getVodName() { return vod_name; }
    public void setVodName(String vod_name) { this.vod_name = vod_name; }
    public String getVodPic() { return vod_pic; }
    public void setVodPic(String vod_pic) { this.vod_pic = vod_pic; }
    public String getVodRemarks() { return vod_remarks; }
    public void setVodRemarks(String vod_remarks) { this.vod_remarks = vod_remarks; }
    public String getTypeName() { return type_name; }
    public void setTypeName(String type_name) { this.type_name = type_name; }
    public String getVodYear() { return vod_year; }
    public void setVodYear(String vod_year) { this.vod_year = vod_year; }
    public String getVodArea() { return vod_area; }
    public void setVodArea(String vod_area) { this.vod_area = vod_area; }
    public String getVodActor() { return vod_actor; }
    public void setVodActor(String vod_actor) { this.vod_actor = vod_actor; }
    public String getVodDirector() { return vod_director; }
    public void setVodDirector(String vod_director) { this.vod_director = vod_director; }
    public String getVodAuthor() { return vod_author; }
    public void setVodAuthor(String vod_author) { this.vod_author = vod_author; }
    public String getVodState() { return vod_state; }
    public void setVodState(String vod_state) { this.vod_state = vod_state; }
    public String getVodContent() { return vod_content; }
    public void setVodContent(String vod_content) { this.vod_content = vod_content; }
    public String getVodPlayFrom() { return vod_play_from; }
    public void setVodPlayFrom(String vod_play_from) { this.vod_play_from = vod_play_from; }
    public String getVodPlayUrl() { return vod_play_url; }
    public void setVodPlayUrl(String vod_play_url) { this.vod_play_url = vod_play_url; }
}
