import tkinter as tk
import threading
import requests
import time

API_URL = "http://127.0.0.1:8000/transcribe"

class TranscriptionGUI:
    def __init__(self, master):
        self.master = master
        master.title("Live Transcription")

        self.text = tk.Text(master, height=20, width=80)
        self.text.pack()

        # Start polling thread
        self.running = True
        threading.Thread(target=self.poll_transcription, daemon=True).start()

    def poll_transcription(self):
        last_transcript = ""
        while self.running:
            try:
                response = requests.get("http://127.0.0.1:8000/latest")  # implement this endpoint
                if response.status_code == 200:
                    data = response.json()
                    transcript = data.get("transcript", "")
                    if transcript != last_transcript:
                        self.text.insert(tk.END, transcript + "\n")
                        self.text.see(tk.END)
                        last_transcript = transcript
                time.sleep(0.5)  # poll every 0.5 seconds
            except Exception as e:
                print("Error polling:", e)
                time.sleep(1)

    def stop(self):
        self.running = False


if __name__ == "__main__":
    root = tk.Tk()
    gui = TranscriptionGUI(root)
    try:
        root.mainloop()
    finally:
        gui.stop()
