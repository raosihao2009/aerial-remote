package app.orchard.remote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private WebView webView;
    private SharedPreferences prefs;
    private LinearLayout tabBar;
    private String currentUrl;
    private String scannedUrl = null;
    private TextView scanResultView;
    private AlertDialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("aerial", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF2F2F7);

        // Tab bar - white background like web
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setBackgroundColor(Color.WHITE);
        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setPadding(dp(8), dp(8), dp(8), dp(8));
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
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                Intent intent = fileChooserParams.createIntent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
                try {
                    startActivityForResult(Intent.createChooser(intent, "选择文件"), 200);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        refreshTabs();
    }

    private ValueCallback<Uri[]> filePathCallback;

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private List<String> getDevices() {
        return new ArrayList<>(prefs.getStringSet("devices", new HashSet<>()));
    }

    private void saveDevices(List<String> devices) {
        prefs.edit().putStringSet("devices", new HashSet<>(devices)).apply();
    }

    private String getName(String url) {
        String n = prefs.getString("name_" + url, null);
        if (n != null) return n;
        String host = url.replace("https://","").replace("http://","").replace(":8791/","").replace(":8790/","").split("/")[0];
        return host;
    }

    private void refreshTabs() {
        tabBar.removeAllViews();
        List<String> devices = getDevices();
        for (String url : devices) {
            Button btn = new Button(this);
            btn.setText(getName(url));
            btn.setAllCaps(false);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            btn.setTextColor(0xFF333333);
            btn.setBackgroundColor(0xFFF2F2F7);
            btn.setPadding(dp(14), dp(8), dp(14), dp(8));
            if (url.equals(currentUrl)) {
                btn.setTextColor(Color.WHITE);
                btn.setBackgroundColor(0xFF007AFF);
            }
            btn.setOnClickListener(v -> loadUrl(url));
            btn.setOnLongClickListener(v -> { showRenameDialog(url); return true; });
            tabBar.addView(btn);
        }
        Button addBtn = new Button(this);
        addBtn.setText("+ 添加");
        addBtn.setAllCaps(false);
        addBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        addBtn.setTextColor(Color.WHITE);
        addBtn.setBackgroundColor(0xFF34C759);
        addBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        addBtn.setOnClickListener(v -> showAddDialog());
        tabBar.addView(addBtn);

        if (currentUrl == null && !devices.isEmpty()) loadUrl(devices.get(0));
        else if (currentUrl == null) showAddDialog();
    }

    // Bottom sheet style dialog
    private void showAddDialog() {
        scannedUrl = null;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(24), dp(20), dp(16));
        card.setBackgroundColor(0xFFF2F2F7);

        TextView title = new TextView(this);
        title.setText("添加 TV 设备");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTextColor(0xFF000000);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(16));
        card.addView(title);

        // Scan button - white card
        LinearLayout scanCard = new LinearLayout(this);
        scanCard.setOrientation(LinearLayout.VERTICAL);
        scanCard.setBackgroundColor(Color.WHITE);
        scanCard.setPadding(0, 0, 0, 0);
        Button scan = new Button(this);
        scan.setText("扫描二维码");
        scan.setAllCaps(false);
        scan.setBackgroundColor(Color.WHITE);
        scan.setTextColor(0xFF007AFF);
        scan.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        scan.setPadding(0, dp(14), 0, dp(14));
        scan.setOnClickListener(v -> scanQR());
        scanCard.addView(scan);
        card.addView(scanCard);

        scanResultView = new TextView(this);
        scanResultView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        scanResultView.setTextColor(0xFF8E8E93);
        scanResultView.setPadding(dp(8), dp(8), 0, dp(4));
        scanResultView.setText("尚未扫码");
        card.addView(scanResultView);

        // Name input - white card
        LinearLayout fieldCard = new LinearLayout(this);
        fieldCard.setOrientation(LinearLayout.HORIZONTAL);
        fieldCard.setGravity(Gravity.CENTER_VERTICAL);
        fieldCard.setBackgroundColor(Color.WHITE);
        fieldCard.setPadding(dp(14), dp(4), dp(14), dp(4));
        final EditText input = new EditText(this);
        input.setHint("设备名称（如：当贝盒子）");
        input.setBackground(null);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        fieldCard.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        card.addView(fieldCard);

        // OK button - white card
        LinearLayout okCard = new LinearLayout(this);
        okCard.setOrientation(LinearLayout.VERTICAL);
        okCard.setBackgroundColor(Color.WHITE);
        okCard.setPadding(0, dp(8), 0, dp(8));
        Button ok = new Button(this);
        ok.setText("确定");
        ok.setAllCaps(false);
        ok.setBackgroundColor(Color.WHITE);
        ok.setTextColor(0xFF007AFF);
        ok.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        ok.setPadding(0, dp(10), 0, dp(10));
        ok.setEnabled(false);
        ok.setAlpha(0.35f);
        okCard.addView(ok);
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        okLp.topMargin = dp(10);
        card.addView(okCard, okLp);

        // Cancel
        Button cancel = new Button(this);
        cancel.setText("取消");
        cancel.setAllCaps(false);
        cancel.setBackgroundColor(Color.WHITE);
        cancel.setTextColor(0xFF007AFF);
        cancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        cancel.getPaint().setFakeBoldText(true);
        cancel.setPadding(0, dp(14), 0, dp(14));
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cancelLp.topMargin = dp(10);
        cancel.setOnClickListener(v -> dismissDialog());
        card.addView(cancel, cancelLp);

        ok.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            prefs.edit().putString("name_" + scannedUrl, name).apply();
            List<String> devices = getDevices();
            if (!devices.contains(scannedUrl)) { devices.add(scannedUrl); saveDevices(devices); }
            dismissDialog();
            loadUrl(scannedUrl);
        });

        input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                boolean ready = scannedUrl != null && !s.toString().trim().isEmpty();
                ok.setEnabled(ready);
                ok.setAlpha(ready ? 1f : 0.35f);
            }
        });

        // Build as bottom sheet
        dialog = new AlertDialog.Builder(this)
            .setView(card)
            .setCancelable(true)
            .show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0x00000000));
            dialog.getWindow().setGravity(Gravity.BOTTOM);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showRenameDialog(String url) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(24), dp(20), dp(16));
        card.setBackgroundColor(0xFFF2F2F7);

        TextView title = new TextView(this);
        title.setText("设备设置");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTextColor(0xFF000000);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(16));
        card.addView(title);

        LinearLayout fieldCard = new LinearLayout(this);
        fieldCard.setOrientation(LinearLayout.HORIZONTAL);
        fieldCard.setGravity(Gravity.CENTER_VERTICAL);
        fieldCard.setBackgroundColor(Color.WHITE);
        fieldCard.setPadding(dp(14), dp(4), dp(14), dp(4));
        final EditText input = new EditText(this);
        input.setText(getName(url));
        input.setBackground(null);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        fieldCard.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        card.addView(fieldCard);

        // OK
        Button ok = new Button(this);
        ok.setText("确定");
        ok.setAllCaps(false);
        ok.setBackgroundColor(Color.WHITE);
        ok.setTextColor(0xFF007AFF);
        ok.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        ok.setPadding(0, dp(14), 0, dp(14));
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        okLp.topMargin = dp(10);
        ok.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) prefs.edit().putString("name_" + url, name).apply();
            dismissDialog();
            refreshTabs();
        });
        card.addView(ok, okLp);

        // Delete
        Button del = new Button(this);
        del.setText("删除设备");
        del.setAllCaps(false);
        del.setBackgroundColor(Color.WHITE);
        del.setTextColor(0xFFFF3B30);
        del.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        del.setPadding(0, dp(14), 0, dp(14));
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        delLp.topMargin = dp(10);
        del.setOnClickListener(v -> {
            dismissDialog();
            List<String> devices = getDevices();
            devices.remove(url);
            saveDevices(devices);
            prefs.edit().remove("name_" + url).apply();
            if (url.equals(currentUrl)) {
                currentUrl = null;
                if (!devices.isEmpty()) loadUrl(devices.get(0));
                else showAddDialog();
            } else refreshTabs();
        });
        card.addView(del, delLp);

        // Cancel
        Button cancel = new Button(this);
        cancel.setText("取消");
        cancel.setAllCaps(false);
        cancel.setBackgroundColor(Color.WHITE);
        cancel.setTextColor(0xFF007AFF);
        cancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        cancel.getPaint().setFakeBoldText(true);
        cancel.setPadding(0, dp(14), 0, dp(14));
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelLp.topMargin = dp(10);
        cancel.setOnClickListener(v -> dismissDialog());
        card.addView(cancel, cancelLp);

        dialog = new AlertDialog.Builder(this)
            .setView(card)
            .show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0x00000000));
            dialog.getWindow().setGravity(Gravity.BOTTOM);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void dismissDialog() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
    }

    private void scanQR() {
        try {
            Intent intent = new Intent("com.google.zxing.client.android.SCAN");
            intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
            startActivityForResult(intent, 100);
        } catch (Exception e) {
            Toast.makeText(this, "请用微信或浏览器扫描电视上的二维码，然后回到这里", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 200) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                results = new Uri[]{data.getData()};
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
            return;
        }
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            String url = data.getStringExtra("SCAN_RESULT");
            if (url != null && url.startsWith("http")) {
                url = url.replace(":8790/", ":8791/").replace("http://", "https://");
                scannedUrl = url;
                if (scanResultView != null) {
                    String host = url.replace("https://","").replace(":8791/","").split("/")[0];
                    scanResultView.setText("已扫码: " + host);
                    scanResultView.setTextColor(0xFF34C759);
                }
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
