package com.vpaypro.monitor.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.vpaypro.monitor.BuildConfig;
import com.vpaypro.monitor.manager.AppConstants;

import org.json.JSONObject;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 版本检查：只向商户自己配置的服务端查询，不访问任何第三方域名。
 * 下载地址只接受 https，避免被降级到明文。
 */
public final class UpdateChecker {

    private static final String TAG = "UpdateChecker";

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build();

    public interface Callback {
        /** hasNew=false 时 version/url 可能为空 */
        void onResult(boolean hasNew, String version, String url);
    }

    private UpdateChecker() {
    }

    public static void checkAsync(Context ctx, final Callback cb) {
        SharedPreferences sp = ctx.getApplicationContext()
                .getSharedPreferences(AppConstants.SP_NAME_CONFIG, Context.MODE_PRIVATE);
        final String host = sp.getString(AppConstants.SP_KEY_HOST, "");
        final String key = sp.getString(AppConstants.SP_KEY_KEY, "");
        if (host == null || host.isEmpty() || key == null || key.isEmpty()) {
            if (cb != null) {
                cb.onResult(false, "", "");
            }
            return;
        }

        String t = String.valueOf(new Date().getTime());
        String url = AppConstants.baseUrl(host) + "/getState?t=" + t + "&sign=" + md5(t + key);

        CLIENT.newCall(new Request.Builder().url(url).method("GET", null).build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.w(TAG, "检查更新失败：" + e.getMessage());
                        if (cb != null) {
                            cb.onResult(false, "", "");
                        }
                    }

                    @Override
                    public void onResponse(Call call, Response resp) {
                        boolean hasNew = false;
                        String ver = "";
                        String dl = "";
                        try {
                            String body = resp.body() == null ? "" : resp.body().string();
                            JSONObject root = new JSONObject(body);
                            if (root.optInt("code", 0) == 1) {
                                JSONObject d = root.optJSONObject("data");
                                if (d != null) {
                                    ver = d.optString("latest_version", "");
                                    dl = d.optString("download_url", "");
                                    // 服务端返回整型版本号时优先用它；尚未配置时回落到版本名比较
                                    int latestCode = d.optInt("latest_version_code", 0);
                                    boolean newer = latestCode > 0
                                            ? latestCode > BuildConfig.VERSION_CODE
                                            : compareVersion(ver, BuildConfig.VERSION_NAME) > 0;
                                    hasNew = newer && dl.startsWith("https://");
                                }
                            } else {
                                Log.w(TAG, "检查更新返回：" + body);
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "检查更新解析失败", e);
                        } finally {
                            resp.close();
                        }
                        if (cb != null) {
                            cb.onResult(hasNew, ver, dl);
                        }
                    }
                });
    }

    /** 按点分段比较版本号，返回 >0 表示 a 比 b 新 */
    static int compareVersion(String a, String b) {
        int[] pa = versionParts(a);
        int[] pb = versionParts(b);
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int va = i < pa.length ? pa[i] : 0;
            int vb = i < pb.length ? pb[i] : 0;
            if (va != vb) {
                return va > vb ? 1 : -1;
            }
        }
        return 0;
    }

    /** 取出所有数字段，忽略 "v" 前缀、空段等噪声 */
    private static int[] versionParts(String v) {
        if (v == null || v.trim().isEmpty()) {
            return new int[0];
        }
        String[] raw = v.trim().split("[^0-9]+");
        int start = 0;
        int end = raw.length;
        while (start < end && raw[start].isEmpty()) {
            start++;
        }
        while (end > start && raw[end - 1].isEmpty()) {
            end--;
        }
        int[] out = new int[end - start];
        for (int i = start; i < end; i++) {
            out[i - start] = parseIntSafe(raw[i]);
        }
        return out;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bs = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bs) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            Log.w(TAG, "md5 计算失败", e);
            return "";
        }
    }
}
