package com.github.catvod.spider;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Crypto;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AppDrama extends Spider {

    private static final String SEPARATOR;
    private static final String AES_KEY_CBC = "ed5fdsgucxumegqa";

    static {
        byte b = 0x7C;
        byte[] xorKey = new byte[]{0x50, (byte) 0x91, 0x07, 0x6B, (byte) 0xCE, (byte) 0xB2, 0x05, (byte) 0x85};
        b = (byte) (b ^ xorKey[0]);
        SEPARATOR = new String(new byte[]{b}, StandardCharsets.UTF_8);
    }

    private JSONObject extJson;
    private String[] dataKeys;
    private String host;
    private String publicKey;
    private final String cbcKey;
    private String rsaPublicKey;
    private String pkg;
    private String appName;
    private String decrypt;

    public AppDrama() {
        host = "";
        publicKey = "";
        cbcKey = AES_KEY_CBC;
        rsaPublicKey = "";
        extJson = new JSONObject();
        pkg = "";
        appName = "";
        decrypt = "0";
        dataKeys = new String[2];
    }

    private static byte[] writeVarint(long value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            if ((value & ~0x7FL) == 0) {
                out.write((int) value);
                return out.toByteArray();
            } else {
                out.write(((int) value & 0x7F) | 0x80);
                value >>>= 7;
            }
        }
    }

    private static long readVarint(byte[] data, int[] position) {
        long result = 0;
        int shift = 0;
        while (true) {
            if (position[0] >= data.length) break;
            byte b = data[position[0]++];
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        return result;
    }

    private static byte[] writeTag(int fieldNumber, int wireType) {
        return writeVarint(((long) fieldNumber << 3) | wireType);
    }

    private static int readTag(byte[] data, int[] position) {
        return (int) readVarint(data, position);
    }

    private static int getTagFieldNumber(int tag) {
        return tag >>> 3;
    }

    private static int getTagWireType(int tag) {
        return tag & 0x7;
    }

    private static byte[] writeStringField(int fieldNumber, String value) {
        if (value == null || value.isEmpty()) return new byte[0];
        byte[] strBytes = value.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(writeTag(fieldNumber, 2));
            out.write(writeVarint(strBytes.length));
            out.write(strBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    private static String readStringField(byte[] data, int[] position) {
        int length = (int) readVarint(data, position);
        if (position[0] + length > data.length) length = data.length - position[0];
        String result = new String(data, position[0], length, StandardCharsets.UTF_8);
        position[0] += length;
        return result;
    }

    private static byte[] writeBytesField(int fieldNumber, byte[] value) {
        if (value == null || value.length == 0) return new byte[0];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(writeTag(fieldNumber, 2));
            out.write(writeVarint(value.length));
            out.write(value);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    private static byte[] readBytesField(byte[] data, int[] position) {
        int length = (int) readVarint(data, position);
        if (position[0] + length > data.length) length = data.length - position[0];
        byte[] result = new byte[length];
        System.arraycopy(data, position[0], result, 0, length);
        position[0] += length;
        return result;
    }

    private static byte[] writeInt64Field(int fieldNumber, long value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(writeTag(fieldNumber, 0));
            out.write(writeVarint(value));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    private static long readInt64Field(byte[] data, int[] position) {
        return readVarint(data, position);
    }

    private static byte[] writeInt32Field(int fieldNumber, int value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(writeTag(fieldNumber, 0));
            out.write(writeVarint(value & 0xFFFFFFFFL));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    private static int readInt32Field(byte[] data, int[] position) {
        return (int) readVarint(data, position);
    }

    private static byte[] writeMessageField(int fieldNumber, byte[] messageBytes) {
        if (messageBytes == null || messageBytes.length == 0) return new byte[0];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(writeTag(fieldNumber, 2));
            out.write(writeVarint(messageBytes.length));
            out.write(messageBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    private static byte[] buildSecureRequest(String aesEncrypt1, String aesEncrypt2, String aesFakestr, long timestamp, String randomStr) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(writeStringField(1, aesEncrypt1));
            out.write(writeStringField(2, aesEncrypt2));
            out.write(writeStringField(3, aesFakestr));
            out.write(writeInt64Field(4, timestamp));
            out.write(writeStringField(5, randomStr));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    private static byte[] parseApiResultData(byte[] data) {
        // ApiResultProto: field 1=code(int64), field 2=msg(String), field 3=data(bytes)
        int[] pos = {0};
        while (pos[0] < data.length) {
            int tag = readTag(data, pos);
            int fieldNum = getTagFieldNumber(tag);
            int wireType = getTagWireType(tag);
            if (fieldNum == 3 && wireType == 2) {
                return readBytesField(data, pos);
            } else {
                skipField(data, pos, wireType);
            }
        }
        return new byte[0];
    }

    private static void skipField(byte[] data, int[] position, int wireType) {
        switch (wireType) {
            case 0:
                readVarint(data, position);
                break;
            case 1:
                position[0] += 8;
                break;
            case 2:
                int length = (int) readVarint(data, position);
                position[0] += length;
                break;
            case 3:
            case 4:
                break;
            case 5:
                position[0] += 4;
                break;
            default:
                break;
        }
    }

    private static class DramaCoverImageBean {
        String path;
        String thumbnailPath;

        static DramaCoverImageBean parseFrom(byte[] data) {
            DramaCoverImageBean bean = new DramaCoverImageBean();
            int[] pos = {0};
            while (pos[0] < data.length) {
                int tag = readTag(data, pos);
                int fieldNum = getTagFieldNumber(tag);
                int wireType = getTagWireType(tag);
                if (fieldNum == 1 && wireType == 2) {
                    bean.path = readStringField(data, pos);
                } else if (fieldNum == 2 && wireType == 2) {
                    bean.thumbnailPath = readStringField(data, pos);
                } else {
                    skipField(data, pos, wireType);
                }
            }
            return bean;
        }
    }

    private static class DramaBean {
        // DramaProto$DramaBean: field 1=area, 2=coverImage(msg), 3=id(int32),
        //   4=brief, 5=name, 6=stars, 7=director, 8=type, 9=cateType2, 10=updateTime,
        //   11=vodPubdate, 12=actor, 13=remark, 14=year, 15=clazz
        int id;
        String name;
        DramaCoverImageBean coverImage;
        String remark;

        static DramaBean parseFrom(byte[] data) {
            DramaBean bean = new DramaBean();
            int[] pos = {0};
            while (pos[0] < data.length) {
                int tag = readTag(data, pos);
                int fieldNum = getTagFieldNumber(tag);
                int wireType = getTagWireType(tag);
                if (fieldNum == 2 && wireType == 2) {
                    byte[] msgData = readBytesField(data, pos);
                    bean.coverImage = DramaCoverImageBean.parseFrom(msgData);
                } else if (fieldNum == 3 && wireType == 0) {
                    bean.id = readInt32Field(data, pos);
                } else if (fieldNum == 5 && wireType == 2) {
                    bean.name = readStringField(data, pos);
                } else if (fieldNum == 13 && wireType == 2) {
                    bean.remark = readStringField(data, pos);
                } else {
                    skipField(data, pos, wireType);
                }
            }
            return bean;
        }
    }

    private static class DramaBeanPage {
        List<DramaBean> dramaBeanList = new ArrayList<>();

        static DramaBeanPage parseFrom(byte[] data) {
            DramaBeanPage page = new DramaBeanPage();
            int[] pos = {0};
            while (pos[0] < data.length) {
                int tag = readTag(data, pos);
                int fieldNum = getTagFieldNumber(tag);
                int wireType = getTagWireType(tag);
                if (fieldNum == 1 && wireType == 2) {
                    byte[] msgData = readBytesField(data, pos);
                    page.dramaBeanList.add(DramaBean.parseFrom(msgData));
                } else {
                    skipField(data, pos, wireType);
                }
            }
            return page;
        }
    }

    private static class DramaVideoBean {
        // DramaVideoProto$DramaVideoBean: field 1=id, 2=title, 3=titleOld, 4=path,
        //   5=size, 6=time, 7=format, 8=type, 9=source, 10=sourceCn, 11=sourceOld,
        //   12=season, 13=episode, 14=isVip, 15=dramaId, 16=priority, 17=classType, 18=sort
        String sourceCn;
        String source;
        String path;
        String title;

        static DramaVideoBean parseFrom(byte[] data) {
            DramaVideoBean bean = new DramaVideoBean();
            int[] pos = {0};
            while (pos[0] < data.length) {
                int tag = readTag(data, pos);
                int fieldNum = getTagFieldNumber(tag);
                int wireType = getTagWireType(tag);
                if (fieldNum == 2 && wireType == 2) {
                    bean.title = readStringField(data, pos);
                } else if (fieldNum == 4 && wireType == 2) {
                    bean.path = readStringField(data, pos);
                } else if (fieldNum == 9 && wireType == 2) {
                    bean.source = readStringField(data, pos);
                } else if (fieldNum == 10 && wireType == 2) {
                    bean.sourceCn = readStringField(data, pos);
                } else {
                    skipField(data, pos, wireType);
                }
            }
            return bean;
        }
    }

    private static class DramaDetailBean {
        // DramaDetailProto$DramaDetailBean: 1=area, 2=coverImage(msg), 3=createTime, 4=id,
        //   5=favoriteId, 6=intro, 7=brief, 8=like, 9=name, 10=stars, 11=starsCount,
        //   12=director, 13=tag, 14=type, 15=cateType2, 16=cateType1, 17=updateTime,
        //   18=year, 19=hits, 20=hitsDay, 21=hitsWeek, 22=hitsMonth, 23=keyword,
        //   24=config, 25=actor, 26=remark, 27=isEnd, 28=vodPubdate,
        //   29=videos(repeated msg), 30=downloads, 31=userLikes, 32=favorite,
        //   33=season, 34=serial, 35=vip, 36=hot
        String name;
        DramaCoverImageBean coverImage;
        String actor;
        String tag;
        String area;
        int year;
        String remark;
        String intro;
        List<DramaVideoBean> videosList = new ArrayList<>();

        static DramaDetailBean parseFrom(byte[] data) {
            DramaDetailBean bean = new DramaDetailBean();
            int[] pos = {0};
            while (pos[0] < data.length) {
                int tag = readTag(data, pos);
                int fieldNum = getTagFieldNumber(tag);
                int wireType = getTagWireType(tag);
                if (fieldNum == 1 && wireType == 2) {
                    bean.area = readStringField(data, pos);
                } else if (fieldNum == 2 && wireType == 2) {
                    byte[] msgData = readBytesField(data, pos);
                    bean.coverImage = DramaCoverImageBean.parseFrom(msgData);
                } else if (fieldNum == 6 && wireType == 2) {
                    bean.intro = readStringField(data, pos);
                } else if (fieldNum == 9 && wireType == 2) {
                    bean.name = readStringField(data, pos);
                } else if (fieldNum == 13 && wireType == 2) {
                    bean.tag = readStringField(data, pos);
                } else if (fieldNum == 18 && wireType == 0) {
                    bean.year = readInt32Field(data, pos);
                } else if (fieldNum == 25 && wireType == 2) {
                    bean.actor = readStringField(data, pos);
                } else if (fieldNum == 26 && wireType == 2) {
                    bean.remark = readStringField(data, pos);
                } else if (fieldNum == 29 && wireType == 2) {
                    byte[] msgData = readBytesField(data, pos);
                    bean.videosList.add(DramaVideoBean.parseFrom(msgData));
                } else {
                    skipField(data, pos, wireType);
                }
            }
            return bean;
        }
    }

    private static class RSARequest {
        // RSARequestProto$RSARequest: 1=timestamp(int64), 2=sign(String),
        //   3=fake1(String), 4=randomStr(String), 5=fake2(String)
        long timestamp;
        String randomStr;
        String sign;
        String fake1;
        String fake2;

        byte[] toByteArray() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                out.write(writeInt64Field(1, timestamp));
                out.write(writeStringField(2, sign));
                out.write(writeStringField(3, fake1));
                out.write(writeStringField(4, randomStr));
                out.write(writeStringField(5, fake2));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return out.toByteArray();
        }
    }

    private static class RSAPublic {
        String str2;
        String str3;
        String str4;
        String str5;

        static RSAPublic parseFrom(byte[] data) {
            RSAPublic bean = new RSAPublic();
            int[] pos = {0};
            while (pos[0] < data.length) {
                int tag = readTag(data, pos);
                int fieldNum = getTagFieldNumber(tag);
                int wireType = getTagWireType(tag);
                if (fieldNum == 2 && wireType == 2) {
                    bean.str2 = readStringField(data, pos);
                } else if (fieldNum == 3 && wireType == 2) {
                    bean.str3 = readStringField(data, pos);
                } else if (fieldNum == 4 && wireType == 2) {
                    bean.str4 = readStringField(data, pos);
                } else if (fieldNum == 5 && wireType == 2) {
                    bean.str5 = readStringField(data, pos);
                } else {
                    skipField(data, pos, wireType);
                }
            }
            return bean;
        }
    }

    private static class ParsePlayUrlBean {
        // ParsePlayUrlProto$ParsePlayUrlBean: 1=playUrl(String), 2=fitMode(int32),
        //   3=mirrorMode(int32), 4=timeout(int32), 5=direct(int32),
        //   6=headers(map<string,string>), 7=androidPlayCore(String),
        //   8=iosPlayCore(String), 9=msg(String)
        // headers is a proto map<string,string>: each entry is a sub-message
        // with field 1=key, field 2=value, repeated under field 6.
        String playUrl;
        Map<String, String> headersMap = new HashMap<>();

        static ParsePlayUrlBean parseFrom(byte[] data) {
            ParsePlayUrlBean bean = new ParsePlayUrlBean();
            int[] pos = {0};
            while (pos[0] < data.length) {
                int tag = readTag(data, pos);
                int fieldNum = getTagFieldNumber(tag);
                int wireType = getTagWireType(tag);
                if (fieldNum == 1 && wireType == 2) {
                    bean.playUrl = readStringField(data, pos);
                } else if (fieldNum == 6 && wireType == 2) {
                    byte[] entryData = readBytesField(data, pos);
                    parseMapEntry(entryData, bean.headersMap);
                } else {
                    skipField(data, pos, wireType);
                }
            }
            return bean;
        }

        private static void parseMapEntry(byte[] data, Map<String, String> map) {
            int[] pos = {0};
            String key = null;
            String value = null;
            while (pos[0] < data.length) {
                int tag = readTag(data, pos);
                int fieldNum = getTagFieldNumber(tag);
                int wireType = getTagWireType(tag);
                if (fieldNum == 1 && wireType == 2) {
                    key = readStringField(data, pos);
                } else if (fieldNum == 2 && wireType == 2) {
                    value = readStringField(data, pos);
                } else {
                    skipField(data, pos, wireType);
                }
            }
            if (key != null && value != null) {
                map.put(key, value);
            }
        }
    }

    private static String aesEncryptECB(String data, String key) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    private static String aesDecryptECB(String data, String key) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.doFinal(Base64.decode(data, Base64.DEFAULT));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static String aesEncryptCBC(String data, String key, String iv) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : encrypted) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static String aesDecryptCBC(String data, String key, String iv) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            byte[] decrypted = cipher.doFinal(hexToBytes(data));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static String rsaEncrypt(String data, String publicKeyStr) {
        try {
            // Crypto.rsaEncrypt uses Base64.DEFAULT but smali x0 uses Base64.NO_WRAP (flag 2)
            String result = Crypto.rsaEncrypt(data, publicKeyStr);
            byte[] decoded = Base64.decode(result, Base64.DEFAULT);
            return Base64.encodeToString(decoded, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    private static String randomString(int length) {
        // 对应 smali merge/C/a.r0(int)：前 length-1 位从 62 字符表随机取，末尾固定追加 '='
        String chars = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length - 1; i++) {
            sb.append(chars.charAt(rnd.nextInt(62)));
        }
        sb.append('=');
        return sb.toString();
    }

    private byte[] buildSecureRequest(HashMap<String, String> params) {
        long timestamp = System.currentTimeMillis();
        String randomStr8 = randomString(8);
        String randomStr20 = randomString(20);

        StringBuilder sb = new StringBuilder();
        Iterator<String> keys = params.keySet().iterator();
        while (keys.hasNext()) {
            String key = keys.next();
            String value = params.get(key);
            if (value == null || value.isEmpty()) continue;
            if (sb.length() > 0) sb.append('&');
            sb.append(key).append('=').append(value);
        }

        String toEncrypt = sb.toString() + timestamp;
        String encrypted = aesEncryptECB(toEncrypt, dataKeys[0]);

        String mixed = randomStr8 + encrypted;
        String aesEncrypt1 = mixed.substring(0, 20);
        String aesEncrypt2 = mixed.substring(20);

        return buildSecureRequest(aesEncrypt1, aesEncrypt2, randomStr20, timestamp, randomStr8);
    }

    private JSONObject buildDeviceInfo() {
        JSONObject json = new JSONObject();
        try {
            String uuid = java.util.UUID.randomUUID().toString().replace("-", "").toUpperCase();

            json.put("country", "CN");
            json.put("vName", extJson.optString("version"));
            json.put("cpuId", "MT6893Z%2FCZA");
            json.put("young", 0);
            json.put("facturer", Build.MANUFACTURER);
            json.put("pkg", pkg);
            json.put("uuid", uuid);
            json.put("resolution", "1080x2272");
            json.put("mac", "02%3A00%3A00%3A00%3A00%3A00");
            json.put("abid", "397");
            json.put("model", Build.MODEL);
            json.put("plat", "android");
            json.put("udid", uuid);
            json.put("dpi", "440");
            json.put("net", "1");
            json.put("lang", "zh");
            json.put("brand", Build.BRAND);
            json.put("density", "2.75");
            json.put("appName", appName);
            json.put("cpu", "arm64-v8a");
            json.put("chid", "10000");
            json.put("carrier", "%E8%81%94%E9%80%9A");
            json.put("_vOsCode", Build.VERSION.SDK_INT);
            json.put("vOs", Build.VERSION.RELEASE);
            json.put("v", 1);
            json.put("tenantId", "");
            String version = extJson.optString("version").replace(".", "");
            json.put("vApp", version);
            json.put("device", 0);

            String androidId = Settings.Secure.getString(Init.context().getContentResolver(), "android_id");
            json.put("androidID", androidId);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return json;
    }

    private HashMap<String, String> getPublicParams() {
        HashMap<String, String> headers = new HashMap<>();
        try {
            String deviceJson = buildDeviceInfo().toString();
            String encrypted = aesEncryptCBC(deviceJson, cbcKey, cbcKey);

            JSONObject paramsJson = new JSONObject();
            paramsJson.put("paramsData", encrypted);

            headers.put("User-Agent", "okhttp/3.12.1");
            headers.put("Accept", "application/json");
            headers.put("Content-Type", "application/json; charset=utf-8");
            headers.put("publicParams", paramsJson.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return headers;
    }

    private HashMap<String, String> getProtoHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        try {
            String rsaKey;
            if (TextUtils.isEmpty(rsaPublicKey)) {
                rsaKey = publicKey;
            } else {
                rsaKey = rsaPublicKey;
            }

            JSONObject deviceJson = buildDeviceInfo();
            long timestamp = System.currentTimeMillis();
            String randomStr16 = randomString(16);

            StringBuilder signBuilder = new StringBuilder();
            signBuilder.append(timestamp).append(randomStr16);
            signBuilder.append(deviceJson.optString("vApp", "3019"));
            String rsaSign = rsaEncrypt(signBuilder.toString(), rsaKey);

            StringBuilder aesBuilder = new StringBuilder();
            aesBuilder.append(timestamp).append(randomStr16);
            String aesSign = aesEncryptECB(aesBuilder.toString(), dataKeys[1]);

            JSONObject paramsJson = new JSONObject();
            paramsJson.put("sig", rsaSign);
            paramsJson.put("random_str", randomStr16);
            paramsJson.put("timestamp", timestamp);

            String sig2 = aesSign.substring(0, 8);
            String sig3 = aesSign.substring(8);
            paramsJson.put("sig2", sig2);
            paramsJson.put("sig3", sig3);

            String encrypted = aesEncryptCBC(paramsJson.toString(), cbcKey, cbcKey);

            JSONObject outerJson = new JSONObject();
            outerJson.put("paramsData", encrypted);

            headers.put("User-Agent", "okhttp/3.12.1");
            headers.put("Accept", "application/x-protobuf");
            headers.put("Content-Type", "application/x-protobuf");
            headers.put("publicParams", outerJson.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return headers;
    }

    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        url = url.trim();
        url = url.replace("&amp;", "&");
        if (url.startsWith("//")) {
            return "https:" + url;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        if (url.startsWith("/")) {
            return host + url;
        }
        if (!url.contains("://") && !TextUtils.isEmpty(host)) {
            return host + "/" + url;
        }
        return url;
    }

    private String getCoverImageUrl(DramaCoverImageBean cover) {
        if (cover == null) return "";
        if (!TextUtils.isEmpty(cover.thumbnailPath)) {
            return fixUrl(cover.thumbnailPath);
        }
        return fixUrl(cover.path);
    }

    private byte[] postProto(String url, byte[] body, Map<String, String> headers) {
        if (url == null || !url.startsWith("http")) return new byte[0];
        try {
            OkHttpClient client = OkHttp.client();
            Request.Builder builder = new Request.Builder().url(url);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    builder.addHeader(entry.getKey(), entry.getValue());
                }
            }
            RequestBody requestBody = RequestBody.create(body, MediaType.parse("application/x-protobuf"));
            builder.post(requestBody);
            Response response = client.newCall(builder.build()).execute();
            if (response.body() == null) {
                return new byte[0];
            }
            return response.body().bytes();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new byte[0];
    }

    private String postJson(String url, Map<String, String> headers) {
        // 对应 smali merge/C/a.P(String, HashMap)
        if (url == null || !url.startsWith("http")) return "";
        try {
            if (headers == null) headers = new HashMap<>();
            return OkHttp.string(url, null, headers);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private ArrayList<Vod> parseDramaList(byte[] data) {
        ArrayList<Vod> list = new ArrayList<>();
        try {
            byte[] apiResultData = parseApiResultData(data);
            if (apiResultData.length == 0) return list;

            DramaBeanPage page = DramaBeanPage.parseFrom(apiResultData);
            for (DramaBean bean : page.dramaBeanList) {
                String pic = getCoverImageUrl(bean.coverImage);
                Vod vod = new Vod(String.valueOf(bean.id), bean.name, pic, bean.remark);
                list.add(vod);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public void init(Context context, String extend) {
        try {
            super.init(context, extend);
            extJson = new JSONObject(extend);
            host = extJson.optString("host");
            publicKey = extJson.optString("publicKey");
            pkg = extJson.optString("pkg");
            appName = extJson.optString("appName");
            decrypt = extJson.optString("decrypt", "0");

            dataKeys = new String[2];
            dataKeys[0] = extJson.optString("dataKey");
            dataKeys[1] = extJson.optString("dataIv");

            String site = extJson.optString("site");
            if (!TextUtils.isEmpty(site)) {
                try {
                    String siteResponse = OkHttp.string(site);
                    JSONObject siteJson = new JSONObject(siteResponse);
                    String domain = siteJson.optString("domain");
                    if (!TextUtils.isEmpty(domain)) {
                        host = domain;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            initRsaPublicKey();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initRsaPublicKey() {
        try {
            long timestamp = System.currentTimeMillis();
            String randomStr16 = randomString(16);

            RSARequest rsaRequest = new RSARequest();
            rsaRequest.timestamp = timestamp;
            rsaRequest.randomStr = randomStr16;

            StringBuilder signBuilder = new StringBuilder();
            signBuilder.append(timestamp).append(randomStr16);
            String sign = rsaEncrypt(signBuilder.toString(), publicKey);
            rsaRequest.sign = sign;
            rsaRequest.fake1 = randomString(16);
            rsaRequest.fake2 = randomString(16);

            String url = host + "/api/v5/find/app/zone";
            byte[] response = postProto(url, rsaRequest.toByteArray(), getProtoHeaders());

            byte[] apiData = parseApiResultData(response);
            if (apiData.length > 0) {
                RSAPublic rsaPublic = RSAPublic.parseFrom(apiData);
                StringBuilder sb = new StringBuilder();
                sb.append(rsaPublic.str2);
                sb.append(rsaPublic.str3);
                sb.append(rsaPublic.str4);
                sb.append(rsaPublic.str5);
                rsaPublicKey = sb.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        try {
            String url = host + "/api/v3/drama/getCategory?orderBy=type_id";
            String response = postJson(url, getPublicParams());
            JSONObject json = new JSONObject(response);
            JSONArray dataArray = json.optJSONArray("data");
            if (dataArray == null) {
                return Result.string(classes, filters);
            }

            List<String> filterKeys = Arrays.asList("class", "lang", "area", "year", "extend_sort");

            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject item = dataArray.getJSONObject(i);
                String name = item.optString("name");
                if ("公告".equals(name)) continue;

                String id = item.optString("id");
                classes.add(new Class(id, name));

                String converUrl = item.optString("converUrl");
                if (TextUtils.isEmpty(converUrl)) continue;

                JSONObject converJson = new JSONObject(converUrl);
                List<Filter> filterList = new ArrayList<>();

                for (String key : filterKeys) {
                    if (!converJson.has(key)) continue;
                    String valuesStr = converJson.optString(key);
                    if (TextUtils.isEmpty(valuesStr)) continue;

                    List<Filter.Value> valueList = new ArrayList<>();
                    String[] values = valuesStr.split(SEPARATOR);
                    for (String v : values) {
                        if (TextUtils.isEmpty(v)) continue;
                        valueList.add(new Filter.Value(v, v));
                    }

                    if (!valueList.isEmpty()) {
                        filterList.add(new Filter(key, key, valueList));
                    }
                }

                if (!filterList.isEmpty()) {
                    filters.put(id, filterList);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.string(classes, filters);
    }

    @Override
    public String homeVideoContent() {
        List<Vod> list = new ArrayList<>();
        try {
            String url = host + "/api/ex/v3/security/tag/list";
            String response = postJson(url, getPublicParams());
            JSONObject json = new JSONObject(response);
            String dataStr = json.optString("data");

            if (TextUtils.isEmpty(dataStr)) {
                return Result.string(list);
            }

            if (!"0".equals(decrypt)) {
                dataStr = aesDecryptECB(dataStr, dataKeys[0]);
                dataStr = aesDecryptECB(dataStr, dataKeys[1]);
            }

            JSONArray dataArray = new JSONArray(dataStr);
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject tagItem = dataArray.getJSONObject(i);
                JSONArray sections = tagItem.optJSONArray("sections");
                if (sections == null) continue;

                for (int j = 0; j < sections.length(); j++) {
                    JSONObject section = sections.getJSONObject(j);
                    JSONArray vodList = section.optJSONArray("vodList");
                    if (vodList == null) continue;

                    for (int k = 0; k < vodList.length(); k++) {
                        JSONObject vodItem = vodList.getJSONObject(k);
                        String pic = "";
                        JSONObject coverImage = vodItem.optJSONObject("coverImage");
                        if (coverImage != null) {
                            // 与 smali homeVideoContent 一致：优先 path，path 为空才用 thumbnailPath
                            String path = coverImage.optString("path");
                            String thumbnailPath = coverImage.optString("thumbnailPath");
                            if (!TextUtils.isEmpty(path)) {
                                pic = fixUrl(path);
                            } else if (!TextUtils.isEmpty(thumbnailPath)) {
                                pic = fixUrl(thumbnailPath);
                            }
                        }
                        Vod vod = new Vod(
                                vodItem.optString("id"),
                                vodItem.optString("name"),
                                pic,
                                vodItem.optString("remark")
                        );
                        list.add(vod);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            HashMap<String, String> params = new HashMap<>();
            params.put("pagesize", "21");
            params.put("typeId1", tid);
            params.put("page", pg);

            if (extend != null) {
                if (extend.containsKey("extend_sort")) params.put("vodOrderBy", extend.get("extend_sort"));
                if (extend.containsKey("area")) params.put("vodArea", extend.get("area"));
                if (extend.containsKey("lang")) params.put("vodLang", extend.get("lang"));
                if (extend.containsKey("class")) params.put("vodClass", extend.get("class"));
                if (extend.containsKey("year")) params.put("vodYear", extend.get("year"));
            }

            String url = host + "/api/proto/v5/drama/category";
            byte[] requestBody = buildSecureRequest(params);
            byte[] response = postProto(url, requestBody, getProtoHeaders());

            ArrayList<Vod> list = parseDramaList(response);

            int page;
            try {
                page = Integer.parseInt(pg);
            } catch (Exception e) {
                page = 1;
            }

            return Result.get().vod(list).page(page, 0, 0, 0).string();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.string(new ArrayList<Vod>());
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String id = ids.get(0);

            HashMap<String, String> params = new HashMap<>();
            params.put("id", id);

            String url = host + "/api/proto/v5/drama/getDetail";
            byte[] requestBody = buildSecureRequest(params);
            byte[] response = postProto(url, requestBody, getProtoHeaders());

            byte[] apiData = parseApiResultData(response);
            if (apiData.length == 0) return Result.error("详情获取失败");

            DramaDetailBean detail = DramaDetailBean.parseFrom(apiData);

            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(detail.name);
            if (detail.coverImage != null) {
                String pic = getCoverImageUrl(detail.coverImage);
                if (!TextUtils.isEmpty(pic)) {
                    vod.setVodPic(pic);
                }
            }
            vod.setVodActor(detail.actor);
            vod.setVodDirector("");
            vod.setVodTag(detail.tag);
            vod.setVodArea(detail.area);
            vod.setVodYear(String.valueOf(detail.year));
            vod.setVodRemarks(detail.remark);
            vod.setVodContent(detail.intro);

            LinkedHashMap<String, List<String>> playMap = new LinkedHashMap<>();
            for (DramaVideoBean video : detail.videosList) {
                String source = video.sourceCn;
                if (TextUtils.isEmpty(source)) {
                    if (TextUtils.isEmpty(appName)) {
                        source = "线路";
                    } else {
                        source = appName;
                    }
                }

                if (!playMap.containsKey(source)) {
                    playMap.put(source, new ArrayList<String>());
                }

                String playUrl = video.path;
                if (!playUrl.matches("(?i).*\\.(mp4|m3u8|flv|mkv|avi|ts|mov|mpd|m4a|wmv)(\\?.*)?$")) {
                    JSONObject playJson = new JSONObject();
                    playJson.put("vodPlayFrom", video.source);
                    playJson.put("playUrl", playUrl);
                    byte[] jsonBytes = playJson.toString().getBytes(StandardCharsets.UTF_8);
                    playUrl = Base64.encodeToString(jsonBytes, Base64.NO_WRAP);
                }

                String playStr = video.title + "$" + playUrl;
                playMap.get(source).add(playStr);
            }

            List<String> playFrom = new ArrayList<>();
            List<String> playUrl = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : playMap.entrySet()) {
                playFrom.add(entry.getKey());
                playUrl.add(TextUtils.join("#", entry.getValue()));
            }

            vod.setVodPlayFrom(TextUtils.join("$$$", playFrom));
            vod.setVodPlayUrl(TextUtils.join("$$$", playUrl));

            return Result.string(vod);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("详情获取失败");
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            if (id.matches("(?i).*\\.(mp4|m3u8|flv|mkv|avi|ts|mov|mpd|m4a|wmv)(\\?.*)?$")) {
                return Result.get().url(id).string();
            }

            JSONObject paramJson = new JSONObject();
            byte[] decoded = Base64.decode(id, Base64.DEFAULT);
            String jsonStr = new String(decoded, StandardCharsets.UTF_8);
            JSONObject inputJson = new JSONObject(jsonStr);
            Iterator<String> keys = inputJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                paramJson.put(key, inputJson.optString(key));
            }

            HashMap<String, String> params = new HashMap<>();
            Iterator<String> paramKeys = paramJson.keys();
            while (paramKeys.hasNext()) {
                String key = paramKeys.next();
                params.put(key, paramJson.optString(key));
            }

            String url = host + "/api/proto/v5/videoUsableUrl";
            byte[] requestBody = buildSecureRequest(params);
            byte[] response = postProto(url, requestBody, getProtoHeaders());

            byte[] apiData = parseApiResultData(response);
            if (apiData.length == 0) return Result.error("播放链接解析失败");

            ParsePlayUrlBean playUrlBean = ParsePlayUrlBean.parseFrom(apiData);

            HashMap<String, String> headers = new HashMap<>();
            if (playUrlBean.headersMap != null && !playUrlBean.headersMap.isEmpty()) {
                headers.putAll(playUrlBean.headersMap);
            }

            return Result.get().url(playUrlBean.playUrl).header(headers).string();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("播放链接解析失败");
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        try {
            HashMap<String, String> params = new HashMap<>();
            params.put("searchKeys", key);
            params.put("page", pg);
            params.put("pagesize", "21");

            String url = host + "/api/proto/v5/drama/search";
            byte[] requestBody = buildSecureRequest(params);
            byte[] response = postProto(url, requestBody, getProtoHeaders());

            ArrayList<Vod> list = parseDramaList(response);
            return Result.string(list);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.string(new ArrayList<Vod>());
        }
    }
}
