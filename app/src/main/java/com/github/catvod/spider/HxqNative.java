package com.github.catvod.spider;

import android.app.Application;
import android.os.Build;

import com.github.catvod.utils.Util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class HxqNative {

    private static final String CACHE_MARK = ".hxqfnw";
    private static volatile boolean loaded = false;

    private HxqNative() {
    }

    public static native String aesSign(String payload, String uk);

    public static native String aesUk(String uid);

    public static native String decodeSegment(String data, String key);

    public static native String decryptData(String data, String uk, String ts, String key);

    public static native String gateAesIv();

    public static native String gateAesKey();

    public static native String gateControlUrl();

    public static native String gateLiveFallback();

    public static native String gateWallMobile();

    public static native String gateWallTv();

    public static native String rewardAps(String json);

    public static native String rewardBody(String pid, String traceId, String scene);

    public static native String rslvSign(String vn, String devId, String p1, String p2, String p3, String p4, long ts, String uid, int guard);

    public static void ensureLoaded() {
        if (loaded) return;
        synchronized (HxqNative.class) {
            if (loaded) return;
            loadSo();
            loaded = true;
        }
    }

    private static void loadSo() {
        Application app = Init.context();
        if (app == null) {
            throw new RuntimeException("HxqNative: Init.context() is null");
        }
        try {
            String cacheDir = app.getCacheDir().getAbsolutePath() + File.separator;
            deleteFilesWithFeature(cacheDir, CACHE_MARK);

            String abi = Build.CPU_ABI.contains("64") ? "v8" : "v7";
            String soName = "hxq_native_" + abi + ".so";

            String tempName = CACHE_MARK + Util.randomString(10);
            File tempFile = new File(app.getCacheDir(), tempName);

            ClassLoader classLoader = app.getClassLoader();
            String assetPath = "assets/" + soName;
            InputStream is = classLoader.getResourceAsStream(assetPath);
            if (is == null) {
                throw new RuntimeException("HxqNative: missing assets/" + soName);
            }
            FileOutputStream fos = new FileOutputStream(tempFile);
            byte[] buf = new byte[0x2000];
            int n;
            while ((n = is.read(buf)) != -1) {
                fos.write(buf, 0, n);
            }
            is.close();
            fos.close();

            System.load(tempFile.getAbsolutePath());
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException("HxqNative load failed", e);
        }
    }

    private static void deleteFilesWithFeature(String dir, String feature) {
        File d = new File(dir);
        if (!d.isDirectory()) return;
        File[] files = d.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.getName().contains(feature)) {
                f.delete();
            }
        }
    }
}
