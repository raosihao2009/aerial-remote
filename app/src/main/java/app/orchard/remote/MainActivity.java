package app.orchard.remote;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    /** B 站手机客户端的包名。 */
    private static final String BILI = "tv.danmaku.bili";

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
        // 滑到底/顶再拖时, Android 12 会给 WebView 做一段拉伸回弹(overscroll stretch),
        // 整页跟着被拉长, 看着像页面坏了。这颗开关直接把那段效果关掉, 到边就停。
        contentWv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // 网页 CSS 藏不住系统 WebView 的视口原生滚动条: 元素级滚轴听 CSS, 整页视口这条由原生 View 画,
        // 只能在原生侧把纵向/横向滚动条开关关掉, 页面右侧那条长条才会消失。
        contentWv.setVerticalScrollBarEnabled(false);
        contentWv.setHorizontalScrollBarEnabled(false);
        contentWv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (url == null || url.isEmpty()) return true;
                if (url.startsWith("file:///android_asset")) return false;
                // 这一页只管「这台电视」: 同一台电视的主机地址继续在页里走, 别的一律交给系统。
                // 以前把什么地址都塞回同一个 WebView, 于是 B 站的登录链接、bilibili:// 之类的
                // 自定义 scheme 都被当成设备地址, 一路掉到 error.html 的 -10(地址格式不对)。
                if (isSameDevice(url)) { v.loadUrl(url); return true; }
                return openExternally(url);
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
            // 页面里 window.open 出来的新窗口也交给系统, 不在这里再开一个 WebView。
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message msg) {
                String hit = null;
                try {
                    WebView.HitTestResult r = view.getHitTestResult();
                    if (r != null) hit = r.getExtra();
                } catch (Exception e) { }
                if (hit != null && !hit.isEmpty()) { openExternally(hit); return true; }
                final WebView tmp = new WebView(MainActivity.this);
                tmp.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, String u) {
                        openExternally(u);
                        tmp.destroy();
                        return true;
                    }
                });
                WebView.WebViewTransport t = (WebView.WebViewTransport) msg.obj;
                t.setWebView(tmp);
                msg.sendToTarget();
                return true;
            }
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
        // WebView 遇到附件响应默认什么都不做; 不挂这个监听器, 网页里点导出不会有任何反应。
        // 套壳 WebView 自己不下载, 直接把链接交给系统浏览器: 浏览器能正常下完整份, 也能显示大小。
        contentWv.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String disposition, String mime, long length) {
                openInBrowser(url);
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
        tabsWv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        tabsWv.setVerticalScrollBarEnabled(false);
        tabsWv.setHorizontalScrollBarEnabled(false);
        tabsWv.setWebViewClient(new WebViewClient());
        tabsWv.addJavascriptInterface(new TabsBridge(), "Android");
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        root.addView(tabsWv, tlp);
        tabsWv.loadUrl("file:///android_asset/tabs.html");

        setContentView(root);
    }

    /** 是不是这台电视自己的地址: 同主机就算(电视同时开着 8790 明文和 8791 自签证书两个口)。 */
    private boolean isSameDevice(String url) {
        Uri u = Uri.parse(url);
        String scheme = u.getScheme();
        if (scheme == null) return false;
        if (!"http".equals(scheme) && !"https".equals(scheme)) return false;
        String host = u.getHost();
        String curHost = Uri.parse(currentUrl()).getHost();
        return host != null && curHost != null && host.equalsIgnoreCase(curHost);
    }

    /**
     * 把页面里点出来的外部地址交给系统: http/https 由用户挑浏览器或 App 打开, 其他 scheme
     * (bilibili://、intent://、market:// 之类) 直接丢给系统去解析。打不开就提示, 不能把整页
     * 顶成 error.html。
     */
    private boolean openExternally(String url) {
        if (url == null || url.isEmpty()) return true;
        Uri u = Uri.parse(url);
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase();
        // B 站那条登录链接: 手机里装着 B 站 App 就直接点名交给它, 没装才走浏览器。
        // 以前一律弹选择器让用户自己挑, 装着 B 站也要多点一下。
        if (("http".equals(scheme) || "https".equals(scheme)) && isBiliHost(u.getHost()) && hasPackage(BILI)) {
            Intent direct = new Intent(Intent.ACTION_VIEW, u).setPackage(BILI);
            direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(direct); return true; } catch (Exception ignored) {
                // B 站不收这条链接(版本差异): 下面照常交给系统, 不在这里顶成提示。
            }
        }
        try {
            Intent intent;
            if ("intent".equals(scheme)) {
                intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                if (intent.getPackage() != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { startActivity(intent); return true; } catch (Exception ignored) { }
                }
                // 点名的包没装: 按 intent:// 自己给的退路走。
                String fallback = intent.getStringExtra("browser_fallback_url");
                if (fallback != null && !fallback.isEmpty()) return openExternally(fallback);
            } else {
                intent = new Intent(Intent.ACTION_VIEW, u);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // 默认浏览器(或唯一能处理的应用)直接开, 系统要用户挑的时候它自己会弹。
            try { startActivity(intent); }
            catch (Exception e) { startActivity(Intent.createChooser(intent, "选择打开方式").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        } catch (Exception e) {
            Toast.makeText(this, "没有能打开这个地址的应用", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    /** 手机里装没装这个包: 不查它能不能处理意图, 只按包名判断。 */
    private boolean hasPackage(String pkg) {
        try {
            getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** B 站的域名(含 b23 短链): 只有这些地址才优先交给 B 站 App。 */
    private boolean isBiliHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase();
        return h.equals("bilibili.com") || h.endsWith(".bilibili.com")
                || h.equals("b23.tv") || h.endsWith(".b23.tv")
                || h.equals("bili2233.cn") || h.endsWith(".bili2233.cn");
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    /**
     * 附件链接交给系统浏览器下载。WebView 自己信任 TV 的自签证书, 但系统浏览器打开 8791 的
     * HTTPS 会弹证书警告, 所以把 8791 改走同一台 TV 的 8790 明文 HTTP, 令牌路径原样保留。
     */
    private void openInBrowser(String url) {
        Uri u = Uri.parse(url);
        int port = u.getPort();
        if ("https".equals(u.getScheme()) && port == 8791) port = 8790;
        String host = u.getHost();
        if (host == null) return;
        String target = u.buildUpon()
                .scheme("http")
                .encodedAuthority(host + (port == -1 ? "" : ":" + port))
                .build().toString();
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // 弹应用选择器, 让用户挑用哪个浏览器下载, 而不是直接丢给默认浏览器。
        Intent chooser = Intent.createChooser(intent, "选择浏览器");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(chooser);
            Toast.makeText(this, "选择浏览器后开始下载", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "没有可用的浏览器", Toast.LENGTH_SHORT).show();
        }
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
            sheetWv.setOverScrollMode(View.OVER_SCROLL_NEVER);
            sheetWv.setVerticalScrollBarEnabled(false);
            sheetWv.setHorizontalScrollBarEnabled(false);
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
