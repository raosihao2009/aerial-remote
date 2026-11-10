package app.orchard.remote;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.webkit.GeolocationPermissions;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.app.AlertDialog;

public class MainActivity extends Activity {
    private WebView webView;
    private SharedPreferences prefs;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("aerial", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        webView = new WebView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0);
        lp.weight = 1;
        root.addView(webView, lp);
        setContentView(root);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setGeolocationEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);

        // 自动信任自签名证书
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                handler.proceed();
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                // 页面加载完成后隐藏地址栏等不需要的元素
            }
        });

        // GPS权限对话框
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });

        String savedUrl = prefs.getString("url", null);
        if (savedUrl != null) {
            webView.loadUrl(savedUrl);
        } else {
            showUrlDialog();
        }
    }

    private void showUrlDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("https://192.168.1.4:8790/xxxx/");
        input.setText(prefs.getString("lastUrl", "https://192.168.1.4:8790/"));
        new AlertDialog.Builder(this)
            .setTitle("TV 地址")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("连接", (d, w) -> {
                String url = input.getText().toString().trim();
                if (!url.startsWith("http")) url = "https://" + url;
                prefs.edit().putString("url", url).putString("lastUrl", url).apply();
                webView.loadUrl(url);
            })
            .setNeutralButton("清除地址", (d, w) -> {
                prefs.edit().remove("url").apply();
            })
            .show();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
