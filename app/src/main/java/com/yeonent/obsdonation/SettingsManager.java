package com.yeonent.obsdonation;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsManager {
    private static final String PREF_NAME = "obs_settings";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_PROJECT_ID = "project_id";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LOG = "recent_log";

    // ★ 기본 서버 주소 - 실제 서버로 미리 설정됨
    private static final String DEFAULT_URL = "http://175.126.38.54/obs/api.php";
    private static final int DEFAULT_PROJECT_ID = 1;

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public String getServerUrl() {
        return prefs.getString(KEY_SERVER_URL, DEFAULT_URL);
    }

    public void setServerUrl(String url) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply();
    }

    public int getProjectId() {
        return prefs.getInt(KEY_PROJECT_ID, DEFAULT_PROJECT_ID);
    }

    public void setProjectId(int id) {
        prefs.edit().putInt(KEY_PROJECT_ID, id).apply();
    }

    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, true);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public String getRecentLog() {
        return prefs.getString(KEY_LOG, "");
    }

    public void addLog(String message) {
        String timestamp = new SimpleDateFormat("MM/dd HH:mm:ss", Locale.KOREA).format(new Date());
        String newLog = "[" + timestamp + "] " + message + "\n" + getRecentLog();
        // 최대 20줄만 보관
        String[] lines = newLog.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(lines.length, 20); i++) {
            sb.append(lines[i]).append("\n");
        }
        prefs.edit().putString(KEY_LOG, sb.toString()).apply();
    }
}
