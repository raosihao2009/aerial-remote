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
                if (url != null && url.startsWith("file:///android_asset")) return false;
                v.loadUrl(url);
                return true;
            }
            @Override
            public void onReceivedError(WebView v, android.webkit.WebResourceRequest req, android.webkit.WebResourceError err) {
                if (req != null && req.isForMainFrame()) {
                    int code = -1;
                    try { code = err.getErrorCode(); } catch (Exception e) { }
                    showError(code);
                }
            }
            @Override
            public void onReceivedError(WebView v, int code, String desc, String failingUrl) {
                if (android.os.Build.VERSION.SDK_INT < 23) showError(code);
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
        contentWv.addJavascriptInterface(new ErrorBridge(), "Android");
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        clp.topMargin = dp(50);
        root.addView(contentWv, clp);

        tabsWv = new WebView(this);
        tabsWv.getSettings().setJavaScriptEnabled(true);
        tabsWv.setBackgroundColor(Color.TRANSPARENT);
        tabsWv.setWebViewClient(new WebViewClient());
        tabsWv.addJavascriptInterface(new TabsBridge(), "Android");
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
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

    private void showError(int code) {
        String cur = currentUrl();
        if (cur == null || cur.isEmpty()) return;
        final String u = "file:///android_asset/error.html?code=" + code + "&url=" + Uri.encode(cur);
        contentWv.loadUrl(u);
    }

    private String nameOf(String url) {
        for (int i = 0; i < count(); i++) {
            if (url.equals(urlAt(i))) return nameAt(i);
        }
        return "TV";
    }

    private void updateDevice(String oldUrl, String newUrl, String newName) {
        int idx = -1;
        for (int i = 0; i < count(); i++) {
            if (oldUrl.equals(urlAt(i))) { idx = i; break; }
        }
        if (idx < 0) return;
        // Another device already on this address: swap it out instead of duplicating.
        for (int i = 0; i < count(); i++) {
            if (i != idx && newUrl.equals(urlAt(i))) {
                sp.edit().remove("url_" + i).remove("name_" + i).apply();
                break;
            }
        }
        sp.edit().putString("url_" + idx, newUrl).putString("name_" + idx, newName).apply();
        if (oldUrl.equals(currentUrl())) {
            sp.edit().putString("current", newUrl).apply();
            contentWv.loadUrl(newUrl);
        }
        refreshTabs();
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
        String cur = currentUrl();
        SharedPreferences.Editor ed = sp.edit();
        for (int i = 0; i < n; i++) {
            ed.remove("url_" + i);
            ed.remove("name_" + i);
        }
        for (int i = 0; i < urls.size(); i++) {
            ed.putString("url_" + i, urls.get(i));
            ed.putString("name_" + i, names.get(i));
        }
        ed.putInt("count", urls.size());
        String next = urls.isEmpty() ? "" : urls.get(0);
        if (url.equals(cur)) ed.putString("current", next);
        ed.apply();
        if (url.equals(cur)) {
            if (next.isEmpty()) contentWv.loadUrl("about:blank");
            else contentWv.loadUrl(next);
        }
        refreshTabs();
        final int cc = urls.size();
        if (cc == 0) {
            root.postDelayed(new Runnable() { public void run() { showSheet("add", "", ""); } }, 400);
        }
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
                public void run() { showSheet("edit", url, name); }
            });
        }
    }

    public class SheetBridge {
        @android.webkit.JavascriptInterface
        public void save(final String url, final String name) {
            runOnUiThread(new Runnable() { public void run() { addDevice(url, name); } });
        }

        @android.webkit.JavascriptInterface
        public void saveEdit(final String oldUrl, final String newUrl, final String name) {
            runOnUiThread(new Runnable() { public void run() { updateDevice(oldUrl, newUrl, name); } });
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

    public class ErrorBridge {
        @android.webkit.JavascriptInterface
        public void retry(final String url) {
            runOnUiThread(new Runnable() { public void run() { contentWv.loadUrl(url); } });
        }

        @android.webkit.JavascriptInterface
        public void edit(final String url) {
            runOnUiThread(new Runnable() {
                public void run() { showSheet("edit", url, nameOf(url)); }
            });
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
