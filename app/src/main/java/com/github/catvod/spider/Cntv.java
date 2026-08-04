package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 央视频 Spider — 直播源
 * 站点：https://bkliveinfo.ysp.cctv.cn
 * 基于 TEA + CBC + XOR + 自定义 Base64 加密生成 CKey 请求播放地址
 */
public class Cntv extends Spider {

    // ==================== 加密常量 ====================
    private static final int DELTA = 0x9e3779b9;
    private static final int ROUNDS = 16;
    private static final int LOG_ROUNDS = 4;
    private static final int SALT_LEN = 2;
    private static final int ZERO_LEN = 7;

    private static final byte[] TEA_CKEY = hexToBytes("59b2f7cf725ef43c34fdd7c123411ed3");
    private static final byte[] GUARD_TEA_KEY = hexToBytes("110DBEC10C23E7D2E56A1CAD6914EF1B");

    private static final int[] XOR_KEY = {
        0x84, 0x2E, 0xED, 0x08, 0xF0, 0x66, 0xE6, 0xEA,
        0x48, 0xB4, 0xCA, 0xA9, 0x91, 0xED, 0x6F, 0xF3
    };
    private static final int[] GUARD_XOR_KEY = {
        0xB3, 0xC9, 0x53, 0xA0, 0x69, 0x13, 0xAD, 0x4D
    };

    private static final String STANDARD_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
    private static final String CUSTOM_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-=";

    private static final String API_HOST = "https://bkliveinfo.ysp.cctv.cn";
    private static final String APP_VER = "V8.22.1035.3031";
    private static final String APP_VERSION = "300090";
    private static final String PLATFORM = "4330403";

    // ==================== 频道图标 ====================
    private static final Map<String, String> LOGOS = new LinkedHashMap<>();
    // ==================== 频道数据 ====================
    // {key: [cnlid, livepid, defn, display_name]}
    private static final Map<String, String[]> CHANNELS = new LinkedHashMap<>();
    // ==================== 分类定义 ====================
    private static final List<String[]> CATS = new ArrayList<>(); // [name, ids...]

    static {
        // 频道图标初始化
        String logoBase = "https://cdn.jsdelivr.net/gh/wanglindl/TVlogo@main/img/";
        LOGOS.put("cctv1", logoBase + "CCTV1.png");
        LOGOS.put("cctv2", logoBase + "CCTV2.png");
        LOGOS.put("cctv3", logoBase + "CCTV3.png");
        LOGOS.put("cctv4", logoBase + "CCTV4.png");
        LOGOS.put("cctv5", logoBase + "CCTV5.png");
        LOGOS.put("cctv5p", logoBase + "CCTV5plus.png");
        LOGOS.put("cctv6", logoBase + "CCTV6.png");
        LOGOS.put("cctv7", logoBase + "CCTV7.png");
        LOGOS.put("cctv8", logoBase + "CCTV8.png");
        LOGOS.put("cctv9", logoBase + "CCTV9.png");
        LOGOS.put("cctv10", logoBase + "CCTV10.png");
        LOGOS.put("cctv11", logoBase + "CCTV11.png");
        LOGOS.put("cctv12", logoBase + "CCTV12.png");
        LOGOS.put("cctv13", logoBase + "CCTV13.png");
        LOGOS.put("cctv14", logoBase + "CCTV14.png");
        LOGOS.put("cctv15", logoBase + "CCTV15.png");
        LOGOS.put("cctv16", logoBase + "CCTV16.png");
        LOGOS.put("cctv164k", logoBase + "CCTV16.png");
        LOGOS.put("cctv17", logoBase + "CCTV17.png");
        LOGOS.put("cctv4k", logoBase + "CCTV4K.png");
        LOGOS.put("cctv8k", logoBase + "CCTV8K.png");
        LOGOS.put("cgtn", logoBase + "CGTN.png");
        LOGOS.put("cgtnfy", logoBase + "CGTNfy.png");
        LOGOS.put("cgtney", logoBase + "CGTNey.png");
        LOGOS.put("cgtnalby", logoBase + "CGTNalby.png");
        LOGOS.put("cgtnxby", logoBase + "CGTNxbyy.png");
        LOGOS.put("cgtnwyjl", logoBase + "CGTNjilu.png");
        LOGOS.put("cctvfyjc", logoBase + "CCTVfyjc.png");
        LOGOS.put("cctvdyjc", logoBase + "CCTVdyjc.png");
        LOGOS.put("cctvhjjc", logoBase + "CCTVhjjc.png");
        LOGOS.put("cctvsjdl", logoBase + "CCTVsjdl.png");
        LOGOS.put("cctvfyyy", logoBase + "CCTVfyyy.png");
        LOGOS.put("cctvbqkj", logoBase + "CCTVbqkj.png");
        LOGOS.put("cctvfyzq", logoBase + "CCTVfyzq.png");
        LOGOS.put("cctvgeqwq", logoBase + "CCTVgefwq.png");
        LOGOS.put("cctvnxss", logoBase + "CCTVnxss.png");
        LOGOS.put("cctvyswhjp", logoBase + "CCTVyswhjp.png");
        LOGOS.put("cctvystq", logoBase + "CCTVystq.png");
        LOGOS.put("cctvdszn", logoBase + "CCTVdszn.png");
        LOGOS.put("cctvwsjk", logoBase + "CCTVwsjk.png");
        LOGOS.put("bjws", logoBase + "Beijing.png");
        LOGOS.put("jsws", logoBase + "Jiangsu.png");
        LOGOS.put("dfws", logoBase + "Dongfang.png");
        LOGOS.put("zjws", logoBase + "Zhejiang.png");
        LOGOS.put("hnws", logoBase + "Hunan.png");
        LOGOS.put("hbws", logoBase + "Hubei.png");
        LOGOS.put("gdws", logoBase + "Guangdong.png");
        LOGOS.put("gxws", logoBase + "Guangxi.png");
        LOGOS.put("hljws", logoBase + "Heilongjiang.png");
        LOGOS.put("hnws2", logoBase + "Hainan.png");
        LOGOS.put("cqws", logoBase + "Chongqing.png");
        LOGOS.put("szws", logoBase + "Shenzhen.png");
        LOGOS.put("scws", logoBase + "Sichuan.png");
        LOGOS.put("henanws", logoBase + "Henan.png");
        LOGOS.put("fjdnhz", logoBase + "Dongnan.png");
        LOGOS.put("gzhws", logoBase + "Guizhou.png");
        LOGOS.put("jxws", logoBase + "Jiangxi.png");
        LOGOS.put("lnws", logoBase + "Liaoning.png");
        LOGOS.put("ahws", logoBase + "Anhui.png");
        LOGOS.put("hbws2", logoBase + "Hebei.png");
        LOGOS.put("sdws", logoBase + "Shandong.png");
        LOGOS.put("tjws", logoBase + "Tianjin.png");
        LOGOS.put("jlws", logoBase + "Jilin.png");
        LOGOS.put("shanxiws", logoBase + "Shanxi.png");
        LOGOS.put("nxws", logoBase + "Ningxia.png");
        LOGOS.put("nmgws", logoBase + "Neimeng.png");
        LOGOS.put("ynws", logoBase + "Yunnan.png");
        LOGOS.put("shanxiws2", logoBase + "Shanxi_.png");
        LOGOS.put("qhws", logoBase + "Qinghai.png");
        LOGOS.put("xzws", logoBase + "Xizang.png");
        LOGOS.put("xjws", logoBase + "Xinjiang.png");
        LOGOS.put("cetv1", logoBase + "CETV1.png");
        LOGOS.put("gxpd", "");

        // 频道数据 {key: [cnlid, livepid, defn, display_name]}
        CHANNELS.put("cctv1", new String[]{"2024078201", "600001859", "fhd", "CCTV-1"});
        CHANNELS.put("cctv2", new String[]{"2024075401", "600001800", "fhd", "CCTV-2"});
        CHANNELS.put("cctv3", new String[]{"2024068501", "600001801", "fhd", "CCTV-3"});
        CHANNELS.put("cctv4", new String[]{"2029797101", "600001814", "fhd", "CCTV-4"});
        CHANNELS.put("cctv5", new String[]{"2024078401", "600001818", "fhd", "CCTV-5"});
        CHANNELS.put("cctv5p", new String[]{"2024078001", "600001817", "fhd", "CCTV-5+"});
        CHANNELS.put("cctv6", new String[]{"2013693901", "600108442", "fhd", "CCTV-6"});
        CHANNELS.put("cctv7", new String[]{"2024072001", "600004092", "fhd", "CCTV-7"});
        CHANNELS.put("cctv8", new String[]{"2029793001", "600001803", "fhd", "CCTV-8"});
        CHANNELS.put("cctv9", new String[]{"2024078601", "600004078", "fhd", "CCTV-9"});
        CHANNELS.put("cctv10", new String[]{"2024078701", "600001805", "fhd", "CCTV-10"});
        CHANNELS.put("cctv11", new String[]{"2027248701", "600001806", "fhd", "CCTV-11"});
        CHANNELS.put("cctv12", new String[]{"2027248801", "600001807", "fhd", "CCTV-12"});
        CHANNELS.put("cctv13", new String[]{"2029797201", "600001811", "fhd", "CCTV-13"});
        CHANNELS.put("cctv14", new String[]{"2027248901", "600001809", "fhd", "CCTV-14"});
        CHANNELS.put("cctv15", new String[]{"2027249001", "600001815", "fhd", "CCTV-15"});
        CHANNELS.put("cctv16", new String[]{"2027249101", "600098637", "fhd", "CCTV-16"});
        CHANNELS.put("cctv164k", new String[]{"2027249301", "600099502", "fhd", "CCTV-16(4K)"});
        CHANNELS.put("cctv17", new String[]{"2027249401", "600001810", "fhd", "CCTV-17"});
        CHANNELS.put("cctv4k", new String[]{"2029810301", "600002264", "fhd", "CCTV-4K"});
        CHANNELS.put("cctv8k", new String[]{"2026774101", "600156816", "fhd", "CCTV-8K"});
        CHANNELS.put("cgtn", new String[]{"2024181701", "600014550", "fhd", "CGTN"});
        CHANNELS.put("cgtnfy", new String[]{"2024181801", "600084704", "fhd", "CGTN法语"});
        CHANNELS.put("cgtney", new String[]{"2024181901", "600084758", "fhd", "CGTN俄语"});
        CHANNELS.put("cgtnalby", new String[]{"2024182001", "600084782", "fhd", "CGTN阿拉伯语"});
        CHANNELS.put("cgtnxby", new String[]{"2024182101", "600084744", "fhd", "CGTN西班牙语"});
        CHANNELS.put("cgtnwyjl", new String[]{"2024182301", "600084781", "fhd", "CGTN外语纪录"});
        CHANNELS.put("cctvfyjc", new String[]{"2025637103", "600099658", "shd", "风云剧场"});
        CHANNELS.put("cctvdyjc", new String[]{"2026874203", "600099655", "shd", "第一剧场"});
        CHANNELS.put("cctvhjjc", new String[]{"2026874303", "600099620", "shd", "怀旧剧场"});
        CHANNELS.put("cctvsjdl", new String[]{"2026874403", "600099637", "shd", "世界地理"});
        CHANNELS.put("cctvfyyy", new String[]{"2026874503", "600099660", "shd", "风云音乐"});
        CHANNELS.put("cctvbqkj", new String[]{"2026874603", "600099649", "shd", "兵器科技"});
        CHANNELS.put("cctvfyzq", new String[]{"2026966203", "600099636", "shd", "风云足球"});
        CHANNELS.put("cctvgeqwq", new String[]{"2026874703", "600099659", "shd", "高尔夫·网球"});
        CHANNELS.put("cctvnxss", new String[]{"2026874803", "600099650", "shd", "女性时尚"});
        CHANNELS.put("cctvyswhjp", new String[]{"2026874903", "600099653", "shd", "央视文化精品"});
        CHANNELS.put("cctvystq", new String[]{"2026875003", "600099652", "shd", "央视台球"});
        CHANNELS.put("cctvdszn", new String[]{"2026875103", "600099656", "shd", "电视指南"});
        CHANNELS.put("cctvwsjk", new String[]{"2025637003", "600099651", "shd", "卫生健康"});
        CHANNELS.put("bjws", new String[]{"2024052703", "600002309", "fhd", "北京卫视"});
        CHANNELS.put("jsws", new String[]{"2024171103", "600002521", "fhd", "江苏卫视"});
        CHANNELS.put("dfws", new String[]{"2024054503", "600002483", "fhd", "东方卫视"});
        CHANNELS.put("zjws", new String[]{"2024054703", "600002520", "fhd", "浙江卫视"});
        CHANNELS.put("hnws", new String[]{"2024054803", "600002475", "fhd", "湖南卫视"});
        CHANNELS.put("hbws", new String[]{"2024171203", "600002508", "fhd", "湖北卫视"});
        CHANNELS.put("gdws", new String[]{"2024060903", "600002485", "fhd", "广东卫视"});
        CHANNELS.put("gxws", new String[]{"2024060703", "600002509", "fhd", "广西卫视"});
        CHANNELS.put("hljws", new String[]{"2029797003", "600002498", "fhd", "黑龙江卫视"});
        CHANNELS.put("hnws2", new String[]{"2024055603", "600002506", "fhd", "海南卫视"});
        CHANNELS.put("cqws", new String[]{"2024061103", "600002531", "fhd", "重庆卫视"});
        CHANNELS.put("szws", new String[]{"2024061303", "600002481", "fhd", "深圳卫视"});
        CHANNELS.put("scws", new String[]{"2024061403", "600002516", "fhd", "四川卫视"});
        CHANNELS.put("henanws", new String[]{"2029797303", "600002525", "fhd", "河南卫视"});
        CHANNELS.put("fjdnhz", new String[]{"2024061503", "600002484", "fhd", "福建东南卫视"});
        CHANNELS.put("gzhws", new String[]{"2024061603", "600002490", "fhd", "贵州卫视"});
        CHANNELS.put("jxws", new String[]{"2024061703", "600002503", "fhd", "江西卫视"});
        CHANNELS.put("lnws", new String[]{"2024171303", "600002505", "fhd", "辽宁卫视"});
        CHANNELS.put("ahws", new String[]{"2024171403", "600002532", "fhd", "安徽卫视"});
        CHANNELS.put("hbws2", new String[]{"2024171503", "600002493", "fhd", "河北卫视"});
        CHANNELS.put("sdws", new String[]{"2029787903", "600002513", "fhd", "山东卫视"});
        CHANNELS.put("tjws", new String[]{"2019927003", "600152137", "fhd", "天津卫视"});
        CHANNELS.put("jlws", new String[]{"2025561503", "600190405", "fhd", "吉林卫视"});
        CHANNELS.put("shanxiws", new String[]{"2029795103", "600190400", "fhd", "陕西卫视"});
        CHANNELS.put("nxws", new String[]{"2025608503", "600190737", "fhd", "宁夏卫视"});
        CHANNELS.put("nmgws", new String[]{"2025561203", "600190401", "fhd", "内蒙古卫视"});
        CHANNELS.put("ynws", new String[]{"2025561303", "600190402", "fhd", "云南卫视"});
        CHANNELS.put("shanxiws2", new String[]{"2025560803", "600190407", "fhd", "山西卫视"});
        CHANNELS.put("qhws", new String[]{"2025559103", "600190406", "fhd", "青海卫视"});
        CHANNELS.put("xzws", new String[]{"2025558003", "600190403", "fhd", "西藏卫视"});
        CHANNELS.put("xjws", new String[]{"2019927403", "600152138", "fhd", "新疆卫视"});
        CHANNELS.put("cetv1", new String[]{"2022823801", "600171827", "fhd", "中国教育电视台"});
        CHANNELS.put("gxpd", new String[]{"2029360403", "600213139", "fhd", "国学频道"});

        // 分类定义
        CATS.add(new String[]{"央视", "cctv1", "cctv2", "cctv3", "cctv4", "cctv5", "cctv5p",
            "cctv6", "cctv7", "cctv8", "cctv9", "cctv10", "cctv11",
            "cctv12", "cctv13", "cctv14", "cctv15", "cctv16", "cctv164k",
            "cctv17", "cctv4k", "cctv8k"});
        CATS.add(new String[]{"CGTN", "cgtn", "cgtnfy", "cgtney", "cgtnalby", "cgtnxby", "cgtnwyjl"});
        CATS.add(new String[]{"央视付费", "cctvfyjc", "cctvdyjc", "cctvhjjc", "cctvsjdl", "cctvfyyy",
            "cctvbqkj", "cctvfyzq", "cctvgeqwq", "cctvnxss", "cctvyswhjp",
            "cctvystq", "cctvdszn", "cctvwsjk"});
        CATS.add(new String[]{"卫视", "bjws", "jsws", "dfws", "zjws", "hnws", "hbws", "gdws",
            "gxws", "hljws", "hnws2", "cqws", "szws", "scws", "henanws",
            "fjdnhz", "gzhws", "jxws", "lnws", "ahws", "hbws2", "sdws",
            "tjws", "jlws", "shanxiws", "nxws", "nmgws", "ynws",
            "shanxiws2", "qhws", "xzws", "xjws"});
        CATS.add(new String[]{"其他", "cetv1", "gxpd"});
    }

    private String guid;
    private final Map<String, String> sessionHeaders = new HashMap<>();

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context);
        guid = genGuid();
        sessionHeaders.put("User-Agent", "qqlive");
        sessionHeaders.put("Connection", "Keep-Alive");
        sessionHeaders.put("Accept", "application/json");
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        for (int i = 0; i < CATS.size(); i++) {
            classes.add(new Class(String.valueOf(i), CATS.get(i)[0]));
        }
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String homeVideoContent() throws Exception {
        return Result.string(new ArrayList<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int idx;
        try {
            idx = Integer.parseInt(tid);
        } catch (Exception e) {
            idx = 0;
        }
        if (idx < 0 || idx >= CATS.size()) return Result.string(new ArrayList<>());

        String[] cat = CATS.get(idx);
        List<Vod> list = new ArrayList<>();
        for (int i = 1; i < cat.length; i++) {
            String cid = cat[i];
            String[] ch = CHANNELS.get(cid);
            if (ch == null) continue;
            String logo = LOGOS.getOrDefault(cid, "");
            list.add(new Vod(cid, ch[3], logo, ch[2].toUpperCase()));
        }
        return Result.get().page(1, 1, list.size(), list.size()).vod(list).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String cid = ids.get(0);
        String[] ch = CHANNELS.get(cid);
        if (ch == null) return Result.string(new ArrayList<>());

        Vod vod = new Vod();
        vod.setVodId(cid);
        vod.setVodName(ch[3]);
        vod.setVodPic(LOGOS.getOrDefault(cid, ""));
        vod.setVodPlayFrom("央视频");
        vod.setVodPlayUrl("直播$" + cid);
        vod.setVodContent(ch[3] + " 高清直播");
        List<Vod> list = new ArrayList<>();
        list.add(vod);
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String[] ch = CHANNELS.get(id);
        if (ch == null) return Result.get().parse(0).url("").string();
        String playUrl = getPlayUrl(ch[0], ch[1], ch[2]);
        return Result.get().parse(0).url(TextUtils.isEmpty(playUrl) ? "" : playUrl).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return Result.string(new ArrayList<>());
    }

    // ==================== 播放地址获取 ====================

    private String getPlayUrl(String cnlid, String livepid, String defn) {
        try {
            guid = genGuid();
            String[] ck = genCKey(cnlid);
            String flowid = genFlowId();

            Map<String, String> params = new LinkedHashMap<>();
            params.put("atime", "120");
            params.put("livepid", livepid);
            params.put("cnlid", cnlid);
            params.put("appVer", APP_VER);
            params.put("app_version", APP_VERSION);
            params.put("caplv", "1");
            params.put("cmd", "2");
            params.put("defn", defn);
            params.put("device", "iPhone");
            params.put("encryptVer", "4.2");
            params.put("getpreviewinfo", "0");
            params.put("hevclv", "33");
            params.put("lang", "zh-Hans_JP");
            params.put("livequeue", "0");
            params.put("logintype", "1");
            params.put("nettype", "1");
            params.put("newnettype", "1");
            params.put("newplatform", PLATFORM);
            params.put("platform", PLATFORM);
            params.put("sdtfrom", "v3021");
            params.put("spacode", "23");
            params.put("spaudio", "1");
            params.put("spdemuxer", "6");
            params.put("spdrm", "2");
            params.put("spdynamicrange", "7");
            params.put("spflv", "1");
            params.put("spflvaudio", "1");
            params.put("sphdrfps", "60");
            params.put("sphttps", "0");
            params.put("spvcode", "MSgzMDoyMTYwLDYwOjIxNjB8MzA6MjE2MCw2MDoyMTYwKTsyKDMwOjIxNjAsNjA6MjE2MHwzMDoyMTYwLDYwOjIxNjAp");
            params.put("spvideo", "4");
            params.put("stream", "1");
            params.put("system", "1");
            params.put("sysver", "ios18.2.1");
            params.put("uhd_flag", "4");
            params.put("cKey", ck[0]);
            params.put("guid", guid);
            params.put("fntick", ck[1]);
            params.put("flowid", flowid);
            params.put("playbacktime", "0");

            StringBuilder urlBuilder = new StringBuilder(API_HOST);
            urlBuilder.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!first) urlBuilder.append("&");
                urlBuilder.append(entry.getKey()).append("=").append(java.net.URLEncoder.encode(entry.getValue(), "UTF-8"));
                first = false;
            }

            String resp = OkHttp.string(urlBuilder.toString(), sessionHeaders);
            com.google.gson.JsonObject obj = Json.safeObject(resp);
            if (Json.getInt(obj, "iretcode") == 0) {
                String playurl = Json.getString(obj, "playurl");
                if (!TextUtils.isEmpty(playurl)) return playurl;
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return null;
    }

    // ==================== GUID / FlowID ====================

    private String genGuid() {
        Random r = new Random();
        long v1 = r.nextInt() & 0xFFFFFFFFL;
        int v2 = r.nextInt(0x10000);
        int v3 = r.nextInt(0x10000);
        int v4 = r.nextInt(0x10000);
        long v5 = r.nextLong() & 0xFFFFFFFFFFFFL;
        return String.format("%08x%04x%04x%04x%012x", v1, v2, v3, v4, v5);
    }

    private String genFlowId() {
        Random r = new Random();
        int[] p = new int[8];
        p[0] = r.nextInt(0xffff + 1);
        p[1] = r.nextInt(0xffff + 1);
        p[2] = r.nextInt(0xffff + 1);
        p[3] = (r.nextInt(0x0fff + 1)) | 0x4000;
        p[4] = (r.nextInt(0x3fff + 1)) | 0x8000;
        p[5] = r.nextInt(0xffff + 1);
        p[6] = r.nextInt(0xffff + 1);
        p[7] = r.nextInt(0xffff + 1);
        return String.format("%04X%04X-%04X-%04X-%04X-%04X%04X%04X_4330403",
            p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7]);
    }

    // ==================== 签名 ====================

    private long calcSig(byte[] buf) {
        long s = 0;
        for (byte b : buf) {
            s = (0x83 * s + (b & 0xFF)) & 0x7FFFFFFF;
        }
        return s;
    }

    // ==================== 自定义 Base64 ====================

    private String b64Enc(byte[] data) {
        String enc = java.util.Base64.getEncoder().encodeToString(data);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < enc.length(); i++) {
            char c = enc.charAt(i);
            int idx = STANDARD_ALPHABET.indexOf(c);
            if (idx >= 0) {
                result.append(CUSTOM_ALPHABET.charAt(idx));
            } else {
                result.append(c);
            }
        }
        // strip '='
        int len = result.length();
        while (len > 0 && result.charAt(len - 1) == '=') len--;
        return result.substring(0, len);
    }

    // ==================== XOR ====================

    private byte[] xorBytes(byte[] arr) {
        byte[] result = new byte[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = (byte) (arr[i] ^ XOR_KEY[i & 0xF]);
        }
        return result;
    }

    // ==================== TEA ====================

    private byte[] teaEnc(byte[] data, byte[] key) {
        if (data.length < 8) {
            data = Arrays.copyOf(data, 8);
        }
        long y = readUInt32BE(data, 0);
        long z = readUInt32BE(data, 4);
        long[] k = new long[4];
        for (int i = 0; i < 4; i++) k[i] = readUInt32BE(key, i * 4);
        long s = 0;
        for (int i = 0; i < ROUNDS; i++) {
            s = (s + DELTA) & 0xFFFFFFFFL;
            y = (y + (((z << 4) + k[0]) ^ (z + s) ^ ((z >>> 5) + k[1]))) & 0xFFFFFFFFL;
            z = (z + (((y << 4) + k[2]) ^ (y + s) ^ ((y >>> 5) + k[3]))) & 0xFFFFFFFFL;
        }
        byte[] result = new byte[8];
        writeUInt32BE(result, 0, y);
        writeUInt32BE(result, 4, z);
        return result;
    }

    // ==================== CBC 加密 ====================

    private byte[] cbcEnc(byte[] pIn, int nLen, byte[] pKey) {
        int padSaltZero = nLen + 1 + SALT_LEN + ZERO_LEN;
        int nPad = padSaltZero % 8;
        if (nPad != 0) nPad = 8 - nPad;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] src = new byte[8];
        Random random = new Random();
        src[0] = (byte) ((random.nextInt(256) & 0xF8) | nPad);
        int si = 1;

        while (nPad > 0) {
            src[si++] = (byte) random.nextInt(256);
            nPad--;
        }

        byte[] ivP = new byte[8];
        byte[] ivC = new byte[8];

        // salt
        int i = 0;
        while (i < SALT_LEN) {
            if (si < 8) {
                src[si++] = (byte) random.nextInt(256);
                i++;
            }
            if (si == 8) {
                byte[] tb = cbcBlockEnc(src, ivC, ivP, pKey);
                out.write(tb, 0, 8);
                System.arraycopy(src, 0, ivP, 0, 8);
                System.arraycopy(tb, 0, ivC, 0, 8);
                si = 0;
            }
        }

        // body
        int pi = 0;
        while (nLen > 0) {
            if (si < 8) {
                src[si++] = pIn[pi++];
                nLen--;
            }
            if (si == 8) {
                byte[] tb = cbcBlockEnc(src, ivC, ivP, pKey);
                out.write(tb, 0, 8);
                System.arraycopy(src, 0, ivP, 0, 8);
                System.arraycopy(tb, 0, ivC, 0, 8);
                si = 0;
            }
        }

        // zero
        i = 0;
        while (i < ZERO_LEN) {
            if (si < 8) {
                src[si++] = 0;
                i++;
            }
            if (si == 8) {
                byte[] tb = cbcBlockEnc(src, ivC, ivP, pKey);
                out.write(tb, 0, 8);
                System.arraycopy(src, 0, ivP, 0, 8);
                System.arraycopy(tb, 0, ivC, 0, 8);
                si = 0;
            }
        }

        // last
        if (si > 0) {
            for (int j = si; j < 8; j++) src[j] = 0;
            byte[] tb = cbcBlockEnc(src, ivC, ivP, pKey);
            out.write(tb, 0, 8);
        }

        return out.toByteArray();
    }

    private byte[] cbcBlockEnc(byte[] src, byte[] ivC, byte[] ivP, byte[] pKey) {
        // 必须原地修改 src（与 Python 一致：src[j] ^= iv_c[j]），
        // 因为调用方会将 src 作为下一轮的 ivP
        for (int j = 0; j < 8; j++) src[j] = (byte) (src[j] ^ ivC[j]);
        byte[] tb = teaEnc(src, pKey);
        for (int j = 0; j < 8; j++) tb[j] = (byte) (tb[j] ^ ivP[j]);
        return tb;
    }

    // ==================== Guard Time ====================

    private String last5(String v) {
        return v.length() >= 5 ? v.substring(v.length() - 5) : "";
    }

    private String genGuardTime(int ts, String guid) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeUInt32BE(body, ts);
        String[] parts = {last5(guid), last5("null"), last5("null"), "-1"};
        for (String part : parts) {
            byte[] pb = part.getBytes(StandardCharsets.UTF_8);
            writeUInt16BE(body, pb.length);
            body.write(pb, 0, pb.length);
        }

        byte[] bodyBytes = body.toByteArray();
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        writeUInt16BE(plain, bodyBytes.length);
        plain.write(bodyBytes, 0, bodyBytes.length);
        byte[] plainBytes = plain.toByteArray();

        long chk = calcSig(plainBytes);
        byte[] enc = cbcEnc(plainBytes, plainBytes.length, GUARD_TEA_KEY);
        byte[] encWithChk = new byte[enc.length + 4];
        System.arraycopy(enc, 0, encWithChk, 0, enc.length);
        writeUInt32BE(encWithChk, enc.length, chk);

        // XOR with guard key
        for (int i = 0; i < encWithChk.length; i++) {
            encWithChk[i] = (byte) (encWithChk[i] ^ GUARD_XOR_KEY[i & 7]);
        }
        return bytesToHex(encWithChk).toUpperCase();
    }

    // ==================== CKey ====================

    private String encryptCKey(byte[] data) {
        long chk = calcSig(data);
        byte[] enc = cbcEnc(data, data.length, TEA_CKEY);
        byte[] encWithChk = new byte[enc.length + 4];
        System.arraycopy(enc, 0, encWithChk, 0, enc.length);
        writeUInt32BE(encWithChk, enc.length, chk);
        byte[] xored = xorBytes(encWithChk);
        return "--01" + b64Enc(xored);
    }

    private byte[] buildPkt(Map<String, Object> params) {
        ByteArrayOutputStream d = new ByteArrayOutputStream();
        // 12-byte header
        byte[] header = hexToBytes("0000004200000004000004d2");
        d.write(header, 0, header.length);
        writeUInt32BE(d, ((Number) params.get("Platform")).intValue());
        writeUInt32BE(d, 0); // sig placeholder
        writeUInt32BE(d, ((Number) params.get("Timestamp")).intValue());

        String[] keys = {"Sdtfrom", "randFlag", "appVer", "vid", "guid"};
        for (String k : keys) {
            byte[] v = ((String) params.get(k)).getBytes(StandardCharsets.UTF_8);
            writeUInt16BE(d, v.length);
            d.write(v, 0, v.length);
        }

        writeUInt32BE(d, 1);  // part1
        writeUInt32BE(d, 1);  // isDlna

        byte[][] fixed = {b("2622783A"), b("nil")};
        for (byte[] v : fixed) {
            writeUInt16BE(d, v.length);
            d.write(v, 0, v.length);
        }

        byte[] uuid4 = ((String) params.get("uuid4")).getBytes(StandardCharsets.UTF_8);
        writeUInt16BE(d, uuid4.length);
        d.write(uuid4, 0, uuid4.length);

        byte[] nil = b("nil");
        writeUInt16BE(d, 3);
        d.write(nil, 0, nil.length); // bundleID1

        byte[][] fixed2 = {
            b("v0.1.000"),
            b("com.cctv.yangshipin.app.iphone"),
            b("4330403"),
            b("ex_json_bus"),
            b("ex_json_vs")
        };
        for (byte[] v : fixed2) {
            writeUInt16BE(d, v.length);
            d.write(v, 0, v.length);
        }

        byte[] cgt = ((String) params.get("ck_guard_time")).getBytes(StandardCharsets.UTF_8);
        writeUInt16BE(d, cgt.length);
        d.write(cgt, 0, cgt.length);

        byte[] dBytes = d.toByteArray();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        writeUInt16BE(buf, dBytes.length);
        buf.write(dBytes, 0, dBytes.length);
        byte[] bufBytes = buf.toByteArray();

        long sig = calcSig(bufBytes);
        // Replace sig at offset 18 (2-byte length prefix + 12-byte header + 4-byte Platform = 18)
        writeUInt32BE(bufBytes, 18, sig);
        return bufBytes;
    }

    /** Returns [ckey, timestamp] */
    private String[] genCKey(String cnlid) {
        int ts = (int) (System.currentTimeMillis() / 1000);
        String cgt = genGuardTime(ts, guid);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("Platform", 4330403);
        params.put("Timestamp", ts);
        params.put("Sdtfrom", "dcgh");
        params.put("vid", cnlid);
        params.put("guid", guid);
        params.put("appVer", APP_VER);
        params.put("randFlag", "_zj1A5Gh6QYcxWjIUGos2w==");
        params.put("uuid4", "57eab0c4-2c58-44c6-8ae9-dd2757525dc5");
        params.put("ck_guard_time", cgt);
        byte[] pkt = buildPkt(params);
        return new String[]{encryptCKey(pkt), String.valueOf(ts)};
    }

    // ==================== 字节工具 ====================

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xFF));
        return sb.toString();
    }

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static long readUInt32BE(byte[] data, int offset) {
        return ((long) (data[offset] & 0xFF) << 24)
            | ((long) (data[offset + 1] & 0xFF) << 16)
            | ((long) (data[offset + 2] & 0xFF) << 8)
            | (data[offset + 3] & 0xFF);
    }

    private static void writeUInt32BE(byte[] data, int offset, long value) {
        data[offset] = (byte) ((value >> 24) & 0xFF);
        data[offset + 1] = (byte) ((value >> 16) & 0xFF);
        data[offset + 2] = (byte) ((value >> 8) & 0xFF);
        data[offset + 3] = (byte) (value & 0xFF);
    }

    private static void writeUInt32BE(ByteArrayOutputStream out, long value) {
        out.write((int) ((value >> 24) & 0xFF));
        out.write((int) ((value >> 16) & 0xFF));
        out.write((int) ((value >> 8) & 0xFF));
        out.write((int) (value & 0xFF));
    }

    private static void writeUInt16BE(ByteArrayOutputStream out, int value) {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}
