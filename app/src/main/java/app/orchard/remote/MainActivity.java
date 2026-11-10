package app.orchard.remote;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private FrameLayout root;
    private WebView contentWv;
    private WebView tabsWv;
    private WebView sheetWv;
    private SharedPreferences sp;
    private ValueCallback<Uri[]> fileCb;
    private String sheetMode = "add";
    private static final int FILE_REQ = 200;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("devices", MODE_PRIVATE);
        root = new FrameLayout(this);

        contentWv = new WebView(this);
        contentWv.getSettings().setJavaScriptEnabled(true);
        contentWv.getSettings().setDomStorageEnabled(true);
        contentWv.getSettings().setDatabaseEnabled(true);
        contentWv.getSettings().setMediaPlaybackRequiresUserGesture(false);
        contentWv.setBackgroundColor(Color.WHITE);
        contentWv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                v.loadUrl(url);
                return true;
            }
            @Override
            public void onReceivedSslError(WebView v, android.webkit.SslErrorHandler handler, android.net.http.SslError e) {
                handler.proceed();
            }
        });
        contentWv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest req) {
                req.grant(req.getResources());
            }
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                cb.invoke(origin, true, false);
            }
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                fileCb = cb;
                try {
                    startActivityForResult(params.createIntent(), FILE_REQ);
                    return true;
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
            }
        });
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        clp.topMargin = dp(48);
        root.addView(contentWv, clp);

        tabsWv = new WebView(this);
        tabsWv.getSettings().setJavaScriptEnabled(true);
        tabsWv.setBackgroundColor(Color.TRANSPARENT);
        tabsWv.setWebViewClient(new WebViewClient());
        tabsWv.addJavascriptInterface(new TabsBridge(), "Android");
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        root.addView(tabsWv, tlp);
        tabsWv.loadUrl("file:///android_asset/tabs.html");

        setContentView(root);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private int count() { return sp.getInt("count", 0); }

    private String urlAt(int i) { return sp.getString("url_" + i, ""); }

    private String nameAt(int i) { return sp.getString("name_" + i, "TV"); }

    private String currentUrl() { return sp.getString("current", ""); }

    private void setCurrent(String url) { sp.edit().putString("current", url).apply(); }

    private void refreshTabs() {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < count(); i++) {
            JSONObject o = new JSONObject();
            try {
                o.put("url", urlAt(i));
                o.put("name", nameAt(i));
            } catch (Exception e) {
            }
            arr.put(o);
        }
        final String js = "renderTabs(" + arr.toString() + "," + JSONObject.quote(currentUrl()) + ")";
        runOnUiThread(new Runnable() {
            public void run() { tabsWv.evaluateJavascript(js, null); }
        });
    }

    private void open(String url) {
        setCurrent(url);
        contentWv.loadUrl(url);
        refreshTabs();
    }

    private void showSheet(String mode, String url, String name) {
        sheetMode = mode;
        String q = "?mode=" + Uri.encode(mode) + "&url=" + Uri.encode(url == null ? "" : url)
                + "&name=" + Uri.encode(name == null ? "" : name);
        if (sheetWv == null) {
            sheetWv = new WebView(this);
            sheetWv.getSettings().setJavaScriptEnabled(true);
            sheetWv.setBackgroundColor(Color.TRANSPARENT);
            sheetWv.setWebViewClient(new WebViewClient());
            sheetWv.addJavascriptInterface(new SheetBridge(), "Android");
        }
        sheetWv.loadUrl("file:///android_asset/sheet.html" + q);
        if (sheetWv.getParent() == null) {
            root.addView(sheetWv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    private void hideSheet() {
        if (sheetWv != null && sheetWv.getParent() != null) {
            root.removeView(sheetWv);
        }
    }

    private void addDevice(String url, String name) {
        for (int i = 0; i < count(); i++) {
            if (url.equals(urlAt(i))) {
                sp.edit().putString("name_" + i, name).apply();
                open(url);
                return;
            }
        }
        int n = count();
        sp.edit().putString("url_" + n, url).putString("name_" + n, name).putInt("count", n + 1).apply();
        open(url);
    }

    private void renameDevice(String url, String name) {
        for (int i = 0; i < count(); i++) {
            if (url.equals(urlAt(i))) {
                sp.edit().putString("name_" + i, name).apply();
                break;
            }
        }
        refreshTabs();
    }

    private void removeDevice(String url) {
        int n = count();
        java.util.List<String> urls = new java.util.ArrayList<>();
        java.util.List<String> names = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (url.equals(urlAt(i))) continue;
            urls.add(urlAt(i));
            names.add(nameAt(i));
        }
        SharedPreferences.Editor ed = sp.edit();
        ed.clear();
        for (int i = 0; i < urls.size(); i++) {
            ed.putString("url_" + i, urls.get(i));
            ed.putString("name_" + i, names.get(i));
        }
        ed.putInt("count", urls.size());
        if (url.equals(currentUrl())) {
            ed.putString("current", urls.isEmpty() ? "" : urls.get(0));
        }
        ed.apply();
        if (url.equals(currentUrl())) {
            if (urls.isEmpty()) contentWv.loadUrl("about:blank");
            else contentWv.loadUrl(urls.get(0));
        }
        refreshTabs();
    }

    public class TabsBridge {
        @android.webkit.JavascriptInterface
        public void ready() {
            runOnUiThread(new Runnable() {
                public void run() {
                    refreshTabs();
                    if (!currentUrl().isEmpty()) contentWv.loadUrl(currentUrl());
                    else if (count() > 0) open(urlAt(0));
                    else showSheet("add", "", "");
                }
            });
        }

        @android.webkit.JavascriptInterface
        public void openUrl(final String url) {
            runOnUiThread(new Runnable() { public void run() { open(url); } });
        }

        @android.webkit.JavascriptInterface
        public void add() {
            runOnUiThread(new Runnable() { public void run() { showSheet("add", "", ""); } });
        }

        @android.webkit.JavascriptInterface
        public void rename(final String url, final String name) {
            runOnUiThread(new Runnable() {
                public void run() { showSheet("rename", url, name); }
            });
        }
    }

    public class SheetBridge {
        @android.webkit.JavascriptInterface
        public void save(final String url, final String name) {
            runOnUiThread(new Runnable() { public void run() { addDevice(url, name); } });
        }

        @android.webkit.JavascriptInterface
        public void renameSave(final String url, final String name) {
            runOnUiThread(new Runnable() { public void run() { renameDevice(url, name); } });
        }

        @android.webkit.JavascriptInterface
        public void remove(final String url) {
            runOnUiThread(new Runnable() { public void run() { removeDevice(url); } });
        }

        @android.webkit.JavascriptInterface
        public void close() {
            runOnUiThread(new Runnable() { public void run() { hideSheet(); } });
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == FILE_REQ && fileCb != null) {
            Uri[] result = null;
            if (res == RESULT_OK && data != null) {
                if (data.getData() != null) result = new Uri[]{data.getData()};
                else if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    result = new Uri[n];
                    for (int i = 0; i < n; i++) result[i] = data.getClipData().getItemAt(i).getUri();
                }
            }
            fileCb.onReceiveValue(result);
            fileCb = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (sheetWv != null && sheetWv.getParent() != null) {
            hideSheet();
            return;
        }
        if (contentWv.canGoBack()) contentWv.goBack();
        else super.onBackPressed();
    }
}
