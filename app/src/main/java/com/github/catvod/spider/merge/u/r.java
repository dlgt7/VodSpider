package com.github.catvod.spider.merge.u;

/** Account-status refresh runnable posted to {@link com.github.catvod.spider.InitOrigin} from {@link com.github.catvod.spider.FishConfig}. */
public class r implements Runnable {

    private final int mode;

    public r(int mode) {
        this.mode = mode;
    }

    @Override
    public void run() {
    }
}
