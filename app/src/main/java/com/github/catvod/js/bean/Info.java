package com.github.catvod.js.bean;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.js.utils.JSUtil;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class Info {

    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String SECRET_KEY_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int KEY_LENGTH = 256;
    private static final int ITERATION_COUNT = 10000;
    private static final int IV_LENGTH = 16;
    
    private static String deviceSecret = null;

    private Context context;
    private Spider spider;
    private String key;
    private String name;
    private String api;
    private String ext;
    private String group;
    private String search;
    private String categories;
    private String timeout;
    private String encoding;
    private String proxy;
    private boolean play;
    private boolean danmaku;
    private boolean lazy;
    private String type;
    private String user;
    private String password;
    private String cookie;
    private String token;
    private String referer;
    private String userAgent;
    private Map<String, String> headers;
    private Cache cache;

    public Info(Context context, Spider spider) {
        this.context = context;
        this.spider = spider;
        this.cache = new Cache();
        this.headers = new HashMap<>();
        initDeviceSecret(context);
    }

    public Info(Context context, Spider spider, JSONObject object) {
        this(context, spider);
        init(object);
    }

    private static synchronized void initDeviceSecret(Context context) {
        if (deviceSecret != null) return;
        try {
            String androidId = android.provider.Settings.Secure.getString(
                    context.getContentResolver(), 
                    android.provider.Settings.Secure.ANDROID_ID
            );
            if (androidId == null || androidId.length() < 16) {
                androidId = "default_secret_key_" + System.currentTimeMillis();
            }
            deviceSecret = androidId;
        } catch (Exception e) {
            deviceSecret = "fallback_secret_key_" + context.getPackageName();
        }
    }

    private static String encrypt(String data) {
        if (data == null || data.isEmpty()) return data;
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            
            SecretKey secretKey = generateKey(deviceSecret);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));
            
            byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return data;
        }
    }

    private static String decrypt(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) return encryptedData;
        try {
            byte[] combined = Base64.decode(encryptedData, Base64.NO_WRAP);
            if (combined.length < IV_LENGTH) return encryptedData;
            
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);
            
            SecretKey secretKey = generateKey(deviceSecret);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
            
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return encryptedData;
        }
    }

    private static SecretKey generateKey(String password) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(SECRET_KEY_ALGORITHM);
        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                password.getBytes(StandardCharsets.UTF_8),
                ITERATION_COUNT,
                KEY_LENGTH
        );
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    public Context getContext() {
        return context;
    }

    public Spider getSpider() {
        return spider;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getApi() {
        return api;
    }

    public void setApi(String api) {
        this.api = api;
    }

    public String getExt() {
        return ext;
    }

    public void setExt(String ext) {
        this.ext = ext;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public String getCategories() {
        return categories;
    }

    public void setCategories(String categories) {
        this.categories = categories;
    }

    public String getTimeout() {
        return timeout;
    }

    public void setTimeout(String timeout) {
        this.timeout = timeout;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getProxy() {
        return proxy;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    public boolean isPlay() {
        return play;
    }

    public void setPlay(boolean play) {
        this.play = play;
    }

    public boolean isDanmaku() {
        return danmaku;
    }

    public void setDanmaku(boolean danmaku) {
        this.danmaku = danmaku;
    }

    public boolean isLazy() {
        return lazy;
    }

    public void setLazy(boolean lazy) {
        this.lazy = lazy;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDecryptedPassword() {
        return decrypt(password);
    }

    public void setEncryptedPassword(String password) {
        this.password = encrypt(password);
    }

    public String getCookie() {
        return cookie;
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    public String getDecryptedCookie() {
        return decrypt(cookie);
    }

    public void setEncryptedCookie(String cookie) {
        this.cookie = encrypt(cookie);
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getDecryptedToken() {
        return decrypt(token);
    }

    public void setEncryptedToken(String token) {
        this.token = encrypt(token);
    }

    public String getReferer() {
        return referer;
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public void addHeader(String key, String value) {
        this.headers.put(key, value);
    }

    public void removeHeader(String key) {
        this.headers.remove(key);
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public boolean hasKey() {
        return key != null && !key.isEmpty();
    }

    public boolean hasName() {
        return name != null && !name.isEmpty();
    }

    public boolean hasApi() {
        return api != null && !api.isEmpty();
    }

    public boolean hasExt() {
        return ext != null && !ext.isEmpty();
    }

    public boolean hasGroup() {
        return group != null && !group.isEmpty();
    }

    public boolean hasSearch() {
        return search != null && !search.isEmpty();
    }

    public boolean hasCategories() {
        return categories != null && !categories.isEmpty();
    }

    public boolean hasTimeout() {
        return timeout != null && !timeout.isEmpty();
    }

    public boolean hasEncoding() {
        return encoding != null && !encoding.isEmpty();
    }

    public boolean hasProxy() {
        return proxy != null && !proxy.isEmpty();
    }

    public boolean hasType() {
        return type != null && !type.isEmpty();
    }

    public boolean hasUser() {
        return user != null && !user.isEmpty();
    }

    public boolean hasPassword() {
        return password != null && !password.isEmpty();
    }

    public boolean hasCookie() {
        return cookie != null && !cookie.isEmpty();
    }

    public boolean hasToken() {
        return token != null && !token.isEmpty();
    }

    public boolean hasReferer() {
        return referer != null && !referer.isEmpty();
    }

    public boolean hasUserAgent() {
        return userAgent != null && !userAgent.isEmpty();
    }

    public boolean hasHeaders() {
        return headers != null && !headers.isEmpty();
    }

    public boolean isConfigured() {
        return hasKey() && hasApi();
    }

    public boolean isValid() {
        return hasApi() && new File(api).exists();
    }

    public boolean isRemote() {
        return hasApi() && api.startsWith("http");
    }

    public boolean isLocal() {
        return hasApi() && !isRemote();
    }

    public boolean requiresAuth() {
        return hasUser() || hasPassword() || hasToken();
    }

    public void init(JSONObject object) {
        try {
            key = object.optString("key");
            name = object.optString("name");
            api = object.optString("api");
            ext = object.optString("ext");
            group = object.optString("group");
            search = object.optString("search");
            categories = object.optString("categories");
            timeout = object.optString("timeout");
            encoding = object.optString("encoding");
            proxy = object.optString("proxy");
            play = object.optBoolean("play", false);
            danmaku = object.optBoolean("danmaku", false);
            lazy = object.optBoolean("lazy", false);
            type = object.optString("type");
            user = object.optString("user");
            
            String pwd = object.optString("password");
            password = encrypt(pwd);
            
            String ck = object.optString("cookie");
            cookie = encrypt(ck);
            
            String tk = object.optString("token");
            token = encrypt(tk);
            
            referer = object.optString("referer");
            userAgent = object.optString("userAgent");
            
            if (hasProxy()) {
                Uri uri = Uri.parse(proxy);
                if (uri.getHost() != null) {
                    JSUtil.setProxy(uri.getHost(), uri.getPort());
                }
            }
            
            SpiderDebug.log("Info initialized: " + toString());
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    @Override
    public String toString() {
        return "Info{" +
                "key='" + key + '\'' +
                ", name='" + name + '\'' +
                ", api='" + api + '\'' +
                ", type='" + type + '\'' +
                ", group='" + group + '\'' +
                ", play=" + play +
                ", danmaku=" + danmaku +
                ", lazy=" + lazy +
                ", hasPassword=" + hasPassword() +
                ", hasCookie=" + hasCookie() +
                ", hasToken=" + hasToken() +
                '}';
    }
}