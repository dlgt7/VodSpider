package com.github.catvod.spider;

import android.os.Environment;
import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Proxy {

    private static Method method;
    private static int port;
    private static String host;
    private static boolean enabled;
    
    private static final List<String> ALLOWED_PATHS = new ArrayList<>();
    private static final List<String> FORBIDDEN_PATHS = new ArrayList<>();
    
    static {
        ALLOWED_PATHS.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getAbsolutePath());
        ALLOWED_PATHS.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
        ALLOWED_PATHS.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath());
        ALLOWED_PATHS.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath());
        ALLOWED_PATHS.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath());
        
        FORBIDDEN_PATHS.add("/system");
        FORBIDDEN_PATHS.add("/data/data");
        FORBIDDEN_PATHS.add("/data/app");
        FORBIDDEN_PATHS.add("/proc");
        FORBIDDEN_PATHS.add("/dev");
        FORBIDDEN_PATHS.add("/sys");
        FORBIDDEN_PATHS.add("/root");
        FORBIDDEN_PATHS.add("/vendor");
        FORBIDDEN_PATHS.add("/cache");
    }

    public static Object[] proxy(Map<String, String> params) {
        if (params == null) return null;
        
        String action = params.get("do");
        if (TextUtils.isEmpty(action)) return null;
        
        try {
            switch (action) {
                case "ck":
                    return check();
                case "file":
                    return file(params);
                case "text":
                    return text(params);
                case "json":
                    return json(params);
                case "image":
                    return image(params);
                case "video":
                    return video(params);
                case "audio":
                    return audio(params);
                default:
                    return null;
            }
        } catch (SecurityException e) {
            SpiderDebug.log("Proxy security violation: " + e.getMessage());
            return error(403, "Access denied");
        }
    }

    private static Object[] check() {
        return new Object[]{200, "text/plain; charset=utf-8", new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8))};
    }

    private static Object[] file(Map<String, String> params) {
        String path = params.get("path");
        if (TextUtils.isEmpty(path)) return error(400, "Path is empty");
        
        if (!isPathAllowed(path)) {
            SpiderDebug.log("Proxy: access denied to path: " + path);
            return error(403, "Access denied");
        }
        
        File file = new File(path);
        if (!file.exists()) return error(404, "File not found");
        if (!file.canRead()) return error(403, "File not readable");
        if (!file.isFile()) return error(400, "Not a file");
        
        try {
            InputStream is = new FileInputStream(file);
            String mimeType = getMimeType(file.getName());
            return new Object[]{200, mimeType, is};
        } catch (FileNotFoundException e) {
            return error(404, "File not found");
        }
    }

    private static Object[] text(Map<String, String> params) {
        String content = params.get("content");
        if (TextUtils.isEmpty(content)) content = "";
        if (content.length() > 10000) content = content.substring(0, 10000);
        return new Object[]{200, "text/plain; charset=utf-8", new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))};
    }

    private static Object[] json(Map<String, String> params) {
        String content = params.get("content");
        if (TextUtils.isEmpty(content)) content = "{}";
        if (content.length() > 10000) content = content.substring(0, 10000);
        return new Object[]{200, "application/json; charset=utf-8", new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))};
    }

    private static Object[] image(Map<String, String> params) {
        String path = params.get("path");
        if (TextUtils.isEmpty(path)) return error(400, "Path is empty");
        
        if (!isPathAllowed(path)) {
            SpiderDebug.log("Proxy: access denied to image: " + path);
            return error(403, "Access denied");
        }
        
        File file = new File(path);
        if (!file.exists()) return error(404, "Image not found");
        if (!file.canRead()) return error(403, "Image not readable");
        if (!file.isFile()) return error(400, "Not a file");
        
        String ext = getExtension(file.getName());
        if (!isImageExtension(ext)) {
            return error(400, "Not an image file");
        }
        
        try {
            InputStream is = new FileInputStream(file);
            String mimeType = getMimeType(file.getName());
            return new Object[]{200, mimeType, is};
        } catch (FileNotFoundException e) {
            return error(404, "Image not found");
        }
    }

    private static Object[] video(Map<String, String> params) {
        String path = params.get("path");
        if (TextUtils.isEmpty(path)) return error(400, "Path is empty");
        
        if (!isPathAllowed(path)) {
            SpiderDebug.log("Proxy: access denied to video: " + path);
            return error(403, "Access denied");
        }
        
        File file = new File(path);
        if (!file.exists()) return error(404, "Video not found");
        if (!file.canRead()) return error(403, "Video not readable");
        if (!file.isFile()) return error(400, "Not a file");
        
        String ext = getExtension(file.getName());
        if (!isVideoExtension(ext)) {
            return error(400, "Not a video file");
        }
        
        try {
            InputStream is = new FileInputStream(file);
            String mimeType = getMimeType(file.getName());
            return new Object[]{200, mimeType, is};
        } catch (FileNotFoundException e) {
            return error(404, "Video not found");
        }
    }

    private static Object[] audio(Map<String, String> params) {
        String path = params.get("path");
        if (TextUtils.isEmpty(path)) return error(400, "Path is empty");
        
        if (!isPathAllowed(path)) {
            SpiderDebug.log("Proxy: access denied to audio: " + path);
            return error(403, "Access denied");
        }
        
        File file = new File(path);
        if (!file.exists()) return error(404, "Audio not found");
        if (!file.canRead()) return error(403, "Audio not readable");
        if (!file.isFile()) return error(400, "Not a file");
        
        String ext = getExtension(file.getName());
        if (!isAudioExtension(ext)) {
            return error(400, "Not an audio file");
        }
        
        try {
            InputStream is = new FileInputStream(file);
            String mimeType = getMimeType(file.getName());
            return new Object[]{200, mimeType, is};
        } catch (FileNotFoundException e) {
            return error(404, "Audio not found");
        }
    }

    private static Object[] error(int code, String message) {
        return new Object[]{code, "text/plain; charset=utf-8", new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8))};
    }

    private static boolean isPathAllowed(String path) {
        if (TextUtils.isEmpty(path)) return false;
        
        try {
            String canonicalPath = new File(path).getCanonicalPath();
            
            for (String forbidden : FORBIDDEN_PATHS) {
                if (canonicalPath.startsWith(forbidden)) {
                    return false;
                }
            }
            
            if (canonicalPath.contains("..")) {
                return false;
            }
            
            for (String allowed : ALLOWED_PATHS) {
                if (canonicalPath.startsWith(allowed)) {
                    return true;
                }
            }
            
            return false;
        } catch (IOException e) {
            SpiderDebug.log(e);
            return false;
        }
    }

    private static String getExtension(String filename) {
        if (TextUtils.isEmpty(filename)) return "";
        int lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 ? filename.substring(lastDot + 1).toLowerCase() : "";
    }

    private static boolean isImageExtension(String ext) {
        if (TextUtils.isEmpty(ext)) return false;
        return ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") 
            || ext.equals("gif") || ext.equals("webp") || ext.equals("bmp")
            || ext.equals("svg");
    }

    private static boolean isVideoExtension(String ext) {
        if (TextUtils.isEmpty(ext)) return false;
        return ext.equals("mp4") || ext.equals("mkv") || ext.equals("avi")
            || ext.equals("mov") || ext.equals("wmv") || ext.equals("flv")
            || ext.equals("webm") || ext.equals("m3u8") || ext.equals("ts");
    }

    private static boolean isAudioExtension(String ext) {
        if (TextUtils.isEmpty(ext)) return false;
        return ext.equals("mp3") || ext.equals("wav") || ext.equals("flac")
            || ext.equals("aac") || ext.equals("ogg") || ext.equals("m4a")
            || ext.equals("wma");
    }

    public static void init() {
        try {
            Class<?> clz = Class.forName("com.github.catvod.Proxy");
            port = (int) clz.getMethod("getPort").invoke(null);
            method = clz.getMethod("getUrl", boolean.class);
            enabled = true;
            SpiderDebug.log("本地代理端口: " + port);
        } catch (Throwable e) {
            findPort();
        }
    }

    public static int getPort() {
        return port;
    }

    public static String getHost() {
        return host != null ? host : "127.0.0.1";
    }

    public static void setHost(String h) {
        host = h;
    }

    public static boolean isEnabled() {
        return enabled && port > 0;
    }

    public static String getUrl(String siteKey, String param) {
        return "proxy://do=csp&siteKey=" + siteKey + param;
    }

    public static String getUrl() {
        return getUrl(true);
    }

    public static String getUrl(boolean local) {
        try {
            return (String) method.invoke(null, local);
        } catch (Throwable e) {
            return "http://127.0.0.1:" + port + "/proxy";
        }
    }

    public static String getUrl(int port) {
        return "http://127.0.0.1:" + port + "/proxy";
    }

    public static String getUrl(String host, int port) {
        return "http://" + host + ":" + port + "/proxy";
    }

    public static String buildUrl(String action, Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        sb.append(getUrl()).append("?do=").append(action);
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                sb.append("&").append(entry.getKey()).append("=").append(entry.getValue());
            }
        }
        return sb.toString();
    }

    private static void findPort() {
        if (port > 0) return;
        for (int p = 8964; p < 9999; p++) {
            if ("ok".equals(OkHttp.string("http://127.0.0.1:" + p + "/proxy?do=ck", null))) {
                SpiderDebug.log("本地代理端口: " + p);
                port = p;
                enabled = true;
                break;
            }
        }
        if (port == 0) {
            enabled = false;
            SpiderDebug.log("未找到本地代理端口");
        }
    }

    private static String getMimeType(String filename) {
        String ext = getExtension(filename);
        switch (ext) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "webp":
                return "image/webp";
            case "bmp":
                return "image/bmp";
            case "svg":
                return "image/svg+xml";
            case "mp4":
                return "video/mp4";
            case "mkv":
                return "video/x-matroska";
            case "avi":
                return "video/x-msvideo";
            case "mov":
                return "video/quicktime";
            case "wmv":
                return "video/x-ms-wmv";
            case "flv":
                return "video/x-flv";
            case "webm":
                return "video/webm";
            case "m3u8":
                return "application/x-mpegURL";
            case "ts":
                return "video/MP2T";
            case "mp3":
                return "audio/mpeg";
            case "wav":
                return "audio/wav";
            case "flac":
                return "audio/flac";
            case "aac":
                return "audio/aac";
            case "ogg":
                return "audio/ogg";
            case "m4a":
                return "audio/mp4";
            case "wma":
                return "audio/x-ms-wma";
            default:
                return "application/octet-stream";
        }
    }
}