package com.yeonent.obsdonation;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import okhttp3.*;

public class MainActivity extends AppCompatActivity {

    private static final int SMS_PERMISSION_CODE = 100;
    private EditText etServerUrl, etProjectId;
    private Switch swEnabled;
    private TextView tvStatus, tvLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etServerUrl = findViewById(R.id.etServerUrl);
        etProjectId = findViewById(R.id.etProjectId);
        swEnabled = findViewById(R.id.swEnabled);
        tvStatus = findViewById(R.id.tvStatus);
        tvLog = findViewById(R.id.tvLog);

        // 저장된 설정 불러오기
        SettingsManager settings = new SettingsManager(this);
        etServerUrl.setText(settings.getServerUrl());
        etProjectId.setText(String.valueOf(settings.getProjectId()));
        swEnabled.setChecked(settings.isEnabled());

        updateStatusUI(settings.isEnabled());

        // 저장 버튼
        findViewById(R.id.btnSave).setOnClickListener(v -> {
            String url = etServerUrl.getText().toString().trim();
            String pid = etProjectId.getText().toString().trim();
            if (url.isEmpty() || pid.isEmpty()) {
                Toast.makeText(this, "서버 주소와 프로젝트 ID를 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            settings.setServerUrl(url);
            settings.setProjectId(Integer.parseInt(pid));
            settings.setEnabled(swEnabled.isChecked());
            updateStatusUI(swEnabled.isChecked());
            Toast.makeText(this, "✅ 설정이 저장되었습니다!", Toast.LENGTH_SHORT).show();
        });

        swEnabled.setOnCheckedChangeListener((btn, checked) -> {
            updateStatusUI(checked);
        });

        // 테스트 전송 버튼
        findViewById(R.id.btnTest).setOnClickListener(v -> {
            sendTestDonation(settings);
        });

        // 배터리 최적화 제외 버튼
        findViewById(R.id.btnBatteryOpt).setOnClickListener(v -> {
            requestBatteryOptimizationExclusion();
        });

        // SMS 권한 요청
        requestSmsPermission();

        // 로그 업데이트
        updateLog(settings);
    }

    private void sendTestDonation(SettingsManager settings) {
        String url = etServerUrl.getText().toString().trim();
        String pid = etProjectId.getText().toString().trim();

        if (url.isEmpty() || pid.isEmpty()) {
            Toast.makeText(this, "서버 주소와 프로젝트 ID를 먼저 설정하고 저장해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        int projectId;
        try {
            projectId = Integer.parseInt(pid);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "프로젝트 ID가 잘못되었습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        tvStatus.setText("🔄 테스트 전송 중...");
        tvStatus.setTextColor(0xFFFF9800);

        String testName = "테스트후원자";
        int testAmount = 1000;

        OkHttpClient client = new OkHttpClient();
        RequestBody body = new FormBody.Builder()
            .add("action", "add_donation")
            .add("project_id", String.valueOf(projectId))
            .add("donor_name", testName)
            .add("streamer_name", testName)
            .add("amount", String.valueOf(testAmount))
            .add("type", "bank")
            .add("raw_sms", "[테스트] 케이뱅크 입금 1,000원 테스트후원자")
            .build();

        Request request = new Request.Builder()
            .url(url + "?action=add_donation&project_id=" + projectId)
            .post(body)
            .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String log = "❌ 테스트 전송 실패: " + e.getMessage();
                settings.addLog(log);
                runOnUiThread(() -> {
                    tvStatus.setText("❌ 서버 연결 실패 - URL 확인필요");
                    tvStatus.setTextColor(0xFFf44336);
                    updateLog(settings);
                    Toast.makeText(MainActivity.this, "서버 연결 실패!\n" + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String respBody = response.body() != null ? response.body().string() : "";
                String log = "✅ 테스트 전송 성공 → " + respBody;
                settings.addLog(log);
                runOnUiThread(() -> {
                    if (swEnabled.isChecked()) {
                        tvStatus.setText("🟢 SMS 자동 전송 활성화됨");
                        tvStatus.setTextColor(0xFF4CAF50);
                    }
                    updateLog(settings);
                    Toast.makeText(MainActivity.this, "✅ 서버 연결 성공!\n대기 큐에 테스트 데이터가 들어갔어요.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void requestBatteryOptimizationExclusion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                Toast.makeText(this, "배터리 최적화 제외 설정 화면이 열립니다.\n'허용'을 선택해주세요.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "✅ 이미 배터리 최적화 제외되어 있습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateStatusUI(boolean enabled) {
        if (enabled) {
            tvStatus.setText("🟢 SMS 자동 전송 활성화됨");
            tvStatus.setTextColor(0xFF4CAF50);
        } else {
            tvStatus.setText("🔴 SMS 자동 전송 비활성화됨");
            tvStatus.setTextColor(0xFFf44336);
        }
    }

    private void updateLog(SettingsManager settings) {
        String log = settings.getRecentLog();
        tvLog.setText(log.isEmpty() ? "아직 수신된 입금 문자가 없습니다." : log);
    }

    private void requestSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS},
                SMS_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "✅ SMS 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "⚠️ SMS 권한이 거부되었습니다.\n설정에서 SMS 권한을 허용해주세요.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLog(new SettingsManager(this));
    }
}
