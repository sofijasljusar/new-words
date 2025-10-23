package com.parallelproject.newwords;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class WavFileWriter {
    private final AudioFormat format;

    public WavFileWriter(AudioFormat format) {
        this.format = format;
    }

    public File writeWavFile(byte[] pcmData) throws IOException {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File out = new File("chunk_" + ts + ".wav");
        try (ByteArrayInputStream bais = new ByteArrayInputStream(pcmData);
             AudioInputStream ais = new AudioInputStream(bais, format, pcmData.length / format.getFrameSize())) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, out);
        }
        System.out.println("Wrote " + out.getName() + " (" + pcmData.length + " bytes)");
        return out;
    }
}
