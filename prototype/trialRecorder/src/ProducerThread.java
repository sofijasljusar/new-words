
import javax.sound.sampled.TargetDataLine;
import java.util.concurrent.BlockingQueue;


/**
 * ProducerThread continuously reads audio data from a TargetDataLine (microphone)
 * and pushes it into a BlockingQueue for consumption by a consumer thread.
 * <p>
 * Each read produces a chunk of raw PCM audio bytes of a specified size (chunkBytes),
 * which is copied into the queue. This class implements Runnable and is intended
 * to be run in its own thread.
 */
public class ProducerThread implements Runnable{
    /** Queue to store audio chunks for the consumer thread */
    private final BlockingQueue<byte[]> queue;
    /** Microphone input line, source of raw audio data */
    private final TargetDataLine microphone;
    /** Temporary array to hold one chunk of audio read from the microphone before it’s copied to the queue */
    byte[] buffer;
    private volatile boolean running = true;


    /**
     * Constructs a ProducerThread.
     *
     * @param chunkBytes the size in bytes of each audio chunk to read
     * @param queue      the BlockingQueue to put audio chunks into
     * @param microphone the TargetDataLine (microphone) to read from
     */
    public ProducerThread(int chunkBytes, BlockingQueue<byte[]> queue, TargetDataLine microphone) {
        this.queue = queue;
        this.microphone = microphone;
        this.buffer = new byte[chunkBytes];
    }


    @Override
    public void run() {
        try {
            while (running) {
                int read = microphone.read(buffer, 0, buffer.length);
                if (read > 0) {
                    byte[] chunkCopy = new byte[read];
                    System.arraycopy(buffer, 0, chunkCopy, 0, read);
                    queue.put(chunkCopy);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Producer interrupted");
        }
    }


    /**
     * Stops the producer thread gracefully by setting the running flag to false.
     * The thread will exit its loop on the next iteration.
     */
    public void stop() {
        running = false;
    }

}
