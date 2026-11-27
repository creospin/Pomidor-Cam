[English](README.md) | [Русский](README.ru.md)

<p align="center">
  <img src="app/src/main/res/drawable/pomidor_logo.png" width="108" alt="Pomidor Cam Logo" />
</p>

<h1 align="center">Pomidor Cam</h1>

<p align="center">
  <strong>AI camera-driven Pomodoro focus tracker for Android with on-device YOLO26 person detection, adaptive flashlight alerts, Ubuntu/Windows fullscreen break overlay, and workday journal export.</strong>
</p>

---

## How It Works

1. **Zero-clutter presence detection:** Every **10 seconds** (configurable `5–60s`), Pomidor Cam captures a camera frame in the background—even with the screen turned off—and checks whether a person is at the desk.
2. **Silent focus start:** When no one is in frame, the timer stays idle. As soon as you sit down, your **20-minute** focus timer (configurable `1–120 min`) starts silently without distracting popups.
3. **Away tolerance:** Briefly turning away or stepping out is controlled by a configurable **miss tolerance (`1–5` consecutive missed checks)** before an active session is marked interrupted.
4. **End-of-Pomodoro alerts:** Completing a continuous focus session triggers two synchronized signals:
   - **Flashlight blink:** Blinks **3 times** (configurable count `1–5` and brightness **`10%–100%`** via Android 13+ `turnOnTorchWithStrengthLevel` or adaptive pulse-width duration on Android 11–12 / Helio G95).
   - **LAN break overlay:** Sends an `HTTP POST` (`/pomodoro/break`) and `UDP` signal over your local Wi-Fi to show a fullscreen **5-minute break** screensaver on your **Ubuntu** or **Windows** PC.

---

## Key Features

- **Infinix Note 10 Pro & Multi-Camera Ready:**
  - Dedicated Camera2 + CameraX discovery (`CameraCatalog`) supporting Front (`16MP`), Main (`64MP`), Ultrawide physical/probed sensors, and wide `0.6×` min-zoom fallback.
  - Tuned for MediaTek Helio G95 / 8 GB RAM with a foreground wake-lock monitoring service (`PomidorMonitorService`).
- **On-Device YOLO26 + Built-in Detector Fallback:**
  - Supports custom imported **YOLO26 `.onnx`** (ONNX Runtime) and **`.tflite`** models via Android's file picker, plus a built-in on-device detector fallback (`Auto` mode).
  - Adjustable detection confidence threshold (`20%–90%`).
- **Workday Journal (`06:00 – 04:00`) & Auto-Export:**
  - Records every session—both completed continuous Pomodoros and interrupted/manual sessions—grouped into a configurable night-owl workday window (`06:00` to `04:00` next morning).
  - Exports structured **JSON + Markdown** full workday reports directly to your PC over LAN, or via Share / Save to file.
  - **Automatic Morning Export:** Scheduled WorkManager job (`MorningReportWorker`) uploads the previous workday's report once your PC comes online after your configured morning hour (default `08:00`).
- **English by Default + Russian:**
  - Opens in **English** by default with instant in-app switching between **English**, **Русский**, and **System (EN/RU)** in **Settings**.
- **PC Companion Script (`pomidor_pc_overlay.py`):**
  - Lightweight pure-Python (`tkinter` + stdlib `http.server` / `socket`) bridge generated right inside the **PC Bridge** tab.
  - Closes on a **single `Esc` press** (or run with `--strict` to lock `Esc` during breaks) and saves incoming reports to `./pomidor_reports/`.

---

## YOLO26 Model Export Guide

You can import your own exported YOLO26 weights directly inside **Settings → AI Detector → Import `.onnx` / `.tflite`**:

```bash
# Recommended for Infinix Note 10 Pro (Helio G95) — fast 10s background inference
yolo export model=yolo26n.pt format=onnx imgsz=640 simplify=True

# Higher accuracy if the phone sits far from your desk (slower per frame)
yolo export model=yolo26s.pt format=onnx imgsz=640 simplify=True
```

> **Note on `yolo26m` / `imgsz=1280`:** Exporting larger models (`yolo26m` / `yolo26l`) at `imgsz=1280` improves small-object accuracy at a distance, but significantly increases CPU inference time and heat on mid-range mobile SoCs like Helio G95. For single-person desk presence every 10 seconds, `yolo26n` or `yolo26s` at `640` provides the best balance of speed, battery life, and accuracy.

---

## Ubuntu / Windows PC Setup

Save `pomidor_pc_overlay.py` from the **PC Bridge** tab in the app (Save / Copy / Share) and run it on your computer on the same local network:

### Ubuntu / Linux
```bash
sudo apt install python3-tk
python3 pomidor_pc_overlay.py --port 8765
```

### Windows
```powershell
python pomidor_pc_overlay.py --port 8765
```

Optional flags:
- `--port 8765` — HTTP & UDP listening port (must match the port in **PC Bridge**).
- `--strict` — Disables closing the break overlay with `Esc`.

In the app's **PC Bridge** tab, enter your PC's local IP (e.g., `192.168.1.100`), tap **Save**, and verify connectivity with **Ping PC** or **Test Overlay**.

---

## Building the Android App

- **Requirements:** JDK 17+, Android SDK (Compile SDK 36, Min SDK 26), Gradle.
- **Tech Stack:** Kotlin, Jetpack Compose (Material 3), CameraX + Camera2, Room, DataStore, WorkManager, ONNX Runtime Android, LiteRT/TFLite, ML Kit Object Detection.

```bash
# Build debug APK
gradle assembleDebug

# Run JVM unit tests
gradle :app:testDebugUnitTest \
  --tests "com.example.PomodoroSessionEngineTest" \
  --tests "com.example.WorkdayClockAndReportTest" \
  --tests "com.example.Yolo26DecoderTest" \
  --tests "com.example.CameraCatalogAndTorchTest" \
  --tests "com.example.MorningAutoExportTest"
```
