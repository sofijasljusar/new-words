package com.parallelproject.newwords;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ConsumerThread implements Runnable{
    private volatile boolean running = true;
    private final BlockingQueue<byte[]> queue;
    private final int chunkBytes;
    private final ExecutorService senderPool = Executors.newFixedThreadPool(2); // send concurrently
    private final HttpClient httpClient;
    private final AudioFormat format;

    public ConsumerThread(int chunkBytes, BlockingQueue<byte[]> queue) {
        this.chunkBytes = chunkBytes;
        this.queue = queue;
        this.format = AudioFormatConfig.getFormat();
        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }


    @Override
    public void run() {
        try {
            while (running || !queue.isEmpty()) {

                byte[] bigChunk = collectSizedChunk();
                if (bigChunk.length > 0) {
                    if (isSilent(bigChunk)) {
                        System.out.println("Chunk skipped (silence detected)");
                        continue;
                    }

                    submitToSenderPool(bigChunk);


                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] collectSizedChunk() throws InterruptedException, IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream(chunkBytes);
        int collected = 0;
        while (collected < chunkBytes) {
            byte[] piece = queue.poll(1, TimeUnit.SECONDS);
            if (piece == null) {
                if (!running) break;
                continue;
            }
            bout.write(piece);
            collected += piece.length;
        }
        return bout.toByteArray();
    }

    private boolean isSilent(byte[] pcmData) { // pcmData = list of bytes from the mic
        for (int i = 0; i < pcmData.length - 1; i += 2) { // sound sample uses 2 bytes
            // pcmData[i] = first byte (low byte)
            // pcmData[i+1] = second byte (high byte)
            // << 8 means “move this byte 8 bits to the left”
            // & 0xFF makes sure this byte is treated as 0–255 (unsigned)
            // | (bitwise OR) combines the high byte and low byte into one single 16-bit number
            // so, in the line below we combine 2 numbers stored for sound into one to know how loud this moment is
            int sample = (pcmData[i + 1] << 8) | (pcmData[i] & 0xFF);
            if (Math.abs(sample) > 1000) {  // peak threshold
                return false;
            }
        }
        return true;
    }

    private void submitToSenderPool(byte[] bigChunk) {
        senderPool.submit(() -> {
            try {
                File wavFile = writeWavFile(bigChunk);
                Thread.sleep(200);
                sendToPython(wavFile);
                wavFile.delete();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    private File writeWavFile(byte[] pcmData) throws IOException {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File out = new File("chunk_" + ts + ".wav");
        try (ByteArrayInputStream bais = new ByteArrayInputStream(pcmData);
             AudioInputStream ais = new AudioInputStream(bais, format, pcmData.length / format.getFrameSize())) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, out);
        }
        System.out.println("Wrote " + out.getName() + " (" + pcmData.length + " bytes)");
        return out;
    }

    private void sendToPython(File wavFile) throws IOException, InterruptedException {
        byte[] wavBytes = java.nio.file.Files.readAllBytes(wavFile.toPath());
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8000/transcribe?autolearn=true"))
                .header("Content-Type", "audio/wav")
                .POST(HttpRequest.BodyPublishers.ofByteArray(wavBytes))
                .build();
        System.out.println("Sending chunk to Python...");
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("Python response: " + resp.statusCode() + " " + resp.body());
    }


    /**
     * Stops the consumer thread gracefully by setting the running flag to false.
     * The thread will exit its loop on the next iteration.
     */
    public void stop() {
        running = false;
    }
}
