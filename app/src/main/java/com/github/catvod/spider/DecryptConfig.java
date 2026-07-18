package com.github.catvod.spider;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.crawler.Spider;
import com.github.catvod.spider.obf.Str;
import com.github.catvod.utils.Path;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 解密配置 Spider。
 * 提供配置文件管理、解密密钥更新、缓存清理等功能的操作入口。
 */
public class DecryptConfig extends Spider {

    /**
     * 配置文件名称（混淆字符串）
     */
    private static final String CONFIG_FILE_NAME = "jm_decrypt_last.txt";

    @Override
    public void init(Context context, String extend) throws Exception {
        Init.checkPermission();
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        try {
            ArrayList<Class> classes = new ArrayList<>();
            
            // 读取配置文件
            File configFile = new File(Init.context().getFilesDir(), CONFIG_FILE_NAME);
            String configContent = Path.read(configFile);
            
            // 根据配置文件是否存在选择不同的类型名称
            String typeName;
            if (!TextUtils.isEmpty(configContent)) {
                typeName = "解密设置·已缓存";
            } else {
                typeName = "解密设置";
            }
            
            // 创建分类
            String typeId = "jm_cfg";
            String typePic = "1";
            classes.add(new Class(typeId, typeName, typePic));
            
            return Result.string(classes, new ArrayList<>());
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        // 检查特殊的分类 ID
        String specialTid1 = "0";
        String specialTid2 = "jm_cfg";
        
        if (specialTid1.equals(tid) || specialTid2.equals(tid)) {
            return com.github.catvod.spider.merge.f.I0.O();
        }
        
        return com.github.catvod.spider.merge.f.I0.O();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        return Str.u("");
    }

    /**
     * 执行指定的配置操作。
     * 支持多种操作：清除配置、更新配置、清理缓存等。
     *
     * @param action 操作名称（混淆字符串）
     * @return 执行结果（通常为空字符串）
     */
    public String action(String action) {
        // 获取 Activity 用于对话框显示
        Activity activity = Init.activityForDialog();
        if (activity != null) {
            Init.setActivity(activity);
        }
        
        String emptyResult = "";
        
        // 处理空参数
        if (action == null) {
            action = emptyResult;
        }
        
        // 根据操作名称执行对应的任务
        int actionIndex = getActionIndex(action);
        
        switch (actionIndex) {
            case 0:
                // 清除配置
                Init.run(new com.github.catvod.spider.merge.f.q(2));
                break;
                
            case 1:
                // 更新配置文件
                File configFile = new File(Init.context().getFilesDir(), CONFIG_FILE_NAME);
                String content = Path.read(configFile);
                Init.run(new com.github.catvod.spider.merge.f.m0(content, false));
                break;
                
            case 2:
                // 更新特定配置项（参数：4）
                com.github.catvod.spider.merge.f.D.j(4);
                break;
                
            case 3:
                // 执行清理任务（参数：0）
                Init.execute(new com.github.catvod.spider.merge.f.b0(0));
                break;
                
            case 4:
                // 更新特定配置（参数：2）
                Init.run(new com.github.catvod.spider.merge.f.t(2));
                break;
                
            case 5:
                // 执行其他配置任务（参数：4）
                Init.run(new com.github.catvod.spider.merge.a.c0(4));
                break;
                
            case 6:
                // 运行配置任务（参数：2）
                Init.run(new com.github.catvod.spider.merge.f.p(2));
                break;
                
            case 7:
                // 执行特殊任务（参数：3）
                Init.execute(new com.github.catvod.spider.merge.a.c0(3));
                break;
                
            case 8:
                // 执行清理操作（参数：1）
                Init.execute(new com.github.catvod.spider.merge.f.P(1));
                break;
                
            case 9:
                // 更新特定配置项（参数：3）
                com.github.catvod.spider.merge.f.D.j(3);
                break;
                
            case 10:
                // 执行最终任务（参数：0）
                Init.execute(new com.github.catvod.spider.merge.f.l0(0));
                break;
                
            default:
                // 默认操作：显示提示信息
                String message = "未知操作";
                com.github.catvod.spider.merge.f.l4.A3(message);
                break;
        }
        
        return emptyResult;
    }

    /**
     * 根据 action 名称获取对应的索引值。
     * 使用字符串混淆保护操作名称。
     *
     * @param action 操作名称
     * @return 操作索引（-1 表示未找到匹配的操作）
     */
    private int getActionIndex(String action) {
        // 以下字符串均为混淆后的操作名称
        // 通过 Str.u() 方法在运行时解混淆
        
        if (action.equals("jm_path")) return 0;
        if (action.equals("jm_view")) return 1;
        if (action.equals("jm_web")) return 2;
        if (action.equals("jm_dl_jar")) return 3;
        if (action.equals("jm_search")) return 4;
        if (action.equals("jm_history")) return 5;
        if (action.equals("jm_input")) return 6;
        if (action.equals("jm_dl_json")) return 7;
        if (action.equals("jm_dl_config")) return 8;
        if (action.equals("jm_jar_url")) return 9;
        if (action.equals("jm_site_order")) return 10;
        
        return -1;
    }
}