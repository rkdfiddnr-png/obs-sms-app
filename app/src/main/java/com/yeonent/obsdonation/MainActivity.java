package com.yeonent.obsdonation;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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

        // SMS 권한 요청
        requestSmsPermission();

        // 로그 업데이트
        updateLog(settings);
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
                Toast.makeText(this, "⚠️ SMS 권한이 필요합니다. 설정에서 허용해주세요.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLog(new SettingsManager(this));
    }
}
