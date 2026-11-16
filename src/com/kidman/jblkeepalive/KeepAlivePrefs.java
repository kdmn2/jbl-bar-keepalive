package com.kidman.jblkeepalive;

import android.content.Context;
import android.content.SharedPreferences;

public final class KeepAlivePrefs {
    private static final String PREFS_NAME = "jbl_keep_alive_prefs";

    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_MODE = "signal_mode";
    public static final String KEY_AMPLITUDE = "amplitude_level";
    public static final String KEY_PAUSE_SCREEN_OFF = "pause_screen_off";
    public static final String KEY_START_ON_BOOT = "start_on_boot";

    // Signal Modes
    public static final int MODE_ORPHEUS_SUB1HZ = 0; // ~0.02 Hz ultra-slow drift (Orpheus JBL Bar 3.1 formula)
    public static final int MODE_INFRASONIC_10HZ = 1; // 10 Hz sine wave (below 35 Hz JBL subwoofer filter)
    public static final int MODE_ULTRASONIC_21KHZ = 2; // 21 kHz ultrasonic wave

    // Digital Amplitude Presets (16-bit PCM max is 32767)
    public static final int AMP_JBL_STANDARD = 0; // 10000 (~-10 dBFS, default Orpheus value for JBL Bar 3.1)
    public static final int AMP_MEDIUM = 1;       // 2500  (~-22 dBFS)
    public static final int AMP_LOW = 2;          // 500   (~-36 dBFS)
    public static final int AMP_ULTRALOW = 3;     // 100   (~-50 dBFS)

    private KeepAlivePrefs() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static int getMode(Context ctx) {
        return prefs(ctx).getInt(KEY_MODE, MODE_ORPHEUS_SUB1HZ);
    }

    public static void setMode(Context ctx, int mode) {
        prefs(ctx).edit().putInt(KEY_MODE, mode).apply();
    }

    public static int getAmplitudePreset(Context ctx) {
        return prefs(ctx).getInt(KEY_AMPLITUDE, AMP_JBL_STANDARD);
    }

    public static void setAmplitudePreset(Context ctx, int preset) {
        prefs(ctx).edit().putInt(KEY_AMPLITUDE, preset).apply();
    }

    public static short getAmplitudeValue(Context ctx) {
        switch (getAmplitudePreset(ctx)) {
            case AMP_MEDIUM:
                return 2500;
            case AMP_LOW:
                return 500;
            case AMP_ULTRALOW:
                return 100;
            case AMP_JBL_STANDARD:
            default:
                return 10000;
        }
    }

    public static boolean isPauseOnScreenOff(Context ctx) {
        return prefs(ctx).getBoolean(KEY_PAUSE_SCREEN_OFF, true);
    }

    public static void setPauseOnScreenOff(Context ctx, boolean pause) {
        prefs(ctx).edit().putBoolean(KEY_PAUSE_SCREEN_OFF, pause).apply();
    }

    public static boolean isStartOnBoot(Context ctx) {
        return prefs(ctx).getBoolean(KEY_START_ON_BOOT, true);
    }

    public static void setStartOnBoot(Context ctx, boolean start) {
        prefs(ctx).edit().putBoolean(KEY_START_ON_BOOT, start).apply();
    }

    public static String getModeLabel(int mode) {
        switch (mode) {
            case MODE_INFRASONIC_10HZ:
                return "Infrasonic 10 Hz (Sub-Bass Cutoff)";
            case MODE_ULTRASONIC_21KHZ:
                return "Ultrasonic 21 kHz (High-Freq)";
            case MODE_ORPHEUS_SUB1HZ:
            default:
                return "JBL Orpheus (~0.02 Hz Zero-Sound Drift)";
        }
    }

    public static String getAmplitudeLabel(int preset) {
        switch (preset) {
            case AMP_MEDIUM:
                return "Medium (PCM 2500 / -22 dBFS)";
            case AMP_LOW:
                return "Low (PCM 500 / -36 dBFS)";
            case AMP_ULTRALOW:
                return "Ultra-Low (PCM 100 / -50 dBFS)";
            case AMP_JBL_STANDARD:
            default:
                return "JBL Bar 3.1 Standard (PCM 10000 / Orpheus)";
        }
    }
}
