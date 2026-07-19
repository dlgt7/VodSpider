package com.github.catvod.spider.merge.u;

import com.github.catvod.spider.FishConfig;

/**
 * 弹幕 action Runnable（synthetic，绑定 FishConfig 实例）。
 * <p>
 * 原 smali 文件 5.8KB，构造器接受 {@link FishConfig} 实例与 action 类型 int 标识，
 * {@link #run()} 方法体含 moyu/fucking 混淆控制流，按 action 类型分发到具体弹幕操作。
 * <p>
 * 当前为骨架实现：{@link #run()} 为空操作，待混淆控制流完整还原后接入真实逻辑。
 */
public final class g implements Runnable {

    public final int a;
    public final FishConfig b;

    public g(FishConfig config, int action) {
        this.a = action;
        this.b = config;
    }

    @Override
    public void run() {
        // TODO: 原 smali run() 方法体含 sparse-switch 控制流混淆，
        //  按 a 字段分发到具体弹幕 action 处理逻辑。待解码后接入真实实现。
    }
}
