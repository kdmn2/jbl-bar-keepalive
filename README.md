# JBL Bar 3.1 Keep-Alive (Android TV / Google TV / Chromecast 4K)

An ultra-lightweight (~21 KB) Android TV / Google TV background service and Leanback app that prevents the **JBL Bar 3.1** (and other soundbars with aggressive digital noise gates) from muting or entering standby during silent audio gaps.

---

## The Problem

The **JBL Bar 3.1** has two built-in DSP behaviors that cannot be disabled via hardware buttons or firmware settings:

1. **Aggressive Digital PCM Noise-Gate (~1–2 seconds of silence):** When connected over HDMI ARC or Optical receiving stereo PCM, the soundbar's internal DAC mutes the amplifier as soon as digital zeroes are sent (such as between YouTube videos, during quiet movie dialogue pauses, or while navigating menus). When audio resumes, the soundbar takes **1–1.5 seconds** to wake back up (flashing `PCM` on its front display) and cuts off the first words of dialogue.
2. **10-Minute Auto-Standby:** After 10 minutes without signal above its digital noise gate, the soundbar powers off completely.

Because the JBL Bar 3.1 has a high digital amplitude threshold on its noise gate, standard `-90 dBFS` dither is ignored by the bar.

---

## How This App Solves It

1. **JBL "Orpheus" Ultra-Slow Drift Waveform (`~0.02 Hz`):**
   - Uses the proven mathematical waveform from [`silvanocerza/orpheus`](https://github.com/silvanocerza/orpheus):
     ```java
     short sample = (short) (Math.sin(idx * Math.PI / 1000000.0) * amplitude);
     ```
   - Because the signal drifts at `~0.02 Hz` (well below the JBL Bar 3.1 subwoofer's 35 Hz physical high-pass filter), **it produces zero audible sound and zero speaker cone oscillation**, while presenting non-zero digital PCM values (`10000` / `-10 dBFS` by default) that keep the JBL DSP continuously locked and awake.
2. **Zero Audio Focus Interference:**
   - Streams via Android's `AudioTrack` (`USAGE_MEDIA` / `CONTENT_TYPE_MUSIC`) **without ever calling `AudioManager.requestAudioFocus()`**.
   - Android's `AudioFlinger` mixer blends the inaudible signal underneath **YouTube, Netflix, Disney+, Plex, Kodi, Spotify**, and system sounds without ever pausing, ducking, or interrupting any foreground app.
3. **Smart TV Screen-Off Standby Handling:**
   - Listens for `Intent.ACTION_SCREEN_OFF` and `Intent.ACTION_SCREEN_ON`.
   - By default, when you turn off your Chromecast/TV with the remote, the app pauses its keep-alive stream so your JBL Bar 3.1 can still enter its normal 10-minute auto-standby. The moment you turn the Chromecast back on, the keep-alive stream resumes immediately.
4. **Native Android TV / Google TV D-Pad Interface & Auto-Boot:**
   - Appears directly in the Google TV / Android TV **Apps** row (`LEANBACK_LAUNCHER`).
   - Starts automatically on Chromecast boot (`RECEIVE_BOOT_COMPLETED`).

---

## Configurable Settings (via Chromecast Remote)

Open **JBL Bar Keep-Alive** on your TV to configure:

- **Keep-Alive Service:** `ENABLED` / `DISABLED`
- **Signal Waveform:**
  - `JBL Orpheus (~0.02 Hz Zero-Sound Drift)` *(Default — recommended for JBL Bar 3.1)*
  - `Infrasonic 10 Hz (Sub-Bass Cutoff)` *(Below the 35 Hz subwoofer roll-off)*
  - `Ultrasonic 21 kHz (High-Freq)`
- **Digital Level (16-bit PCM Amplitude):**
  - `JBL Bar 3.1 Standard (PCM 10000 / Orpheus)` *(Default)*
  - `Medium (PCM 2500 / -22 dBFS)`
  - `Low (PCM 500 / -36 dBFS)`
  - `Ultra-Low (PCM 100 / -50 dBFS)`
- **Pause When TV/Screen is Off:** `YES` *(default)* / `NO (Always On 24/7)`
- **Auto-Start on Chromecast Boot:** `YES` *(default)* / `NO`

---

## Installation on Chromecast 4K / Android TV (via Wireless ADB)

Download the prebuilt **`JBLBarKeepAlive.apk`** from the repository root (or [Releases](../../releases)):

### 1. Enable Wireless Debugging on Chromecast
1. On Chromecast, open **Settings → System → About**, scroll down to **Android TV OS build**, and press **OK** **7 times** until Developer Options are unlocked.
2. Go to **Settings → System → Developer options** and enable **Wireless debugging** (or **USB debugging**).
3. Select **Pair device with pairing code** to view the IP address, port, and 6-digit PIN.

### 2. Pair, Install & Whitelist via ADB
```bash
# 1. Pair with Chromecast (use the IP:Port and PIN shown on TV)
adb pair 192.168.X.X:XXXXX 123456

# 2. Connect to Chromecast's Wireless Debugging port
adb connect 192.168.X.X:YYYYY

# 3. Install the APK
adb install -r JBLBarKeepAlive.apk

# 4. Exempt from Android TV Doze / background power restrictions
adb shell cmd deviceidle whitelist +com.kidman.jblkeepalive

# 5. Launch the app once to activate the Foreground Service
adb shell am start -n com.kidman.jblkeepalive/.MainActivity
```

---

## Building from Source

This project requires **zero Gradle overhead** and builds in ~2 seconds using a standard JDK and Android `build-tools` (`aapt2`, `d8`, `zipalign`, `apksigner`).

```bash
chmod +x build.sh
./build.sh
```
*(If `ANDROID_HOME` is not set, `build.sh` automatically downloads the minimal Linux Android `build-tools` and `platform-34` packages into `.sdk/`.)*

---

## Project Structure

```text
├── AndroidManifest.xml                            # Android TV Leanback & ForegroundService manifest
├── JBLBarKeepAlive.apk                            # Prebuilt signed APK (21 KB)
├── build.sh                                       # Standalone reproducible build script
├── res/
│   └── drawable/
│       ├── banner.xml                             # Android TV Leanback 320x180 app banner
│       └── ic_launcher.xml                        # App icon vector drawable
└── src/com/kidman/jblkeepalive/
    ├── BootReceiver.java                          # Auto-starts service on Chromecast boot / update
    ├── KeepAlivePrefs.java                        # SharedPreferences state & waveform presets
    ├── KeepAliveService.java                      # Foreground AudioTrack generator & screen listener
    └── MainActivity.java                          # D-Pad friendly Android TV configuration UI
```

## License

[MIT](./LICENSE)
