package com.github.catvod.bean;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Vod {

    @SerializedName("type_name")
    private String typeName;
    @SerializedName("vod_id")
    private String vodId;
    @SerializedName("vod_name")
    private String vodName;
    @SerializedName("vod_pic")
    private String vodPic;
    @SerializedName("vod_remarks")
    private String vodRemarks;
    @SerializedName("vod_year")
    private String vodYear;
    @SerializedName("vod_area")
    private String vodArea;
    @SerializedName("vod_actor")
    private String vodActor;
    @SerializedName("vod_director")
    private String vodDirector;
    @SerializedName("vod_content")
    private String vodContent;
    @SerializedName("vod_play_from")
    private String vodPlayFrom;
    @SerializedName("vod_play_url")
    private String vodPlayUrl;
    @SerializedName("vod_tag")
    private String vodTag;
    @SerializedName("action")
    private String action;
    @SerializedName("style")
    private Style style;

    public static Vod objectFrom(String str) {
        Vod item = new Gson().fromJson(str, Vod.class);
        return item == null ? new Vod() : item;
    }

    public static Vod action(String action) {
        Vod vod = new Vod();
        vod.action = action;
        return vod;
    }

    public Vod() {
    }

    public Vod(String vodId, String vodName, String vodPic) {
        setVodId(vodId);
        setVodName(vodName);
        setVodPic(vodPic);
    }

    public Vod(String vodId, String vodName, String vodPic, String vodRemarks) {
        setVodId(vodId);
        setVodName(vodName);
        setVodPic(vodPic);
        setVodRemarks(vodRemarks);
    }

    public Vod(String vodId, String vodName, String vodPic, String vodRemarks, String action) {
        setVodId(vodId);
        setVodName(vodName);
        setVodPic(vodPic);
        setVodRemarks(vodRemarks);
        setAction(action);
    }

    public Vod(String vodId, String vodName, String vodPic, String vodRemarks, Style style) {
        setVodId(vodId);
        setVodName(vodName);
        setVodPic(vodPic);
        setVodRemarks(vodRemarks);
        setStyle(style);
    }

    public Vod(String vodId, String vodName, String vodPic, String vodRemarks, Style style, String action) {
        setVodId(vodId);
        setVodName(vodName);
        setVodPic(vodPic);
        setVodRemarks(vodRemarks);
        setStyle(style);
        setAction(action);
    }

    public Vod(String vodId, String vodName, String vodPic, String vodRemarks, boolean folder) {
        setVodId(vodId);
        setVodName(vodName);
        setVodPic(vodPic);
        setVodRemarks(vodRemarks);
        setVodTag(folder ? "folder" : "file");
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public String getVodId() {
        return vodId;
    }

    public void setVodId(String vodId) {
        this.vodId = vodId;
    }

    public String getVodName() {
        return vodName;
    }

    public void setVodName(String vodName) {
        this.vodName = vodName;
    }

    public String getVodPic() {
        return vodPic;
    }

    public void setVodPic(String vodPic) {
        this.vodPic = vodPic;
    }

    public String getVodRemarks() {
        return vodRemarks;
    }

    public void setVodRemarks(String vodRemarks) {
        this.vodRemarks = vodRemarks;
    }

    public String getVodYear() {
        return vodYear;
    }

    public void setVodYear(String vodYear) {
        this.vodYear = vodYear;
    }

    public String getVodArea() {
        return vodArea;
    }

    public void setVodArea(String vodArea) {
        this.vodArea = vodArea;
    }

    public String getVodActor() {
        return vodActor;
    }

    public void setVodActor(String vodActor) {
        this.vodActor = vodActor;
    }

    public String getVodDirector() {
        return vodDirector;
    }

    public void setVodDirector(String vodDirector) {
        this.vodDirector = vodDirector;
    }

    public String getVodContent() {
        return vodContent;
    }

    public void setVodContent(String vodContent) {
        this.vodContent = vodContent;
    }

    public String getVodPlayFrom() {
        return vodPlayFrom;
    }

    public void setVodPlayFrom(String vodPlayFrom) {
        this.vodPlayFrom = vodPlayFrom;
    }

    public String getVodPlayUrl() {
        return vodPlayUrl;
    }

    public void setVodPlayUrl(String vodPlayUrl) {
        this.vodPlayUrl = vodPlayUrl;
    }

    public String getVodTag() {
        return vodTag;
    }

    public void setVodTag(String vodTag) {
        this.vodTag = vodTag;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Style getStyle() {
        return style;
    }

    public void setStyle(Style style) {
        this.style = style;
    }

    public boolean hasTypeName() {
        return typeName != null && !typeName.isEmpty();
    }

    public boolean hasVodId() {
        return vodId != null && !vodId.isEmpty();
    }

    public boolean hasVodName() {
        return vodName != null && !vodName.isEmpty();
    }

    public boolean hasVodPic() {
        return vodPic != null && !vodPic.isEmpty();
    }

    public boolean hasVodRemarks() {
        return vodRemarks != null && !vodRemarks.isEmpty();
    }

    public boolean hasVodYear() {
        return vodYear != null && !vodYear.isEmpty();
    }

    public boolean hasVodArea() {
        return vodArea != null && !vodArea.isEmpty();
    }

    public boolean hasVodActor() {
        return vodActor != null && !vodActor.isEmpty();
    }

    public boolean hasVodDirector() {
        return vodDirector != null && !vodDirector.isEmpty();
    }

    public boolean hasVodContent() {
        return vodContent != null && !vodContent.isEmpty();
    }

    public boolean hasVodPlayFrom() {
        return vodPlayFrom != null && !vodPlayFrom.isEmpty();
    }

    public boolean hasVodPlayUrl() {
        return vodPlayUrl != null && !vodPlayUrl.isEmpty();
    }

    public boolean hasVodTag() {
        return vodTag != null && !vodTag.isEmpty();
    }

    public boolean hasAction() {
        return action != null && !action.isEmpty();
    }

    public boolean hasStyle() {
        return style != null;
    }

    public boolean isFolder() {
        return "folder".equals(vodTag);
    }

    public boolean isFile() {
        return "file".equals(vodTag);
    }

    public boolean isAction() {
        return action != null && !action.isEmpty();
    }

    public boolean isEmpty() {
        return !hasVodId() && !hasVodName();
    }

    public boolean isValid() {
        return hasVodId() || hasVodName();
    }

    @Override
    public String toString() {
        return new GsonBuilder().disableHtmlEscaping().create().toJson(this);
    }

    public static class Style {

        @SerializedName("type")
        private String type;
        @SerializedName("ratio")
        private Float ratio;

        public static Style rect() {
            return rect(0.75f);
        }

        public static Style rect(float ratio) {
            return new Style("rect", ratio);
        }

        public static Style oval() {
            return new Style("oval", 1.0f);
        }

        public static Style full() {
            return new Style("full");
        }

        public static Style list() {
            return new Style("list");
        }

        public Style() {
        }

        public Style(String type) {
            this.type = type;
        }

        public Style(String type, Float ratio) {
            this.type = type;
            this.ratio = ratio;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Float getRatio() {
            return ratio;
        }

        public void setRatio(Float ratio) {
            this.ratio = ratio;
        }

        public boolean hasType() {
            return type != null && !type.isEmpty();
        }

        public boolean hasRatio() {
            return ratio != null && ratio > 0;
        }

        public boolean isRect() {
            return "rect".equals(type);
        }

        public boolean isOval() {
            return "oval".equals(type);
        }

        public boolean isFull() {
            return "full".equals(type);
        }

        public boolean isList() {
            return "list".equals(type);
        }

        @Override
        public String toString() {
            return new GsonBuilder().disableHtmlEscaping().create().toJson(this);
        }
    }
}