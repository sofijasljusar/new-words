package com.parallelproject.newwords;

import java.util.concurrent.*;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;


public class AudioRecorder {
    private TargetDataLine microphone;
    private final AudioFormat format = AudioFormatConfig.getFormat();

    private final ExecutorService senderPool = Executors.newFixedThreadPool(2);
    private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();

    private final int audioChunkLengthInBytes = getAudioChunkLengthInBytes();

    private int getAudioChunkLengthInBytes() {
        int AUDIO_CHUNK_LENGTH_IN_SECONDS = 5;
        float SAMPLE_RATE = 16000.0f;
        int bytesPerSecond = (int) (SAMPLE_RATE * format.getFrameSize());
        int chunkBytes = bytesPerSecond * AUDIO_CHUNK_LENGTH_IN_SECONDS;
        return chunkBytes;
    }

    public static void main(String[] args) throws Exception {
        AudioRecorder recorder = new AudioRecorder();
        recorder.start();

        System.out.println("Recording... Press ENTER to stop.");
        System.in.read();

        recorder.stop();
    }

    public void start() throws LineUnavailableException {
        startMicrophone();

        startProducerThread();
        startConsumerThread();

        System.out.println("Recording started.");
    }

    private void startMicrophone() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        microphone = (TargetDataLine) AudioSystem.getLine(info);
        microphone.open(format);
        microphone.start();
    }

    private void startProducerThread() {
        ProducerThread producerRunnable = new ProducerThread(audioChunkLengthInBytes, queue, microphone);
        Thread producer = new Thread(producerRunnable, "audio-producer");
        producer.setDaemon(true);
        producer.start();
    }

    private void startConsumerThread() {
        WavFileWriter wavFileWriter = new WavFileWriter(format);
        RequestSender requestSender = new RequestSender();
        SilenceDetector silenceDetector = new SilenceDetector();

        ConsumerThread consumerRunnable = new ConsumerThread(audioChunkLengthInBytes, queue, senderPool, wavFileWriter, silenceDetector, requestSender);
        Thread consumer = new Thread(consumerRunnable, "audio-consumer");
        consumer.setDaemon(true);

        consumer.start();
    }

    public void stop() {
        stopMicrophone();
        waitForQueueToDrain();
        shutdownSenderPool();
        System.out.println("Recorder stopped.");
    }

    private void stopMicrophone() {
        if (microphone != null) {
            microphone.stop();
            microphone.close();
        }
    }

    private void waitForQueueToDrain() {
        while (!queue.isEmpty()) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void shutdownSenderPool() {
        senderPool.shutdown();
        try {
            if (!senderPool.awaitTermination(10, TimeUnit.SECONDS)) {
                senderPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            senderPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }


}
