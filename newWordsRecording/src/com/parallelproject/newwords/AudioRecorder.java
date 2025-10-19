package com.parallelproject.newwords;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;


public class AudioRecorder {
    private static final int BUFFER_SIZE = 4096;
    private ByteArrayOutputStream recordBytes;
    private TargetDataLine audioLine;
    private AudioFormat format;

    private boolean isRunning;
    AudioFormat getAudioFormat() {
        float sampleRate = 44100;
        int sampleSizeInBits = 16;
        int channels = 1;
        boolean signed = true;
        boolean bigEndian = false;
        // Mono, 16-bit PCM, little-endian is what Whisper/FasterWhisper need

        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }

    public void start() throws  LineUnavailableException {
        format = getAudioFormat();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("The system does not support the specified format.");
        }

        audioLine = AudioSystem.getTargetDataLine(format);

        audioLine.open(format);
        audioLine.start();

        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead = 0;

        recordBytes = new ByteArrayOutputStream();
        isRunning = true;

        while (isRunning) {
            bytesRead = audioLine.read(buffer, 0, buffer.length);
            recordBytes.write(buffer, 0, bytesRead);
        }
    }

    public void stop() throws IOException {
        isRunning = false;

        if (audioLine != null) {
            audioLine.drain();
            audioLine.close();
        }
    }

    public void save(File wavfile) throws IOException {
        byte[] audioData = recordBytes.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
        AudioInputStream audioInputStream = new AudioInputStream(bais, format, audioData.length / format.getFrameSize());
        AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, wavfile);

        audioInputStream.close();
        recordBytes.close();
    }

}
