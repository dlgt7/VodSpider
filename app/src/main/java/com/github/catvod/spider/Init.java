package com.github.catvod.spider;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class Init {

    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private final Handler handler;
    private Application app;
    private SharedPreferences prefers;

    private static volatile Activity mActivity;
    private static volatile ClassLoader mClassLoader;
    private static final AtomicReference<Runnable> mDialogRunnable = new AtomicReference<>(null);
    private static volatile long mDialogTime = 0;
    private static volatile boolean mChecking = false;

    private static class Loader {
        static volatile Init INSTANCE = new Init();
    }

    public static Init get() {
        return Loader.INSTANCE;
    }

    public Init() {
        this.handler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newFixedThreadPool(5);
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    public static Application context() {
        return get().app;
    }

    public static void init(Context context) {
        get().app = ((Application) context);
        get().prefers = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        Proxy.init();
    }

    public static void execute(Runnable runnable) {
        get().executor.execute(runnable);
    }

    public static Future<?> submit(Runnable runnable) {
        return get().executor.submit(runnable);
    }

    public static void post(Runnable runnable) {
        get().handler.post(runnable);
    }

    public static void post(Runnable runnable, int delay) {
        get().handler.postDelayed(runnable, delay);
    }

    public static void postAtTime(Runnable runnable, long uptimeMillis) {
        get().handler.postAtTime(runnable, uptimeMillis);
    }

    public static void postAtFrontOfQueue(Runnable runnable) {
        get().handler.postAtFrontOfQueue(runnable);
    }

    public static void removeCallbacks(Runnable runnable) {
        get().handler.removeCallbacks(runnable);
    }

    public static void removeCallbacksAndMessages(Object token) {
        get().handler.removeCallbacksAndMessages(token);
    }

    public static ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit unit) {
        return get().scheduler.schedule(runnable, delay, unit);
    }

    public static ScheduledFuture<?> scheduleAtFixedRate(Runnable runnable, long initialDelay, long period, TimeUnit unit) {
        return get().scheduler.scheduleAtFixedRate(runnable, initialDelay, period, unit);
    }

    public static ScheduledFuture<?> scheduleWithFixedDelay(Runnable runnable, long initialDelay, long delay, TimeUnit unit) {
        return get().scheduler.scheduleWithFixedDelay(runnable, initialDelay, delay, unit);
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    public static void runOnUiThread(Runnable runnable) {
        if (isMainThread()) {
            runnable.run();
        } else {
            post(runnable);
        }
    }

    public static void runOnBackgroundThread(Runnable runnable) {
        if (isMainThread()) {
            execute(runnable);
        } else {
            runnable.run();
        }
    }

    public static String getString(String key, String defaultValue) {
        return get().prefers == null ? defaultValue : get().prefers.getString(key, defaultValue);
    }

    public static String getString(String key) {
        return getString(key, "");
    }

    public static int getInt(String key, int defaultValue) {
        return get().prefers == null ? defaultValue : get().prefers.getInt(key, defaultValue);
    }

    public static int getInt(String key) {
        return getInt(key, 0);
    }

    public static long getLong(String key, long defaultValue) {
        return get().prefers == null ? defaultValue : get().prefers.getLong(key, defaultValue);
    }

    public static long getLong(String key) {
        return getLong(key, 0L);
    }

    public static float getFloat(String key, float defaultValue) {
        return get().prefers == null ? defaultValue : get().prefers.getFloat(key, defaultValue);
    }

    public static float getFloat(String key) {
        return getFloat(key, 0f);
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        return get().prefers == null ? defaultValue : get().prefers.getBoolean(key, defaultValue);
    }

    public static boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    public static void put(String key, Object obj) {
        if (get().prefers == null || obj == null) return;
        SharedPreferences.Editor editor = get().prefers.edit();
        if (obj instanceof String) {
            editor.putString(key, (String) obj);
        } else if (obj instanceof Boolean) {
            editor.putBoolean(key, (Boolean) obj);
        } else if (obj instanceof Float) {
            editor.putFloat(key, (Float) obj);
        } else if (obj instanceof Integer) {
            editor.putInt(key, (Integer) obj);
        } else if (obj instanceof Long) {
            editor.putLong(key, (Long) obj);
        }
        editor.apply();
    }

    public static void remove(String key) {
        if (get().prefers == null) return;
        get().prefers.edit().remove(key).apply();
    }

    public static void clear() {
        if (get().prefers == null) return;
        get().prefers.edit().clear().apply();
    }

    public static boolean contains(String key) {
        return get().prefers != null && get().prefers.contains(key);
    }

    public static void shutdown() {
        get().executor.shutdown();
        get().scheduler.shutdown();
    }

    public static void shutdownNow() {
        get().executor.shutdownNow();
        get().scheduler.shutdownNow();
    }

    public static boolean isShutdown() {
        return get().executor.isShutdown();
    }

    public static boolean isTerminated() {
        return get().executor.isTerminated();
    }

    public static boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return get().executor.awaitTermination(timeout, unit);
    }

    // ==================== Activity相关方法 ====================

    /**
     * 获取当前Activity (反射方式)
     */
    public static Activity getActivity() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod = activityThreadClass.getMethod("currentActivityThread");
            Object activityThread = currentActivityThreadMethod.invoke(null);
            Field mActivitiesField = activityThreadClass.getDeclaredField("mActivities");
            mActivitiesField.setAccessible(true);
            Map<?, ?> mActivities = (Map<?, ?>) mActivitiesField.get(activityThread);
            if (mActivities == null) return null;
            for (Object value : mActivities.values()) {
                if (value == null) continue;
                Class<?> klass = value.getClass();
                Field pausedField = klass.getDeclaredField("paused");
                pausedField.setAccessible(true);
                if (pausedField.getBoolean(value)) continue;
                Field activityField = klass.getDeclaredField("activity");
                activityField.setAccessible(true);
                return (Activity) activityField.get(value);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * 获取配置Activity
     */
    public static Activity getConfigActivity() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod = activityThreadClass.getMethod("currentActivityThread");
            Object activityThread = currentActivityThreadMethod.invoke(null);
            Field mActivitiesField = activityThreadClass.getDeclaredField("mActivities");
            mActivitiesField.setAccessible(true);
            Map<?, ?> mActivities = (Map<?, ?>) mActivitiesField.get(activityThread);
            if (mActivities == null) return null;
            for (Object value : mActivities.values()) {
                if (value == null) continue;
                Class<?> klass = value.getClass();
                Field activityField = klass.getDeclaredField("activity");
                activityField.setAccessible(true);
                Activity activity = (Activity) activityField.get(value);
                if (activity != null && !activity.isFinishing()) return activity;
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * 获取用于显示对话框的Activity
     */
    public static Activity activityForDialog() {
        try {
            if (mActivity != null && !mActivity.isFinishing() && !mActivity.isDestroyed()) {
                return mActivity;
            }
            Activity activity = getActivity();
            if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                return activity;
            }
            return getConfigActivity();
        } catch (Exception e) {
            return getConfigActivity();
        }
    }

    /**
     * 设置Activity
     */
    public static void setActivity(Activity activity) {
        mActivity = activity;
    }

    /**
     * 设置ClassLoader
     */
    public static void setJarClassLoader(ClassLoader classLoader) {
        if (classLoader == null) return;
        ClassLoader old = mClassLoader;
        mClassLoader = classLoader;
    }

    /**
     * 获取ClassLoader
     */
    public static ClassLoader getClassLoader() {
        return mClassLoader;
    }

    /**
     * 在关闭详情页后显示对话框
     */
    public static void showDialogAfterKillingDetail(Runnable runnable) {
        mDialogRunnable.set(runnable);
        mDialogTime = System.currentTimeMillis() + 1200;
        if (mChecking || context() == null) return;
        mChecking = true;
        schedule(() -> {
            try {
                long now = System.currentTimeMillis();
                if (now >= mDialogTime) {
                    Runnable r = mDialogRunnable.getAndSet(null);
                    if (r != null) {
                        post(r);
                    }
                    mChecking = false;
                } else {
                    mChecking = false;
                    showDialogAfterKillingDetail(null);
                }
            } catch (Exception e) {
                mChecking = false;
            }
        }, 200, TimeUnit.MILLISECONDS);
    }

    /**
     * 检查权限
     */
    public static void checkPermission() {
        try {
            Activity activity = getActivity();
            if (activity == null) return;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
            java.util.ArrayList<String> permissions = new java.util.ArrayList<>();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add("android.permission.READ_MEDIA_VIDEO");
                permissions.add("android.permission.READ_MEDIA_AUDIO");
                permissions.add("android.permission.READ_MEDIA_IMAGES");
            } else {
                permissions.add("android.permission.READ_EXTERNAL_STORAGE");
                permissions.add("android.permission.WRITE_EXTERNAL_STORAGE");
            }
            java.util.ArrayList<String> needed = new java.util.ArrayList<>();
            for (String permission : permissions) {
                if (activity.checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    needed.add(permission);
                }
            }
            if (needed.isEmpty()) return;
            activity.requestPermissions(needed.toArray(new String[0]), 0);
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * 显示通知
     */
    public static void show(String text) {
        try {
            com.github.catvod.utils.Notify.show(text);
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * 运行Runnable
     */
    public static void run(Runnable runnable) {
        if (isMainThread()) {
            runnable.run();
        } else {
            post(runnable);
        }
    }
}