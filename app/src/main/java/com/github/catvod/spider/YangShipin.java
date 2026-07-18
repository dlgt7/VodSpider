package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 央视频直播源Spider
 * 获取CCTV各频道直播流
 */
public class YangShipin extends Spider {

    private static final String[][] CHANNELS = {
            {"CCTV1", "综合", "2000210103", "600001859"},
            {"CCTV2", "财经", "2000203603", "600001800"},
            {"CCTV3", "综艺", "2000203803", "600001801"},
            {"CCTV4", "中文国际", "2000204803", "600001814"},
            {"CCTV5", "体育", "2000205103", "600001818"},
            {"CCTV5P", "体育赛事", "2000204503", "600001817"},
            {"CCTV6", "电影", "2000203303", "600001802"},
            {"CCTV7", "国防军事", "2000510003", "600004092"},
            {"CCTV8", "电视剧", "2000203903", "600001803"},
            {"CCTV9", "纪录", "2000499403", "600004078"},
            {"CCTV10", "科教", "2000203503", "600001805"},
            {"CCTV11", "戏曲", "2000204103", "600001806"},
            {"CCTV12", "社会与法", "2000202603", "600001807"},
            {"CCTV13", "新闻", "2000204603", "600001811"},
            {"CCTV14", "少儿", "2000204403", "600001809"},
            {"CCTV15", "音乐", "2000205003", "600001815"},
            {"CCTV17", "农业农村", "2000204203", "600001810"},
    };

    private static final String AES_KEY = "4E2918885FD98109869D14E0231A0BF4";
    private static final String AES_IV = "16B17E519DDD0CE5B79D7A63A4DD801C";

    @Override
    public void init(Context context, String extend) throws Exception {
    }

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", "https://w.yangshipin.cn/");
        return headers;
    }

    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private String generateCKey(String vid, String tm, String appVer, String guid, String platform) throws Exception {
        String sr = "mg3c3b04ba";
        String nn = "https://w.yangshipin.cn/";
        String fn = "|" + vid + "|" + tm + "|" + sr + "|" + appVer + "|" + guid + "|" + platform + "|" + nn + "|mozilla/5.0 (windows nt ||Mozilla|Netscape|Win32|";

        int qn = 0;
        for (int i = 0; i < fn.length(); i++) {
            qn = (qn << 5) - qn + fn.charAt(i);
            qn &= 0xFFFFFFFF;
        }

        String yn = "|" + qn + fn;

        byte[] keyBytes = hexStringToByteArray(AES_KEY);
        byte[] ivBytes = hexStringToByteArray(AES_IV);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
        byte[] encrypted = cipher.doFinal(yn.getBytes("UTF-8"));

        StringBuilder sb = new StringBuilder("--01");
        for (byte b : encrypted) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private String generateFlowId() {
        String timePart = Long.toString(System.currentTimeMillis(), 36);
        String randomPart = Long.toString((long) (Math.random() * Long.MAX_VALUE), 36);
        return timePart + "_" + randomPart;
    }

    private String generateGuid() {
        return Long.toHexString(System.currentTimeMillis()) + Long.toHexString((long) (Math.random() * Long.MAX_VALUE));
    }

    private String getPlayUrl(String vid, String pid) {
        try {
            String guid = generateGuid();
            String tm = String.valueOf(System.currentTimeMillis() / 1000);
            String appVer = "0.2.0";
            String platform = "4330701";
            String flowid = generateFlowId();
            String cKey = generateCKey(vid, tm, appVer, guid, platform);

            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append("https://playvv.yangshipin.cn/playvinfo?");
            urlBuilder.append("guid=").append(guid);
            urlBuilder.append("&platform=").append(platform);
            urlBuilder.append("&vid=").append(vid);
            urlBuilder.append("&defn=fhd");
            urlBuilder.append("&charge=0");
            urlBuilder.append("&defaultfmt=auto");
            urlBuilder.append("&otype=json");
            urlBuilder.append("&defnpayver=1");
            urlBuilder.append("&appVer=").append(appVer);
            urlBuilder.append("&sphttps=1");
            urlBuilder.append("&sphls=1");
            urlBuilder.append("&spwm=4");
            urlBuilder.append("&dtype=3");
            urlBuilder.append("&defsrc=2");
            urlBuilder.append("&encryptVer=8.1");
            urlBuilder.append("&sdtfrom=").append(platform);
            urlBuilder.append("&cKey=").append(cKey);
            urlBuilder.append("&flowid=").append(flowid);

            String resp = OkHttp.string(urlBuilder.toString(), getHeaders());

            // 返回格式为QZOutputJson=...需要去除前缀
            String json = resp;
            if (json.startsWith("QZOutputJson=")) {
                json = json.substring("QZOutputJson=".length());
            }

            JSONObject obj = new JSONObject(json);
            JSONObject data = obj.optJSONObject("vl");
            if (data == null) {
                JSONObject videoInfo = obj.optJSONObject("video_info");
                if (videoInfo != null) data = videoInfo.optJSONObject("vl");
            }
            if (data == null) return "";

            JSONArray videoList = data.optJSONArray("vi");
            if (videoList == null || videoList.length() == 0) return "";

            JSONObject video = videoList.optJSONObject(0);
            if (video == null) return "";

            String fn = video.optString("fn");
            String fvkey = video.optString("fvkey");
            String baseUrl = "";

            JSONObject ui = video.optJSONObject("ul");
            if (ui != null) {
                JSONArray uiList = ui.optJSONArray("ui");
                if (uiList != null && uiList.length() > 0) {
                    baseUrl = uiList.optJSONObject(0).optString("url");
                }
            }

            if (TextUtils.isEmpty(fn) || TextUtils.isEmpty(baseUrl)) return "";

            StringBuilder playUrl = new StringBuilder();
            playUrl.append(baseUrl);
            if (!baseUrl.endsWith("/")) playUrl.append("/");
            playUrl.append(fn);
            if (!TextUtils.isEmpty(fvkey)) {
                playUrl.append("?vkey=").append(fvkey);
            }

            return playUrl.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        classes.add(new Class("cctv", "央视频道"));

        ArrayList<Vod> list = new ArrayList<>();
        for (String[] channel : CHANNELS) {
            String vodId = channel[2] + "###" + channel[3];
            String vodName = channel[0] + " " + channel[1];
            list.add(new Vod(vodId, vodName, "", "直播"));
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        ArrayList<Vod> list = new ArrayList<>();
        for (String[] channel : CHANNELS) {
            String vodId = channel[2] + "###" + channel[3];
            String vodName = channel[0] + " " + channel[1];
            list.add(new Vod(vodId, vodName, "", "直播"));
        }
        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String[] parts = id.split("###");
        String vid = parts[0];
        String pid = parts.length > 1 ? parts[1] : "";

        String channelName = "";
        for (String[] channel : CHANNELS) {
            if (channel[2].equals(vid)) {
                channelName = channel[0] + " " + channel[1];
                break;
            }
        }
        if (TextUtils.isEmpty(channelName)) channelName = "CCTV直播";

        Vod vod = new Vod();
        vod.setVodId(id);
        vod.setVodName(channelName);
        vod.setVodPic("");
        vod.setTypeName("央视频道");
        vod.setVodPlayFrom("直播");
        vod.setVodPlayUrl(channelName + "$" + vid + "###" + pid);

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String[] parts = id.split("###");
        String vid = parts[0];
        String pid = parts.length > 1 ? parts[1] : "";

        String playUrl = getPlayUrl(vid, pid);

        // 如果API获取失败，回退到cntv API
        if (TextUtils.isEmpty(playUrl) && !TextUtils.isEmpty(pid)) {
            try {
                String cntvUrl = "https://vdn.apps.cntv.cn/api/getHttpVideoInfo.do?pid=" + pid;
                String resp = OkHttp.string(cntvUrl, getHeaders());
                JSONObject obj = new JSONObject(resp);
                playUrl = obj.optString("hls_url", "");
            } catch (Exception e) {
                // skip
            }
        }

        if (TextUtils.isEmpty(playUrl)) {
            playUrl = id;
        }

        return Result.get().url(playUrl).header(getHeaders()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return Result.string(new ArrayList<>());
    }
}
