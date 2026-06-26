package com.github.catvod.spider;

import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.bean.Result;
import com.github.catvod.bean.Sub;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Image;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Push extends Spider {

    private static final Map<String, String> PLAY_FROM_MAP = new HashMap<>();
    
    static {
        PLAY_FROM_MAP.put("直連", "直連");
        PLAY_FROM_MAP.put("解析", "解析");
        PLAY_FROM_MAP.put("嗅探", "嗅探");
        PLAY_FROM_MAP.put("迅雷", "迅雷");
        PLAY_FROM_MAP.put("YouTube", "YouTube");
        PLAY_FROM_MAP.put("Bilibili", "Bilibili");
        PLAY_FROM_MAP.put("Vimeo", "Vimeo");
        PLAY_FROM_MAP.put("Dailymotion", "Dailymotion");
        PLAY_FROM_MAP.put("Vimeo", "Vimeo");
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            if (ids == null || ids.isEmpty()) {
                return Result.error("缺少推送地址");
            }
            return Result.string(vod(ids.get(0)));
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("加载详情失败: " + e.getMessage());
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            if (TextUtils.isEmpty(id)) {
                return Result.error("缺少播放地址");
            }
            
            String playFrom = PLAY_FROM_MAP.getOrDefault(flag, flag);
            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodPlayFrom(playFrom);
            
            if ("直連".equals(flag)) {
                vod.setVodPlayUrl(id);
                vod.setVodPic(Image.PUSH);
            } else if ("解析".equals(flag)) {
                vod.setVodPlayUrl(id);
                vod.setVodPic(Image.PUSH);
                vod.setVodName("解析播放");
            } else if ("嗅探".equals(flag)) {
                vod.setVodPlayUrl(id);
                vod.setVodPic(Image.PUSH);
                vod.setVodName("嗅探播放");
            } else if ("迅雷".equals(flag)) {
                if (Util.isThunder(id)) {
                    vod.setVodPlayUrl(id);
                    vod.setVodPlayFrom(playFrom);
                    vod.setVodPic(Image.PUSH);
                    vod.setVodName("迅雷下载");
                } else {
                    return Result.error("不支持的迅雷链接");
                }
            } else if ("YouTube".equals(flag)) {
                vod.setVodPlayUrl(id);
                vod.setVodPlayFrom(playFrom);
                vod.setVodPic(Image.PUSH);
                vod.setVodName("YouTube播放");
            } else if (id.contains("://")) {
                String url = id;
                String name = extractName(url);
                vod.setVodName(name);
                vod.setVodPlayUrl(url);
                vod.setVodPlayFrom(playFrom);
                vod.setVodPic(Image.PUSH);
                
                if (Util.isThunder(url)) {
                    vod.setVodPlayFrom("迅雷");
                }
            } else if (id.contains("#")) {
                List<String> urls = Arrays.asList(id.split("\n"));
                vod.setVodPlayUrl(TextUtils.join("#", urls));
                vod.setVodPlayFrom(playFrom);
                vod.setVodPic(Image.PUSH);
                vod.setVodName("多线路播放");
            } else {
                vod.setVodPlayUrl(id);
                vod.setVodPlayFrom(playFrom);
                vod.setVodPic(Image.PUSH);
            }
            
            return Result.string(vod);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("播放失败: " + e.getMessage());
        }
    }

    private Vod vod(String url) {
        Vod vod = new Vod();
        vod.setVodId(url);
        vod.setVodName(extractName(url));
        vod.setVodPic(Image.PUSH);
        vod.setVodPlayFrom("推送");
        vod.setVodPlayUrl(url);
        return vod;
    }

    private String extractName(String url) {
        try {
            if (url.startsWith("file://")) {
                File file = new File(url.replace("file://", ""));
                return file.getName();
            } else if (url.startsWith("http://") || url.startsWith("https://")) {
                Uri uri = Uri.parse(url);
                String name = uri.getLastPathSegment();
                if (name == null || name.isEmpty()) {
                    name = "未知";
                }
                return name;
            } else if (url.contains("#")) {
                return "多线路播放";
            } else {
                return "推送播放";
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "推送播放";
        }
    }

    private List<Sub> getSubs(String url) {
        List<Sub> subs = new ArrayList<>();
        
        try {
            if (url.startsWith("file://")) {
                File videoFile = new File(url.replace("file://", ""));
                File parentFile = videoFile.getParentFile();
                
                if (parentFile != null && parentFile.exists() && parentFile.canRead()) {
                    for (File f : Path.list(parentFile)) {
                        if (f.isFile()) {
                            String ext = Util.getExt(f.getName());
                            if (Util.isSub(ext)) {
                                String videoName = Util.removeExt(videoFile.getName()).toLowerCase();
                                String subName = Util.removeExt(f.getName()).toLowerCase();
                                
                                if (subName.equals(videoName) || subName.contains(videoName) || videoName.contains(subName)) {
                                    Sub sub = Sub.create()
                                            .name(Util.removeExt(f.getName()))
                                            .ext(ext)
                                            .url("file://" + f.getAbsolutePath());
                                    subs.add(sub);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        
        return subs;
    }

    private List<Sub> getHttpSubs(String url) {
        List<Sub> subs = new ArrayList<>();
        
        try {
            String fileName = extractName(url);
            if (fileName == null || fileName.isEmpty()) {
                return subs;
            }
            
            String videoName = Util.removeExt(fileName).toLowerCase();
            
            List<String> subExtensions = Arrays.asList("srt", "ass", "ssa", "vtt", "sub", "smi");
            for (String ext : subExtensions) {
                String subUrl = url.replace("." + Util.getExt(fileName), "." + ext);
                String subName = Util.removeExt(fileName);
                subs.add(Sub.create().name(subName).ext(ext).url(subUrl));
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        
        return subs;
    }

    private List<Sub> getFileSubs(String url) {
        List<Sub> subs = new ArrayList<>();
        
        try {
            File videoFile = new File(url.replace("file://", ""));
            File parentFile = videoFile.getParentFile();
            
            if (parentFile != null && parentFile.exists() && parentFile.canRead()) {
                String videoName = Util.removeExt(videoFile.getName()).toLowerCase();
                
                for (File f : Path.list(parentFile)) {
                    if (f.isFile()) {
                        String ext = Util.getExt(f.getName());
                        if (Util.isSub(ext)) {
                            String subName = Util.removeExt(f.getName()).toLowerCase();
                            
                            if (subName.equals(videoName) || subName.contains(videoName) || videoName.contains(subName)) {
                                Sub sub = Sub.create()
                                            .name(Util.removeExt(f.getName()))
                                            .ext(ext)
                                            .url("file://" + f.getAbsolutePath());
                                subs.add(sub);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        
        return subs;
    }
}