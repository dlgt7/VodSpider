package com.github.catvod.spider.merge.a;

/** Status refresh runnable posted to {@link com.github.catvod.spider.InitOrigin#execute(Runnable)} from {@link com.github.catvod.spider.FishConfig#refreshStatus(String)}. */
public class w implements Runnable {

    private final String key;
    private final int mode;

    public w(String key, int mode) {
        this.key = key;
        this.mode = mode;
    }

    @Override
    public void run() {
    }
}
