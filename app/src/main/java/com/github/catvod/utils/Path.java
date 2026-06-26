package com.github.catvod.utils;

import android.os.Environment;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.github.catvod.crawler.SpiderDebug;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Path {

    private static final String TAG = Path.class.getSimpleName();
    private static final int MAX_RECURSION_DEPTH = 20;
    private static final long MAX_DIR_SIZE = 1024L * 1024 * 1024 * 10;

    private static File mkdir(File file) {
        if (!file.exists()) file.mkdirs();
        return file;
    }

    public static File download() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }

    public static File root() {
        return Environment.getExternalStorageDirectory();
    }

    public static File tv() {
        return mkdir(new File(root() + File.separator + "TV"));
    }

    public static File tv(String name) {
        if (!name.startsWith(".")) name = "." + name;
        return new File(tv(), name);
    }

    public static File cache() {
        return mkdir(new File(tv(), "cache"));
    }

    public static File cache(String name) {
        return new File(cache(), name);
    }

    public static File temp() {
        return mkdir(new File(tv(), "temp"));
    }

    public static File temp(String name) {
        return new File(temp(), name);
    }

    public static File log() {
        return mkdir(new File(tv(), "log"));
    }

    public static File log(String name) {
        return new File(log(), name);
    }

    public static File config() {
        return mkdir(new File(tv(), "config"));
    }

    public static File config(String name) {
        return new File(config(), name);
    }

    public static File plugin() {
        return mkdir(new File(tv(), "plugin"));
    }

    public static File plugin(String name) {
        return new File(plugin(), name);
    }

    public static String read(File file) {
        try {
            return new String(readToByte(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public static String read(InputStream is) {
        try {
            return new String(readToByte(is), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public static byte[] readToByte(File file) throws IOException {
        try (FileInputStream is = new FileInputStream(file)) {
            return readToByte(is);
        }
    }

    public static byte[] readToByte(InputStream is) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            int read;
            byte[] buffer = new byte[16384];
            while ((read = is.read(buffer)) != -1) bos.write(buffer, 0, read);
            return bos.toByteArray();
        }
    }

    public static File write(File file, byte[] data) {
        try (FileOutputStream fos = new FileOutputStream(create(file))) {
            fos.write(data);
            fos.flush();
            return file;
        } catch (IOException e) {
            SpiderDebug.log(e);
            return file;
        }
    }

    public static File write(File file, String data) {
        return write(file, data.getBytes(StandardCharsets.UTF_8));
    }

    public static File append(File file, byte[] data) {
        try (FileOutputStream fos = new FileOutputStream(file, true)) {
            fos.write(data);
            fos.flush();
            return file;
        } catch (IOException e) {
            SpiderDebug.log(e);
            return file;
        }
    }

    public static File append(File file, String data) {
        return append(file, data.getBytes(StandardCharsets.UTF_8));
    }

    public static void move(File in, File out) {
        if (in.renameTo(out)) return;
        copy(in, out);
        delete(in);
    }

    public static void copy(File in, File out) {
        try (FileInputStream fis = new FileInputStream(in);
             FileOutputStream fos = new FileOutputStream(create(out))) {
            int read;
            byte[] buffer = new byte[16384];
            while ((read = fis.read(buffer)) != -1) fos.write(buffer, 0, read);
        } catch (IOException e) {
            SpiderDebug.log(e);
        }
    }

    public static void copy(InputStream in, File out) {
        try (FileOutputStream fos = new FileOutputStream(create(out))) {
            int read;
            byte[] buffer = new byte[16384];
            while ((read = in.read(buffer)) != -1) fos.write(buffer, 0, read);
            in.close();
        } catch (IOException e) {
            SpiderDebug.log(e);
        }
    }

    public static void sort(File[] files) {
        Arrays.sort(files, (o1, o2) -> {
            if (o1.isDirectory() && o2.isFile()) return -1;
            if (o1.isFile() && o2.isDirectory()) return 1;
            return o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase());
        });
    }

    public static void sortByTime(File[] files) {
        Arrays.sort(files, (o1, o2) -> Long.compare(o2.lastModified(), o1.lastModified()));
    }

    public static void sortBySize(File[] files) {
        Arrays.sort(files, (o1, o2) -> Long.compare(o2.length(), o1.length()));
    }

    public static List<File> list(@Nullable File dir) {
        if (dir == null) return new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) sort(files);
        return files == null ? new ArrayList<>() : Arrays.asList(files);
    }

    public static List<File> listFiles(File dir, String extension) {
        List<File> result = new ArrayList<>();
        if (dir == null || !dir.isDirectory()) return result;
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(extension.toLowerCase()));
        if (files != null) result.addAll(Arrays.asList(files));
        return result;
    }

    public static List<File> listFilesRecursive(File dir) {
        return listFilesRecursive(dir, 0);
    }

    private static List<File> listFilesRecursive(File dir, int depth) {
        List<File> result = new ArrayList<>();
        if (dir == null || !dir.isDirectory() || depth > MAX_RECURSION_DEPTH) return result;
        
        try {
            String canonicalPath = dir.getCanonicalPath();
            if (isSymlink(dir)) {
                SpiderDebug.log("Skipping symlink: " + canonicalPath);
                return result;
            }
        } catch (IOException e) {
            SpiderDebug.log(e);
            return result;
        }
        
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    result.addAll(listFilesRecursive(file, depth + 1));
                } else {
                    result.add(file);
                }
            }
        }
        return result;
    }

    public static void clear(File dir) {
        delete(dir);
    }

    public static void clearDir(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                delete(file);
            }
        }
    }

    public static File create(File file) {
        try {
            if (file.getParentFile() != null) mkdir(file.getParentFile());
            if (!file.canWrite()) file.setWritable(true);
            if (!file.exists()) file.createNewFile();
            return file;
        } catch (IOException e) {
            SpiderDebug.log(e);
            return file;
        }
    }

    public static boolean exists(String path) {
        return !TextUtils.isEmpty(path) && new File(path).exists();
    }

    public static boolean exists(File file) {
        return file != null && file.exists();
    }

    public static boolean isFile(String path) {
        return !TextUtils.isEmpty(path) && new File(path).isFile();
    }

    public static boolean isFile(File file) {
        return file != null && file.isFile();
    }

    public static boolean isDirectory(String path) {
        return !TextUtils.isEmpty(path) && new File(path).isDirectory();
    }

    public static boolean isDirectory(File file) {
        return file != null && file.isDirectory();
    }

    public static boolean canRead(String path) {
        return !TextUtils.isEmpty(path) && new File(path).canRead();
    }

    public static boolean canRead(File file) {
        return file != null && file.canRead();
    }

    public static boolean canWrite(String path) {
        return !TextUtils.isEmpty(path) && new File(path).canWrite();
    }

    public static boolean canWrite(File file) {
        return file != null && file.canWrite();
    }

    public static long length(String path) {
        return TextUtils.isEmpty(path) ? 0 : new File(path).length();
    }

    public static long length(File file) {
        return file == null ? 0 : file.length();
    }

    public static long lastModified(String path) {
        return TextUtils.isEmpty(path) ? 0 : new File(path).lastModified();
    }

    public static long lastModified(File file) {
        return file == null ? 0 : file.lastModified();
    }

    public static String getName(String path) {
        if (TextUtils.isEmpty(path)) return "";
        return new File(path).getName();
    }

    public static String getName(File file) {
        return file == null ? "" : file.getName();
    }

    public static String getParent(String path) {
        if (TextUtils.isEmpty(path)) return "";
        File parent = new File(path).getParentFile();
        return parent == null ? "" : parent.getAbsolutePath();
    }

    public static String getParent(File file) {
        if (file == null) return "";
        File parent = file.getParentFile();
        return parent == null ? "" : parent.getAbsolutePath();
    }

    public static String getExtension(String filename) {
        if (TextUtils.isEmpty(filename)) return "";
        int lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 ? filename.substring(lastDot + 1).toLowerCase() : "";
    }

    public static String getExtension(File file) {
        return file == null ? "" : getExtension(file.getName());
    }

    public static String removeExtension(String filename) {
        if (TextUtils.isEmpty(filename)) return "";
        int lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 ? filename.substring(0, lastDot) : filename;
    }

    public static String getMimeType(String filename) {
        String ext = getExtension(filename);
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
            case "avi":
                return "video/x-msvideo";
            case "mov":
                return "video/quicktime";
            case "mp3":
                return "audio/mpeg";
            case "wav":
                return "audio/wav";
            case "flac":
                return "audio/flac";
            case "pdf":
                return "application/pdf";
            case "txt":
                return "text/plain";
            case "json":
                return "application/json";
            case "xml":
                return "application/xml";
            case "html":
                return "text/html";
            case "js":
                return "application/javascript";
            case "css":
                return "text/css";
            case "zip":
                return "application/zip";
            case "rar":
                return "application/x-rar-compressed";
            case "7z":
                return "application/x-7z-compressed";
            default:
                return "application/octet-stream";
        }
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    public static int countFiles(File dir) {
        if (dir == null || !dir.isDirectory()) return 0;
        File[] files = dir.listFiles(File::isFile);
        return files == null ? 0 : files.length;
    }

    public static int countDirs(File dir) {
        if (dir == null || !dir.isDirectory()) return 0;
        File[] files = dir.listFiles(File::isDirectory);
        return files == null ? 0 : files.length;
    }

    public static long getDirSize(File dir) {
        return getDirSize(dir, 0);
    }

    private static long getDirSize(File dir, int depth) {
        if (dir == null || depth > MAX_RECURSION_DEPTH) return 0;
        if (dir.isFile()) return dir.length();
        
        try {
            String canonicalPath = dir.getCanonicalPath();
            if (isSymlink(dir)) {
                SpiderDebug.log("Skipping symlink in size calc: " + canonicalPath);
                return 0;
            }
        } catch (IOException e) {
            return 0;
        }
        
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                size += getDirSize(file, depth + 1);
                if (size > MAX_DIR_SIZE) {
                    SpiderDebug.log("Dir size exceeds limit: " + dir.getAbsolutePath());
                    return MAX_DIR_SIZE;
                }
            }
        }
        return size;
    }

    public static boolean rename(File file, String newName) {
        if (file == null || TextUtils.isEmpty(newName)) return false;
        File newFile = new File(file.getParent(), newName);
        return file.renameTo(newFile);
    }

    public static boolean delete(File file) {
        return delete(file, 0);
    }

    private static boolean delete(File file, int depth) {
        if (file == null || depth > MAX_RECURSION_DEPTH) return false;
        
        try {
            String canonicalPath = file.getCanonicalPath();
            
            if (isSymlink(file)) {
                SpiderDebug.log("Skipping symlink deletion: " + canonicalPath);
                return file.delete();
            }
            
            if (isProtectedPath(canonicalPath)) {
                SpiderDebug.log("Protected path, skipping deletion: " + canonicalPath);
                return false;
            }
        } catch (IOException e) {
            SpiderDebug.log(e);
            return false;
        }
        
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    delete(f, depth + 1);
                }
            }
        }
        
        boolean deleted = file.delete();
        if (deleted) {
            SpiderDebug.log("Deleted: " + file.getAbsolutePath());
        }
        return deleted;
    }

    private static boolean isSymlink(File file) {
        try {
            File canonicalFile = file.getCanonicalFile();
            File absoluteFile = file.getAbsoluteFile();
            return !canonicalFile.equals(absoluteFile);
        } catch (IOException e) {
            return true;
        }
    }

    private static boolean isProtectedPath(String path) {
        if (TextUtils.isEmpty(path)) return true;
        String[] protectedPaths = {
            "/system", "/data/data", "/data/app", "/proc", "/dev", 
            "/sys", "/root", "/vendor", "/cache", "/etc"
        };
        for (String protectedPath : protectedPaths) {
            if (path.startsWith(protectedPath)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEmpty(File file) {
        if (file == null) return true;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            return files == null || files.length == 0;
        }
        return file.length() == 0;
    }
}