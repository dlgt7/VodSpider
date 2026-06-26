package com.github.catvod.spider;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Sub;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Image;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Local extends Spider {

    private SimpleDateFormat format;
    private SimpleDateFormat dateFormat;

    @Override
    public void init(Context context, String extend) {
        format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault());
        dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SpiderDebug.log("Local Spider initialized");
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            List<Class> classes = new ArrayList<>();
            classes.add(new Class(Environment.getExternalStorageDirectory().getAbsolutePath(), "本地文件", "1"));
            
            File[] files = new File("/storage").listFiles();
            if (files == null) {
                return Result.string(classes);
            }
            
            List<String> exclude = Arrays.asList("emulated", "sdcard", "self");
            for (File file : files) {
                if (exclude.contains(file.getName())) continue;
                if (file.isDirectory() && file.canRead()) {
                    classes.add(new Class(file.getAbsolutePath(), file.getName(), "1"));
                }
            }
            return Result.string(classes);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("加载本地文件失败: " + e.getMessage());
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            List<Vod> items = new ArrayList<>();
            File dir = new File(tid);
            
            if (!dir.exists() || !dir.isDirectory()) {
                return Result.error("目录不存在或无法访问");
            }
            
            String sortBy = extend != null ? extend.get("sort") : null;
            String fileType = extend != null ? extend.get("type") : null;
            
            List<File> files = Path.list(dir);
            for (File file : files) {
                if (file.getName().startsWith(".")) continue;
                
                boolean shouldAdd = false;
                if (file.isDirectory()) {
                    shouldAdd = true;
                } else if (TextUtils.isEmpty(fileType)) {
                    shouldAdd = Util.isMedia(file.getName());
                } else {
                    switch (fileType) {
                        case "video":
                            shouldAdd = Util.isVideo(file.getName());
                            break;
                        case "audio":
                            shouldAdd = Util.isAudio(file.getName());
                            break;
                        case "image":
                            shouldAdd = Util.isImage(file.getName());
                            break;
                        default:
                            shouldAdd = Util.isMedia(file.getName());
                            break;
                    }
                }
                
                if (shouldAdd) {
                    items.add(create(file));
                }
            }
            
            sortItems(items, sortBy);
            
            int page = TextUtils.isEmpty(pg) ? 1 : Integer.parseInt(pg);
            int pageSize = 50;
            int total = items.size();
            int pageCount = (int) Math.ceil((double) total / pageSize);
            
            int fromIndex = (page - 1) * pageSize;
            int toIndex = Math.min(fromIndex + pageSize, total);
            
            List<Vod> pagedItems = fromIndex < total ? items.subList(fromIndex, toIndex) : new ArrayList<>();
            
            return Result.get().vod(pagedItems).page(page, pageCount, pageSize, total).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("加载目录内容失败: " + e.getMessage());
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            if (ids == null || ids.isEmpty()) {
                return Result.error("缺少文件路径");
            }
            
            String url = ids.get(0);
            if (url.startsWith("http")) {
                String name = Uri.parse(url).getLastPathSegment();
                if (name == null) name = "未知";
                return Result.string(create(name, url));
            } else {
                File file = new File(url);
                if (!file.exists()) {
                    return Result.error("文件不存在");
                }
                
                if (file.isDirectory()) {
                    List<File> files = Path.list(file);
                    return Result.string(createFolder(file, files));
                } else {
                    File parent = file.getParentFile();
                    if (parent == null) {
                        return Result.string(createSingle(file));
                    }
                    
                    List<File> files = Path.list(parent);
                    return Result.string(createFolder(parent, files));
                }
            }
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
            
            if (id.startsWith("http")) {
                return Result.get().url(id).string();
            } else {
                File file = new File(id);
                if (!file.exists()) {
                    return Result.error("文件不存在");
                }
                
                String mimeType = getMimeType(file.getName());
                Result result = Result.get().url("file://" + id);
                
                if (Util.isVideo(file.getName())) {
                    result.subs(getSubs(id));
                }
                
                if (!TextUtils.isEmpty(mimeType)) {
                    result.format(mimeType);
                }
                
                return result.string();
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("播放失败: " + e.getMessage());
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        try {
            if (TextUtils.isEmpty(key)) {
                return Result.error("搜索关键词不能为空");
            }
            
            List<Vod> items = new ArrayList<>();
            File rootDir = Environment.getExternalStorageDirectory();
            
            searchFiles(rootDir, key.toLowerCase(), items, 100);
            
            int page = TextUtils.isEmpty(pg) ? 1 : Integer.parseInt(pg);
            int pageSize = 20;
            int total = items.size();
            int pageCount = (int) Math.ceil((double) total / pageSize);
            
            int fromIndex = (page - 1) * pageSize;
            int toIndex = Math.min(fromIndex + pageSize, total);
            
            List<Vod> pagedItems = fromIndex < total ? items.subList(fromIndex, toIndex) : new ArrayList<>();
            
            return Result.get().vod(pagedItems).page(page, pageCount, pageSize, total).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("搜索失败: " + e.getMessage());
        }
    }

    @Override
    public Object[] proxy(Map<String, String> params) {
        try {
            if (params == null || !params.containsKey("path")) {
                return new Object[]{400, "text/plain", new ByteArrayInputStream("Missing path parameter".getBytes())};
            }
            
            String path = new String(Base64.decode(params.get("path"), Base64.DEFAULT | Base64.URL_SAFE));
            File file = new File(path);
            
            if (!file.exists()) {
                return new Object[]{404, "text/plain", new ByteArrayInputStream("File not found".getBytes())};
            }
            
            if (!file.canRead()) {
                return new Object[]{403, "text/plain", new ByteArrayInputStream("File not readable".getBytes())};
            }
            
            byte[] data = getThumbnail(path);
            return new Object[]{200, "image/jpeg", new ByteArrayInputStream(data)};
        } catch (Exception e) {
            SpiderDebug.log(e);
            return new Object[]{500, "text/plain", new ByteArrayInputStream("Internal error".getBytes())};
        }
    }

    private void searchFiles(File dir, String keyword, List<Vod> results, int maxResults) {
        if (results.size() >= maxResults) return;
        if (!dir.exists() || !dir.isDirectory() || !dir.canRead()) return;
        
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (results.size() >= maxResults) break;
            
            if (file.getName().toLowerCase().contains(keyword)) {
                if (file.isFile() && Util.isMedia(file.getName())) {
                    results.add(create(file));
                }
            }
            
            if (file.isDirectory() && !file.getName().startsWith(".")) {
                searchFiles(file, keyword, results, maxResults);
            }
        }
    }

    private void sortItems(List<Vod> items, String sortBy) {
        if (TextUtils.isEmpty(sortBy)) {
            sortBy = "name";
        }
        
        switch (sortBy) {
            case "name":
                Collections.sort(items, (a, b) -> a.getVodName().compareToIgnoreCase(b.getVodName()));
                break;
            case "name_desc":
                Collections.sort(items, (a, b) -> b.getVodName().compareToIgnoreCase(a.getVodName()));
                break;
            case "date":
                Collections.sort(items, (a, b) -> b.getVodRemarks().compareTo(a.getVodRemarks()));
                break;
            case "date_asc":
                Collections.sort(items, (a, b) -> a.getVodRemarks().compareTo(b.getVodRemarks()));
                break;
            default:
                Collections.sort(items, (a, b) -> a.getVodName().compareToIgnoreCase(b.getVodName()));
                break;
        }
    }

    private Vod create(String name, String url) {
        Vod vod = new Vod();
        vod.setTypeName("FongMi");
        vod.setVodId(url);
        vod.setVodName(name);
        vod.setVodPic(Image.VIDEO);
        vod.setVodPlayFrom("播放");
        vod.setVodPlayUrl(name + "$" + url);
        return vod;
    }

    private Vod createSingle(File file) {
        Vod vod = new Vod();
        vod.setTypeName("FongMi");
        vod.setVodId(file.getAbsolutePath());
        vod.setVodName(file.getName());
        vod.setVodPic(Image.VIDEO);
        vod.setVodPlayFrom("播放");
        vod.setVodPlayUrl(file.getName() + "$" + file.getAbsolutePath());
        return vod;
    }

    private Vod createFolder(File folder, List<File> files) {
        Vod vod = new Vod();
        vod.setTypeName("FongMi");
        vod.setVodId(folder.getAbsolutePath());
        vod.setVodName(folder.getName());
        vod.setVodPic(Image.VIDEO);
        vod.setVodPlayFrom("播放");
        
        List<String> playUrls = new ArrayList<>();
        for (File f : files) {
            if (f.isFile() && Util.isMedia(f.getName())) {
                playUrls.add(f.getName() + "$" + f.getAbsolutePath());
            }
        }
        
        if (playUrls.isEmpty()) {
            playUrls.add("无媒体文件$" + folder.getAbsolutePath());
        }
        
        vod.setVodPlayUrl(Util.join("#", playUrls));
        return vod;
    }

    private Vod create(File file) {
        Vod vod = new Vod();
        vod.setVodId(file.getAbsolutePath());
        vod.setVodName(file.getName());
        
        if (file.isFile()) {
            String thumbUrl = Proxy.getUrl(siteKey, "&path=" + Base64.encodeToString(file.getAbsolutePath().getBytes(), Base64.DEFAULT | Base64.URL_SAFE));
            vod.setVodPic(thumbUrl);
            
            String size = formatFileSize(file.length());
            String date = format.format(file.lastModified());
            vod.setVodRemarks(size + " | " + date);
        } else {
            vod.setVodPic(Image.FOLDER);
            vod.setVodRemarks(format.format(file.lastModified()));
        }
        
        vod.setVodTag(file.isDirectory() ? "folder" : "file");
        return vod;
    }

    private byte[] getThumbnail(String path) {
        try {
            Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND);
            if (bitmap == null) {
                return Base64.decode(Image.VIDEO.split("base64,")[1], Base64.DEFAULT);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            byte[] result = baos.toByteArray();
            bitmap.recycle();
            baos.close();
            return result;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Base64.decode(Image.VIDEO.split("base64,")[1], Base64.DEFAULT);
        }
    }

    private List<Sub> getSubs(String path) {
        List<Sub> subs = new ArrayList<>();
        try {
            File videoFile = new File(path);
            File parentFile = videoFile.getParentFile();
            
            if (parentFile == null || !parentFile.exists() || !parentFile.canRead()) {
                return subs;
            }
            
            String videoName = Util.removeExt(videoFile.getName()).toLowerCase();
            
            for (File f : Path.list(parentFile)) {
                if (f.isFile()) {
                    String ext = Util.getExt(f.getName());
                    if (isSubtitleFile(ext)) {
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
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return subs;
    }

    private boolean isSubtitleFile(String ext) {
        if (TextUtils.isEmpty(ext)) return false;
        String lower = ext.toLowerCase();
        return lower.equals("srt") || lower.equals("ass") || lower.equals("ssa") 
            || lower.equals("vtt") || lower.equals("sub") || lower.equals("smi");
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    private String getMimeType(String filename) {
        String ext = Util.getExt(filename);
        if (TextUtils.isEmpty(ext)) return "";
        
        switch (ext.toLowerCase()) {
            case "mp4":
                return "video/mp4";
            case "mkv":
                return "video/x-matroska";
            case "avi":
                return "video/x-msvideo";
            case "mov":
                return "video/quicktime";
            case "wmv":
                return "video/x-ms-wmv";
            case "flv":
                return "video/x-flv";
            case "webm":
                return "video/webm";
            case "m3u8":
                return "application/x-mpegURL";
            case "ts":
                return "video/MP2T";
            case "mp3":
                return "audio/mpeg";
            case "wav":
                return "audio/wav";
            case "flac":
                return "audio/flac";
            case "aac":
                return "audio/aac";
            case "ogg":
                return "audio/ogg";
            case "m4a":
                return "audio/mp4";
            default:
                return "";
        }
    }
}