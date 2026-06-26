package com.github.catvod.api;

import android.text.TextUtils;
import android.os.Environment;
import android.os.StatFs;

import com.github.catvod.bean.Danmaku;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.spider.Init;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 弹幕本地缓存类
 * 
 * 功能说明：
 * 1. 缓存已加载的弹幕，减少重复网络请求
 * 2. 支持按番剧名称和集数缓存
 * 3. 支持缓存过期检查
 * 4. 支持缓存清理
 * 
 * 缓存策略：
 * - 缓存Key: 番剧名称 + 集数
 * - 缓存有效期: 默认24小时
 * - 最大缓存条目: 100条
 * - 磁盘空间阈值: 50MB，低于此值不写入文件
 */
public class DanmakuCache {

    private static final String CACHE_DIR = "danmaku_cache";
    private static final String KEY_CACHE_ENABLED = "danmaku_cache_enabled";
    private static final String KEY_CACHE_EXPIRE_HOURS = "danmaku_cache_expire_hours";
    private static final String KEY_CACHE_MAX_SIZE = "danmaku_cache_max_size";
    
    private static final boolean DEFAULT_CACHE_ENABLED = true;
    private static final int DEFAULT_CACHE_EXPIRE_HOURS = 24;
    private static final int DEFAULT_CACHE_MAX_SIZE = 100;
    
    // 磁盘空间阈值（字节），低于此值不写入文件
    private static final long MIN_DISK_SPACE = 50 * 1024 * 1024; // 50MB

    // 内存缓存
    private static final Map<String, CacheEntry> memoryCache = new ConcurrentHashMap<>();
    
    // 最近一次API结果（用于备用源切换时获取上次的搜索结果）
    private static String lastResult;
    
    // Gson实例
    private static final Gson gson = new Gson();
    
    // 文件IO线程池
    private static final ExecutorService fileExecutor = Executors.newSingleThreadExecutor();

    /**
     * 缓存条目
     */
    private static class CacheEntry {
        long timestamp;
        String key;
        List<Danmaku> danmakuList;
        
        CacheEntry(String key, List<Danmaku> danmakuList) {
            this.key = key;
            this.danmakuList = danmakuList;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired(int expireHours) {
            long expireMillis = expireHours * 60L * 60L * 1000L;
            return System.currentTimeMillis() - timestamp > expireMillis;
        }
    }

    // ========== 设置方法 ==========

    /**
     * 是否启用缓存
     */
    public static boolean isEnabled() {
        return Init.getBoolean(KEY_CACHE_ENABLED, DEFAULT_CACHE_ENABLED);
    }

    public static void putEnabled(boolean value) {
        Init.put(KEY_CACHE_ENABLED, value);
    }

    /**
     * 获取缓存有效期（小时）
     */
    public static int getExpireHours() {
        return Init.getInt(KEY_CACHE_EXPIRE_HOURS, DEFAULT_CACHE_EXPIRE_HOURS);
    }

    public static void putExpireHours(int value) {
        Init.put(KEY_CACHE_EXPIRE_HOURS, value);
    }

    /**
     * 获取最大缓存条目数
     */
    public static int getMaxSize() {
        return Init.getInt(KEY_CACHE_MAX_SIZE, DEFAULT_CACHE_MAX_SIZE);
    }

    public static void putMaxSize(int value) {
        Init.put(KEY_CACHE_MAX_SIZE, value);
    }

    // ========== 缓存操作 ==========

    /**
     * 生成缓存Key
     * 
     * @param name 番剧名称
     * @param episode 集数
     * @return 缓存Key
     */
    public static String makeKey(String name, String episode) {
        String key = name;
        if (!TextUtils.isEmpty(episode)) {
            key = key + "_" + episode;
        }
        return hashKey(key);
    }

    /**
     * Hash Key避免特殊字符问题
     */
    private static String hashKey(String key) {
        return String.valueOf(key.hashCode());
    }

    /**
     * 获取缓存的弹幕
     * 
     * @param name 番剧名称
     * @param episode 集数
     * @return 缓存的弹幕列表，null表示无缓存或已过期
     */
    public static List<Danmaku> get(String name, String episode) {
        if (!isEnabled()) {
            return null;
        }
        
        String key = makeKey(name, episode);
        CacheEntry entry = memoryCache.get(key);
        
        if (entry != null) {
            if (entry.isExpired(getExpireHours())) {
                // 已过期，移除
                memoryCache.remove(key);
                deleteFile(key);
                SpiderDebug.log("弹幕缓存已过期: " + key);
                return null;
            }
            SpiderDebug.log("弹幕缓存命中: " + key);
            return entry.danmakuList;
        }
        
        // 尝试从文件加载（主线程调用，同步加载）
        entry = loadFromFile(key);
        if (entry != null) {
            if (entry.isExpired(getExpireHours())) {
                deleteFile(key);
                SpiderDebug.log("弹幕缓存文件已过期: " + key);
                return null;
            }
            memoryCache.put(key, entry);
            SpiderDebug.log("弹幕缓存文件命中: " + key);
            return entry.danmakuList;
        }
        
        return null;
    }

    /**
     * 保存弹幕到缓存
     * 
     * @param name 番剧名称
     * @param episode 集数
     * @param danmakuList 弹幕列表
     */
    public static void put(String name, String episode, List<Danmaku> danmakuList) {
        if (!isEnabled() || danmakuList == null || danmakuList.isEmpty()) {
            return;
        }
        
        String key = makeKey(name, episode);
        CacheEntry entry = new CacheEntry(key, danmakuList);
        
        // 保存到内存
        memoryCache.put(key, entry);
        
        // 检查缓存大小，清理过期条目（同步清理文件和内存）
        cleanIfNeeded();
        
        // 检查磁盘空间是否足够
        if (!hasEnoughDiskSpace()) {
            SpiderDebug.log("磁盘空间不足，跳过文件缓存");
            return;
        }
        
        // 异步保存到文件
        saveToFileAsync(key, entry);
        
        SpiderDebug.log("弹幕已缓存: " + key + ", 数量: " + danmakuList.size());
    }

    /**
     * 移除指定缓存
     */
    public static void remove(String name, String episode) {
        String key = makeKey(name, episode);
        memoryCache.remove(key);
        deleteFile(key);
        SpiderDebug.log("弹幕缓存已移除: " + key);
    }

    /**
     * 清空所有缓存
     */
    public static void clear() {
        memoryCache.clear();
        clearCacheFiles();
        SpiderDebug.log("弹幕缓存已清空");
    }

    /**
     * 获取缓存统计
     */
    public static String getStats() {
        int memoryCount = memoryCache.size();
        int fileCount = getCacheFileCount();
        int expireHours = getExpireHours();
        int maxSize = getMaxSize();
        boolean enabled = isEnabled();
        
        return String.format("弹幕缓存: 内存%d, 文件%d, 有效期%d小时, 最大%d条, 启用:%s",
                memoryCount, fileCount, expireHours, maxSize, enabled);
    }

    // ========== 文件操作 ==========

    /**
     * 获取缓存目录
     */
    private static File getCacheDir() {
        File cacheDir = new File(Init.context().getCacheDir(), CACHE_DIR);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        return cacheDir;
    }

    /**
     * 获取缓存文件
     */
    private static File getCacheFile(String key) {
        return new File(getCacheDir(), key + ".json");
    }

    /**
     * 检查磁盘空间是否足够
     */
    private static boolean hasEnoughDiskSpace() {
        try {
            File cacheDir = getCacheDir();
            StatFs stat = new StatFs(cacheDir.getPath());
            long availableBytes = stat.getAvailableBytes();
            return availableBytes > MIN_DISK_SPACE;
        } catch (Exception e) {
            // 如果检查失败，假设空间足够
            return true;
        }
    }

    /**
     * 异步保存到文件
     */
    private static void saveToFileAsync(String key, CacheEntry entry) {
        fileExecutor.execute(() -> {
            try {
                File file = getCacheFile(key);
                String json = gson.toJson(entry);
                FileWriter writer = new FileWriter(file);
                writer.write(json);
                writer.close();
            } catch (IOException e) {
                SpiderDebug.log("弹幕缓存保存失败: " + e.getMessage());
            }
        });
    }

    /**
     * 保存到文件（同步）
     */
    private static void saveToFile(String key, CacheEntry entry) {
        try {
            File file = getCacheFile(key);
            String json = gson.toJson(entry);
            FileWriter writer = new FileWriter(file);
            writer.write(json);
            writer.close();
        } catch (IOException e) {
            SpiderDebug.log("弹幕缓存保存失败: " + e.getMessage());
        }
    }

    /**
     * 从文件加载（同步）
     */
    private static CacheEntry loadFromFile(String key) {
        try {
            File file = getCacheFile(key);
            if (!file.exists()) {
                return null;
            }
            FileReader reader = new FileReader(file);
            CacheEntry entry = gson.fromJson(reader, CacheEntry.class);
            reader.close();
            return entry;
        } catch (IOException e) {
            SpiderDebug.log("弹幕缓存加载失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 删除缓存文件
     */
    private static void deleteFile(String key) {
        File file = getCacheFile(key);
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * 清空所有缓存文件
     */
    private static void clearCacheFiles() {
        File cacheDir = getCacheDir();
        File[] files = cacheDir.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
    }

    /**
     * 获取缓存文件数量
     */
    private static int getCacheFileCount() {
        File cacheDir = getCacheDir();
        File[] files = cacheDir.listFiles();
        return files != null ? files.length : 0;
    }

    // ========== 缓存管理 ==========

    /**
     * 清理过期缓存（同时清理内存和文件）
     */
    public static void cleanExpired() {
        int expireHours = getExpireHours();
        List<String> toRemove = new ArrayList<>();
        
        for (Map.Entry<String, CacheEntry> entry : memoryCache.entrySet()) {
            if (entry.getValue().isExpired(expireHours)) {
                toRemove.add(entry.getKey());
            }
        }
        
        for (String key : toRemove) {
            memoryCache.remove(key);
            deleteFile(key);  // 同步删除文件
        }
        
        if (!toRemove.isEmpty()) {
            SpiderDebug.log("已清理" + toRemove.size() + "条过期弹幕缓存");
        }
    }

    /**
     * 如果缓存过大，清理部分（同时清理内存和文件）
     */
    private static void cleanIfNeeded() {
        int maxSize = getMaxSize();
        int currentSize = memoryCache.size();
        
        if (currentSize <= maxSize) {
            return;
        }
        
        // 按时间排序，删除最旧的
        List<Map.Entry<String, CacheEntry>> sorted = new ArrayList<>(memoryCache.entrySet());
        sorted.sort((a, b) -> Long.compare(a.getValue().timestamp, b.getValue().timestamp));
        
        int toRemove = currentSize - maxSize;
        for (int i = 0; i < toRemove; i++) {
            String key = sorted.get(i).getKey();
            memoryCache.remove(key);
            deleteFile(key);  // 同步删除文件
        }
        
        SpiderDebug.log("弹幕缓存已清理，移除" + toRemove + "条");
    }

    /**
     * 重置所有设置为默认值
     */
    public static void resetAll() {
        putEnabled(DEFAULT_CACHE_ENABLED);
        putExpireHours(DEFAULT_CACHE_EXPIRE_HOURS);
        putMaxSize(DEFAULT_CACHE_MAX_SIZE);
        clear();
    }

    /**
     * 保存最近一次API结果（用于备用源切换）
     */
    public static void putLastResult(String result) {
        lastResult = result;
    }

    /**
     * 获取最近一次API结果
     */
    public static String getLastResult() {
        return lastResult;
    }
    
    /**
     * 关闭线程池
     */
    public static void shutdown() {
        fileExecutor.shutdown();
    }
}
