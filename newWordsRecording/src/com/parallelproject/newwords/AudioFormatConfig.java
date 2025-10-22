package com.parallelproject.newwords;

import javax.sound.sampled.AudioFormat;

public class AudioFormatConfig {
    public static final float SAMPLE_RATE = 16000.0f;
    public static final int SAMPLE_SIZE_BITS = 16;
    public static final int CHANNELS = 1;
    public static final boolean SIGNED = true;
    public static final boolean BIG_ENDIAN = false;
    // Mono, 16-bit PCM, little-endian is what Whisper/FasterWhisper need

    public static AudioFormat getFormat() {
        return new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS, SIGNED, BIG_ENDIAN);
    }
}