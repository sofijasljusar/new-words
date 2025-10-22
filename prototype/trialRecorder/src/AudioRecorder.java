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
        format = AudioFormatConfig.getFormat();
    }

    private boolean isSilent(byte[] pcmData, int peakThreshold) { // list of bytes from the mic
        for (int i = 0; i < pcmData.length - 1; i += 2) { // sound sample uses 2 bytes
            // pcmData[i] = first byte (low byte)
            // pcmData[i+1] = second byte (high byte)
            // << 8 means “move this byte 8 bits to the left”
            // & 0xFF → make sure this byte is treated as 0–255 (unsigned)
            // | (bitwise OR) combines the high byte and low byte into one single 16-bit number
            // so, in the line below we combine 2 numbers stored for sound into one to know how loud this moment is
            int sample = (pcmData[i + 1] << 8) | (pcmData[i] & 0xFF);
            if (Math.abs(sample) > peakThreshold) {
                return false;
            }
        }
        return true;
    }

    public void start() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        microphone = (TargetDataLine) AudioSystem.getLine(info);
        microphone.open(format);
        running = true;
        microphone.start();
        int bytesPerSecond = (int) (SAMPLE_RATE * format.getFrameSize());
        int chunkBytes = bytesPerSecond * CHUNK_SECONDS;

        // Producer thread - reads raw bytes continuously and splits into chunks
        ProducerThread producerRunnable = new ProducerThread(chunkBytes, queue, microphone);
        Thread producer = new Thread(producerRunnable, "audio-producer");
        producer.setDaemon(true);
        producer.start();

        ConsumerThread consumerRunnable = new ConsumerThread(chunkBytes, queue);
        Thread consumer = new Thread(consumerRunnable, "audio-consumer");

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

        while (!queue.isEmpty()) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        senderPool.shutdown();

        try {
            if (!senderPool.awaitTermination(10, TimeUnit.SECONDS)) {
                senderPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            senderPool.shutdownNow();
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
