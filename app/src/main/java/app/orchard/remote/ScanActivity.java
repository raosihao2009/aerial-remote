package app.orchard.remote;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

public class ScanActivity extends Activity {
    private WebView wv;
    private static final int CAM_REQ = 100;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, CAM_REQ);
        } else {
            startScan();
        }
    }

    private void startScan() {
        wv = new WebView(this);
        wv.getSettings().setJavaScriptEnabled(true);
        wv.getSettings().setMediaPlaybackRequiresUserGesture(false);
        wv.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onScan(String url) {
                runOnUiThread(() -> {
                    setResult(RESULT_OK, new Intent().putExtra("SCAN_RESULT", url));
                    finish();
                });
            }
        }, "Android");
        wv.setWebChromeClient(new WebChromeClient());
        wv.loadUrl("file:///android_asset/scan.html");
        setContentView(wv);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] res) {
        super.onRequestPermissionsResult(req, p, res);
        if (req == CAM_REQ && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) startScan();
        else finish();
    }
}
