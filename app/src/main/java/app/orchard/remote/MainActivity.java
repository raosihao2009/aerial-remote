package app.orchard.remote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import android.webkit.ValueCallback;
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
            btn.setMinHeight(dp(36));
            btn.setPadding(dp(14), dp(6), dp(14), dp(6));
            btn.setBackgroundColor(0xFFF2F2F7);
            btn.setTextColor(0xFF333333);
            btn.setClipToOutline(true);
            btn.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(18));
                }
            });
            if (url.equals(currentUrl)) {
                btn.setTextColor(Color.WHITE);
                btn.setBackgroundColor(0xFF007AFF);
            }
            btn.setOnClickListener(v -> loadUrl(url));
            btn.setOnLongClickListener(v -> { showRenameDialog(url); return true; });
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            bp.rightMargin = dp(8);
            btn.setLayoutParams(bp);
            tabBar.addView(btn);
        }
        Button addBtn = new Button(this);
        addBtn.setText("+ 添加");
        addBtn.setAllCaps(false);
        addBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        addBtn.setMinHeight(dp(36));
        addBtn.setPadding(dp(14), dp(6), dp(14), dp(6));
        addBtn.setBackgroundColor(0xFF34C759);
        addBtn.setTextColor(Color.WHITE);
        addBtn.setClipToOutline(true);
        addBtn.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(18));
            }
        });
        addBtn.setOnClickListener(v -> showAddDialog());
        tabBar.addView(addBtn);

        if (currentUrl == null && !devices.isEmpty()) loadUrl(devices.get(0));
        else if (currentUrl == null) showAddDialog();
    }

    // 网页同款底部弹窗
    private LinearLayout newCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(dp(12));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        card.setLayoutParams(lp);
        return card;
    }

    private Button newSheetButton(String text, int color, boolean bold) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setBackgroundColor(0xFFFFFFFF);
        b.setTextColor(color);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        b.setMinHeight(dp(52));
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        b.setGravity(Gravity.CENTER);
        if (bold) b.getPaint().setFakeBoldText(true);
        return b;
    }

    private void addSeparator(LinearLayout card) {
        View line = new View(this);
        line.setBackgroundColor(0x303C3C4B);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = dp(16);
        line.setLayoutParams(lp);
        card.addView(line);
    }

    private void showAddDialog() {
        scannedUrl = null;
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackgroundColor(0xFFF2F2F7);
        sheet.setPadding(dp(16), dp(8), dp(16), dp(16));

        // Grab handle
        View grab = new View(this);
        grab.setBackgroundColor(0x4D3C3C4B);
        LinearLayout.LayoutParams grabLp = new LinearLayout.LayoutParams(dp(36), dp(5));
        grabLp.gravity = Gravity.CENTER_HORIZONTAL;
        grabLp.bottomMargin = dp(14);
        grab.setLayoutParams(grabLp);
        sheet.addView(grab);

        // Title
        TextView title = new TextView(this);
        title.setText("添加 TV 设备");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTextColor(0xFF000000);
        title.setGravity(Gravity.CENTER);
        title.getPaint().setFakeBoldText(true);
        title.setPadding(0, 0, 0, dp(16));
        sheet.addView(title);

        // Card 1: scan + paste
        LinearLayout card1 = newCard();
        Button scan = newSheetButton("扫描二维码", 0xFF007AFF, false);
        card1.addView(scan);
        View sep = new View(this);
        sep.setBackgroundColor(0x303C3C4B);
        LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
        sepLp.leftMargin = dp(16);
        sep.setLayoutParams(sepLp);
        card1.addView(sep);
        Button paste = newSheetButton("粘贴链接", 0xFF007AFF, false);
        paste.setOnClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
                String clip = cm.getPrimaryClip().getItemAt(0).getText().toString().trim();
                if (clip.startsWith("http")) {
                    clip = clip.replace(":8790/", ":8791/").replace("http://", "https://");
                    scannedUrl = clip;
                    scanResultView.setText("已粘贴: " + clip.replace("https://","").replace(":8791/","").split("/")[0]);
                    scanResultView.setTextColor(0xFF34C759);
                } else {
                    scanResultView.setText("剪贴板没有链接");
                    scanResultView.setTextColor(0xFFFF3B30);
                }
            }
        });
        card1.addView(paste);
        sheet.addView(card1);

        // Scan result
        scanResultView = new TextView(this);
        scanResultView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        scanResultView.setTextColor(0xFFFF3B30);
        scanResultView.setPadding(dp(16), dp(8), 0, 0);
        scanResultView.setText("尚未扫码");
        sheet.addView(scanResultView);

        // Card 2: name input
        LinearLayout card2 = newCard();
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.HORIZONTAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(dp(16), 0, dp(16), 0);
        field.setMinimumHeight(dp(52));
        final EditText input = new EditText(this);
        input.setHint("设备名称（如：当贝盒子）");
        input.setBackground(null);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        field.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        card2.addView(field);
        LinearLayout.LayoutParams c2lp = (LinearLayout.LayoutParams) card2.getLayoutParams();
        c2lp.topMargin = dp(4);
        sheet.addView(card2);

        // Card 3: OK
        LinearLayout card3 = newCard();
        Button ok = newSheetButton("确定", 0xFF007AFF, false);
        ok.setEnabled(false);
        ok.setAlpha(0.35f);
        card3.addView(ok);
        sheet.addView(card3);

        ok.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            prefs.edit().putString("name_" + scannedUrl, name).apply();
            List<String> devices = getDevices();
            if (!devices.contains(scannedUrl)) { devices.add(scannedUrl); saveDevices(devices); }
            dismissDialog();
            loadUrl(scannedUrl);
        });

        input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence a, int b, int c, int d) {}
            public void onTextChanged(CharSequence a, int b, int c, int d) {}
            public void afterTextChanged(android.text.Editable a) {
                boolean ready = scannedUrl != null && !a.toString().trim().isEmpty();
                ok.setEnabled(ready);
                ok.setAlpha(ready ? 1f : 0.35f);
            }
        });

        scan.setOnClickListener(v -> scanQR());

        dialog = new AlertDialog.Builder(this)
            .setView(sheet)
            .setCancelable(true)
            .show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0x00000000));
            dialog.getWindow().setGravity(Gravity.BOTTOM);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showRenameDialog(String url) {
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackgroundColor(0xFFF2F2F7);
        sheet.setPadding(dp(16), dp(8), dp(16), dp(16));

        View grab = new View(this);
        grab.setBackgroundColor(0x4D3C3C4B);
        LinearLayout.LayoutParams g = new LinearLayout.LayoutParams(dp(36), dp(5));
        g.gravity = Gravity.CENTER_HORIZONTAL; g.bottomMargin = dp(14);
        grab.setLayoutParams(g);
        sheet.addView(grab);

        TextView title = new TextView(this);
        title.setText("设备设置");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTextColor(0xFF000000);
        title.setGravity(Gravity.CENTER);
        title.getPaint().setFakeBoldText(true);
        title.setPadding(0, 0, 0, dp(16));
        sheet.addView(title);

        // Name input
        LinearLayout card1 = newCard();
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.HORIZONTAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(dp(16), 0, dp(16), 0);
        field.setMinimumHeight(dp(52));
        final EditText input = new EditText(this);
        input.setText(getName(url));
        input.setBackground(null);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        field.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        card1.addView(field);
        sheet.addView(card1);

        // OK
        LinearLayout card2 = newCard();
        Button ok = newSheetButton("确定", 0xFF007AFF, false);
        ok.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) prefs.edit().putString("name_" + url, name).apply();
            dismissDialog();
            refreshTabs();
        });
        card2.addView(ok);
        sheet.addView(card2);

        // Delete
        LinearLayout card3 = newCard();
        Button del = newSheetButton("删除设备", 0xFFFF3B30, false);
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
        card3.addView(del);
        sheet.addView(card3);

        dialog = new AlertDialog.Builder(this).setView(sheet).show();
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
        startActivityForResult(new Intent(this, ScanActivity.class), 100);
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
            if (url != null) {
            if (url.startsWith("http")) {
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
