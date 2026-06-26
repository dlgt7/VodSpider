package com.github.catvod.bean.bili;

import android.text.TextUtils;

import com.github.catvod.bean.Vod;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import org.jsoup.Jsoup;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class Resp {

    @SerializedName("code")
    private Integer code;
    @SerializedName("message")
    private String message;
    @SerializedName("data")
    private Data data;

    public static Resp objectFrom(String str) {
        if (TextUtils.isEmpty(str)) return new Resp();
        str = str.trim();
        if (!str.startsWith("{")) return new Resp();
        try {
            return new Gson().fromJson(str, Resp.class);
        } catch (Exception e) {
            return new Resp();
        }
    }

    public int getCode() {
        return code == null ? -1 : code;
    }

    public String getMessage() {
        return message == null ? "" : message;
    }

    public Data getData() {
        return data == null ? new Data() : data;
    }

    public static class Result {

        @SerializedName("bvid")
        private String bvid;
        @SerializedName("aid")
        private JsonElement aid;
        @SerializedName("id")
        private JsonElement id;
        @SerializedName("type")
        private String type;
        @SerializedName("title")
        private String title;
        @SerializedName("pic")
        private String pic;
        @SerializedName("duration")
        private String duration;
        @SerializedName("length")
        private String length;

        public static List<Result> arrayFrom(JsonElement str) {
            if (str == null || str.isJsonNull() || !str.isJsonArray()) return new ArrayList<>();
            try {
                Type listType = new TypeToken<List<Result>>() {}.getType();
                return new Gson().fromJson(str, listType);
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }

        public static List<Result> videoArrayFrom(JsonElement str) {
            List<Result> list = new ArrayList<>();
            if (str == null || !str.isJsonArray()) return list;
            JsonArray array = str.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonElement element = array.get(i);
                if (!element.isJsonObject()) continue;
                try {
                    Result item = new Gson().fromJson(element, Result.class);
                    if (item != null && item.isVideo()) list.add(item);
                } catch (Exception ignored) {
                }
            }
            return list;
        }

        public boolean isVideo() {
            return TextUtils.isEmpty(type) || "video".equals(type);
        }

        public String getBvId() {
            return TextUtils.isEmpty(bvid) ? "" : bvid;
        }

        public String getAid() {
            if (aid != null && !aid.isJsonNull()) return aid.getAsString();
            if (id != null && !id.isJsonNull()) return id.getAsString();
            return "";
        }

        public String getTitle() {
            return TextUtils.isEmpty(title) ? "" : title;
        }

        public String getDuration() {
            if (TextUtils.isEmpty(duration)) return getLength();
            if (duration.contains(":")) return duration.split(":")[0] + "分鐘";
            if (Integer.parseInt(duration) < 60) return duration + "秒";
            return Integer.parseInt(duration) / 60 + "分鐘";
        }

        public String getLength() {
            return TextUtils.isEmpty(length) ? "" : length;
        }

        public String getPic() {
            return TextUtils.isEmpty(pic) ? "" : pic;
        }

        public Vod getVod() {
            Vod vod = new Vod();
            vod.setVodId(getBvId() + "@" + getAid());
            vod.setVodName(Jsoup.parse(getTitle()).text());
            vod.setVodPic(getPic().startsWith("//") ? "https:" + getPic() : getPic());
            vod.setVodRemarks(getDuration());
            return vod;
        }
    }
}
