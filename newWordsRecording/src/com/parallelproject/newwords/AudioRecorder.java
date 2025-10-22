package com.parallelproject.newwords;

import java.util.concurrent.*;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;


public class AudioRecorder {
    private static final int AUDIO_CHUNK_LENGTH_IN_SECONDS = 5;
    private static final float SAMPLE_RATE = 16000.0f;

    private final ExecutorService senderPool = Executors.newFixedThreadPool(2);

    private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();

    private TargetDataLine microphone;
    private final AudioFormat format;

    private volatile boolean isRunning = false;

    public AudioRecorder() {
        format = AudioFormatConfig.getFormat();
    }

    public void start() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        microphone = (TargetDataLine) AudioSystem.getLine(info);
        microphone.open(format);
        isRunning = true;
        microphone.start();

        int bytesPerSecond = (int) (SAMPLE_RATE * format.getFrameSize());
        int chunkBytes = bytesPerSecond * AUDIO_CHUNK_LENGTH_IN_SECONDS;

        ProducerThread producerRunnable = new ProducerThread(chunkBytes, queue, microphone);
        Thread producer = new Thread(producerRunnable, "audio-producer");
        producer.setDaemon(true);
        producer.start();

        ConsumerThread consumerRunnable = new ConsumerThread(chunkBytes, queue, senderPool);
        Thread consumer = new Thread(consumerRunnable, "audio-consumer");
        consumer.setDaemon(true);
        consumer.start();

        System.out.println("Recording started.");
    }


    public void stop() {
        isRunning = false;
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
