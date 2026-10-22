package com.rommayo.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.LruCache;
import android.webkit.CookieManager;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Direct native API client. The WebView cookie store is the shared session authority. */
final class RommApi {
    interface Result { void complete(String body, String error); }
    interface ImageResult { void complete(Bitmap bitmap); }
    private final ExecutorService requests = Executors.newFixedThreadPool(2);
    private final ExecutorService images = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> covers = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private volatile boolean closed;
    String server;
    RommApi(String server) { this.server = server; }
    void get(String path, Result result) { request(path, null, false, result); }
    void logout(Result result) { request("api/logout", null, true, result); }
    void login(String user, String password, Result result) {
        request("api/login", Base64.encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP), true, result);
    }
    private void request(String path, String basic, boolean post, Result result) {
        String address = server + path;
        String cookie = basic == null ? CookieManager.getInstance().getCookie(address) : null;
        requests.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = open(address, cookie);
                if (post) connection.setRequestMethod("POST");
                if (basic != null) connection.setRequestProperty("Authorization", "Basic " + basic);
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) throw new ApiError(code == 401 || code == 403 ? "Your session expired or sign-in was rejected. Sign in again, or use Full RomM for SSO." : "RomM returned HTTP " + code + ". Retry or open Full RomM.");
                String body;
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[16384]; int count;
                    while ((count = input.read(buffer)) != -1) { output.write(buffer, 0, count); if (output.size() > 12 * 1024 * 1024) throw new ApiError("RomM returned too much data. Try a narrower search."); }
                    body = output.toString("UTF-8");
                }
                List<String> cookies = new ArrayList<>();
                for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) if (header.getKey() != null && header.getKey().equalsIgnoreCase("Set-Cookie")) cookies.addAll(header.getValue());
                if (basic != null && cookies.isEmpty()) throw new ApiError("RomM did not return a session cookie. Use Full RomM sign-in.");
                main.post(() -> {
                    if (closed) return;
                    if (cookies.isEmpty()) { result.complete(body, null); return; }
                    java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(cookies.size());
                    for (String value : cookies) CookieManager.getInstance().setCookie(server, value, success -> {
                        if (remaining.decrementAndGet() == 0 && !closed) { CookieManager.getInstance().flush(); result.complete(body, null); }
                    });
                });
            } catch (Exception error) {
                String message = error instanceof ApiError ? error.getMessage() : "Could not reach RomM. Check the address, network and certificate.";
                main.post(() -> { if (!closed) result.complete(null, message); });
            } finally { if (connection != null) connection.disconnect(); }
        });
    }
    private static final class ApiError extends Exception { ApiError(String message) { super(message); } }
    private static HttpURLConnection open(String address, String cookie) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10000); connection.setReadTimeout(20000); connection.setInstanceFollowRedirects(false);
        if (cookie != null) connection.setRequestProperty("Cookie", cookie);
        return connection;
    }
    void cover(String path, ImageResult result) {
        if (path == null || path.isEmpty() || path.equals("null")) return;
        try {
            String address = new URL(new URL(server), path.replace(" ", "%20")).toString();
            // Authenticated cover requests never leave the configured RomM origin.
            if (!ServerPolicy.origin(server).equals(ServerPolicy.origin(address))) return;
            Bitmap cached = covers.get(address); if (cached != null) { result.complete(cached); return; }
            String cookie = CookieManager.getInstance().getCookie(address);
            images.execute(() -> {
                HttpURLConnection connection = null;
                try {
                    connection = open(address, cookie);
                    if (connection.getResponseCode() != 200) return;
                    byte[] data;
                    try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[8192]; int count;
                        while ((count = input.read(buffer)) != -1) { output.write(buffer, 0, count); if (output.size() > 4 * 1024 * 1024) return; }
                        data = output.toByteArray();
                    }
                    BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(data, 0, data.length, options);
                    options.inSampleSize = 1;
                    while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 640) options.inSampleSize *= 2;
                    options.inJustDecodeBounds = false;
                    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                    if (bitmap != null && !closed) { covers.put(address, bitmap); main.post(() -> { if (!closed) result.complete(bitmap); }); }
                } catch (Exception ignored) { /* A missing cover keeps its native placeholder. */ }
                finally { if (connection != null) connection.disconnect(); }
            });
        } catch (Exception ignored) {}
    }
    void close() { closed = true; requests.shutdownNow(); images.shutdownNow(); covers.evictAll(); }
}
