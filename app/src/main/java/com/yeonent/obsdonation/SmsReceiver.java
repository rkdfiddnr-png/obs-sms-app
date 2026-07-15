package com.yeonent.obsdonation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.*;

public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "ObsSmsReceiver";

    // ============================================================
    // 한국 주요 은행 입금 문자 파싱 패턴
    // ============================================================
    // 국민은행: "[국민은행] 입금 홍길동 50,000원 ..."
    // 신한은행: "[신한] 입금 홍길동 50,000원 ..."
    // 우리은행: "[우리은행] 홍길동님으로부터 50,000원이 입금되었습니다."
    // 하나은행: "[하나은행] 입금 50,000원 홍길동 ..."
    // 카카오뱅크: "카카오뱅크 홍길동 50,000원 입금 ..."
    // 토스뱅크: "토스뱅크 홍길동 50,000원 이체완료"
    // 농협: "[농협은행] 입금 홍길동 50,000원 ..."
    // 케이뱅크(다중라인): "[케이뱅크]\n송승*(2185)\n입금 1원\n잔액 ****원\n메모텍스트"
    // ============================================================

    private static final Pattern[] PATTERNS = {
        // 패턴1: 입금 이름 금액 (국민, 신한, 농협, 하나 등)
        Pattern.compile("입금[\\s]*([가-힣a-zA-Z0-9]+)[\\s]+(\\d[\\d,]+)원"),
        // 패턴2: 이름님으로부터 금액 (우리은행)
        Pattern.compile("([가-힣a-zA-Z0-9]+)님으로부터[\\s]+(\\d[\\d,]+)원.*입금"),
        // 패턴3: 이름 금액 입금 (카카오, 토스 등)
        Pattern.compile("([가-힣a-zA-Z0-9]+)[\\s]+(\\d[\\d,]+)원[\\s]*입금"),
        // 패턴4: 이름 금액 이체완료 (토스뱅크)
        Pattern.compile("([가-힣a-zA-Z0-9]+)[\\s]+(\\d[\\d,]+)원[\\s]*이체완료"),
        // 패턴5: 입금 금액 이름 순서 (하나은행 일부)
        Pattern.compile("입금[\\s]+(\\d[\\d,]+)원[\\s]+([가-힣a-zA-Z0-9]+)"),
    };

    // 케이뱅크 다중라인 패턴: "입금 N원" 이 별도 줄에 있는 형태
    private static final Pattern KBANK_AMOUNT_PATTERN = Pattern.compile("입금[\\s]*(\\d[\\d,]*)원");

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;

        SettingsManager settings = new SettingsManager(context);
        if (!settings.isEnabled()) return;

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null) return;

        StringBuilder fullMessage = new StringBuilder();
        String sender = "";

        for (Object pdu : pdus) {
            SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
            sender = sms.getDisplayOriginatingAddress();
            fullMessage.append(sms.getMessageBody());
        }

        String msgBody = fullMessage.toString();
        Log.d(TAG, "SMS 수신 from " + sender + ": " + msgBody);

        // 입금 문자인지 확인
        if (!isDepositSms(msgBody)) {
            Log.d(TAG, "입금 문자 아님 - 무시");
            return;
        }

        // 금액/이름 파싱
        ParsedDeposit deposit = parseDeposit(msgBody);
        if (deposit == null) {
            Log.d(TAG, "파싱 실패 - 패턴 불일치");
            return;
        }

        Log.d(TAG, "파싱 성공: 이름=" + deposit.donorName + ", 금액=" + deposit.amount);

        // API로 전송
        sendToApi(context, settings, deposit, msgBody);
    }

    private boolean isDepositSms(String body) {
        String lower = body.toLowerCase();
        return lower.contains("입금") || lower.contains("이체완료") || lower.contains("이체");
    }

    private ParsedDeposit parseDeposit(String body) {
        // ── 케이뱅크 다중라인 처리 (우선 시도) ──────────────────────────────
        if (body.contains("케이뱅크") || body.contains("KBank") || body.contains("kbank")) {
            ParsedDeposit kbankResult = parseKbankMultiline(body);
            if (kbankResult != null) return kbankResult;
        }

        // ── 일반 패턴 매칭 ──────────────────────────────────────────────────
        for (int i = 0; i < PATTERNS.length; i++) {
            Matcher m = PATTERNS[i].matcher(body);
            if (m.find()) {
                String name, amountStr;
                if (i == 4) {
                    amountStr = m.group(1);
                    name = m.group(2);
                } else {
                    name = m.group(1);
                    amountStr = m.group(2);
                }
                if (name == null || amountStr == null) continue;
                int amount = Integer.parseInt(amountStr.replace(",", ""));
                if (amount <= 0) continue;
                return new ParsedDeposit(name.trim(), amount);
            }
        }
        return null;
    }

    private ParsedDeposit parseKbankMultiline(String body) {
        Matcher amountMatcher = KBANK_AMOUNT_PATTERN.matcher(body);
        if (!amountMatcher.find()) return null;

        String amountStr = amountMatcher.group(1);
        int amount;
        try {
            amount = Integer.parseInt(amountStr.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
        if (amount <= 0) return null;

        String[] lines = body.split("[\\n\\r]+");
        String donorLine = null;

        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("[")) continue;
            if (line.contains("잔액")) continue;
            if (line.contains("입금")) continue;
            if (line.contains("이체")) continue;
            if (line.matches(".*\\(\\d+\\).*")) continue;
            if (line.matches("[\\d,\\s\\*]+")) continue;
            if (line.matches(".*[가-힣a-zA-Z].*")) {
                donorLine = line;
                break;
            }
        }

        if (donorLine == null) return null;

        String donorName = donorLine.trim();
        if (donorName.length() > 30) {
            donorName = donorName.substring(0, 30);
        }

        return new ParsedDeposit(donorName, amount);
    }

    private void sendToApi(Context context, SettingsManager settings, ParsedDeposit deposit, String rawMsg) {
        String serverUrl = settings.getServerUrl();
        int projectId = settings.getProjectId();

        OkHttpClient client = new OkHttpClient();
        RequestBody body = new FormBody.Builder()
            .add("action", "add_donation")
            .add("project_id", String.valueOf(projectId))
            .add("donor_name", deposit.donorName)
            .add("streamer_name", deposit.donorName)
            .add("amount", String.valueOf(deposit.amount))
            .add("type", "bank")
            .add("raw_sms", rawMsg)
            .build();

        Request request = new Request.Builder()
            .url(serverUrl + "?action=add_donation&project_id=" + projectId)
            .post(body)
            .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String log = "❌ 전송 실패 [" + deposit.donorName + " " + deposit.amount + "원]: " + e.getMessage();
                Log.e(TAG, log);
                settings.addLog(log);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String respBody = response.body() != null ? response.body().string() : "";
                String log = "✅ 전송 성공 [" + deposit.donorName + " " + deposit.amount + "원] → " + respBody;
                Log.d(TAG, log);
                settings.addLog(log);
            }
        });
    }

    static class ParsedDeposit {
        String donorName;
        int amount;
        ParsedDeposit(String name, int amt) {
            this.donorName = name;
            this.amount = amt;
        }
    }
}
