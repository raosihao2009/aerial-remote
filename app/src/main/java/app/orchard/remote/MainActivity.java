package app.orchard.remote;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import android.webkit.WebView;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.Manifest;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private WebView webView;
    private SharedPreferences sp;
    private static final int SCAN_REQ = 100;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("devices", MODE_PRIVATE);

        webView = new WebView(this);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new Bridge(), "Android");
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
                v.loadUrl(url); return true;
            }
            @Override public void onReceivedSslError(WebView v, SslHandler handler, android.net.SslError e) {
                handler.proceed();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(final PermissionRequest req) {
                req.grant(req.getResources());
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                cb.invoke(origin, true, false);
            }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                filePathCallback = cb;
                Intent i = params.createIntent();
                try { startActivityForResult(i, 200); } catch (Exception e) { return false; }
                return true;
            }
        });
        webView.loadUrl("file:///android_asset/ui.html");
        setContentView(webView);
    }

    private ValueCallback<Uri[]> filePathCallback;

    class Bridge {
        @JavascriptInterface
        public void getDevices() {
            List<String> urls = new ArrayList<>();
            List<String> names = new ArrayList<>();
            int n = sp.getInt("count", 0);
            for (int i = 0; i < n; i++) {
                urls.add(sp.getString("url_" + i, ""));
                names.add(sp.getString("name_" + i, "TV"));
            }
            StringBuilder js = new StringBuilder();
            js.append("devices=[");
            for (int i = 0; i < urls.size(); i++) {
                if (i > 0) js.append(",");
                js.append("{url:'").append(urls.get(i)).append("',name:'").append(names.get(i)).append("'}");
            }
            js.append("];renderTabs();");
            final String f = js.toString();
            runOnUiThread(() -> webView.evaluateJavascript(f, null));
        }

        @JavascriptInterface
        public void addDevice(String url, String name) {
            int n = sp.getInt("count", 0);
            sp.edit().putString("url_" + n, url).putString("name_" + n, name).putInt("count", n + 1).apply();
            runOnUiThread(() -> {
                webView.evaluateJavascript("Android.getDevices();location.hash='" + url + "';document.getElementById('frame').src='" + url + "';", null);
            });
        }

        @JavascriptInterface
        public void scan() {
            runOnUiThread(() -> startActivityForResult(new Intent(MainActivity.this, ScanActivity.class), SCAN_REQ));
        }

        @JavascriptInterface
        public String paste() {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData cd = cm.getPrimaryClip();
            if (cd != null && cd.getItemCount() > 0) return cd.getItemAt(0).getText().toString();
            return "";
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == SCAN_REQ && res == RESULT_OK && data != null) {
            String url = data.getStringExtra("SCAN_RESULT");
            if (url != null) webView.evaluateJavascript("onScan('" + url + "')", null);
        }
        if (req == 200 && filePathCallback != null) {
            filePathCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            filePathCallback = null;
        }
    }
}
