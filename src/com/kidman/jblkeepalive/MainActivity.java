package com.kidman.jblkeepalive;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView statusBadge;
    private Button btnToggleService;
    private Button btnCycleMode;
    private Button btnCycleAmplitude;
    private Button btnToggleScreenOff;
    private Button btnToggleBoot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Start service automatically if enabled
        if (KeepAlivePrefs.isEnabled(this)) {
            syncServiceState();
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.parseColor("#12161F"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padH = dp(48);
        int padV = dp(32);
        root.setPadding(padH, padV, padH, padV);

        // Header
        TextView title = new TextView(this);
        title.setText("JBL Bar 3.1 Keep-Alive");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("OS-wide inaudible signal • Prevents JBL Bar 3.1 PCM sleep & audio cutoffs • Never steals Audio Focus");
        subtitle.setTextColor(Color.parseColor("#9AA5B8"));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(6);
        subLp.bottomMargin = dp(20);
        root.addView(subtitle, subLp);

        // Status banner
        statusBadge = new TextView(this);
        statusBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        statusBadge.setTypeface(Typeface.DEFAULT_BOLD);
        statusBadge.setPadding(dp(18), dp(12), dp(18), dp(12));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.bottomMargin = dp(20);
        root.addView(statusBadge, badgeLp);

        // Remote D-pad buttons
        btnToggleService = createTvButton();
        btnToggleService.setOnClickListener(v -> {
            boolean next = !KeepAlivePrefs.isEnabled(this);
            KeepAlivePrefs.setEnabled(this, next);
            syncServiceState();
            refreshUi();
        });
        root.addView(btnToggleService, buttonLayoutParams());

        btnCycleMode = createTvButton();
        btnCycleMode.setOnClickListener(v -> {
            int nextMode = (KeepAlivePrefs.getMode(this) + 1) % 3;
            KeepAlivePrefs.setMode(this, nextMode);
            syncServiceState();
            refreshUi();
        });
        root.addView(btnCycleMode, buttonLayoutParams());

        btnCycleAmplitude = createTvButton();
        btnCycleAmplitude.setOnClickListener(v -> {
            int nextAmp = (KeepAlivePrefs.getAmplitudePreset(this) + 1) % 4;
            KeepAlivePrefs.setAmplitudePreset(this, nextAmp);
            syncServiceState();
            refreshUi();
        });
        root.addView(btnCycleAmplitude, buttonLayoutParams());

        btnToggleScreenOff = createTvButton();
        btnToggleScreenOff.setOnClickListener(v -> {
            boolean next = !KeepAlivePrefs.isPauseOnScreenOff(this);
            KeepAlivePrefs.setPauseOnScreenOff(this, next);
            syncServiceState();
            refreshUi();
        });
        root.addView(btnToggleScreenOff, buttonLayoutParams());

        btnToggleBoot = createTvButton();
        btnToggleBoot.setOnClickListener(v -> {
            boolean next = !KeepAlivePrefs.isStartOnBoot(this);
            KeepAlivePrefs.setStartOnBoot(this, next);
            refreshUi();
        });
        root.addView(btnToggleBoot, buttonLayoutParams());

        scroll.addView(root);
        setContentView(scroll);

        refreshUi();
        btnToggleService.requestFocus();
    }

    private void syncServiceState() {
        Intent svc = new Intent(this, KeepAliveService.class);
        if (KeepAlivePrefs.isEnabled(this)) {
            svc.setAction(KeepAliveService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
        } else {
            svc.setAction(KeepAliveService.ACTION_STOP);
            startService(svc);
        }
    }

    private void refreshUi() {
        boolean enabled = KeepAlivePrefs.isEnabled(this);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(dp(10));
        if (enabled) {
            badgeBg.setColor(Color.parseColor("#163828"));
            badgeBg.setStroke(dp(2), Color.parseColor("#2ECC71"));
            statusBadge.setTextColor(Color.parseColor("#69F0AE"));
            statusBadge.setText("● STATUS: ACTIVE — Mixing inaudible keep-alive signal across all apps");
        } else {
            badgeBg.setColor(Color.parseColor("#3B1B1E"));
            badgeBg.setStroke(dp(2), Color.parseColor("#E74C3C"));
            statusBadge.setTextColor(Color.parseColor("#FF8A80"));
            statusBadge.setText("○ STATUS: STOPPED — Soundbar may enter standby during silence");
        }
        statusBadge.setBackground(badgeBg);

        btnToggleService.setText("Keep-Alive Service:  " + (enabled ? "ENABLED (Press OK to Stop)" : "DISABLED (Press OK to Start)"));
        btnCycleMode.setText("Signal Waveform:  " + KeepAlivePrefs.getModeLabel(KeepAlivePrefs.getMode(this)));
        btnCycleAmplitude.setText("Digital Level:  " + KeepAlivePrefs.getAmplitudeLabel(KeepAlivePrefs.getAmplitudePreset(this)));
        btnToggleScreenOff.setText("Pause When TV/Screen is Off:  " + (KeepAlivePrefs.isPauseOnScreenOff(this) ? "YES (Allows 10m Standby when Off)" : "NO (Always On 24/7)"));
        btnToggleBoot.setText("Auto-Start on Chromecast Boot:  " + (KeepAlivePrefs.isStartOnBoot(this) ? "YES" : "NO"));
    }

    private Button createTvButton() {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setPadding(dp(20), dp(14), dp(20), dp(14));
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);

        GradientDrawable focused = new GradientDrawable();
        focused.setColor(Color.parseColor("#253652"));
        focused.setCornerRadius(dp(10));
        focused.setStroke(dp(3), Color.parseColor("#4FC3F7"));

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(Color.parseColor("#1C2230"));
        normal.setCornerRadius(dp(10));
        normal.setStroke(dp(1), Color.parseColor("#2E384D"));

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{}, normal);
        b.setBackground(states);
        return b;
    }

    private LinearLayout.LayoutParams buttonLayoutParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        return lp;
    }

    private int dp(int val) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics());
    }
}
