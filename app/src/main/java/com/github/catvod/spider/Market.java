package com.github.catvod.spider;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.bean.market.Data;
import com.github.catvod.bean.market.Item;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.FileUtil;
import com.github.catvod.utils.Notify;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import okhttp3.Response;

public class Market extends Spider {

    private static final String TAG = Market.class.getSimpleName();
    private static final int MAX_FILE_SIZE = 100 * 1024 * 1024;
    
    private static final Set<String> TRUSTED_SIGNATURES = new HashSet<>();
    
    static {
        TRUSTED_SIGNATURES.add("YOUR_APP_SIGNATURE_HERE");
    }
    
    private static final boolean SIGNATURE_CHECK_ENABLED = false;

    private List<Data> datas;
    private Context context;

    @Override
    public void init(Context context, String extend) {
        this.context = context;
        try {
            if (TextUtils.isEmpty(extend)) {
                datas = new ArrayList<>();
                SpiderDebug.log("Market: extend is empty");
                return;
            }
            
            if (extend.startsWith("http")) {
                SpiderDebug.log("Market: loading from url: " + extend);
                extend = OkHttp.string(extend);
            }
            
            datas = Data.arrayFrom(extend);
            SpiderDebug.log("Market: loaded " + datas.size() + " data sources");
        } catch (Exception e) {
            SpiderDebug.log(e);
            datas = new ArrayList<>();
        }
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            if (datas == null || datas.isEmpty()) {
                return Result.error("没有可用的数据源");
            }
            
            List<Class> classes = new ArrayList<>();
            if (datas.size() > 1) {
                for (int i = 1; i < datas.size(); i++) {
                    Class type = datas.get(i).type();
                    if (type != null) {
                        classes.add(type);
                    }
                }
            }
            
            List<Vod> vodList = datas.get(0).getVod();
            return Result.string(classes, vodList != null ? vodList : new ArrayList<>());
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("加载首页内容失败: " + e.getMessage());
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            if (TextUtils.isEmpty(tid)) {
                return Result.error("缺少分类ID");
            }
            
            if (datas == null || datas.isEmpty()) {
                return Result.error("没有可用的数据源");
            }
            
            for (Data data : datas) {
                if (data != null && tid.equals(data.getName())) {
                    List<Vod> vodList = data.getVod();
                    return Result.get()
                            .vod(vodList != null ? vodList : new ArrayList<>())
                            .page()
                            .string();
                }
            }
            
            return Result.error("未找到分类: " + tid);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("加载分类内容失败: " + e.getMessage());
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            if (ids == null || ids.isEmpty()) {
                return Result.error("缺少项目ID");
            }
            
            String id = ids.get(0);
            if (TextUtils.isEmpty(id)) {
                return Result.error("项目ID为空");
            }
            
            if (datas == null || datas.isEmpty()) {
                return Result.error("没有可用的数据源");
            }
            
            for (Data data : datas) {
                if (data == null || data.getList() == null) continue;
                
                int index = data.getList().indexOf(new Item(id));
                if (index != -1) {
                    Item item = data.getList().get(index);
                    Vod vod = createVodFromItem(item);
                    return Result.string(vod);
                }
            }
            
            return Result.error("未找到项目: " + id);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("加载详情失败: " + e.getMessage());
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            if (TextUtils.isEmpty(key)) {
                return Result.error("搜索关键词不能为空");
            }
            
            if (datas == null || datas.isEmpty()) {
                return Result.error("没有可用的数据源");
            }
            
            List<Vod> results = new ArrayList<>();
            String lowerKey = key.toLowerCase(Locale.getDefault());
            
            for (Data data : datas) {
                if (data == null || data.getList() == null) continue;
                
                for (Item item : data.getList()) {
                    if (item != null && item.getName() != null) {
                        if (item.getName().toLowerCase(Locale.getDefault()).contains(lowerKey)) {
                            results.add(createVodFromItem(item));
                        }
                    }
                }
            }
            
            return Result.get().vod(results).page().string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("搜索失败: " + e.getMessage());
        }
    }

    @Override
    public String action(String action) {
        try {
            if (TextUtils.isEmpty(action)) {
                return Result.error("缺少下载地址");
            }
            
            OkHttp.cancel(TAG);
            
            String name = Uri.parse(action).getLastPathSegment();
            if (TextUtils.isEmpty(name)) {
                name = "download_" + System.currentTimeMillis();
            }
            
            Notify.show("正在下载: " + name);
            SpiderDebug.log("Market: downloading " + name + " from " + action);
            
            Response response = OkHttp.newCall(action, TAG);
            if (!response.isSuccessful()) {
                response.close();
                return Result.error("下载失败: HTTP " + response.code());
            }
            
            long contentLength = response.body().contentLength();
            if (contentLength > MAX_FILE_SIZE) {
                response.close();
                return Result.error("文件过大，拒绝下载");
            }
            
            File downloadDir = Path.download();
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }
            
            File file = Path.create(new File(downloadDir, name));
            boolean success = download(file, response.body().byteStream(), contentLength);
            response.close();
            
            if (!success) {
                return Result.error("下载失败: 写入文件失败");
            }
            
            SpiderDebug.log("Market: download completed: " + file.getAbsolutePath());
            
            String extension = getFileExtension(name);
            switch (extension) {
                case "zip":
                    FileUtil.unzip(file, downloadDir);
                    Notify.show("解压完成: " + name);
                    break;
                case "apk":
                    if (SIGNATURE_CHECK_ENABLED && !verifyApkSignature(file)) {
                        file.delete();
                        return Result.error("APK 签名验证失败，已删除文件");
                    }
                    FileUtil.openFile(file);
                    Notify.show("安装包已下载: " + name);
                    break;
                case "json":
                case "txt":
                    Notify.show("文件已下载: " + name);
                    break;
                default:
                    Notify.show("下载完成: " + name);
                    break;
            }
            
            checkCopy(action);
            
            return Result.notify("下载完成: " + name);
        } catch (Exception e) {
            SpiderDebug.log(e);
            Notify.show("下载失败: " + e.getMessage());
            return Result.error("下载失败: " + e.getMessage());
        }
    }

    private boolean download(File file, InputStream is, long contentLength) {
        try (BufferedInputStream input = new BufferedInputStream(is);
             FileOutputStream os = new FileOutputStream(file)) {
            
            byte[] buffer = new byte[16384];
            int readBytes;
            long totalRead = 0;
            long lastNotifyTime = 0;
            
            while ((readBytes = input.read(buffer)) != -1) {
                os.write(buffer, 0, readBytes);
                totalRead += readBytes;
                
                if (contentLength > 0) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastNotifyTime > 1000) {
                        int progress = (int) (totalRead * 100 / contentLength);
                        SpiderDebug.log("Market: download progress " + progress + "%");
                        lastNotifyTime = currentTime;
                    }
                }
            }
            
            return true;
        } catch (IOException e) {
            SpiderDebug.log(e);
            return false;
        }
    }

    private boolean verifyApkSignature(File apkFile) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo packageInfo = pm.getPackageArchiveInfo(
                    apkFile.getAbsolutePath(),
                    PackageManager.GET_SIGNATURES
            );
            
            if (packageInfo == null || packageInfo.signatures == null) {
                SpiderDebug.log("Market: unable to read APK signatures");
                return false;
            }
            
            for (Signature signature : packageInfo.signatures) {
                String signatureHash = getSignatureHash(signature);
                SpiderDebug.log("Market: APK signature hash: " + signatureHash);
                
                if (TRUSTED_SIGNATURES.contains(signatureHash)) {
                    SpiderDebug.log("Market: signature verified successfully");
                    return true;
                }
            }
            
            SpiderDebug.log("Market: signature not in trusted list");
            return !TRUSTED_SIGNATURES.isEmpty();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return false;
        }
    }

    private String getSignatureHash(Signature signature) {
        try {
            byte[] rawCert = signature.toByteArray();
            InputStream certStream = new ByteArrayInputStream(rawCert);
            CertificateFactory certFactory = CertificateFactory.getInstance("X509");
            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(certStream);
            
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] publicKey = md.digest(cert.getPublicKey().getEncoded());
            
            StringBuilder sb = new StringBuilder();
            for (byte b : publicKey) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    private void checkCopy(String url) {
        try {
            if (datas == null) return;
            
            for (Data data : datas) {
                if (data == null || data.getList() == null) continue;
                
                int index = data.getList().indexOf(new Item(url));
                if (index == -1) continue;
                
                String text = data.getList().get(index).getCopy();
                if (!TextUtils.isEmpty(text)) {
                    Util.copy(text);
                    Notify.show("已复制: " + text);
                }
                break;
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    private Vod createVodFromItem(Item item) {
        Vod vod = new Vod();
        vod.setVodId(item.getUrl());
        vod.setVodName(item.getName());
        vod.setVodPic(item.getIcon());
        vod.setVodRemarks(item.getCopy());
        vod.setVodTag("file");
        return vod;
    }

    private String getFileExtension(String filename) {
        if (TextUtils.isEmpty(filename)) return "";
        int lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 ? filename.substring(lastDot + 1).toLowerCase() : "";
    }

    @Override
    public void destroy() {
        try {
            OkHttp.cancel(TAG);
            SpiderDebug.log("Market: destroyed");
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }
}