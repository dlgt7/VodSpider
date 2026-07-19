package com.github.catvod.spider;

import android.app.Activity;
import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 应用初始化上下文提供者。
 * 提供 SO 库路径、主线程 Handler、线程池等基础能力。
 */
public class InitOrigin {

    private static class Loader {
        static volatile InitOrigin INSTANCE = new InitOrigin();
    }

    public static volatile String libPath;
    public static volatile Activity activity;
    public static volatile boolean initialized;
    public static volatile boolean debugMode;

    public final ExecutorService executor;
    public final Handler mainHandler;
    public final ClassLoader classLoader;
    public Application application;

    public InitOrigin() {
        executor = Executors.newFixedThreadPool(4);
        mainHandler = new Handler(Looper.getMainLooper());
        classLoader = getClass().getClassLoader();
    }

    public static InitOrigin get() {
        return Loader.INSTANCE;
    }

    public static void execute(Runnable runnable) {
        if (runnable == null) return;
        get().executor.execute(runnable);
    }
}
