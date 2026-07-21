package com.github.catvod.spider.merge.w;

/** Account status registry polled by {@link com.github.catvod.spider.FishConfig#getAccountStatuses()} and refreshed via {@link com.github.catvod.spider.FishConfig#refreshConsoleStatus(Runnable)}. */
public class a {

    public static String a;

    public static String[] h() {
        return new String[0];
    }

    public static void F(Runnable callback) {
        if (callback != null) {
            callback.run();
        }
    }
}
