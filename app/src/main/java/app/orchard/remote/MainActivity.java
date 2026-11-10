package app.orchard.remote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.webkit.GeolocationPermissions;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.journeyapps.barcodescanner.IntentIntegrator;
import com.journeyapps.barcodescanner.IntentResult;

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
        root.setBackgroundColor(0xFFF5F5F7);

        // Tab bar
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setBackgroundColor(Color.WHITE);
        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setPadding(dp(12), dp(10), dp(12), dp(10));
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

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private List<String> getDevices() {
        Set<String> set = prefs.getStringSet("devices", new HashSet<>());
        return new ArrayList<>(set);
    }

    private void saveDevices(List<String> devices) {
        prefs.edit().putStringSet("devices", new HashSet<>(devices)).apply();
    }

    private String getName(String url) {
        return prefs.getString("name_" + url, url.replace("http://","").replace("https://","").replace(":8790","").replace(":8791","").split("/")[0]);
    }

    private void refreshTabs() {
        tabBar.removeAllViews();
        List<String> devices = getDevices();
        for (String url : devices) {
            Button btn = new Button(this);
            String label = getName(url);
            btn.setText(label);
            btn.setAllCaps(false);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            btn.setPadding(dp(14), dp(8), dp(14), dp(8));
            if (url.equals(currentUrl)) {
                btn.setBackgroundColor(0xFF007AFF);
                btn.setTextColor(Color.WHITE);
            } else {
                btn.setBackgroundColor(0xFFE8E8ED);
                btn.setTextColor(0xFF333333);
            }
            btn.setOnClickListener(v -> loadUrl(url));
            btn.setOnLongClickListener(v -> {
                showRenameDialog(url);
                return true;
            });
            tabBar.addView(btn);
        }
        // Add button
        Button addBtn = new Button(this);
        addBtn.setText("+ 添加");
        addBtn.setAllCaps(false);
        addBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        addBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        addBtn.setBackgroundColor(0xFF34C759);
        addBtn.setTextColor(Color.WHITE);
        addBtn.setOnClickListener(v -> showAddDialog());
        tabBar.addView(addBtn);

        if (currentUrl == null && !devices.isEmpty()) {
            loadUrl(devices.get(0));
        } else if (currentUrl == null) {
            showAddDialog();
        }
    }

    private void showRenameDialog(String url) {
        final EditText input = new EditText(this);
        input.setText(getName(url));
        new AlertDialog.Builder(this)
            .setTitle("设备名称")
            .setView(input)
            .setPositiveButton("保存", (d, w) -> {
                String name = input.getText().toString().trim();
                if (!name.isEmpty()) prefs.edit().putString("name_" + url, name).apply();
                refreshTabs();
            })
            .setNegativeButton("删除设备", (d, w) -> {
                List<String> devices = getDevices();
                devices.remove(url);
                saveDevices(devices);
                prefs.edit().remove("name_" + url).apply();
                if (url.equals(currentUrl)) {
                    currentUrl = null;
                    if (!devices.isEmpty()) loadUrl(devices.get(0));
                    else showAddDialog();
                } else {
                    refreshTabs();
                }
            })
            .show();
    }

    private void showAddDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("http://192.168.1.4:8790/token/");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(10), dp(20), 0);
        layout.addView(input);
        new AlertDialog.Builder(this)
            .setTitle("添加 TV 设备")
            .setView(layout)
            .setPositiveButton("连接", (d, w) -> {
                String url = input.getText().toString().trim();
                if (url.isEmpty()) return;
                if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://" + url;
                url = url.replace(":8790/", ":8791/").replace("http://", "https://");
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
        new IntentIntegrator(this)
            .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            .setPrompt("扫描 TV 二维码")
            .setBeepEnabled(true)
            .initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null && result.getContents() != null) {
            String url = result.getContents().trim();
            if (url.startsWith("http")) {
                url = url.replace(":8790/", ":8791/").replace("http://", "https://");
                List<String> devices = getDevices();
                if (!devices.contains(url)) {
                    devices.add(url);
                    saveDevices(devices);
                }
                loadUrl(url);
            } else {
                Toast.makeText(this, "不是有效的链接", Toast.LENGTH_SHORT).show();
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
