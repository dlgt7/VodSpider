package com.github.catvod.spider;

import com.github.catvod.utils.Crypto;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public final class HxqCrypto {

    public static final String CH = "vivo";
    public static final String HOST = "https://hxqapi.hiyun.tv";
    private static final long INSTALL_AGE_MS = 0x48190800L;
    public static final String UA = "HanjuTV/6.8 (V2238A; Android 12; Scale/2.00)";
    private static final String UID_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    public static final String VC = "a_8260";
    public static final String VN = "6.8";

    private HxqCrypto() {
    }

    public static Map<String, String> buildHeaders(HxqSession session, long ts) throws JSONException {
        HxqNative.ensureLoaded();
        String payload = buildSignPayload(session, ts);
        String uk = HxqNative.aesUk(session.uid);
        String sign = HxqNative.aesSign(payload, session.uid);
        HashMap<String, String> headers = new HashMap<>();
        headers.put("app", "hj");
        headers.put("ch", "vivo");
        headers.put("said", session.ai);
        headers.put("uk", uk);
        headers.put("vn", VN);
        headers.put("sign", sign);
        headers.put("User-Agent", UA);
        headers.put("vc", VC);
        headers.put("Accept-Encoding", "gzip");
        headers.put("Connection", "Keep-Alive");
        return headers;
    }

    public static String buildRewardAps(String input) {
        HxqNative.ensureLoaded();
        if (input == null) input = "";
        return HxqNative.rewardAps(input);
    }

    public static String buildRewardBody(String pid, String traceId, String scene, long ts) {
        HxqNative.ensureLoaded();
        return HxqNative.rewardBody(pid, traceId, scene);
    }

    public static String buildRslvQuerySign(HxqSession session, String p1, String p2, String p3, String p4, long ts) {
        HxqNative.ensureLoaded();
        String vn = VN;
        String devId = session.devId;
        String p4OrEmpty = (p4 == null) ? "" : p4;
        String uid = session.uid;
        int guard = 1;
        return HxqNative.rslvSign(vn, devId, p1, p2, p3, p4OrEmpty, ts, uid, guard);
    }

    public static String buildRslvQuerySignGuard(HxqSession session, String p1, String p2, String p3, String p4, long ts, String p7) {
        HxqNative.ensureLoaded();
        if (p4 != null && !p4.isEmpty() && !"0".equals(p7)) {
            String vn = VN;
            String devId = session.devId;
            String uid = session.uid;
            int guard = 0;
            return HxqNative.rslvSign(vn, devId, p1, p2, p3, p4, ts, uid, guard);
        }
        return buildRslvQuerySign(session, p1, p2, p3, p4, ts);
    }

    private static String buildSignPayload(HxqSession session, long ts) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("emu", 0);
        json.put("ou", 0);
        json.put("it", session.installTime);
        json.put("iit", session.installTime);
        json.put("bs", 0);
        json.put("uid", (Object) session.uid);
        json.put("pc", 0);
        json.put("tm", 0x51);
        json.put("d8m", (Object) "0,0,0,0,0,0,0,4");
        json.put("md", (Object) "V2238A");
        json.put("maker", (Object) "vivo");
        json.put("osv", (Object) "12");
        json.put("br", 0x5f);
        json.put("rpc", 0);
        json.put("scc", 0x2);
        json.put("plc", 0x6);
        json.put("toc", 0x13);
        json.put("tsc", 0xa);
        json.put("ts", ts);
        json.put("pa", 0x1);
        json.put("crec", 0);
        json.put("nw", 0x2);
        json.put("px", (Object) "0");
        json.put("isp", (Object) "");
        json.put("ai", (Object) session.ai);
        json.put("oa", (Object) session.oa);
        json.put("dpc", 0);
        json.put("dsc", 0);
        json.put("qpc", 0);
        json.put("apad", 0);
        json.put("pk", (Object) "com.babycloud.hanju");
        return json.toString();
    }

    public static HxqSession createSession() {
        HxqSession session = new HxqSession();
        session.uid = Crypto.randomKey(0x14, UID_CHARS);
        session.ai = Crypto.randomHex(0x10);
        session.oa = Crypto.randomHex(0x10);
        session.installTime = System.currentTimeMillis() - INSTALL_AGE_MS;
        session.devId = Crypto.randomKey(0x20, "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        return session;
    }

    public static String decodePlaySegment(String data, String key) {
        HxqNative.ensureLoaded();
        return HxqNative.decodeSegment(data, key);
    }

    public static JSONObject decryptResponseData(JSONObject response, String uk) {
        HxqNative.ensureLoaded();
        if (response == null) return null;
        String data = response.optString("data", "");
        if (data.length() <= 0x14) return null;
        String ts = String.valueOf(response.opt("ts"));
        String key = response.optString("key", "");
        String decrypted = HxqNative.decryptData(data, uk, ts, key);
        if (decrypted == null || decrypted.isEmpty()) return null;
        try {
            return new JSONObject(decrypted.trim());
        } catch (JSONException e) {
            return null;
        }
    }
}
