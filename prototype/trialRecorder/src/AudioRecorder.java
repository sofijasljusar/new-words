import javax.sound.sampled.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

public class AudioRecorder {

    private static final int CHUNK_SECONDS = 5; // chunk length
    private static final float SAMPLE_RATE = 16000.0f;
    private static final int SAMPLE_SIZE_BITS = 16;
    private static final int CHANNELS = 1; // mono
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false; // little endian false

    private final AudioFormat format;
    private TargetDataLine microphone;
    private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
    private final ExecutorService senderPool = Executors.newFixedThreadPool(2); // send concurrently
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();
    private volatile boolean running = false;

    public AudioRecorder() {
        format = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS, SIGNED, BIG_ENDIAN);
    }

    public void start() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        microphone = (TargetDataLine) AudioSystem.getLine(info);
        microphone.open(format);
        running = true;
        microphone.start();

        // Producer thread - reads raw bytes continuously and splits into chunks
        Thread producer = new Thread(() -> {
            int bytesPerSecond = (int) (SAMPLE_RATE * format.getFrameSize());
            int chunkBytes = bytesPerSecond * CHUNK_SECONDS;
            byte[] buffer = new byte[chunkBytes];
            ByteArrayOutputStream rolling = new ByteArrayOutputStream(chunkBytes);
            try {
                while (running) {
                    int read = microphone.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        // we will push exact-sized chunk
                        // copy to avoid reuse
                        byte[] chunkCopy = new byte[read];
                        System.arraycopy(buffer, 0, chunkCopy, 0, read);
                        queue.put(chunkCopy);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Producer interrupted");
            }
        }, "audio-producer");
        producer.setDaemon(true);
        producer.start();

        // Consumer thread - assemble consecutive small reads until CHUNK_SECONDS total, then send
        Thread consumer = new Thread(() -> {
            int bytesPerSecond = (int) (SAMPLE_RATE * format.getFrameSize());
            int chunkBytes = bytesPerSecond * CHUNK_SECONDS;
            try {
                while (running || !queue.isEmpty()) {
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
                    byte[] bigChunk = bout.toByteArray();
                    if (bigChunk.length > 0) {
                        // submit to sender pool
                        senderPool.submit(() -> {
                            try {
                                File wavFile = writeWavFile(bigChunk);
                                Thread.sleep(200);
                                sendToPython(wavFile);
                                // optionally delete after sending
                                wavFile.delete();
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        });
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, "audio-consumer");
        consumer.setDaemon(true);
        consumer.start();

        System.out.println("Recording started.");
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

    public void stop() {
        running = false;
        if (microphone != null) {
            microphone.stop();
            microphone.close();
        }
        senderPool.shutdown();
        try {
            senderPool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("Recorder stopped.");
    }

    public static void main(String[] args) throws Exception {
        AudioRecorder recorder = new AudioRecorder();
        recorder.start();

        System.out.println("Recording... Press ENTER to stop.");
        System.in.read();

        recorder.stop();
    }
}
