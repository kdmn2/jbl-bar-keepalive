package com.kidman.jblkeepalive;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public class KeepAliveService extends Service {
    public static final String ACTION_START = "com.kidman.jblkeepalive.START";
    public static final String ACTION_STOP = "com.kidman.jblkeepalive.STOP";
    public static final String ACTION_RELOAD = "com.kidman.jblkeepalive.RELOAD";

    private static final String CHANNEL_ID = "jbl_keepalive_channel";
    private static final int NOTIFICATION_ID = 3101;
    private static final int SAMPLE_RATE = 48000;

    private volatile boolean isRunning = false;
    private volatile boolean isScreenOn = true;
    private Thread audioThread;
    private PowerManager.WakeLock wakeLock;
    private BroadcastReceiver screenReceiver;

    public static volatile boolean sServiceActive = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            isScreenOn = pm.isInteractive();
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JBLBarKeepAlive::AudioWakeLock");
            wakeLock.setReferenceCounted(false);
        }

        screenReceiver = new ScreenReceiver(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, filter);
    }

    void onScreenStateChanged(boolean on) {
        isScreenOn = on;
        updateWakeLock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action) || !KeepAlivePrefs.isEnabled(this)) {
            stopKeepAlive();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification());
        startKeepAliveThread();
        return START_STICKY;
    }

    private void startKeepAliveThread() {
        sServiceActive = true;
        updateWakeLock();
        if (isRunning && audioThread != null && audioThread.isAlive()) {
            return;
        }
        isRunning = true;
        audioThread = new Thread(this::runAudioLoop, "JBLKeepAliveAudioThread");
        audioThread.setPriority(Thread.NORM_PRIORITY);
        audioThread.start();
    }

    private void stopKeepAlive() {
        isRunning = false;
        sServiceActive = false;
        if (audioThread != null) {
            audioThread.interrupt();
            audioThread = null;
        }
        releaseWakeLock();
    }

    private void updateWakeLock() {
        if (wakeLock == null) return;
        boolean shouldHold = isRunning && (!KeepAlivePrefs.isPauseOnScreenOff(this) || isScreenOn);
        try {
            if (shouldHold && !wakeLock.isHeld()) {
                wakeLock.acquire();
            } else if (!shouldHold && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Throwable ignored) {}
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Throwable ignored) {}
        }
    }

    private void runAudioLoop() {
        final int framesPerChunk = 1024;
        final short[] stereoBuffer = new short[framesPerChunk * 2];
        int minBufBytes = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        int bufferSizeBytes = Math.max(minBufBytes * 2, framesPerChunk * 4 * 2);

        AudioTrack track = null;
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();

            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build();

            track = new AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSizeBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();

            track.play();
            double phase = 0.0;
            int orpheusIdx = 0;

            while (isRunning && !Thread.currentThread().isInterrupted()) {
                if (KeepAlivePrefs.isPauseOnScreenOff(this) && !isScreenOn) {
                    if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        track.pause();
                        track.flush();
                    }
                    Thread.sleep(500);
                    continue;
                }

                if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play();
                }

                int mode = KeepAlivePrefs.getMode(this);
                short amp = KeepAlivePrefs.getAmplitudeValue(this);

                if (mode == KeepAlivePrefs.MODE_ORPHEUS_SUB1HZ) {
                    // Exact Orpheus JBL Bar 3.1 algorithm: ultra-slowly varying PCM values
                    // Keeps JBL Bar 3.1 digital noise gate open with zero speaker cone oscillation
                    for (int i = 0; i < framesPerChunk; i++) {
                        short sample = (short) (Math.sin(orpheusIdx * Math.PI / 1000000.0) * amp);
                        orpheusIdx = (orpheusIdx + 1) & 511;
                        stereoBuffer[i * 2] = sample;
                        stereoBuffer[i * 2 + 1] = sample;
                    }
                } else {
                    double freq = (mode == KeepAlivePrefs.MODE_ULTRASONIC_21KHZ) ? 21000.0 : 10.0;
                    double phaseStep = (2.0 * Math.PI * freq) / SAMPLE_RATE;
                    for (int i = 0; i < framesPerChunk; i++) {
                        short sample = (short) (Math.sin(phase) * amp);
                        phase += phaseStep;
                        if (phase >= 2.0 * Math.PI) {
                            phase -= 2.0 * Math.PI;
                        }
                        stereoBuffer[i * 2] = sample;
                        stereoBuffer[i * 2 + 1] = sample;
                    }
                }

                int written = track.write(stereoBuffer, 0, stereoBuffer.length);
                if (written < 0) {
                    Thread.sleep(250);
                }
            }
        } catch (InterruptedException ignored) {
        } catch (Throwable ignored) {
        } finally {
            if (track != null) {
                try {
                    track.stop();
                } catch (Throwable ignored) {}
                try {
                    track.release();
                } catch (Throwable ignored) {}
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "JBL Bar Keep-Alive Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps JBL Bar 3.1 awake during silent gaps");
            channel.setSound(null, null);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent, flags);

        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle("JBL Bar 3.1 Keep-Alive Active")
                .setContentText(KeepAlivePrefs.getModeLabel(KeepAlivePrefs.getMode(this)))
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        stopKeepAlive();
        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

class ScreenReceiver extends BroadcastReceiver {
    private final KeepAliveService service;

    ScreenReceiver(KeepAliveService service) {
        this.service = service;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
            service.onScreenStateChanged(false);
        } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
            service.onScreenStateChanged(true);
        }
    }
}
