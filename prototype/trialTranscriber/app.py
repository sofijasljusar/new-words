# app.py
from fastapi import FastAPI, Request, HTTPException
from fastapi.responses import JSONResponse
import uvicorn
import tempfile
import os
from datetime import datetime
import csv
import jieba
from pathlib import Path

# Choose your transcription backend:
# Option A (recommended for speed on Apple Silicon): faster-whisper
# Option B: openai/whisper (slower but works). Code uses faster-whisper if available.
try:
    from faster_whisper import WhisperModel
    WHISPER_BACKEND = "faster-whisper"
except Exception:
    WHISPER_BACKEND = "whisper"

# ---- Configuration ----
MODEL_SIZE = "small"   # change to "medium" or "base" depending on your machine
KNOWN_CSV = Path("known_words.csv")  # file in repo root: word,meaning
STOPWORDS = set(["的","是","了","我","你","在","和","有","就"])  # common Chinese stopwords to ignore
latest_transcript = ""
# ------------------------

app = FastAPI(title="Local Transcriber")

# load model lazily (first request) to reduce startup time
model = None

def load_model():
    global model
    if model is not None:
        return model
    print("Loading model...", WHISPER_BACKEND)
    if WHISPER_BACKEND == "faster-whisper":
        # faster-whisper supports running on M1/M2 via ONNX/CTranslate2 or CPU.
        model = WhisperModel(MODEL_SIZE, device="cpu", compute_type="int8")

    else:
        # fallback for openai/whisper
        import whisper
        model = whisper.load_model(MODEL_SIZE)
    return model

def read_known_words():
    known = set()
    if KNOWN_CSV.exists():
        with KNOWN_CSV.open("r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                word = row.get("word")
                if word:
                    known.add(word.strip())
    return known

def append_known_words(new_words_with_meta):
    # new_words_with_meta: list of dicts with keys: word,pinyin,meaning,date
    write_header = not KNOWN_CSV.exists()
    with KNOWN_CSV.open("a", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        if write_header:
            writer.writerow(["word","meaning"])
        for w in new_words_with_meta:
            writer.writerow([w.get("word",""), w.get("meaning","")])

def segment_chinese(text):
    # use jieba to split into words
    return [tok for tok in jieba.lcut(text) if tok.strip()]

@app.post("/transcribe")
async def transcribe(request: Request):
    """
    Receives raw WAV bytes in the request body (Content-Type: audio/wav).
    Returns JSON: { "transcript": "...", "new_words": ["..."] }
    Optional query param ?autolearn=true to append new words to known_words.csv
    """
    global latest_transcript
    print("Headers:", request.headers)
    body = await request.body()
    print("Length of body:", len(body))
    if not body:
        raise HTTPException(status_code=400, detail="Empty body")
    # save to temp wav file
    tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".wav")
    tmp.write(body)
    tmp.close()
    wav_path = tmp.name

    try:
        mdl = load_model()
        # transcription (faster-whisper returns segments and text, openai whisper returns dict)
        if WHISPER_BACKEND == "faster-whisper":
            segments, info = mdl.transcribe(wav_path, beam_size=5)
            transcript = "".join([seg.text for seg in segments])
        else:
            # openai whisper
            result = model.transcribe(wav_path)
            transcript = result["text"]
        latest_transcript = transcript
        # simple segmentation & new-word detection
        known = read_known_words()
        words = segment_chinese(transcript)
        # filter: punctuation, whitespace, stopwords
        candidates = []
        for w in words:
            if len(w.strip()) == 0:
                continue
            if w in STOPWORDS:
                continue
            if w in known:
                continue
            # ignore single-character punctuation or digits optionally
            if len(w.strip()) == 1 and w.isdigit():
                continue
            candidates.append(w)
        # dedupe preserving order
        seen = set()
        new_words = []
        for w in candidates:
            if w not in seen:
                seen.add(w)
                new_words.append(w)

        # optional: return minimal metadata; leave pinyin/meaning empty for now
        now = datetime.utcnow().strftime("%Y-%m-%d")
        new_meta = [{"word": w, "meaning": ""} for w in new_words]

        autolearn = request.query_params.get("autolearn", "false").lower() in ("1","true","yes")
        if autolearn and new_meta:
            append_known_words(new_meta)

        return JSONResponse({"transcript": transcript, "new_words": new_words, "autolearn": autolearn})
    finally:
        try:
            os.remove(wav_path)
        except Exception:
            pass

@app.get("/latest")
async def get_latest():
    return {"transcript": latest_transcript}

if __name__ == "__main__":
    uvicorn.run("app:app", host="0.0.0.0", port=8000, reload=False)
