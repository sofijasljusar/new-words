
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ConsumerThread implements Runnable{
    private volatile boolean running = true;
    private final BlockingQueue<byte[]> queue;
    private final int chunkBytes;
    private final ExecutorService senderPool = Executors.newFixedThreadPool(2); // send concurrently
    private final RequestSender requestSender;
    private final WavFileWriter wavFileWriter;
    private final SilenceDetector silenceDetector;

    public ConsumerThread(int chunkBytes, BlockingQueue<byte[]> queue) {
        this.chunkBytes = chunkBytes;
        this.queue = queue;
        this.requestSender = new RequestSender();
        this.wavFileWriter = new WavFileWriter();
        this.silenceDetector = new SilenceDetector();
    }

    @Override
    public void run() {
        try {
            while (running || !queue.isEmpty()) {

                byte[] bigChunk = collectSizedChunk();
                if (bigChunk.length > 0) {
                    if (silenceDetector.isSilent(bigChunk)) {
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

    private void submitToSenderPool(byte[] bigChunk) {
        senderPool.submit(() -> {
            try {
                File wavFile = wavFileWriter.writeWavFile(bigChunk);
                requestSender.sendRequest(wavFile);
                wavFile.delete();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }


    /**
     * Stops the consumer thread gracefully by setting the running flag to false.
     * The thread will exit its loop on the next iteration.
     */
    public void stop() {
        running = false;
    }
}
