import javax.sound.sampled.*;
import java.util.concurrent.*;

public class AudioRecorder {

    private static final int AUDIO_CHUNK_LENGTH_IN_SECONDS = 5; // chunk length
    private static final float SAMPLE_RATE = 16000.0f;

    private final AudioFormat format;
    private TargetDataLine microphone;
    private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
    private final ExecutorService senderPool = Executors.newFixedThreadPool(2); // send concurrently
    private volatile boolean running = false;

    public AudioRecorder() {
        format = AudioFormatConfig.getFormat();
    }

    public void start() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        microphone = (TargetDataLine) AudioSystem.getLine(info);
        microphone.open(format);
        running = true;
        microphone.start();
        int bytesPerSecond = (int) (SAMPLE_RATE * format.getFrameSize());
        int chunkBytes = bytesPerSecond * AUDIO_CHUNK_LENGTH_IN_SECONDS;

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
