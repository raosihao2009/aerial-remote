package app.orchard.remote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
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
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST = 200;

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

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams fileChooserParams) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                Intent intent = fileChooserParams.createIntent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
                try {
                    startActivityForResult(Intent.createChooser(intent, "选择文件"), FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
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
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(8));
        card.setBackgroundColor(0xFFFFFFFF);

        TextView title = new TextView(this);
        title.setText("设备设置");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTextColor(0xFF000000);
        title.setPadding(0, 0, 0, dp(16));
        card.addView(title);

        // 重命名输入
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.HORIZONTAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(dp(12), dp(12), dp(12), dp(12));
        field.setBackgroundColor(0xFFF5F5F7);
        final EditText input = new EditText(this);
        input.setText(getName(url));
        input.setBackground(null);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        field.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        card.addView(field);

        card.addView(new View(this) {{ setBackgroundColor(0xFFE5E5EA); setMinimumHeight(1); }});

        // 确定
        Button ok = new Button(this);
        ok.setText("确定");
        ok.setAllCaps(false);
        ok.setBackgroundColor(0xFFFFFFFF);
        ok.setTextColor(0xFF007AFF);
        ok.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        ok.setPadding(0, dp(12), 0, dp(12));
        ok.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) prefs.edit().putString("name_" + url, name).apply();
            dismissCurrentDialog();
            refreshTabs();
        });
        card.addView(ok);

        // 删除
        Button del = new Button(this);
        del.setText("删除设备");
        del.setAllCaps(false);
        del.setBackgroundColor(0xFFFFFFFF);
        del.setTextColor(0xFFFF3B30);
        del.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        del.setPadding(0, dp(12), 0, dp(12));
        del.setOnClickListener(v -> {
            dismissCurrentDialog();
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
        });
        card.addView(del);

        card.addView(new View(this) {{ setBackgroundColor(0xFFE5E5EA); setMinimumHeight(1); }});

        // 取消
        Button cancel = new Button(this);
        cancel.setText("取消");
        cancel.setAllCaps(false);
        cancel.setBackgroundColor(0xFFFFFFFF);
        cancel.setTextColor(0xFF8E8E93);
        cancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        cancel.setPadding(0, dp(12), 0, dp(12));
        cancel.setOnClickListener(v -> dismissCurrentDialog());
        card.addView(cancel);

        currentDialog = new AlertDialog.Builder(this).setView(card).show();
        if (currentDialog.getWindow() != null) {
            currentDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
        }
    }

    private String scannedUrl = null;

    private void showAddDialog() {
        scannedUrl = null;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(8));
        card.setBackgroundColor(0xFFFFFFFF);

        TextView title = new TextView(this);
        title.setText("添加 TV 设备");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTextColor(0xFF000000);
        title.setPadding(0, 0, 0, dp(16));
        card.addView(title);

        // 扫码行
        Button scan = new Button(this);
        scan.setText("扫描二维码");
        scan.setAllCaps(false);
        scan.setBackgroundColor(0xFFF5F5F7);
        scan.setTextColor(0xFF007AFF);
        scan.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        scan.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.addView(scan);

        // 扫码结果
        TextView scanResult = new TextView(this);
        scanResult.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        scanResult.setTextColor(0xFF8E8E93);
        scanResult.setPadding(dp(4), dp(8), 0, dp(4));
        scanResult.setText("尚未扫码");
        card.addView(scanResult);

        // 命名输入
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.HORIZONTAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(dp(12), dp(12), dp(12), dp(12));
        field.setBackgroundColor(0xFFF5F5F7);
        final EditText input = new EditText(this);
        input.setHint("设备名称（如：客厅电视）");
        input.setBackground(null);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        field.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        card.addView(field);

        // 确定按钮
        Button ok = new Button(this);
        ok.setText("确定");
        ok.setAllCaps(false);
        ok.setBackgroundColor(0xFFFFFFFF);
        ok.setTextColor(0xFF007AFF);
        ok.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        ok.setEnabled(false);
        ok.setAlpha(0.4f);
        ok.setPadding(0, dp(12), 0, dp(12));
        card.addView(ok);

        // 取消
        Button cancel = new Button(this);
        cancel.setText("取消");
        cancel.setAllCaps(false);
        cancel.setBackgroundColor(0xFFFFFFFF);
        cancel.setTextColor(0xFF8E8E93);
        cancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        cancel.setPadding(0, dp(12), 0, dp(12));
        card.addView(cancel);

        scan.setOnClickListener(v -> scanQR());
        cancel.setOnClickListener(v -> dismissCurrentDialog());

        Runnable checkOk = () -> {
            boolean ready = scannedUrl != null && !input.getText().toString().trim().isEmpty();
            ok.setEnabled(ready);
            ok.setAlpha(ready ? 1f : 0.4f);
        };
        input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) { checkOk.run(); }
        });

        ok.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            prefs.edit().putString("name_" + scannedUrl, name).apply();
            List<String> devices = getDevices();
            if (!devices.contains(scannedUrl)) { devices.add(scannedUrl); saveDevices(devices); }
            dismissCurrentDialog();
            loadUrl(scannedUrl);
        });

        currentDialog = new AlertDialog.Builder(this).setView(card).show();
        if (currentDialog.getWindow() != null) {
            currentDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
        }
        // store refs for after scan
        pendingScanResultView = scanResult;
    }

    private TextView pendingScanResultView;

    private AlertDialog currentDialog;
    private void dismissCurrentDialog() {
        if (currentDialog != null && currentDialog.isShowing()) currentDialog.dismiss();
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
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
            return;
        }
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null && result.getContents() != null) {
            String url = result.getContents().trim();
            if (url.startsWith("http")) {
                url = url.replace(":8790/", ":8791/").replace("http://", "https://");
                scannedUrl = url;
                if (pendingScanResultView != null) {
                    pendingScanResultView.setText("已扫码: " + url.replace("https://","").replace(":8791/","").split("/")[0]);
                    pendingScanResultView.setTextColor(0xFF34C759);
                }
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
