package com.example.network

object PcScriptProvider {

    const val SCRIPT_FILENAME = "pomidor_pc_overlay.py"

    fun generatePythonScript(defaultPort: Int = 8765, isRu: Boolean = false): String {
        val docUsage = if (isRu) {
            "Запуск:\n  Ubuntu:  sudo apt install python3-tk && python3 pomidor_pc_overlay.py --port $defaultPort\n  Windows: python pomidor_pc_overlay.py --port $defaultPort\nЗакрывается ОДНИМ нажатием клавиши Esc (или --strict для блокировки Esc)."
        } else {
            "Run:\n  Ubuntu:  sudo apt install python3-tk && python3 pomidor_pc_overlay.py --port $defaultPort\n  Windows: python pomidor_pc_overlay.py --port $defaultPort\nCloses on a SINGLE Esc key press (or pass --strict to lock Esc)."
        }
        val reportSavedLog = if (isRu) "Отчёт сохранён" else "Report saved"
        val udpListenLog = if (isRu) "Слушаю 0.0.0.0" else "Listening on 0.0.0.0"
        val udpBreakLog = if (isRu) "Сигнал перерыва от" else "Break signal from"
        val udpErrLog = if (isRu) "Ошибка пакета" else "Packet error"
        val overlayTitle = if (isRu) "Pomidor Cam — Отдых" else "Pomidor Cam — Break"
        val overlayBadge = if (isRu) "POMIDOR CAM • ВРЕМЯ ОТДЫХА" else "POMIDOR CAM • BREAK TIME"
        val defaultBreakMsg = if (isRu) {
            "Помидор закончился! Отдых {break_minutes} минут."
        } else {
            "Pomodoro completed! Take a {break_minutes}-minute break."
        }
        val tipText = if (isRu) {
            "Отойдите от монитора, посмотрите вдаль и разомните спину."
        } else {
            "Step away from the screen, look into the distance, and stretch your back."
        }
        val closeBtnText = if (isRu) "Закрыть заставку (1× Esc)" else "Close overlay (1× Esc)"
        val serverStartedLog = if (isRu) "Сервер запущен на порту" else "Server started on port"
        val reportsDirLog = if (isRu) "Папка отчётов" else "Reports directory"

        return """#!/usr/bin/env python3
\"\"\"
Pomidor Cam — PC Bridge & Fullscreen Break Overlay (Ubuntu / Windows)
$docUsage
\"\"\"

import argparse
import json
import os
import queue
import socket
import sys
import threading
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import tkinter as tk

EVENT_QUEUE = queue.Queue()
REPORTS_DIR = os.path.join(os.getcwd(), "pomidor_reports")


def save_workday_report(payload: dict) -> dict:
    os.makedirs(REPORTS_DIR, exist_ok=True)
    workday_date = str(payload.get("workday_date") or datetime.now().strftime("%Y-%m-%d"))
    stamp = datetime.now().strftime("%H%M%S")
    base_name = f"pomidor_report_{workday_date}_{stamp}"
    json_path = os.path.join(REPORTS_DIR, f"{base_name}.json")
    md_path = os.path.join(REPORTS_DIR, f"{base_name}.md")

    with open(json_path, "w", encoding="utf-8") as fj:
        json.dump(payload, fj, ensure_ascii=False, indent=2)

    markdown_text = payload.get("markdown", "")
    if markdown_text:
        with open(md_path, "w", encoding="utf-8") as fm:
            fm.write(markdown_text)

    print(f"[Pomidor Cam] $reportSavedLog:\n  JSON: {json_path}\n  MD:   {md_path}")
    return {"status": "saved", "json_file": json_path, "md_file": md_path}


class PomidorHttpHandler(BaseHTTPRequestHandler):
    def _send_json(self, code: int, obj: dict):
        data = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path.startswith("/pomodoro/ping"):
            self._send_json(200, {"status": "ok", "app": "Pomidor Cam PC Bridge"})
        else:
            self._send_json(404, {"error": "not_found"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length) if length > 0 else b"{}"
        try:
            payload = json.loads(raw.decode("utf-8"))
        except Exception as exc:
            self._send_json(400, {"error": f"invalid_json: {exc}"})
            return

        if self.path.startswith("/pomodoro/break"):
            EVENT_QUEUE.put(("break", payload))
            self._send_json(200, {"status": "break_overlay_queued"})
        elif self.path.startswith("/pomodoro/report"):
            res = save_workday_report(payload)
            self._send_json(200, res)
        else:
            self._send_json(404, {"error": "unknown_endpoint"})

    def log_message(self, format, *args):
        print(f"[HTTP] {self.address_string()} - {format % args}")


def udp_listener(port: int):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(("0.0.0.0", port))
    print(f"[UDP] $udpListenLog:{port}")
    while True:
        try:
            data, addr = sock.recvfrom(65535)
            payload = json.loads(data.decode("utf-8"))
            if payload.get("event") == "pomodoro_completed":
                print(f"[UDP] $udpBreakLog {addr[0]}")
                EVENT_QUEUE.put(("break", payload))
        except Exception as exc:
            print(f"[UDP] $udpErrLog: {exc}")


def show_fullscreen_break(root: tk.Tk, break_minutes: int, message: str, strict: bool):
    overlay = tk.Toplevel(root)
    overlay.title("$overlayTitle")
    overlay.configure(bg="#06080D")
    overlay.attributes("-fullscreen", True)
    overlay.attributes("-topmost", True)

    total_sec = max(1, int(break_minutes) * 60)
    remaining = {"sec": total_sec}

    container = tk.Frame(overlay, bg="#06080D")
    container.place(relx=0.5, rely=0.5, anchor="center")

    badge = tk.Label(
        container,
        text="$overlayBadge",
        font=("Helvetica", 16, "bold"),
        fg="#4D94FF",
        bg="#121A28",
        padx=22,
        pady=8,
    )
    badge.pack(pady=(0, 28))

    title_lbl = tk.Label(
        container,
        text=message or f"$defaultBreakMsg",
        font=("Helvetica", 28, "bold"),
        fg="#F3F6FC",
        bg="#06080D",
        wraplength=900,
        justify="center",
    )
    title_lbl.pack(pady=(0, 18))

    timer_lbl = tk.Label(
        container,
        text="",
        font=("Courier", 96, "bold"),
        fg="#4D94FF",
        bg="#06080D",
    )
    timer_lbl.pack(pady=(8, 24))

    tip_lbl = tk.Label(
        container,
        text="$tipText",
        font=("Helvetica", 18),
        fg="#93A5C4",
        bg="#06080D",
    )
    tip_lbl.pack(pady=(0, 36))

    def close_overlay(_event=None):
        try:
            root.unbind_all("<Escape>")
            root.unbind_all("<KeyPress-Escape>")
        except Exception:
            pass
        if overlay.winfo_exists():
            try:
                overlay.grab_release()
            except Exception:
                pass
            overlay.destroy()
        return "break"

    if not strict:
        btn = tk.Button(
            container,
            text="$closeBtnText",
            font=("Helvetica", 13, "bold"),
            fg="#F3F6FC",
            bg="#0F52BA",
            activebackground="#4D94FF",
            activeforeground="#FFFFFF",
            relief="flat",
            padx=24,
            pady=12,
            command=close_overlay,
        )
        btn.pack()
        overlay.bind("<Escape>", close_overlay)
        overlay.bind("<KeyPress-Escape>", close_overlay)
        root.bind_all("<Escape>", close_overlay)
        root.bind_all("<KeyPress-Escape>", close_overlay)
    else:
        overlay.protocol("WM_DELETE_WINDOW", lambda: None)

    def ensure_focus():
        if overlay.winfo_exists():
            overlay.lift()
            overlay.attributes("-topmost", True)
            overlay.focus_force()
            try:
                overlay.grab_set()
            except Exception:
                pass

    # Post-map focus reinforcement so Ubuntu GNOME X11/Wayland captures the very first Esc press
    overlay.update_idletasks()
    ensure_focus()
    overlay.after(60, ensure_focus)
    overlay.after(220, ensure_focus)

    def tick():
        if not overlay.winfo_exists():
            return
        sec = remaining["sec"]
        mins, secs = divmod(sec, 60)
        timer_lbl.config(text=f"{mins:02d}:{secs:02d}")
        if sec <= 0:
            close_overlay()
            return
        remaining["sec"] -= 1
        overlay.after(1000, tick)

    tick()


def main():
    parser = argparse.ArgumentParser(description="Pomidor Cam PC Overlay & Report Receiver")
    parser.add_argument("--port", type=int, default=$defaultPort, help="HTTP/UDP port")
    parser.add_argument("--strict", action="store_true", help="Disable Esc exit during break")
    args = parser.parse_args()

    http_server = ThreadingHTTPServer(("0.0.0.0", args.port), PomidorHttpHandler)
    threading.Thread(target=http_server.serve_forever, daemon=True).start()
    threading.Thread(target=udp_listener, args=(args.port,), daemon=True).start()

    print(f"[Pomidor Cam] $serverStartedLog {args.port}")
    print(f"[Pomidor Cam] $reportsDirLog: {REPORTS_DIR}")

    root = tk.Tk()
    root.withdraw()

    def poll_events():
        while not EVENT_QUEUE.empty():
            kind, payload = EVENT_QUEUE.get_nowait()
            if kind == "break":
                mins = int(payload.get("break_minutes", 5))
                msg = str(payload.get("message", ""))
                show_fullscreen_break(root, mins, msg, args.strict)
        root.after(200, poll_events)

    root.after(200, poll_events)
    try:
        root.mainloop()
    except KeyboardInterrupt:
        sys.exit(0)


if __name__ == "__main__":
    main()
""".trimIndent()
    }
}
