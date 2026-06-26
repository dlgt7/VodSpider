package com.github.catvod.net;

import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

class OkUpload {

    private final Map<String, String> header;
    private final Map<String, String> params;
    private final Map<String, File> files;
    private Request request;
    private String url;

    OkUpload(String url, Map<String, String> params, Map<String, File> files, Map<String, String> header) {
        this.url = url;
        this.params = params;
        this.files = files;
        this.header = header;
        buildRequest();
    }

    private void buildRequest() {
        Request.Builder builder = new Request.Builder();
        MultipartBody.Builder multipartBody = new MultipartBody.Builder();
        multipartBody.setType(MultipartBody.FORM);

        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                multipartBody.addFormDataPart(entry.getKey(), entry.getValue());
            }
        }

        if (files != null) {
            for (Map.Entry<String, File> entry : files.entrySet()) {
                File file = entry.getValue();
                String fileName = file.getName();
                MediaType mediaType = MediaType.parse(getMimeType(fileName));
                multipartBody.addFormDataPart(entry.getKey(), fileName, RequestBody.create(mediaType, file));
            }
        }

        if (header != null) {
            for (String key : header.keySet()) {
                if (!"Content-Type".equalsIgnoreCase(key)) {
                    builder.addHeader(key, header.get(key));
                }
            }
        }

        request = builder.url(url).post(multipartBody.build()).build();
    }

    private String getMimeType(String fileName) {
        if (TextUtils.isEmpty(fileName)) return "application/octet-stream";
        String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        switch (ext) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "webp":
                return "image/webp";
            case "mp4":
                return "video/mp4";
            case "mkv":
                return "video/x-matroska";
            case "webm":
                return "video/webm";
            case "mp3":
                return "audio/mpeg";
            case "wav":
                return "audio/wav";
            case "ogg":
                return "audio/ogg";
            case "pdf":
                return "application/pdf";
            case "zip":
                return "application/zip";
            case "rar":
                return "application/x-rar-compressed";
            case "7z":
                return "application/x-7z-compressed";
            case "txt":
                return "text/plain";
            case "json":
                return "application/json";
            case "xml":
                return "application/xml";
            case "html":
                return "text/html";
            default:
                return "application/octet-stream";
        }
    }

    public OkResult execute(OkHttpClient client) {
        try (Response res = client.newCall(request).execute()) {
            return new OkResult(res.code(), res.body().string(), res.headers().toMultimap());
        } catch (IOException e) {
            SpiderDebug.log(e);
            return new OkResult();
        }
    }
}