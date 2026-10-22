package com.rommayo.app;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends ComponentActivity {
    private static final String SHELL_ORIGIN = "https://appassets.androidplatform.net";
    private static final int PICK_FILES = 41, SAVE_BLOB = 42;
    private WebView shell, remote;
    private LinearLayout root;
    private FrameLayout remoteContainer;
    private SharedPreferences preferences;
    private String server = "";
    private JSONArray servers = new JSONArray();
    private final Set<String> allowedOrigins = new HashSet<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ValueCallback<Uri[]> fileCallback;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private boolean fullscreen = false, connecting = false, steamTheme = true;
    private String theme = "";
    private File blobFile;
    private OutputStream blobStream;
    private String blobToken = "", blobName = "download", blobMime = "application/octet-stream";

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { handleBack(); }
        });
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        preferences = getSharedPreferences("rommayo", MODE_PRIVATE);
        try { servers = new JSONArray(preferences.getString("servers", "[]")); } catch (Exception ignored) {}
        try (InputStream input = getAssets().open("rommayo-theme.css")) { java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream(); copy(input, buffer); theme = buffer.toString("UTF-8"); } catch (Exception ignored) {}
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(16, 24, 34));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets;
        });
        setContentView(root);
        shell = new WebView(this); configure(shell); shell.setBackgroundColor(Color.rgb(16, 24, 34));
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder().addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        shell.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) { return loader.shouldInterceptRequest(request.getUrl()); }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return !SHELL_ORIGIN.equals(ServerPolicy.origin(request.getUrl().toString())); }
        });
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            new AlertDialog.Builder(this).setMessage("Please update Android System WebView or Chrome to use Rommayo.").setPositiveButton("Close", (d, w) -> finish()).show(); return;
        }
        WebViewCompat.addWebMessageListener(shell, "rommayoBridge", java.util.Collections.singleton(SHELL_ORIGIN), (view, message, origin, mainFrame, reply) -> {
            if (!mainFrame || !SHELL_ORIGIN.equals(origin.toString())) return;
            try { handle(new JSONObject(message.getData()), reply); } catch (Exception error) { reply.postMessage(state(error.getMessage()).toString()); }
        });
        root.addView(shell, new LinearLayout.LayoutParams(-1, -1));
        remoteContainer = new FrameLayout(this);
        shell.loadUrl(SHELL_ORIGIN + "/assets/index.html");
        CookieManager.getInstance().setAcceptCookie(true);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
    }
    private void configure(WebView view) {
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportMultipleWindows(true); settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setUseWideViewPort(true); settings.setLoadWithOverviewMode(true);
        view.setFocusable(true); view.setFocusableInTouchMode(true);
    }
    private JSONObject state(String error) {
        JSONObject result = new JSONObject();
        try { result.put("connected", remote != null); result.put("server", server); result.put("servers", servers); if (error != null) result.put("error", error); } catch (Exception ignored) {}
        return result;
    }
    private void notifyState(String error) {
        if (shell == null || isDestroyed()) return;
        shell.evaluateJavascript("window.rommayoState && window.rommayoState(" + state(error) + ");", null);
        if (remote != null) shell.evaluateJavascript("document.getElementById('password').value='';", null);
    }
    private void handle(JSONObject command, JavaScriptReplyProxy reply) throws Exception {
        switch (command.optString("type")) {
            case "init": reply.postMessage(state(null).toString()); return;
            case "connect": connect(command, reply); return;
            case "back": if (remote != null && remote.canGoBack()) remote.goBack(); break;
            case "home": if (remote != null) remote.loadUrl(server); break;
            case "reload": if (remote != null) remote.reload(); break;
            case "fullscreen": setFullscreen(!fullscreen); break;
            case "disconnect": disconnect(); break;
            case "forget":
                new AlertDialog.Builder(this).setTitle("Forget saved sessions?").setMessage("Sign this app out of every saved server and clear its web data? Your server library is unchanged.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Forget", (d, w) -> {
                        disconnect(); CookieManager.getInstance().removeAllCookies(done -> CookieManager.getInstance().flush());
                        android.webkit.WebStorage.getInstance().deleteAllData(); shell.clearCache(true);
                        servers = new JSONArray(); server = ""; preferences.edit().clear().apply(); notifyState(null);
                    }).show(); return;
            default: throw new Exception("Unknown command.");
        }
        reply.postMessage(state(null).toString());
    }
    private void connect(JSONObject command, JavaScriptReplyProxy reply) throws Exception {
        if (connecting) throw new Exception("A connection is already in progress.");
        String address = ServerPolicy.normalize(command.optString("server"));
        if (!command.optBoolean("consent")) throw new Exception("Confirm that you trust this server and understand the content notice.");
        if (address.startsWith("http:") && !command.optBoolean("allowHttp")) throw new Exception("Confirm the unencrypted HTTP connection.");
        boolean webLogin = command.optBoolean("webLogin");
        String username = command.optString("username"), password = command.optString("password");
        if (!webLogin && (username.isEmpty() || password.isEmpty() || username.contains(":") || username.length() > 256 || password.length() > 4096)) throw new Exception("Enter your RomM username and password, or use RomM sign-in.");
        steamTheme = command.optBoolean("steamTheme", true);
        connecting = true;
        worker.execute(() -> {
            HttpURLConnection connection = null;
            try {
                List<String> cookies = null;
                if (!webLogin) {
                    connection = (HttpURLConnection) new URL(address + "api/login").openConnection();
                    connection.setConnectTimeout(15000); connection.setReadTimeout(20000); connection.setInstanceFollowRedirects(false); connection.setRequestMethod("POST");
                    connection.setRequestProperty("Authorization", "Basic " + Base64.encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
                    int code = connection.getResponseCode();
                    if (code < 200 || code > 299) throw new Exception(code == 401 || code == 403 ? "RomM rejected this username or password." : "RomM sign-in failed (HTTP " + code + "). Try RomM sign-in for SSO.");
                    for (String key : connection.getHeaderFields().keySet()) if (key != null && key.equalsIgnoreCase("Set-Cookie")) cookies = connection.getHeaderFields().get(key);
                    if (cookies == null || cookies.isEmpty()) throw new Exception("RomM did not return a session cookie. Try RomM sign-in.");
                }
                List<String> received = cookies;
                runOnUiThread(() -> {
                    connecting = false;
                    if (isDestroyed()) return;
                    CookieManager manager = CookieManager.getInstance();
                    if (received == null) { openServer(address); reply.postMessage(state(null).toString()); }
                    else {
                        java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(received.size());
                        for (String cookie : received) manager.setCookie(address, cookie, done -> {
                            if (remaining.decrementAndGet() == 0) { manager.flush(); openServer(address); reply.postMessage(state(null).toString()); }
                        });
                    }
                });
            } catch (Exception error) {
                String message = error.getMessage() != null && error.getMessage().startsWith("RomM") ? error.getMessage() : "Could not reach RomM. Check the address, network and server certificate.";
                runOnUiThread(() -> { connecting = false; if (!isDestroyed()) reply.postMessage(state(message).toString()); });
            } finally { if (connection != null) connection.disconnect(); command.remove("password"); }
        });
    }
    private void openServer(String address) {
        disconnect(); server = address;
        JSONArray recent = new JSONArray(); recent.put(address);
        for (int i = 0; i < servers.length() && recent.length() < 8; i++) if (!address.equals(servers.optString(i))) recent.put(servers.optString(i));
        servers = recent; preferences.edit().putString("servers", servers.toString()).apply();
        allowedOrigins.clear(); allowedOrigins.add(ServerPolicy.origin(address));
        remote = new WebView(this); configure(remote); remote.setBackgroundColor(Color.rgb(16, 24, 34));
        // This WebView has no general purpose native bridge. The download bridge is origin restricted and token gated.
        WebViewCompat.addWebMessageListener(remote, "rommayoDownload", java.util.Collections.singleton(ServerPolicy.origin(address)), (view, message, origin, mainFrame, reply) -> {
            if (mainFrame && ServerPolicy.origin(address).equals(origin.toString())) receiveBlob(message.getData(), reply);
        });
        remote.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                String url = request.getUrl().toString(), origin = ServerPolicy.origin(url);
                if (!origin.isEmpty() && allowedOrigins.contains(origin)) return false;
                external(url); return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                if (steamTheme && ServerPolicy.origin(server).equals(ServerPolicy.origin(url))) {
                    view.evaluateJavascript("(()=>{let s=document.getElementById('rommayo-theme');if(!s){s=document.createElement('style');s.id='rommayo-theme';document.head.append(s)}s.textContent=" + JSONObject.quote(theme) + ";})()", null);
                }
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (!request.isForMainFrame()) return;
                new AlertDialog.Builder(MainActivity.this).setTitle("Server unavailable").setMessage("Check your network and RomM server. You can retry or switch servers.")
                    .setPositiveButton("Retry", (d, w) -> view.reload()).setNegativeButton("Switch server", (d, w) -> disconnect()).show();
            }
        });
        remote.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                String[] types = params.getAcceptTypes();
                // HTML accept values may contain extensions; Android's picker expects MIME types.
                java.util.ArrayList<String> mime = new java.util.ArrayList<>();
                for (String type : types) if (type.contains("/")) mime.add(type);
                if (!mime.isEmpty()) intent.putExtra(Intent.EXTRA_MIME_TYPES, mime.toArray(new String[0]));
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try { startActivityForResult(intent, PICK_FILES); } catch (Exception error) { fileCallback = null; callback.onReceiveValue(null); toast("No file picker is available on this device."); }
                return true;
            }
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view; customCallback = callback; root.setVisibility(View.GONE);
                ((FrameLayout) getWindow().getDecorView()).addView(view, new FrameLayout.LayoutParams(-1, -1)); setFullscreen(true);
            }
            @Override public void onHideCustomView() { hideCustomView(); }
            @Override public boolean onCreateWindow(WebView view, boolean dialog, boolean gesture, android.os.Message message) {
                if (!gesture) return false;
                WebView popup = new WebView(MainActivity.this);
                popup.setWebViewClient(new WebViewClient() {
                    @Override public boolean shouldOverrideUrlLoading(WebView ignored, WebResourceRequest request) {
                        String url = request.getUrl().toString();
                        if (allowedOrigins.contains(ServerPolicy.origin(url)) && remote != null) remote.loadUrl(url); else external(url);
                        popup.destroy(); return true;
                    }
                });
                ((WebView.WebViewTransport) message.obj).setWebView(popup); message.sendToTarget(); return true;
            }
        });
        remote.setDownloadListener((url, agent, disposition, mime, length) -> download(url, agent, disposition, mime));
        remote.setKeepScreenOn(true);
        shell.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(64)));
        root.addView(remoteContainer, new LinearLayout.LayoutParams(-1, 0, 1));
        remoteContainer.addView(remote, new FrameLayout.LayoutParams(-1, -1));
        remote.loadUrl(address); remote.requestFocus(); notifyState(null);
    }
    private void external(String url) {
        String origin = ServerPolicy.origin(url); if (origin.isEmpty()) { toast("This link type is not supported. Open it in a browser."); return; }
        new AlertDialog.Builder(this).setTitle("Leave your RomM server?").setMessage("Open " + origin + "? For single sign-on, open here. Only continue to a site you trust.")
            .setNegativeButton("Cancel", null).setNeutralButton("Browser", (d, w) -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception error) { toast("No browser is available."); }
            }).setPositiveButton("Open here", (d, w) -> { allowedOrigins.add(origin); if (remote != null) remote.loadUrl(url); }).show();
    }
    private void download(String url, String agent, String disposition, String mime) {
        if (url.startsWith("blob:")) { startBlob(url, disposition, mime); return; }
        if (ServerPolicy.origin(url).isEmpty()) { toast("This download type is not supported by Android WebView."); return; }
        // Never send RomM cookies to another origin. External download hosts are opened in the browser.
        if (!ServerPolicy.origin(server).equals(ServerPolicy.origin(url))) { external(url); return; }
        String name = URLUtil.guessFileName(url, disposition, mime);
        new AlertDialog.Builder(this).setTitle("Save from your server?").setMessage(name + "\n\nOnly download content you are authorized to use. Downloads are at your own risk.")
            .setNegativeButton("Cancel", null).setPositiveButton("Download", (d, w) -> {
                try {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    String cookie = CookieManager.getInstance().getCookie(url);
                    if (cookie != null) request.addRequestHeader("Cookie", cookie);
                    request.addRequestHeader("User-Agent", agent); if (mime != null) request.setMimeType(mime);
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeFilename(name));
                    ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(request); toast("Downloading to Downloads.");
                } catch (Exception error) { toast("Download could not start. Check storage and server access."); }
            }).show();
    }
    private String safeFilename(String input) { String name = input.replaceAll("[\\\\/\\p{Cntrl}]", "_"); return name.trim().isEmpty() || name.equals(".") || name.equals("..") ? "download" : name.substring(0, Math.min(200, name.length())); }
    private void startBlob(String url, String disposition, String mime) {
        if (!ServerPolicy.origin(server).equals(ServerPolicy.origin(remote.getUrl()))) { toast("Return to your RomM server to save this file."); return; }
        if (blobFile != null) { toast("Finish or cancel the current save first."); return; }
        try {
            blobFile = File.createTempFile("rommayo-", ".download", getCacheDir()); blobStream = new FileOutputStream(blobFile);
            blobToken = UUID.randomUUID().toString(); blobName = safeFilename(URLUtil.guessFileName(url, disposition, mime)); blobMime = mime == null ? "application/octet-stream" : mime;
            String token = JSONObject.quote(blobToken);
            remote.evaluateJavascript("(async()=>{const bridge=window.rommayoDownload;const token=" + token + ";let ack;bridge.onmessage=()=>{if(ack){const a=ack;ack=null;a()}};const send=(data)=>new Promise(resolve=>{ack=resolve;bridge.postMessage(JSON.stringify({...data,token}))});try{const r=await fetch(" + JSONObject.quote(url) + ");if(!r.ok)throw Error();const reader=r.body.getReader();while(true){const {done,value}=await reader.read();if(done)break;for(let i=0;i<value.length;i+=131072){const part=value.subarray(i,i+131072);let s='';for(let j=0;j<part.length;j++)s+=String.fromCharCode(part[j]);await send({type:'chunk',data:btoa(s)})}}await send({type:'done'})}catch(e){bridge.postMessage(JSON.stringify({type:'error',token}))}})()", null);
        } catch (Exception error) { clearBlob(); toast("Could not prepare the save."); }
    }
    private void receiveBlob(String data, JavaScriptReplyProxy reply) {
        try {
            if (data == null || data.length() > 200000) throw new Exception();
            JSONObject message = new JSONObject(data);
            if (blobStream == null || !blobToken.equals(message.optString("token"))) return;
            switch (message.optString("type")) {
                case "chunk":
                    if (blobFile.getUsableSpace() < 4 * 1024 * 1024) throw new Exception();
                    blobStream.write(Base64.decode(message.getString("data"), Base64.DEFAULT)); reply.postMessage("ok"); break;
                case "done":
                    blobStream.close(); blobStream = null; reply.postMessage("ok");
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(blobMime).putExtra(Intent.EXTRA_TITLE, blobName);
                    startActivityForResult(intent, SAVE_BLOB); break;
                default: throw new Exception();
            }
        } catch (Exception error) { clearBlob(); reply.postMessage("error"); toast("Could not save this file. Check storage and retry."); }
    }
    private void clearBlob() {
        try { if (blobStream != null) blobStream.close(); } catch (Exception ignored) {}
        if (blobFile != null) blobFile.delete(); blobFile = null; blobStream = null; blobToken = "";
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_FILES && fileCallback != null) {
            Uri[] uris = null;
            if (result == RESULT_OK && data != null) {
                if (data.getClipData() != null) { uris = new Uri[data.getClipData().getItemCount()]; for (int i = 0; i < uris.length; i++) uris[i] = data.getClipData().getItemAt(i).getUri(); }
                else if (data.getData() != null) uris = new Uri[]{data.getData()};
            }
            fileCallback.onReceiveValue(uris); fileCallback = null;
        }
        if (request == SAVE_BLOB) {
            if (result != RESULT_OK || data == null || data.getData() == null || blobFile == null) { clearBlob(); return; }
            Uri destination = data.getData(); File source = blobFile; blobFile = null; blobToken = "";
            worker.execute(() -> {
                try (InputStream input = new java.io.FileInputStream(source); OutputStream output = getContentResolver().openOutputStream(destination)) {
                    if (output == null) throw new Exception(); copy(input, output); runOnUiThread(() -> toast("File saved."));
                } catch (Exception error) { runOnUiThread(() -> toast("Could not save this file. Check storage and retry.")); }
                finally { source.delete(); }
            });
        }
    }
    private void hideCustomView() {
        if (customView == null) return;
        ((FrameLayout) getWindow().getDecorView()).removeView(customView); customView = null; root.setVisibility(View.VISIBLE);
        if (customCallback != null) customCallback.onCustomViewHidden(); customCallback = null; setFullscreen(false);
    }
    private void setFullscreen(boolean value) {
        fullscreen = value;
        getWindow().getDecorView().setSystemUiVisibility(value ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY : View.SYSTEM_UI_FLAG_VISIBLE);
        if (remote != null) shell.setVisibility(value ? View.GONE : View.VISIBLE);
    }
    private void disconnect() {
        hideCustomView(); setFullscreen(false); clearBlob();
        if (fileCallback != null) { fileCallback.onReceiveValue(null); fileCallback = null; }
        if (remote != null) { CookieManager.getInstance().flush(); remoteContainer.removeAllViews(); remote.destroy(); remote = null; }
        root.removeView(remoteContainer); shell.setVisibility(View.VISIBLE); shell.setLayoutParams(new LinearLayout.LayoutParams(-1, -1)); notifyState(null);
    }
    private void handleBack() {
        if (customView != null) hideCustomView();
        else if (fullscreen) setFullscreen(false);
        else if (remote != null && remote.canGoBack()) remote.goBack();
        else if (remote != null) new AlertDialog.Builder(this).setMessage("Switch RomM servers?").setNegativeButton("Stay", null).setPositiveButton("Switch", (d, w) -> disconnect()).show();
        else finish();
    }
    @Override protected void onPause() { super.onPause(); CookieManager.getInstance().flush(); if (remote != null) remote.onPause(); }
    @Override protected void onResume() { super.onResume(); if (remote != null) remote.onResume(); }
    @Override protected void onDestroy() { if (remote != null) remote.destroy(); if (shell != null) shell.destroy(); clearBlob(); worker.shutdown(); super.onDestroy(); }
    private static void copy(InputStream input, OutputStream output) throws java.io.IOException { byte[] buffer = new byte[65536]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
