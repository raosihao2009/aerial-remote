package app.orchard.remote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.webkit.GeolocationPermissions;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private WebView webView;
    private SharedPreferences prefs;
    private LinearLayout tabBar;
    private String currentUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("aerial", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // Tab bar
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setPadding(16, 8, 16, 8);
        scroll.addView(tabBar);
        root.addView(scroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // WebView
        webView = new WebView(this);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0);
        wp.weight = 1;
        root.addView(webView, wp);
        setContentView(root);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setGeolocationEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                handler.proceed();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });

        refreshTabs();
    }

    private List<String> getDevices() {
        Set<String> set = prefs.getStringSet("devices", new HashSet<>());
        return new ArrayList<>(set);
    }

    private void saveDevices(List<String> devices) {
        prefs.edit().putStringSet("devices", new HashSet<>(devices)).apply();
    }

    private void refreshTabs() {
        tabBar.removeAllViews();
        List<String> devices = getDevices();
        for (String url : devices) {
            Button btn = new Button(this);
            String label = url.replace("http://", "").replace("https://", "").replace(":8790", "").replace(":8791", "");
            if (label.length() > 15) label = label.substring(0, 15);
            btn.setText(label);
            btn.setAllCaps(false);
            if (url.equals(currentUrl)) btn.setBackgroundColor(0xFFE3F2FD);
            btn.setOnClickListener(v -> loadUrl(url));
            tabBar.addView(btn);
        }
        // Add button
        Button addBtn = new Button(this);
        addBtn.setText("+ 添加");
        addBtn.setAllCaps(false);
        addBtn.setOnClickListener(v -> showAddDialog());
        tabBar.addView(addBtn);

        if (currentUrl == null && !devices.isEmpty()) {
            loadUrl(devices.get(0));
        } else if (currentUrl == null) {
            showAddDialog();
        }
    }

    private void showAddDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("http://192.168.1.4:8790/xxxx/");
        new AlertDialog.Builder(this)
            .setTitle("添加 TV 设备")
            .setView(input)
            .setPositiveButton("连接", (d, w) -> {
                String url = input.getText().toString().trim();
                if (url.isEmpty()) return;
                if (!url.startsWith("http")) url = "http://" + url;
                List<String> devices = getDevices();
                if (!devices.contains(url)) {
                    devices.add(url);
                    saveDevices(devices);
                }
                loadUrl(url);
            })
            .setNeutralButton("扫码", (d, w) -> scanQR())
            .show();
    }

    private void scanQR() {
        try {
            Intent intent = new Intent("com.google.zxing.client.result.SCAN");
            intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
            startActivityForResult(intent, 100);
        } catch (Exception e) {
            Toast.makeText(this, "请安装扫码 App（如 ZXing）", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            String url = data.getStringExtra("SCAN_RESULT");
            if (url != null && url.startsWith("http")) {
                List<String> devices = getDevices();
                if (!devices.contains(url)) {
                    devices.add(url);
                    saveDevices(devices);
                }
                loadUrl(url);
            }
        }
    }

    private void loadUrl(String url) {
        currentUrl = url;
        webView.loadUrl(url);
        refreshTabs();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
