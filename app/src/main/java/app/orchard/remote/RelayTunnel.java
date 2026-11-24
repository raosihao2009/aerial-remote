package app.orchard.remote;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import android.util.Base64;

/**
 * 手机到中继的隧道客户端。电视和手机不直连, 两边各自出站连到中继, 这里负责:
 * 配对查询、经中继取电视首页、经中继下发一条请求并轮询响应。
 *
 * 协议字段与电视端 RelayClient、relay/server.js 保持一致: JSON + bodyB64,
 * rid 由手机端生成。MVP 只转发控制类小请求, 二进制上传(照片/图标/备份)仍走局域网。
 *
 * 所有方法都在后台线程调用, 不要在 UI 线程里跑。
 */
final class RelayTunnel {
    static final int PAIR_NOT_FOUND = 502;
    static final int PAIR_UNAUTHORIZED = 503;
    static final int DEVICE_OFFLINE = 504;
    static final int NETWORK = 501;

    private static final int REQ_TIMEOUT_MS = 15_000;
    private static final int POLL_INTERVAL_MS = 500;
    private static int ridSeq = 0;

    private RelayTunnel() {
    }

    /** 中继地址归一化: 缺协议补 https://, 去掉末尾斜杠; 空串返回空串。 */
    static String normalizeBase(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) return "";
        if (!v.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) v = "https://" + v;
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }

    /** 配对校验: secret 对得上就返回中继给的设备名和在线状态, 否则抛 RelayException。 */
    static JSONObject pair(String base, String id, String secret) throws IOException, JSONException {
        JSONObject body = new JSONObject().put("id", id).put("secret", secret);
        HttpResult r = postJson(base + "/phone/pair", body.toString());
        JSONObject j = parse(r.text);
        if (r.status == 200 && j != null && j.optBoolean("ok")) return j;
        int code;
        if (r.status == 404) code = PAIR_NOT_FOUND;
        else if (r.status == 401) code = PAIR_UNAUTHORIZED;
        else if (r.status == 503) code = DEVICE_OFFLINE;
        else code = NETWORK;
        throw new RelayException(code, reason(j, r.status));
    }

    /** 经中继取电视首页 HTML(走 GET /)。 */
    static String fetchPage(String base, String id, String secret) throws IOException, JSONException {
        JSONObject res = request(base, id, secret, "GET", "/", "{}", null);
        return decodeB64(res.optString("bodyB64", ""));
    }

    /**
     * 下发一条请求到电视并等响应。返回中继响应: {status, headers, bodyB64}。
     * 超时、离线、鉴权失败都转成 RelayException。
     */
    static JSONObject request(String base, String id, String secret, String method,
                              String path, String headersJson, String bodyB64)
            throws IOException, JSONException {
        String rid = "r" + (ridSeq++) + "_" + System.currentTimeMillis();
        JSONObject headers;
        try {
            headers = new JSONObject(headersJson == null || headersJson.isEmpty() ? "{}" : headersJson);
        } catch (Exception e) {
            headers = new JSONObject();
        }
        JSONObject req = new JSONObject()
                .put("id", id)
                .put("secret", secret)
                .put("rid", rid)
                .put("method", method)
                .put("path", path)
                .put("headers", headers)
                .put("bodyB64", bodyB64 == null ? JSONObject.NULL : bodyB64);

        HttpResult pr = postJson(base + "/phone/req", req.toString());
        JSONObject pj = parse(pr.text);
        if (pr.status == 401) throw new RelayException(PAIR_UNAUTHORIZED, "密钥不对");
        if (pr.status == 503) throw new RelayException(DEVICE_OFFLINE, "电视不在线");
        if (pr.status != 200 || pj == null || !pj.optBoolean("ok")) {
            throw new RelayException(NETWORK, "中继下发失败");
        }

        long deadline = System.currentTimeMillis() + REQ_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            sleep(POLL_INTERVAL_MS);
            String q = "?id=" + enc(id) + "&secret=" + enc(secret) + "&rid=" + enc(rid);
            HttpResult rr = get(base + "/phone/res" + q);
            JSONObject rj = parse(rr.text);
            if (rj == null) continue;
            if (rj.optBoolean("pending")) continue;
            return rj;
        }
        throw new RelayException(NETWORK, "响应超时");
    }

    /** 把中继响应里的 bodyB64 解成 UTF-8 文本。 */
    static String decodeBodyText(JSONObject res) {
        return decodeB64(res == null ? "" : res.optString("bodyB64", ""));
    }

    private static HttpResult postJson(String url, String jsonBody) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(REQ_TIMEOUT_MS);
            c.setReadTimeout(REQ_TIMEOUT_MS);
            c.setInstanceFollowRedirects(false);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            OutputStream o = c.getOutputStream();
            o.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            o.close();
            return readResult(c);
        } finally {
            c.disconnect();
        }
    }

    private static HttpResult get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestMethod("GET");
            c.setConnectTimeout(REQ_TIMEOUT_MS);
            c.setReadTimeout(REQ_TIMEOUT_MS);
            c.setInstanceFollowRedirects(false);
            return readResult(c);
        } finally {
            c.disconnect();
        }
    }

    private static HttpResult readResult(HttpURLConnection c) throws IOException {
        int status = c.getResponseCode();
        InputStream s = status >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = streamToString(s);
        return new HttpResult(status, text);
    }

    private static String streamToString(InputStream s) throws IOException {
        if (s == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = s.read(buf)) >= 0) out.write(buf, 0, n);
        s.close();
        return out.toString("UTF-8");
    }

    private static JSONObject parse(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            return new JSONObject(text);
        } catch (Exception e) {
            return null;
        }
    }

    private static String reason(JSONObject j, int status) {
        if (j != null && j.optString("reason", "").length() > 0) return j.optString("reason");
        return "HTTP " + status;
    }

    private static String decodeB64(String b64) {
        if (b64 == null || b64.isEmpty()) return "";
        try {
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static String enc(String v) throws IOException {
        return URLEncoder.encode(v, "UTF-8");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class HttpResult {
        final int status;
        final String text;

        HttpResult(int status, String text) {
            this.status = status;
            this.text = text;
        }
    }

    /** 中继侧可识别的失败: code 对应 error.html 里的提示条目。 */
    static final class RelayException extends IOException {
        final int code;

        RelayException(int code, String message) {
            super(message);
            this.code = code;
        }
    }
}
